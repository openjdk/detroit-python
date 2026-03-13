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

package org.openjdk.engine.python;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;

// Simple REPL for the python script engine.
final class PythonMain {
    private PythonMain() {}
    public static void main(String[] args) {
        // make it consistent with Python REPL
        if (System.getProperty("java.python.sys.prepend.path") == null) {
            System.setProperty("java.python.sys.prepend.path", "");
        }
        ScriptEngineManager sem = new ScriptEngineManager();
        PythonScriptEngine e = (PythonScriptEngine) sem.getEngineByName("python");
        if (e == null) {
            System.err.println("cannot find python script engine");
            System.exit(1);
        }

        switch (args.length) {
            case 1 -> {
                if (args[0].equals("--version")) {
                    System.out.println("java.python script engine " + e.getFactory().getEngineVersion());
                    e.setExecMode(PyExecMode.SINGLE);
                    try {
                        System.out.print("Python ");
                        e.eval("import sys; print(sys.version)");
                    } catch (ScriptException ex) {
                        System.err.println(ex);
                    }
                    Properties props = new Properties();
                    try (InputStream in =
                            PythonMain.class.getClassLoader().getResourceAsStream("git.properties")) {
                        if (in != null) {
                            props.load(in);
                            System.out.println("Commit ID: " + props.getProperty("git.commit.id"));
                        }
                    } catch (IOException ignored) {}
                    e.close();
                } else {
                    if (args[0].equals("-")) {
                        e.setExecMode(PyExecMode.SINGLE);
                        repl(e);
                    } else {
                        try {
                            File f = new File(args[0]);
                            e.setExecMode(PyExecMode.FILE);
                            e.eval(new FileReader(f));
                        } catch (ScriptException se) {
                            print(se);
                        } catch (IOException io) {
                            System.err.println(io);
                        }
                    }
                }
            }
            case 2 -> {
                switch (args[0]) {
                    case "-c" -> {
                        e.setExecMode(PyExecMode.FILE);
                        try {
                            e.eval(args[1]);
                        } catch (ScriptException se) {
                            print(se);
                        }
                    }

                    case "-m" -> {
                        e.setExecMode(PyExecMode.FILE);
                        try {
                            var str = String.format("""
                                import runpy
                                import sys
                                sys.stdout.isatty = lambda: True
                                runpy.run_module('%s', run_name='__main__')
                            """, args[1]);
                            e.eval(str);
                        } catch (ScriptException se) {
                            print(se);
                        }
                    }

                    default -> {
                        System.err.println("unknown option: " + args[0]);
                        System.exit(1);
                    }
                }
            }
            default -> {
                e.setExecMode(PyExecMode.SINGLE);
                repl(e);
            }
        }
    }

    private static void repl(ScriptEngine engine) {
        try (Scanner in = new Scanner(System.in)) {
            System.out.println("Simple Java Python REPL. Type 'exit' to quit.");

            while (true) {
                System.out.print("jpy> ");
                String line = null;
                try {
                    line = in.nextLine();
                } catch (NoSuchElementException ignored) {}

                if (line == null || line.equalsIgnoreCase("exit")) {
                    System.out.println("Bye!");
                    break;
                }

                try {
                    Object res = engine.eval(line);
                    if (res != null && !isNone(res)) {
                        System.out.println(res);
                    }
                } catch (ScriptException se) {
                    print(se);
                    // allow underlying PyObject to be garbage
                    // collcted by decrementing ref
                    if (se instanceof PythonException pe) {
                        pe.getPyException().decRefCount();
                    }
                }
            }
        }
    }

    private static boolean isNone(Object obj) {
        if (! (obj instanceof PyObject)) {
            return false;
        }
        return ((PyObject)obj).isNone();
    }

    private static void print(ScriptException se) {
        if (se instanceof PythonException pe) {
            pe.print();
            pe.getPyException().decRefCount();
        } else {
            System.err.println(se);
        }
    }
}
