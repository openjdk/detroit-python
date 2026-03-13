
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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JextractPython {

    // do not create!
    private JextractPython() {
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

        return new ExecResult(firstLine ?
            (lines.isEmpty() ? null : lines.get(0)) :
            lines.stream().collect(Collectors.joining("\n")),
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

    // post process jextract include options file
    private static void postProcessOptionsFile(Path input, Path output) throws IOException {
        output.getParent().toFile().mkdirs();
        var lines = new ArrayList<String>();
        try (var reader = Files.newBufferedReader(input); var writer = Files.newBufferedWriter(output)) {
            reader.lines().forEach(line -> {
                // ignore blank files
                if (line.isEmpty()) {
                    return;
                }

                // skip lines not from the "python" headers
                if (!line.contains("python")) {
                    return;
                }

                // Windows issue
                if (line.contains("_setjmp")) {
                    // if _setjmp is included, we get the following error:
                    // setjmp depends on _SETJMP_FLOAT128 which has been excluded
                    return;
                }

                // get rid of comments and trim lines
                int hashIdx = line.indexOf('#');
                if (hashIdx != -1) {
                    line = line.substring(0, hashIdx).trim();
                }

                lines.add(line);
            });

            // sort the list so that saved jextract_python_includes.txt
            // is stable regardless of jextract option dump order change
            // between sessions.
            Collections.sort(lines);

            lines.stream().
                    filter(((Predicate<String>) String::isEmpty).negate()).
                    forEach(str -> {
                        try {
                            writer.append(str);
                            writer.append('\n');
                        } catch (IOException exp) {
                            throw new UncheckedIOException(exp);
                        }
                    });

            // add 'malloc' and 'free' in additon to Python symbols
            writer.append("# othen than C Python API\n");
            writer.append("--include-function malloc\n");
            writer.append("--include-function free\n");
        }
    }

    // post process generated java source file for SymbolLookup initialisation
    private static boolean postProcessForSymbolLookup(Path srcDir) throws IOException, InterruptedException {
        String symbolLookupLine = "static final SymbolLookup SYMBOL_LOOKUP =";
        Path[] found = new Path[1];
        try (Stream<Path> paths = Files.walk(srcDir)) {
            paths.filter(Files::isRegularFile).
                    forEach(path -> {
                        if (found[0] != null) {
                            return;
                        }
                        try {
                            if (Files.lines(path).anyMatch(line -> line.contains(symbolLookupLine))) {
                                found[0] = path;
                            }
                        } catch (IOException e) {
                            System.err.println(e);
                        }
                    });
        }

        if (found[0] == null) {
            return false;
        }

        // Customise SymbolLookup initialisation file
        Path symbolLookupInitJava = found[0];
        Path tempFile = Files.createTempFile(symbolLookupInitJava.getFileName().toString(), "temp");
        // we use PythonLibraryLoader class and so add an import for that class
        String importLine = "\nimport org.openjdk.engine.python.impl.PythonLibraryLoader;\n";
        // replace original init with this line
        String replacementLine = "    static final SymbolLookup SYMBOL_LOOKUP = PythonLibraryLoader.load(LIBRARY_ARENA)";

        // we create a temporary to make modifications
        try (var writer = Files.newBufferedWriter(tempFile)) {
            // have we inserted import line already?
            boolean importInserted = false;
            for (String line : Files.readAllLines(symbolLookupInitJava)) {
                if (!importInserted && line.strip().startsWith("package ")) {
                    writer.append(line);
                    writer.append('\n');
                    writer.append(importLine);
                    importInserted = true;
                } else if (line.contains(symbolLookupLine)) {
                    writer.append(replacementLine);
                } else {
                    writer.append(line);
                }
                writer.append('\n');
            }
        }

        // copy the temporary modified file to the original .java file
        Files.copy(tempFile, symbolLookupInitJava, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private static final String COPYRIGHT_TEXT = """
    /*
     * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
     * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
     *
     * This code is free software; you can redistribute it and/or modify it
     * under the terms of the GNU General Public License version 2 only, as
     * published by the Free Software Foundation.  Oracle designates this
     * particular file as subject to the "Classpath" exception as provided
     * by Oracle in the LICENSE file that accompanied this code.
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

    """;

    // post process generated java source file copyright addition
    private static boolean postProcessForCopyright(Path srcDir) throws IOException, InterruptedException {
        try (Stream<Path> paths = Files.walk(srcDir)) {
            boolean[] res = { true };
            paths.filter(Files::isRegularFile).
                forEach(path -> {
                    try {
                        var origContent = Files.readString(path, StandardCharsets.UTF_8);
                        var newContent = COPYRIGHT_TEXT + origContent;
                        Files.writeString(path, newContent, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        res[0] = false;
                        System.err.println(e);
                    }
                });
            return res[0];
        }
    }

    private static final String JEXTRACT_OPTIONS_FILE_NAME = "jextract_python_includes.txt";

    public static void main(String[] args) {
        // should pass version line 3.14 or 3.13 etc. Only major
        // and minor versions. Do not pass 3.13.2 for example!
        if (args.length != 2) {
            System.err.println("java JextractHelper <python-version> <osdir>");
            System.exit(1);
        }

        String pythonVersion = args[0];
        String osDir = args[1];
        if (THE_OS == OS.UNKNOWN) {
            System.err.println("Unsupported OS: " + System.getProperty("os.name"));
            System.exit(2);
        }

        try {
            String jextractPath = findCommand(THE_OS == OS.WINDOWS ? "jextract.bat" : "jextract");
            if (jextractPath != null) {
                IO.println("Using jextract @ " + jextractPath);
            } else {
                IO.println("Cannot find jextract in PATH");
                System.exit(3);
            }

            // Try python version specified, else 3, else python
            String pythonBin = findCommand("python" + pythonVersion);
            if (pythonBin == null) {
                pythonBin = findCommand("python3");
            }
            if (pythonBin == null) {
                pythonBin = findCommand("python");
            }

            if (pythonBin != null) {
                IO.println("Using Python @ " + pythonBin);
            } else {
                IO.println("Python not found!");
                System.exit(4);
            }

            // make sure that we are on expected python version
            if (!checkPythonVersion(pythonBin, pythonVersion.substring("3.".length()))) {
                System.exit(5);
            }

            // find Python's sys.base_prefix
            String pythonBasePrefix = getPythonBasePrefix(pythonBin);
            var targetDir = Paths.get("target");
            targetDir.toFile().mkdirs();

            var pythonIncludeDir
                    = pythonBasePrefix + (THE_OS == OS.WINDOWS
                            ? "\\include" : ("/include/python" + pythonVersion + "/"));

            IO.println("extracting python option includes file from " + pythonIncludeDir);

            // dump include options from Python.h using jextract
            var optionsFile = targetDir.resolve(JEXTRACT_OPTIONS_FILE_NAME);
            ExecResult execResult;
            if (THE_OS == OS.WINDOWS) {
                execResult = runCommand("cmd.exe",
                    "/c",
                    jextractPath,
                    "-D_Float16=short",
                    "-I",
                    pythonIncludeDir,
                    "\"<Python.h>\"",
                    "--dump-includes",
                    optionsFile.toString());
            } else {
                execResult = runCommand(jextractPath,
                    "-D_Float16=short",
                    "-I",
                    pythonIncludeDir,
                    "<Python.h>",
                    "--dump-includes",
                    optionsFile.toString());
            }

            if (execResult.exitCode() != 0) {
                System.err.println("jextract generate include options file step failed");
                System.err.println(execResult.output());
                System.err.println("Process exited with " + execResult.exitCode());
                System.exit(6);
            }

            Path javaSrcDir = Paths.get("src", "main", "profiles", osDir, "java");
            // post process jextract include options file
            var modifiedOptionsFile = javaSrcDir.resolve(JEXTRACT_OPTIONS_FILE_NAME);
            postProcessOptionsFile(optionsFile, modifiedOptionsFile);

            // run jextract with the post processed options file
            if (THE_OS == OS.WINDOWS) {
                execResult = runCommand("cmd.exe",
                    "/c",
                    jextractPath,
                    "--output",
                    javaSrcDir.toString(),
                    "-D_Float16=short",
                    "-I",
                    pythonIncludeDir,
                    "@" + modifiedOptionsFile,
                    "-t",
                    "org.openjdk.engine.python.bindings",
                    "\"<Python.h>\"");
            } else {
                execResult = runCommand(jextractPath,
                    "--output",
                    javaSrcDir.toString(),
                    "-D_Float16=short",
                    "-I",
                    pythonIncludeDir,
                    "@" + modifiedOptionsFile,
                    "-t",
                    "org.openjdk.engine.python.bindings",
                    "<Python.h>");
            }
            IO.println(execResult.output());

            if (execResult.exitCode() != 0) {
                System.err.println("jextract step failed");
                System.err.println("Process exited with " + execResult.exitCode());
                System.exit(7);
            }

            // post process jextracted sources for SymbolLookup initialisation
            if (!postProcessForSymbolLookup(javaSrcDir)) {
                System.err.println("post processing for SymbolLookup init failed");
                System.exit(8);
            }

            // post process jextracted sources for copyright addition
            if (!postProcessForCopyright(javaSrcDir)) {
                System.err.println("post processing for copyright addition failed");
                System.exit(9);
            }
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace(System.err);
            System.exit(9);
        }
    }
}
