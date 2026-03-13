
/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Properties;

// Tool to get directory where Python's shared object/dll and where
// Python executable lives and write those to a Properties file specified.
public class GetPythonEnvironment {

    private GetPythonEnvironment() {
    }

    public enum OS {
        WINDOWS,
        LINUX,
        MAC,
        UNKNOWN
    }

    // detect the current OS
    private static OS getCurrentOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("linux")) {
            return OS.LINUX;
        } else if (osName.contains("mac")) {
            return OS.MAC;
        } else if (osName.contains("win")) {
            return OS.WINDOWS;
        } else {
            return OS.UNKNOWN;
        }
    }

    private static final OS THE_OS = getCurrentOS();

    // result from a child Process
    record ExecResult(String output, int exitCode) {
    }

    private static ExecResult runCommand(String... commands) throws IOException, InterruptedException {
        return runCommand(false, commands);
    }

    // run a command in a child process and get its output
    private static ExecResult runCommand(boolean firstLine, String... commands) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(commands);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        // collect all output lines
        var lines = new ArrayList<String>();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        return new ExecResult(firstLine
                ? (lines.isEmpty() ? null : lines.get(0))
                : lines.stream().collect(Collectors.joining("\n")),
                process.waitFor());
    }

    // find a OS command using OS specific command search command
    private static String findCommand(String command) throws IOException, InterruptedException {
        ExecResult execResult;
        if (THE_OS == OS.WINDOWS) {
            execResult = runCommand(true, "where.exe", command);
        } else {
            execResult = runCommand(true, "which", command);
        }
        return execResult.exitCode() == 0 ? execResult.output() : null;
    }

    // check python version is expected minor version or not
    private static boolean checkPythonVersion(String pythonBin, String pythonMinor) throws IOException, InterruptedException {
        // Run 'python --version' and capture output (both stdout and stderr)
        ExecResult result = runCommand(pythonBin, "--version");
        String versionStr = result.output();
        if (versionStr == null || versionStr.trim().isEmpty()) {
            IO.println("Could not parse Python version.");
            return false;
        }

        // Regex to match 'Python 3.14.2' (or similar)
        var pattern = Pattern.compile("Python\\s+([0-9]+)\\.([0-9]+)\\.[0-9]+");
        var matcher = pattern.matcher(versionStr);
        if (matcher.matches()) {
            String major = matcher.group(1);
            String minor = matcher.group(2);

            if ("3".equals(major) && pythonMinor.equals(minor)) {
                IO.println("Python 3." + pythonMinor + ".x detected: " + versionStr);
                return true;
            } else {
                IO.println("Python is not 3." + pythonMinor + ".x: " + versionStr);
                return false;
            }
        } else {
            IO.println("Could not parse Python version.");
            return false;
        }
    }

    private static String getPythonBasePrefix(String pythonBin) throws IOException, InterruptedException {
        // get Python's sys.base_prefix value
        ExecResult execResult = runCommand(pythonBin, "-c", "import sys; print(sys.base_prefix)");
        if (execResult.exitCode != 0) {
            throw new IOException("getting python sys.base_prefix failed");
        }
        return execResult.output();
    }

    public static void main(String[] args) {
        try {
            // should pass version line 3.14 or 3.13 etc. Only major
            // and minor versions. Do not pass 3.13.2 for example!
            if (args.length != 2) {
                System.err.println("java JextractHelper <python-version> <target-file>");
                System.exit(1);
            }

            String[] parts = args[0].split("-")[0].split("\\.");
            String pythonVersion = parts[0] + "." + parts[1];
            Path targetFile = Paths.get(args[1]);
            if (THE_OS == OS.UNKNOWN) {
                System.err.println("Unsupported OS: " + System.getProperty("os.name"));
                System.exit(2);
            }

            // Try python version specified, else 3, else python
            String pythonBin = findCommand("python");
            if (pythonBin == null) {
                pythonBin = findCommand("python3");
            }
            if (pythonBin == null) {
                pythonBin = findCommand("python" + pythonVersion);
            }

            if (pythonBin != null) {
                IO.println("Using Python @ " + pythonBin);
            } else {
                IO.println("Python not found!");
                System.exit(3);
            }

            // make sure that we are on expected python version
            if (!checkPythonVersion(pythonBin, pythonVersion.substring("3.".length()))) {
                System.exit(4);
            }

            String pythonLibDir;
            if (THE_OS == OS.WINDOWS) {
                pythonLibDir = getPythonBasePrefix(pythonBin);
            } else {
                ExecResult execResult = runCommand(pythonBin, "-c",
                        "import sysconfig; print(sysconfig.get_config_var('LIBDIR'))");
                if (execResult.exitCode() != 0) {
                    System.err.println("getting python sysconfig.get_config_var('LIBDIR') failed");
                    System.exit(5);
                }
                pythonLibDir = execResult.output();
            }

            ExecResult execResult = runCommand(pythonBin, "-c",
                        "import sys; print(sys.executable)");
            if (execResult.exitCode() != 0) {
                System.err.println("getting python sys.executable failed");
                System.exit(6);
            }
            String pythonExecutable = execResult.output();

            Properties props = new Properties();
            props.setProperty("python.libdir", pythonLibDir);
            props.setProperty("python.program.name", pythonExecutable);
            targetFile.getParent().toFile().mkdirs();
            try (var writer = Files.newBufferedWriter(targetFile)) {
                props.store(writer, "Python shared object and program locations");
            }
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace(System.err);
            System.exit(5);
        }
    }
}
