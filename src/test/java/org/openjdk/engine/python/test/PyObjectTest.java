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

import javax.script.*;
import org.openjdk.engine.python.*;
import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PyObjectTest {

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
    public void testPyObjectCallFunc() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("import math");
            engine.setExecMode(PyExecMode.EVAL);
            var dirFunc = (PyObject) engine.eval("dir");
            var str = (PyObject) engine.eval("'hello'");
            IO.println(dirFunc.call(str));
            var mathPow = (PyObject) engine.eval("math.pow");
            var num1 = (PyObject) engine.eval("23");
            var num2 = (PyObject) engine.eval("2");
            assertEquals((long) mathPow.call(num1, num2).toDouble(), 529);
            assertEquals((long) mathPow.call(29, 2).toDouble(), 841);
            return null;
        });
    }

    @Test
    public void testPyObjectCallFunc2() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.FILE);
            engine.eval("""
                def mul5(a, b, c, d, e):
                    return a * b * c * d * e;

                def sum4(x, y, z, w):
                    return x + y + z + w

                def sum3(x, y, z):
                    return x + y + z
                """);
            engine.setExecMode(PyExecMode.EVAL);
            var sum3 = (PyObject) engine.eval("sum3");
            assertEquals(sum3.call(29, -2332, 33).toLong(), 29 - 2332 + 33);
            var sum4 = (PyObject) engine.eval("sum4");
            assertEquals(sum4.call(29, 2, 33, 443).toLong(), 29 + 2 + 33 + 443);
            var mul5 = (PyObject) engine.eval("mul5");
            assertEquals(mul5.call(3, 5, 7, 9, 11).toLong(), 3 * 5 * 7 * 9 * 11);
            return null;
        });
    }

    @Test
    public void testPyObjectCallWithOtherEngineObject() throws ScriptException {
        engine.withPyObjectManager(() -> {
            try (var engine2 = (PythonScriptEngine) engine.getFactory().getScriptEngine()) {
                engine.setExecMode(PyExecMode.EVAL);
                var dirFunc = (PyObject) engine.eval("dir");
                var strFromOther = (PyObject) engine2.eval("'hello'");

                // single object that is object from another engine
                try {
                    dirFunc.call(strFromOther);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }

                // try PyObject from another engine in different positions
                var mathPow = (PyObject) engine.eval("math.pow");
                var numOther = (PyObject) engine2.eval("23");
                var num = (PyObject) engine.eval("2");
                try {
                    mathPow.call(numOther, num);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }

                try {
                    mathPow.call(num, numOther);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }

                // mix PyObject from another engine in different positions
                try {
                    mathPow.call(numOther, 23);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }

                try {
                    mathPow.call(42, numOther);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }
            }
            return null;
        });
    }

    @Test
    public void testPyObjectCallMethod() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("import math");
            engine.setExecMode(PyExecMode.EVAL);
            var math = (PyObject) engine.eval("math");
            var num1 = (PyObject) engine.eval("23");
            var num2 = (PyObject) engine.eval("2");
            assertEquals((long) math.callMethod("pow", num1, num2).toDouble(), 529);
            assertEquals((long) math.callMethod("pow", 29, 2).toDouble(), 841);
            return null;
        });
    }

    @Test
    public void testPyObjectCallMethod2() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.FILE);
            engine.eval("""
                class Calc:
                    def mul5(self, a, b, c, d, e):
                        return a * b * c * d * e;

                    def sum4(self, x, y, z, w):
                        return x + y + z + w

                    def sum3(self, x, y, z):
                        return x + y + z
                """);
            engine.setExecMode(PyExecMode.EVAL);
            var calc = (PyObject) engine.eval("Calc()");
            assertEquals(calc.callMethod("sum3", 29, -2332, 33).toLong(), 29 - 2332 + 33);
            assertEquals(calc.callMethod("sum4", 29, 2, 33, 443).toLong(), 29 + 2 + 33 + 443);
            assertEquals(calc.callMethod("mul5", 3, 5, 7, 9, 11).toLong(), 3 * 5 * 7 * 9 * 11);
            return null;
        });
    }

    @Test
    public void testPyObjectCallMethodWithOtherEngineObject() throws ScriptException {
        engine.withPyObjectManager(() -> {
            try (var engine2 = (PythonScriptEngine) engine.getFactory().getScriptEngine()) {
                engine.setExecMode(PyExecMode.FILE);
                engine.eval("""
                    class Calc:
                        def sum(self, *args):
                            pass
                    """);
                engine.setExecMode(PyExecMode.EVAL);
                var calc = (PyObject) engine.eval("Calc()");
                var strFromOther = (PyObject) engine2.eval("'hello'");

                // single object that is object from another engine
                try {
                    calc.callMethod("sum", strFromOther);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }

                // try PyObject from another engine in different positions
                var numOther = (PyObject) engine2.eval("23");
                var num = (PyObject) engine.eval("2");
                try {
                    calc.callMethod("sum", numOther, num);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }

                try {
                    calc.callMethod("sum", num, numOther);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }

                // mix PyObject from another engine in different positions
                try {
                    calc.callMethod("sum", numOther, 23);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }

                try {
                    calc.callMethod("sum", 42, numOther);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }
            }
            return null;
        });
    }

    @Test
    public void testIsCallable() throws ScriptException {
        engine.withPyObjectManager(() -> {
            // non-callable constants
            assertFalse(engine.getNone().isCallable());
            assertFalse(engine.getFalse().isCallable());
            assertFalse(engine.getTrue().isCallable());
            assertFalse(engine.getEllipsis().isCallable());
            assertFalse(engine.getNotImplemented().isCallable());

            // few non-callable literals
            PyObject pyObj = (PyObject) engine.eval("'hello'");
            assertFalse(pyObj.isCallable());
            pyObj = (PyObject) engine.eval("[23, 344]");
            assertFalse(pyObj.isCallable());
            pyObj = (PyObject) engine.eval("{ 'hello', 'world' }");
            assertFalse(pyObj.isCallable());

            // few callables
            pyObj = (PyObject) engine.eval("dir");
            assertTrue(pyObj.isCallable());
            pyObj = (PyObject) engine.eval("list");
            assertTrue(pyObj.isCallable());
            return null;
        });
    }

    @Test
    public void testRefCount() throws ScriptException {
        PyObject pyObj = (PyObject) engine.eval("[23, 444, 'hello']");
        assertTrue(pyObj.getRefCount() == 1L);
        pyObj.decRefCount();
        assertTrue(pyObj.isNone());
    }

    @Test
    public void testObjectId() throws ScriptException {
        engine.withPyObjectManager(() -> {
            PyObject obj = (PyObject) engine.eval("'hello'");
            PyObject idStr = (PyObject) engine.eval("id('hello')");
            assertTrue(obj.id() == Long.parseLong(idStr.toString()));
            return null;
        });
    }

    @Test
    public void testObjectIs() throws ScriptException {
        engine.withPyObjectManager(() -> {
            assertTrue(engine.getNone().is((PyObject) engine.eval("None")));
            assertTrue(engine.getTrue().is((PyObject) engine.eval("True")));
            assertTrue(engine.getFalse().is((PyObject) engine.eval("False")));
            assertTrue(engine.getEllipsis().is((PyObject) engine.eval("Ellipsis")));
            assertTrue(engine.getNotImplemented().is((PyObject) engine.eval("NotImplemented")));
            // small integers cached
            assertTrue(((PyObject) engine.eval("22 + 3")).is((PyObject) engine.eval("25")));
            return null;
        });
    }

    @Test
    public void testObjectIsNot() throws ScriptException {
        engine.withPyObjectManager(() -> {
            assertTrue(engine.getNone().isNot((PyObject) engine.eval("'hello'")));
            assertTrue(engine.getTrue().isNot((PyObject) engine.eval("False")));
            assertTrue(engine.getFalse().isNot((PyObject) engine.eval("True")));
            assertTrue(engine.getEllipsis().isNot((PyObject) engine.eval("332")));
            assertTrue(engine.getNotImplemented().isNot((PyObject) engine.eval("{}")));
            // 'large' integers are not cached
            assertTrue(((PyObject) engine.eval("1000")).isNot((PyObject) engine.eval("1000")));
            return null;
        });
    }

    @Test
    public void testAttributes() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var list = (PyObject) engine.eval("[233, 444]");
            var listLen = list.getAttribute("__len__");
            assertTrue(listLen.call().toLong() == 2L);

            var map = (PyObject) engine.eval("{ 'Java': '25', 'Python': '3.14' }");
            var mapGet = map.getAttribute("get");
            assertEquals(mapGet.call("Java").toString(), "25");
            assertEquals(mapGet.call("Python").toString(), "3.14");
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("""
            class Person:
                def __init__(self, name, age):
                    self.name = name
                    self.age = age
            """);
            engine.setExecMode(PyExecMode.EVAL);
            var personCls = (PyObject) engine.eval("Person");
            var personObj = (PyObject) personCls.call("Alice", 7);
            assertEquals(personObj.getAttribute("name").toString(), "Alice");
            assertEquals(personObj.getAttribute("age").toLong(), 7);
            personObj.deleteAttribute("name");
            assertTrue(personObj.getAttributeOptional("name").isNone());
            assertTrue(personObj.setAttribute("name", "Alice Pleasance Liddell"));
            assertEquals(personObj.getAttributeOptional("name").toString(), "Alice Pleasance Liddell");
            personObj.deleteAttribute("age");
            // getAttribute on non-existent property throws exception
            boolean sawException = false;
            try {
                personObj.getAttribute("age");
                throw new AssertionError("should not reach here");
            } catch (ScriptException se) {
                assertTrue(se.getMessage().contains("has no attribute 'age'"));
                IO.println(se);
                sawException = true;
            }
            assertTrue(sawException);
            return null;
        });
    }
}
