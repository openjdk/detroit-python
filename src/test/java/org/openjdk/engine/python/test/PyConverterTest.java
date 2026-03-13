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

import java.util.List;
import java.util.Map;
import javax.script.*;
import org.openjdk.engine.python.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class PyConverterTest {

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
    public void testFromJavaForString() throws Exception {
        engine.withPyObjectManager(() -> {
            PyObject obj = engine.fromJava("hello");
            assertEquals("<class 'str'>",
                    engine.invokeFunction("type", obj).toString());
            assertEquals("hello", obj.toString());

            obj = engine.fromJava("");
            assertEquals("<class 'str'>",
                    engine.invokeFunction("type", obj).toString());
            assertTrue(obj.toString().isEmpty());
            return null;
        });
    }

    @Test
    public void testFromJavaForLong() throws Exception {
        engine.withPyObjectManager(() -> {
            long value = 123556L;
            PyObject obj = engine.fromJava(value);
            assertEquals("<class 'int'>",
                    engine.invokeFunction("type", obj).toString());
            assertTrue(obj.toLong() == value);

            value = -33123556L;
            obj = engine.fromJava(value);
            assertEquals("<class 'int'>",
                    engine.invokeFunction("type", obj).toString());
            assertTrue(obj.toLong() == -33123556);
            return null;
        });
    }

    @Test
    public void testFromJavaForDouble() throws Exception {
        engine.withPyObjectManager(() -> {
            double value = Math.PI;
            double DELTA = 0.0001;
            PyObject obj = engine.fromJava(value);
            assertEquals("<class 'float'>",
                    engine.invokeFunction("type", obj).toString());
            assertEquals(value, obj.toDouble(), DELTA);

            value = Math.E;
            obj = engine.fromJava(value);
            assertEquals("<class 'float'>",
                    engine.invokeFunction("type", obj).toString());
            assertEquals(value, obj.toDouble(), DELTA);
            return null;
        });
    }

    @Test
    public void testFromJavaForBoolean() throws Exception {
        engine.withPyObjectManager(() -> {
            PyObject obj = engine.fromJava(true);
            assertEquals("<class 'bool'>",
                    engine.invokeFunction("type", obj).toString());
            assertTrue(obj == engine.getTrue());

            obj = engine.fromJava(false);
            assertEquals("<class 'bool'>",
                    engine.invokeFunction("type", obj).toString());
            assertTrue(obj == engine.getFalse());
            return null;
        });
    }

    @Test
    public void testFromJavaObject() throws Exception {
        engine.withPyObjectManager(() -> {
            Object hello = "hello";
            PyObject obj = engine.fromJava(hello);
            assertEquals("<class 'str'>",
                    engine.invokeFunction("type", obj).toString());
            assertEquals(hello, obj.toString());

            hello = "";
            obj = engine.fromJava(hello);
            assertEquals("<class 'str'>",
                    engine.invokeFunction("type", obj).toString());
            assertTrue(obj.toString().isEmpty());

            Long longBoxed = 123556L;
            obj = engine.fromJava(longBoxed);
            assertEquals("<class 'int'>",
                    engine.invokeFunction("type", obj).toString());
            assertEquals(longBoxed, obj.toLong());

            longBoxed = -33123556L;
            obj = engine.fromJava(longBoxed);
            assertEquals("<class 'int'>",
                    engine.invokeFunction("type", obj).toString());
            assertEquals(longBoxed, obj.toLong());

            Double doubleBoxed = Math.PI;
            double DELTA = 0.0001;
            obj = engine.fromJava(doubleBoxed);
            assertEquals("<class 'float'>",
                    engine.invokeFunction("type", obj).toString());
            assertEquals(doubleBoxed, obj.toDouble(), DELTA);

            doubleBoxed = Math.E;
            obj = engine.fromJava(doubleBoxed);
            assertEquals("<class 'float'>",
                    engine.invokeFunction("type", obj).toString());
            assertEquals(doubleBoxed, obj.toDouble(), DELTA);

            obj = engine.fromJava(Boolean.TRUE);
            assertEquals("<class 'bool'>",
                    engine.invokeFunction("type", obj).toString());
            assertTrue(obj == engine.getTrue());

            obj = engine.fromJava(Boolean.FALSE);
            assertEquals("<class 'bool'>",
                    engine.invokeFunction("type", obj).toString());
            assertTrue(obj == engine.getFalse());

            // PyObject converts trivially
            assertTrue(obj == engine.fromJava(obj));

            try {
                // arbitrary object conversion should fail
                engine.fromJava(engine);
                throw new AssertionError("should not reach here");
            } catch (IllegalArgumentException iae) {
                assertTrue(iae.getMessage().contains("cannot convert"));
            }

            try (var engine2 = (PythonScriptEngine) engine.getFactory().getScriptEngine()) {
                PyList pyList = (PyList) engine2.eval("[23, 444]");

                try {
                    // passing object from another engine should throw!
                    engine.fromJava(pyList);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }
            }
            return null;
        });
    }

    @Test
    public void testToJava() throws ScriptException {
        engine.withPyObjectManager(() -> {
            assertTrue(engine.toJava(null, Object.class) == null);

            // cls argument cannot be null
            try {
                engine.toJava("hello", null);
                throw new AssertionError("should not read here");
            } catch (NullPointerException e) {
            }

            String msg = "hello";
            PyObject pyObj = engine.fromJava(msg);
            assertEquals(msg, engine.toJava(pyObj, String.class));

            // identity conversion is fine.
            assertTrue(pyObj == engine.toJava(pyObj, PyObject.class));
            // Object conversion is fine as well.
            assertTrue(pyObj == engine.toJava(pyObj, Object.class));

            PyConstant none = engine.getNone();
            assertTrue(none == engine.toJava(none, PyConstant.class));
            assertTrue(none == engine.toJava(none, PyObject.class));
            assertTrue(none == engine.toJava(none, Object.class));

            PyConstant trueObj = engine.getTrue();
            assertTrue(trueObj == engine.toJava(trueObj, PyConstant.class));
            assertTrue(trueObj == engine.toJava(trueObj, PyObject.class));
            assertTrue(trueObj == engine.toJava(trueObj, Object.class));

            PyConstant falseObj = engine.getFalse();
            assertTrue(falseObj == engine.toJava(falseObj, PyConstant.class));
            assertTrue(falseObj == engine.toJava(falseObj, PyObject.class));
            assertTrue(falseObj == engine.toJava(falseObj, Object.class));

            // string length is not 1 and so cannot be converted to char
            try {
                assertEquals('h', engine.toJava(pyObj, Character.class));
                throw new AssertionError("should not read here");
            } catch (ScriptException se) {
                assertTrue(se.getMessage().contains("string length is not 1"));
            }
            try {
                assertEquals('h', engine.toJava(pyObj, char.class));
                throw new AssertionError("should not read here");
            } catch (ScriptException se) {
                assertTrue(se.getMessage().contains("string length is not 1"));
            }

            long longVal = 13342333L;
            pyObj = engine.fromJava(longVal);
            assertEquals(longVal, engine.toJava(pyObj, Long.class));
            assertEquals(longVal, engine.toJava(pyObj, long.class));

            double dblVal = Math.PI;
            double DELTA = 0.0001;
            pyObj = engine.fromJava(dblVal);
            assertEquals((Double)engine.toJava(pyObj, double.class), dblVal, DELTA);
            assertEquals((Double)engine.toJava(pyObj, Double.class), dblVal, DELTA);

            pyObj = engine.fromJava(true);
            assertEquals(engine.toJava(pyObj, Boolean.class), Boolean.TRUE);
            assertEquals(engine.toJava(pyObj, boolean.class), Boolean.TRUE);
            assertTrue(pyObj == engine.toJava(pyObj, Object.class));
            assertTrue(pyObj == engine.toJava(pyObj, PyObject.class));
            assertTrue(pyObj == engine.toJava(pyObj, PyConstant.class));
            assertTrue(pyObj == engine.getTrue());

            pyObj = engine.fromJava(false);
            assertEquals(engine.toJava(pyObj, Boolean.class), Boolean.FALSE);
            assertEquals(engine.toJava(pyObj, boolean.class), Boolean.FALSE);
            assertTrue(pyObj == engine.toJava(pyObj, Object.class));
            assertTrue(pyObj == engine.toJava(pyObj, PyObject.class));
            assertTrue(pyObj == engine.toJava(pyObj, PyConstant.class));
            assertTrue(pyObj == engine.getFalse());

            msg = "A";
            pyObj = engine.fromJava(msg);
            assertEquals('A', engine.toJava(pyObj, Character.class));
            assertEquals('A', engine.toJava(pyObj, char.class));
            assertTrue(pyObj == engine.toJava(pyObj, Object.class));
            assertTrue(pyObj == engine.toJava(pyObj, PyObject.class));

            pyObj = (PyObject) engine.eval("[23, 3423, 544]");
            try {
                // no fancy conversion like python list to Java list etc.
                engine.toJava(pyObj, List.class);
                throw new AssertionError("should not read here");
            } catch (IllegalArgumentException iae) {
                assertTrue(iae.getMessage().contains("cannot convert to"));
            }

            assertTrue(pyObj == engine.toJava(pyObj, PyList.class));
            assertTrue(pyObj == engine.toJava(pyObj, PyObject.class));
            assertTrue(pyObj == engine.toJava(pyObj, Object.class));

            pyObj = (PyObject) engine.eval("{ 'x': 223 }");
            try {
                // no fancy conversion like python dict to Java Map etc.
                engine.toJava(pyObj, Map.class);
                throw new AssertionError("should not read here");
            } catch (IllegalArgumentException iae) {
                assertTrue(iae.getMessage().contains("cannot convert to"));
            }
            assertTrue(pyObj == engine.toJava(pyObj, PyDictionary.class));
            assertTrue(pyObj == engine.toJava(pyObj, PyObject.class));
            assertTrue(pyObj == engine.toJava(pyObj, Object.class));

            try (var engine2 = (PythonScriptEngine) engine.getFactory().getScriptEngine()) {
                PyObject otherEngineObj = engine2.fromJava(longVal);
                try {
                    // passing object from another engine should throw!
                    engine.toJava(otherEngineObj, long.class);
                    throw new AssertionError("should not reach here");
                } catch (IllegalArgumentException iae) {
                    assertTrue(iae.getMessage().contains("PyObject from a different engine"));
                }
            }
            return null;
        });
    }
}