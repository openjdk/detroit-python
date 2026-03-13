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
import static org.testng.Assert.assertTrue;

public class MultipleBindingsTest {

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
    public void differentEngineContextsTest() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("myvar = 233");
            assertEquals(((PyObject)engine.get("myvar")).toLong(), 233);
            ScriptContext oldContext = engine.getContext();

            // The variable "myvar" should not exist in a fresh context
            ScriptContext newContext = new SimpleScriptContext();
            newContext.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
            // set new context as engine context
            engine.setContext(newContext);
            boolean seenException = false;
            try {
                engine.eval("myvar");
                throw new AssertionError("should not reach here");
            } catch (ScriptException se) {
                seenException = true;
            }
            assertTrue(seenException);

            // Let's try setting a different value
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("myvar = 'hello'");
            assertEquals(engine.get("myvar").toString(), "hello");

            // in the default context, we should stll see old value
            // set engine context to be the default context
            engine.setContext(oldContext);
            assertEquals(((PyObject)engine.get("myvar")).toLong(), 233);
            return null;
        });
    }

    @Test
    public void differentEvalContextsTest() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("myvar = 233");
            assertEquals(((PyObject)engine.get("myvar")).toLong(), 233);

            // The variable "myvar" should not exist in a fresh context
            ScriptContext newContext = new SimpleScriptContext();
            newContext.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
            boolean seenException = false;
            try {
                engine.eval("myvar", newContext);
                throw new AssertionError("should not reach here");
            } catch (ScriptException se) {
                seenException = true;
            }
            assertTrue(seenException);

            // Let's try setting a different value
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("myvar = 'hello'", newContext);
            assertEquals(newContext.getAttribute("myvar").toString(), "hello");

            // in the default context, we should stll see old value
            assertEquals(((PyObject)engine.get("myvar")).toLong(), 233);
            return true;
        });
    }
}