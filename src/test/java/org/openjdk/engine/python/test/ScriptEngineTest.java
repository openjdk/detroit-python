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

public class ScriptEngineTest {

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
    public void testEval() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.EVAL);
            assertEquals(engine.eval("'hello' + ' world'").toString(), "hello world");
            assertEquals(engine.eval("24 + 8").toString(), "32");
            assertEquals(engine.eval("24 / 2").toString(), "12.0");
            return null;
        });
    }

    @Test
    public void testScriptCompile() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var code = "23 + 45";
            engine.setExecMode(PyExecMode.EVAL);
            try (var compiledScript = engine.compile(code)) {
                // run compiled script twice
                var res = (PyObject) compiledScript.eval(engine.getContext());
                assertEquals(res.toLong(), 68);
                res = (PyObject) compiledScript.eval(engine.getContext());
                assertEquals(res.toLong(), 68);
            }
            return null;
        });
    }

    @Test(expectedExceptions = {IllegalStateException.class})
    public void testClosedCompileScriptEval() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var code = "23 + 45";
            engine.setExecMode(PyExecMode.EVAL);
            var compiledScript = engine.compile(code);
            compiledScript.eval();
            compiledScript.close();
            compiledScript.eval();
            return null;
        });
    }

    @Test
    public void testInvokeFunction() throws Exception {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("""
                def square(x):
                    return x*x
                """);
            var res = (PyObject) engine.invokeFunction("square", 33.0);
            assertEquals((long) res.toDouble(), 33 * 33);
            return null;
        });
    }

    @Test
    public void testInvokeBuiltin() throws ScriptException, NoSuchMethodException {
        engine.setExecMode(PyExecMode.EVAL);
        engine.invokeFunction("dir", "hello");
    }

    @Test(expectedExceptions = { NoSuchMethodException.class })
    public void testInvokeFunctionNonExistent() throws ScriptException, NoSuchMethodException {
        engine.invokeFunction("myFuncNonExistent", 33.0);
    }

    @Test(expectedExceptions = { NoSuchMethodException.class })
    public void testInvokeFunctionNonFunction() throws ScriptException, NoSuchMethodException {
        engine.setExecMode(PyExecMode.SINGLE);
        engine.eval("abc = 23.33");
        engine.invokeFunction("abc", 33.0);
    }

    @Test
    public void testInvokeMethod() throws Exception {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("""
                class MyClass:
                    def square(self, x):
                        return x*x
                """);
            engine.setExecMode(PyExecMode.EVAL);
            var myObj = engine.eval("MyClass()");
            engine.setExecMode(PyExecMode.EVAL);
            var res = (PyObject) engine.invokeMethod(myObj, "square", 33);
            assertEquals((long) res.toDouble(), 33 * 33);
            return null;
        });
    }

    @Test(expectedExceptions = {IllegalStateException.class})
    public void closedEngineEval() throws ScriptException {
        ScriptEngineManager m = new ScriptEngineManager();
        // create a fresh engine
        var e = (PythonScriptEngine) m.getEngineByName("python");
        // close it immediately
        e.close();
        // eval (deliberate) garbage code - we should not get ScriptException.
        // engine closed state should be detected upfront.
        e.eval("p#@!!");
    }

    @Test
    public void testConstants() throws ScriptException {
        engine.withPyObjectManager(() -> {
            var pyNone = engine.getNone();
            var pyTrue = engine.getTrue();
            var pyFalse = engine.getFalse();
            var pyEllipsis = engine.getEllipsis();
            var pyNotImplemented = engine.getNotImplemented();

            assertTrue(pyNone.isNone());
            assertTrue(pyTrue.isTrue());
            assertTrue(pyFalse.isFalse());
            assertTrue(pyEllipsis.isEllipsis());
            assertTrue(pyNotImplemented.isNotImplemented());

            engine.setExecMode(PyExecMode.EVAL);
            PyObject typeFunc = (PyObject) engine.eval("type");
            assertEquals(typeFunc.call(pyNone).toString(), "<class 'NoneType'>");
            assertEquals(typeFunc.call(pyTrue).toString(), "<class 'bool'>");
            assertEquals(typeFunc.call(pyFalse).toString(), "<class 'bool'>");
            assertEquals(typeFunc.call(pyEllipsis).toString(), "<class 'ellipsis'>");
            assertEquals(typeFunc.call(pyNotImplemented).toString(), "<class 'NotImplementedType'>");

            PyObject strFunc = (PyObject) engine.eval("str");
            assertEquals(strFunc.call(pyNone).toString(), "None");
            assertEquals(strFunc.call(pyTrue).toString(), "True");
            assertEquals(strFunc.call(pyFalse).toString(), "False");
            assertEquals(strFunc.call(pyEllipsis).toString(), "Ellipsis");
            assertEquals(strFunc.call(pyNotImplemented).toString(), "NotImplemented");
            return null;
        });
    }

    @Test
    public void mappedPyObjectTypeTests() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.EVAL);
            assertTrue(engine.eval("[ 34, 44 ]") instanceof PyList);
            assertTrue(engine.eval("{ 'x': 23 }") instanceof PyDictionary);
            assertTrue(engine.eval("(None, True)") instanceof PyTuple);
            assertTrue(engine.eval("None") instanceof PyObject);
            return null;
        });
    }

    @Test
    public void globalInterfaceTest() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.FILE);
            engine.eval("""
                def run():
                    global runCalled
                    runCalled = True
                """);
            // create Runnable interface baked by script global function
            Runnable r = engine.getInterface(Runnable.class);
            r.run();
            // make sure r.run() called global function run.
            assertEquals(engine.get("runCalled"), engine.getTrue());
            return null;
        });
    }

    @Test
    public void objectInterfaceTest() throws ScriptException {
        engine.withPyObjectManager(() -> {
            engine.setExecMode(PyExecMode.FILE);
            engine.eval("""
                class MyClass:
                    def run(self):
                        self.runCalled = True
                """);

            engine.setExecMode(PyExecMode.EVAL);
            // get Python Class object from engine global scope
            PyObject myCls = (PyObject) engine.get("MyClass");
            // create an object
            var myObj = myCls.call();
            // create a Runnable interface on myObj
            Runnable r = engine.getInterface(myObj, Runnable.class);
            r.run();
            // make sure r.run() called MyClass.run method.
            assertEquals(myObj.getAttribute("runCalled"), engine.getTrue());
            return null;
        });
    }

    @Test
    public void testSetExecModeViaScriptContext() throws ScriptException {
        engine.withPyObjectManager(() -> {
             engine.getContext().setAttribute("python-exec-mode",
                "FILE", ScriptContext.ENGINE_SCOPE);
            engine.eval("""
            def cube(x):
                return x*x*x
            """);
            engine.getContext().setAttribute("python-exec-mode",
            "EVAL", ScriptContext.ENGINE_SCOPE);
            var res = (PyObject) engine.eval("cube(3)");
            assertEquals(res.toLong(), 27);
            return null;
        });
    }
}
