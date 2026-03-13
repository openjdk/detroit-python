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

public class PyDictionaryTest {

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
    public void testNewDictionary() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyDict = engine.newPyDictionary();
            engine.setExecMode(PyExecMode.EVAL);
            // make sure that it is indeed a Python dictionary!
            var typeFunc = (PyObject) engine.eval("type");
            assertEquals(typeFunc.call(pyDict).toString(), "<class 'dict'>");
            return null;
        });
    }

    @Test
    public void testSetItemFunction() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyDict = engine.newPyDictionary();
            // try Java Strings for key and value
            pyDict.setItem("hello", "world");
            engine.setExecMode(PyExecMode.EVAL);
            var getItemFunc = (PyObject) engine.eval("lambda d, k: d[k]");
            assertEquals(getItemFunc.call(pyDict, "hello").toString(), "world");

            // try PyObjects for key and value
            pyDict.setItem(engine.fromJava("hello"), engine.fromJava("world"));
            engine.setExecMode(PyExecMode.EVAL);
            assertEquals(getItemFunc.call(pyDict, "hello").toString(), "world");
            return null;
        });
    }

    @Test
    public void testGetItemFunction() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyDict = engine.newPyDictionary();
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("""
                def func(d, k, v):
                    d[k] = v
                """);

            engine.setExecMode(PyExecMode.EVAL);
            var setItemFunc = (PyObject) engine.eval("func");
            setItemFunc.call(pyDict, "hello", "world");
            assertEquals(pyDict.getItem("hello").toString(), "world");
            return null;
        });
    }

    @Test
    public void testSize() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyDict = engine.newPyDictionary();
            engine.setExecMode(PyExecMode.EVAL);
            var lenFunc = (PyObject) engine.eval("len");
            assertEquals(lenFunc.call(pyDict).toLong(), 0);
            assertEquals(pyDict.size(), 0);
            assertEquals(pyDict.len(), 0);

            pyDict.setItem("hello", "world");
            pyDict.setItem("Java", "Great!");
            assertEquals(lenFunc.call(pyDict).toLong(), 2);
            assertEquals(pyDict.size(), 2);
            assertEquals(pyDict.len(), 2);

            pyDict.setItem("Python", "use javax.python");
            assertEquals(lenFunc.call(pyDict).toLong(), 3);
            assertEquals(pyDict.size(), 3);
            assertEquals(pyDict.len(), 3);
            return null;
        });
    }

    @Test
    public void testClear() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyDict = engine.newPyDictionary();
            engine.setExecMode(PyExecMode.EVAL);
            var lenFunc = (PyObject) engine.eval("len");
            pyDict.setItem("hello", "world");
            pyDict.setItem("Java", "Great!");
            assertEquals(pyDict.getItem("hello").toString(), "world");
            assertEquals(pyDict.getItem("Java").toString(), "Great!");
            assertTrue(pyDict.containsKey("hello"));
            assertTrue(pyDict.containsKey("Java"));
            assertEquals(lenFunc.call(pyDict).toLong(), 2);
            assertEquals(pyDict.len(), 2);

            pyDict.clear();
            assertEquals(lenFunc.call(pyDict).toLong(), 0);
            assertEquals(pyDict.len(), 0);
            assertFalse(pyDict.containsKey("hello"));
            assertFalse(pyDict.containsKey("Java"));
            assertTrue(pyDict.getItem("hello").isNone());
            assertEquals(pyDict.getItem("hello"), engine.getNone());
            assertTrue(pyDict.getItem("Java").isNone());
            assertEquals(pyDict.getItem("Java"), engine.getNone());
            return null;
        });
    }

    @Test
    public void keysTest() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyDict = engine.newPyDictionary();
            engine.setExecMode(PyExecMode.EVAL);
            pyDict.setItem("hello", "world");
            pyDict.setItem("Java", "Great!");

            var keys = pyDict.keys();
            assertEquals(keys.getItem(0).toString(), "hello");
            assertEquals(keys.getItem(1).toString(), "Java");
            return null;
        });
    }

    @Test
    public void valuesTest() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyDict = engine.newPyDictionary();
            engine.setExecMode(PyExecMode.EVAL);
            pyDict.setItem("hello", "world");
            pyDict.setItem("Java", "Great!");

            var values = pyDict.values();
            assertEquals(values.getItem(0).toString(), "world");
            assertEquals(values.getItem(1).toString(), "Great!");
            return null;
        });
    }

    @Test
    public void itemsTest() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyDict = engine.newPyDictionary();
            engine.setExecMode(PyExecMode.EVAL);
            pyDict.setItem("hello", "world");
            pyDict.setItem("Java", "Great!");

            var items = pyDict.items();
            var item1 = items.getItem(0);
            var item2 = items.getItem(1);
            assertTrue(item1 instanceof PyTuple);
            assertTrue(item2 instanceof PyTuple);

            assertEquals(((PyTuple)item1).getItem(0).toString(), "hello");
            assertEquals(((PyTuple)item1).getItem(1).toString(), "world");

            assertEquals(((PyTuple)item2).getItem(0).toString(), "Java");
            assertEquals(((PyTuple)item2).getItem(1).toString(), "Great!");
            return null;
        });
    }
}
