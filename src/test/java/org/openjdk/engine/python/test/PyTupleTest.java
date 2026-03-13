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

public class PyTupleTest {
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
    public void testNewTuple() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyList = engine.newPyTuple();
            engine.setExecMode(PyExecMode.EVAL);
            // make sure that it is indeed a Python tuple!
            var typeFunc = (PyObject) engine.eval("type");
            assertEquals(typeFunc.call(pyList).toString(), "<class 'tuple'>");
            return null;
        });
    }

    @Test
    public void testGetItemFunction() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyTuple = engine.newPyTuple(engine.fromJava(23), engine.fromJava("hello"));
            assertEquals(pyTuple.getItem(0).toLong(), 23);
            assertEquals(pyTuple.getItem(1).toString(), "hello");
            return null;
        });
    }

    @Test
    public void testSize() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyTuple = engine.newPyTuple();
            var lenFunc = (PyObject) engine.eval("len");
            assertEquals(lenFunc.call(pyTuple).toLong(), 0);
            assertEquals(pyTuple.size(), 0);
            assertEquals(pyTuple.len(), 0);

            pyTuple = engine.newPyTuple(engine.getNone(), engine.getFalse());
            assertEquals(lenFunc.call(pyTuple).toLong(), 2);
            assertEquals(pyTuple.size(), 2);
            assertEquals(pyTuple.len(), 2);
            return null;
        });
    }
}