/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import javax.script.*;
import org.openjdk.engine.python.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class PyJavaFunctionTest {
    private PythonScriptEngine engine;

    @BeforeClass
    public void createEngine() {
        ScriptEngineManager m = new ScriptEngineManager();
        this.engine = (PythonScriptEngine) m.getEngineByName("python");
    }

    @AfterClass
    public void closeEngine() {
        this.engine.close();
    }

    @Test
    public void testNoArgFunc() throws ScriptException {
        engine.withPyObjectManager(() -> {
            boolean[] reached = new boolean[1];
            PyJavaFunction.NoArgFunc func = () -> {
                reached[0] = true;
                return engine.getNone();
            };

            engine.put("noArgJFunc", func);
            engine.eval("noArgJFunc()");
            assertTrue(reached[0]);
            return null;
        });
    }

    @Test
    public void testNoArgFuncReturnVal() throws ScriptException {
        engine.withPyObjectManager(() -> {
            boolean[] reached = new boolean[1];
            PyJavaFunction.NoArgFunc func = () -> {
                try {
                    reached[0] = true;
                    return engine.fromJava("hello");
                } catch (ScriptException ex) {
                    throw new RuntimeException(ex);
                }
            };

            engine.put("noArgJFunc", func);
            assertTrue(engine.eval("type(noArgJFunc)").toString().contains("function"));
            assertEquals("hello", engine.eval("noArgJFunc()").toString());
            assertTrue(reached[0]);
            return null;
        });
    }

    @Test
    public void testNoArgFuncReturnNull() throws ScriptException {
        engine.withPyObjectManager(() -> {
            boolean[] reached = new boolean[1];
            PyJavaFunction.NoArgFunc func = () -> {
                reached[0] = true;
                return null;
            };

            engine.put("nullReturnFunc", func);
            // make sure null from java callback is exposed as Python None
            assertTrue(((PyObject)engine.eval("nullReturnFunc() is None")).isTrue());
            assertTrue(reached[0]);
            return null;
        });
    }

    @Test
    public void testNoArgFuncException() throws Exception {
        engine.withPyObjectManager(() -> {
            boolean[] reached = new boolean[1];
            PyJavaFunction.NoArgFunc thrower = () -> {
                reached[0] = true;
                throw new RuntimeException("Java Error");
            };

            engine.put("jthrower", thrower);
            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.FILE);
            engine.eval("""
                def pythonCaller():
                    try:
                        jthrower()
                        return None
                    except Exception as e:
                        return str(e);
                """);
            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.EVAL);
            var res = engine.invokeFunction("pythonCaller").toString();
            assertTrue(res.contains("Java Error"));
            assertTrue(reached[0]);
            return null;
        });
    }

    @Test
    public void testOneArgFunc() throws ScriptException {
        engine.withPyObjectManager(() -> {
            boolean[] reached = new boolean[1];
            PyJavaFunction.OneArgFunc square = (arg) -> {
                try {
                    reached[0] = true;
                    long val = arg.toLong();
                    return engine.fromJava(val * val);
                } catch (ScriptException se) {
                    throw new RuntimeException(se);
                }
            };

            engine.put("square", square);
            assertTrue(engine.eval("type(square)").toString().contains("function"));
            long res = ((PyObject)engine.eval("square(79)")).toLong();
            assertTrue(reached[0]);
            assertEquals(79*79, res);
            return null;
        });
    }

    @Test
    public void testOneListArgFunc() throws ScriptException {
        engine.withPyObjectManager(() -> {
            boolean[] reached = new boolean[1];
            PyJavaFunction.OneArgFunc concat = (arg) -> {
                try {
                    reached[0] = true;
                    assertTrue(arg instanceof PyList);
                    PyList listArg = (PyList)arg;
                    int len = (int) listArg.len();
                    var buf = new StringBuilder();
                    for (int i = 0; i < len; i++) {
                        buf.append(listArg.getItem(i).toString());
                    }
                    return engine.fromJava(buf.toString());
                } catch (ScriptException se) {
                    throw new RuntimeException(se);
                }
            };

            engine.put("concat", concat);
            assertTrue(engine.eval("type(concat)").toString().contains("function"));
            String res = engine.eval("concat(['Great-', None, 14])").toString();
            assertTrue(reached[0]);
            assertEquals("Great-None14", res);
            return null;
        });
    }

    @Test
    public void testVarargsFunc() throws ScriptException {
        engine.withPyObjectManager(() -> {
            boolean[] reached = new boolean[1];
            PyJavaFunction.VarArgsFunc concat = (args) -> {
                try {
                    reached[0] = true;
                    var buf = new StringBuilder();
                    for (PyObject arg : args) {
                        buf.append(arg.toString());
                    }
                    return engine.fromJava(buf.toString());
                } catch (ScriptException se) {
                    throw new RuntimeException(se);
                }
            };

            engine.put("concat", concat);
            assertTrue(engine.eval("type(concat)").toString().contains("function"));
            String res = engine.eval("concat('hello-', True, 10)").toString();
            assertTrue(reached[0]);
            assertEquals("hello-True10", res);
            return null;
        });
    }

    @Test
    public void testPassLambdaAsArgument() throws Exception {
        engine.withPyObjectManager(() -> {
            boolean[] reached = new boolean[1];
            PyJavaFunction.OneArgFunc func = (key) -> {
                try {
                    reached[0] = true;
                    var propFound = System.getProperty(key.toString()) != null;
                    return engine.fromJava(propFound);
                } catch (ScriptException se) {
                    throw new RuntimeException(se);
                }
            };

            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.FILE);
            engine.eval("""
                def myPyFunc(propChecker):
                    return propChecker("os.name");
                """);
            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.EVAL);
            boolean val = ((PyObject)engine.invokeFunction("myPyFunc", func)).isTrue();
            assertTrue(val);
            assertTrue(reached[0]);
            return null;
        });
    }

    @Test
    public void testMapWithJavaFunc() throws Exception {
        engine.withPyObjectManager(() -> {
            PyJavaFunction.OneArgFunc doubler = (arg) -> {
                try {
                    long val = arg.toLong();
                    return engine.fromJava(val * 2);
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            };

            PyList pyList = (PyList)engine.eval("[1,2,3,4]");
            PyObject resultMap = (PyObject)engine.invokeFunction("map", doubler, pyList);
            PyList resList = (PyList)engine.invokeFunction("list", resultMap);

            assertEquals(resList.toString(), "[2, 4, 6, 8]");
            return null;
        });
    }

    @Test
    public void testFilterWithJavaFunc() throws Exception {
        engine.withPyObjectManager(() -> {
            PyJavaFunction.OneArgFunc isEven = (arg) -> {
                try {
                    long val = arg.toLong();
                    IO.print(val);
                    return engine.fromJava(val % 2 == 0);
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            };

            PyList pyList = (PyList)engine.eval("[1, 2, 3, 4, 5, 6]");
            PyObject pyObj = (PyObject)engine.invokeFunction("filter", isEven, pyList);
            PyList res = (PyList)engine.invokeFunction("list", pyObj);

            assertEquals(res.toString(), "[2, 4, 6]");
            return null;
        });
    }

    @Test
    public void testDictHandling() throws ScriptException {
        engine.withPyObjectManager(() -> {
            PyJavaFunction.OneArgFunc dictProcessor = (arg) -> {
                try {
                    PyDictionary dict = (PyDictionary) arg;
                    String val = (String) engine.toJava(dict.getItem("name"), String.class);
                    assertEquals(val, "Java");

                    long secondVal = dict.getItem("ver").toLong();
                    assertEquals(secondVal, 26);

                    return engine.fromJava("Valid Dict");
                } catch (ScriptException se) {
                    throw new RuntimeException(se);
                }
            };

            engine.put("processDict", dictProcessor);
            String res = engine.eval("processDict({'name': 'Java', 'ver': 26})").toString();
            assertEquals(res, "Valid Dict");
            return null;
        });
    }

    @Test
    public void testLambdaCallsJava() throws ScriptException {
        engine.withPyObjectManager(() -> {
            PyJavaFunction.OneArgFunc shout = (arg) -> {
                try {
                    return engine.fromJava(arg.toString().toUpperCase());
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            };

            engine.put("javaShout", shout);

            PyObject pyObj = (PyObject)engine.eval("lambda x: javaShout(x) + '!!!'");
            Object result = pyObj.call("hello");
            assertEquals(result.toString(), "HELLO!!!");
            return null;
        });
    }

    @Test
    public void testLargeVarargs() throws ScriptException {
        engine.withPyObjectManager(() -> {
            PyJavaFunction.VarArgsFunc testFunc = (args) -> {
                try {
                    long sum = 0;
                    for (PyObject o : args) {
                        sum += o.toLong();
                    }
                    return engine.fromJava(sum);
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            };

            engine.put("testFunc", testFunc);
            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.FILE);
            engine.eval("args = list(range(1000))");
            engine.eval("total = testFunc(*args)");
            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.EVAL);

            long expectedSum = 999 * (1000 / 2);
            assertEquals(engine.toJava(engine.get("total"), Long.class), expectedSum);
            return null;
        });
    }

    @Test
    public void testCallback() throws ScriptException {
        engine.withPyObjectManager(() -> {
            PyJavaFunction.OneArgFunc runner = (callback) -> {
                try {
                    engine.put("temp_cb", callback);
                    PyObject res = (PyObject)engine.eval("temp_cb(5)");
                    return engine.fromJava(res);
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            };

            engine.put("javaRunner", runner);

            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.FILE);
            engine.eval("def py_callback(x): return x * 10");
            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.EVAL);

            PyObject result = (PyObject)engine.eval("javaRunner(py_callback)");

            assertEquals(engine.toJava(result, Long.class), 50L);
            return null;
        });
    }

    @Test
    public void testCallbackLambda() throws Exception {
        engine.withPyObjectManager(() -> {
            PyJavaFunction.OneArgFunc runner = (callback) -> {
                try {
                    engine.put("temp_cb", callback);
                    PyObject res = (PyObject)engine.eval("temp_cb()");
                    return engine.fromJava(res);
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            };

            PyJavaFunction.NoArgFunc callbackFunc = () -> {
                try {
                    return engine.fromJava("Called Me!!");
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            };

            engine.put("javaRunner", runner);
            PyObject result = (PyObject)engine.invokeFunction("javaRunner", callbackFunc);

            assertEquals(engine.toJava(result, String.class), "Called Me!!");
            return null;
        });
    }

    @Test
    public void testJavaFuncInList() throws ScriptException {
        engine.withPyObjectManager(() -> {
            PyJavaFunction.NoArgFunc funcA = () -> {
                try {
                    return engine.fromJava("A");
                } catch (ScriptException ignored) {
                }
                return null;
            };

            PyJavaFunction.NoArgFunc funcB = () -> {
                try {
                    return engine.fromJava("B");
                } catch (ScriptException ignored) {
                }
                return null;
            };

            PyList pyList = engine.newPyList();
            pyList.append(engine.fromJava(funcA));
            pyList.append(engine.fromJava(funcB));

            assertEquals(pyList.getItem(0).call().toString(), "A");
            assertEquals(pyList.getItem(1).call().toString(), "B");
            return null;
        });
    }

    @Test
    public void testJavaFuncInDict() throws ScriptException {
        engine.withPyObjectManager(() -> {
            PyJavaFunction.NoArgFunc funcA = () -> {
                try {
                    return engine.fromJava("A");
                } catch (ScriptException ignored) {
                    return null;
                }
            };

            PyJavaFunction.NoArgFunc funcB = () -> {
                try {
                    return engine.fromJava("B");
                } catch (ScriptException ignored) {
                    return null;
                }
            };

            engine.put("funcA", funcA);
            engine.put("funcB", funcB);

            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.FILE);
            engine.eval("func_dict = {'first': funcA, 'second': funcB}");
            engine.setExecMode(AbstractPythonScriptEngine.PyExecMode.EVAL);
            assertEquals(engine.eval("func_dict['second']()").toString(), "B");
            return null;
        });
    }
}
