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

package org.openjdk.engine.python.test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import javax.script.*;
import org.openjdk.engine.python.*;

import static org.testng.Assert.assertEquals;

public class AbstractSysPathTest {

    protected PythonScriptEngine engine;
    protected Path moduleDir;

    protected void init(String sysProperty) throws IOException {
        moduleDir = Files.createTempDirectory("python-mods");
        System.setProperty(sysProperty, moduleDir.toString());
        ScriptEngineManager m = new ScriptEngineManager();
        this.engine = (PythonScriptEngine) m.getEngineByName("python");
    }

    protected void destroy() throws IOException {
        this.engine.close();
        deletePath(moduleDir);
    }

    protected void testModuleUsage() throws IOException, ScriptException {
        // create a temporary python file in the current directory
        if (! Files.exists(moduleDir)) {
            throw new RuntimeException("module dir does not exist: " + moduleDir);
        }
        Path tempFile = Files.createTempFile(moduleDir, "mymod", ".py");
        tempFile.toFile().deleteOnExit();

        try {
            // write a python function into the temp python file
            Files.write(tempFile, """
            def mySquareFunc(x):
                return x*x
            """.getBytes());

            String tempFileName = tempFile.getFileName().toString();
            String modName = tempFileName.substring(0, tempFileName.lastIndexOf(".py"));
            engine.setExecMode(PythonScriptEngine.PyExecMode.FILE);
            // import temporary file as a python module.
            engine.eval("from " + modName + " import mySquareFunc");
            engine.setExecMode(PythonScriptEngine.PyExecMode.EVAL);

            // with the import setup, we should be able to call function
            // imported from that module
            var res = (PyObject) engine.eval("mySquareFunc(78)");
            assertEquals(res.toLong(), 78 * 78);
        } finally {
            Files.delete(tempFile);
        }
    }

    protected static void deletePath(Path path) {
        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path,
                        new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                }
                );
            } else {
                Files.delete(path);
            }
        } catch (IOException ioExp) {
        }
    }
}
