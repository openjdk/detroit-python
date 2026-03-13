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

public class PyListTest {

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
    public void testNewList() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyList = engine.newPyList();
            engine.setExecMode(PyExecMode.EVAL);
            // make sure that it is indeed a Python list!
            var typeFunc = (PyObject) engine.eval("type");
            assertEquals(typeFunc.call(pyList).toString(), "<class 'list'>");

            // list with two elements
            pyList = engine.newPyList(engine.fromJava("hello"), engine.getFalse());
            assertEquals(typeFunc.call(pyList).toString(), "<class 'list'>");
            return null;
        });
    }

    @Test
    public void testSetItemFunction() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyList = engine.newPyList(engine.getFalse(), engine.getTrue(), engine.fromJava("abc"));
            pyList.setItem(0, engine.fromJava("hello"));
            pyList.setItem(1, engine.fromJava("Java"));
            pyList.setItem(2, engine.fromJava("Python"));
            engine.setExecMode(PyExecMode.EVAL);
            var getItemFunc = (PyObject) engine.eval("lambda d, k: d[k]");
            assertEquals(getItemFunc.call(pyList, 0).toString(), "hello");
            assertEquals(getItemFunc.call(pyList, 1).toString(), "Java");
            assertEquals(getItemFunc.call(pyList, 2).toString(), "Python");
            return null;
        });
    }

    @Test
    public void testGetItemFunction() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyList = engine.newPyList();
            pyList.append(engine.getNone());
            engine.setExecMode(PyExecMode.SINGLE);
            pyList.setItem(0, engine.fromJava("hello"));
            assertEquals(pyList.getItem(0).toString(), "hello");
            return null;
        });
    }

    @Test
    public void testSize() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyList = engine.newPyList();
            var lenFunc = (PyObject) engine.eval("len");
            assertEquals(lenFunc.call(pyList).toLong(), 0);
            assertEquals(pyList.size(), 0);
            assertEquals(pyList.len(), 0);

            pyList.append(engine.fromJava("hello"));
            assertEquals(lenFunc.call(pyList).toLong(), 1);
            assertEquals(pyList.size(), 1);
            assertEquals(pyList.len(), 1);

            pyList.append(engine.fromJava("Java"));
            assertEquals(lenFunc.call(pyList).toLong(), 2);
            assertEquals(pyList.size(), 2);
            assertEquals(pyList.len(), 2);

            // list with two elements
            pyList = engine.newPyList(engine.fromJava("hello"), engine.getFalse());
            assertEquals(lenFunc.call(pyList).toLong(), 2);
            assertEquals(pyList.size(), 2);
            assertEquals(pyList.len(), 2);
            return null;
        });
    }
}
