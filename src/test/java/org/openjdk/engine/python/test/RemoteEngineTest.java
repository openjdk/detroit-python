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

import java.util.concurrent.Callable;
import javax.script.*;
import org.openjdk.engine.python.*;
import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RemoteEngineTest {

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
    public void testPidsNotEqual() throws ScriptException {
        engine.setExecMode(PyExecMode.FILE);
        engine.eval("import os");
        engine.setExecMode(PyExecMode.EVAL);
        long localPid = ((PyObject)engine.eval("os.getpid()")).toLong();
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.FILE);
            re.eval("import os");
            re.setExecMode(PyExecMode.EVAL);
            long remotePid = ((PyObject)re.eval("os.getpid()")).toLong();
            assertTrue(localPid != remotePid);
        }
    }

    @Test
    public void testEvalWithContext() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            var bindings = re.createBindings();
            var ctxt = new SimpleScriptContext();
            ctxt.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

            int x = 1028;
            bindings.put("x", x);
            int y = 3444;
            bindings.put("y", y);
            re.setExecMode(PyExecMode.FILE);
            re.eval("z = x*y", ctxt);
            re.setExecMode(PyExecMode.EVAL);
            var evalValue = (PyObject) re.eval("z", ctxt);
            assertEquals(evalValue.toLong(), x * y);

            // make sure context is updated properly
            assertTrue(bindings.containsKey("x"));
            assertTrue(bindings.containsKey("y"));
            assertTrue(bindings.containsKey("z"));

            // We evaluated everything in a separate ScriptContext.
            // The default context should be clean
            var defBindings = re.getContext().getBindings(ScriptContext.ENGINE_SCOPE);
            assertFalse(defBindings.containsKey("x"));
            assertFalse(defBindings.containsKey("y"));
            assertFalse(defBindings.containsKey("z"));
        }
    }

    @Test
    public void testEvalWithContextNotPicklable() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            var bindings = re.createBindings();
            var ctxt = new SimpleScriptContext();
            ctxt.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

            var func = engine.fromJava((PyJavaFunction.NoArgFunc)() -> engine.getNone());
            int x = 1028;
            bindings.put("x", x);
            int y = 3444;
            bindings.put("y", y);
            // 'func' is not pickable and so 'w' is not set in
            // to remote bindings
            bindings.put("w", func);
            re.setExecMode(PyExecMode.FILE);
            re.eval("z = x*y", ctxt);
            re.setExecMode(PyExecMode.EVAL);
            var evalValue = (PyObject) re.eval("z", ctxt);
            assertEquals(evalValue.toLong(), x * y);

            // make sure context is updated properly
            assertTrue(bindings.containsKey("x"));
            assertTrue(bindings.containsKey("y"));
            assertTrue(bindings.containsKey("z"));
            assertTrue(bindings.containsKey("w"));

            // We evaluated everything in a separate ScriptContext.
            // The default context should be clean
            var defBindings = re.getContext().getBindings(ScriptContext.ENGINE_SCOPE);
            assertFalse(defBindings.containsKey("x"));
            assertFalse(defBindings.containsKey("y"));
            assertFalse(defBindings.containsKey("z"));
            assertFalse(defBindings.containsKey("w"));
        }
    }

    @Test
    public void testRemoteFunctionCall() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.FILE);
            re.eval("""
                def square(x):
                    return x*x
                """);
            re.setExecMode(PyExecMode.EVAL);
            long squareValue = ((PyObject)re.eval("square(27)")).toLong();
            assertTrue(squareValue == 27*27L);
        }
    }

    @Test
    public void testRemoteMethodCall() throws ScriptException, NoSuchMethodException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.FILE);
            re.eval("""
                class Adder:
                    def __init__(self, x):
                        self.x = x
                    def add(self, y):
                        if y is None:
                            return self.x
                        else:
                            return self.x + y
                """);

            var func = engine.fromJava((PyJavaFunction.NoArgFunc)() -> engine.getTrue());
            re.setExecMode(PyExecMode.EVAL);
            var remoteObj = re.eval("RemoteObjectManager.register(Adder(25))");
            var value = (PyObject) re.invokeMethod(remoteObj, "add", 233);
            assertEquals(value.toLong(), 233 + 25);
            // 'func' is not pickable and so None is sent. Adder takes care
            // of None by returning self.x
            value = (PyObject) re.invokeMethod(remoteObj, "add", func);
            assertEquals(value.toLong(), 25);
        }
    }

    @Test
    public void testRemoteMethodCallOnNotRemoteObject() throws ScriptException, NoSuchMethodException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.EVAL);
            var remoteObj = re.eval("[334, 344]");
            // The following should throw exception, as List is not registered
            // as a remote object and so received here as local after pickling.
            boolean sawPyException = false;
            try {
                re.invokeMethod(remoteObj, "append", 33);
            } catch (PythonException pe) {
                assertTrue(pe.toString().contains("expected a RemoteObject"));
                sawPyException = true;
            }
            assertTrue(sawPyException);

            re.setExecMode(PyExecMode.FILE);
            re.eval("aList = [334, 344]");
            re.setExecMode(PyExecMode.EVAL);
            // now do a proper remote registration
            remoteObj = re.eval("RemoteObjectManager.register(aList)");
            re.invokeMethod(remoteObj, "append", 33);
            var len = (PyObject) re.invokeMethod(remoteObj, "__len__");
            assertEquals(len.toLong(), 3);
        }
    }

    @Test
    public void testGlobalVariableRead() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.FILE);
            re.eval("x = 'hello'");
            re.setExecMode(PyExecMode.EVAL);
            var str = ((PyObject)re.get("x")).toString();
            assertEquals(str, "hello");
        }
    }

    @Test
    public void testGlobalVariableWrite() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            var func = engine.fromJava((PyJavaFunction.NoArgFunc)() -> engine.getNone());
            re.setExecMode(PyExecMode.FILE);
            re.put("x", 23);
            // 'func' is not picklable and so None is set in the remote
            re.put("func", func);
            re.setExecMode(PyExecMode.EVAL);
            var value = ((PyObject)re.eval("x*x*x")).toLong();
            assertEquals(value, 23*23*23);
            var obj = (PyObject)re.eval("func");
            assertTrue(obj.isNone());
        }
    }

    @Test
    public void testGlobalVariableWriteBuiltins() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            try {
                re.put("__builtins__", 23);
                throw new AssertionError("should not reach here");
            } catch (RuntimeException rx) {
                assertTrue(rx.getMessage().contains("cannot overwritte __builtins__"));
            }
        }
    }

    @Test
    public void testGetGlobals() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.FILE);
            re.eval("x = 'hello'");
            re.eval("y = 23");
            Bindings bindings = re.getContext().getBindings(ScriptContext.ENGINE_SCOPE);
            assertEquals(((PyObject)bindings.get("x")).toString(), "hello");
            assertEquals(((PyObject)bindings.get("y")).toLong(), 23L);
        }
    }

    @Test
    public void testSetGlobals() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.FILE);
            var bindings = re.createBindings();
            ScriptContext sc = new SimpleScriptContext();
            int x = 34, y = 25, z = 23;
            bindings.put("x", x);
            bindings.put("y", y);
            bindings.put("z", z);
            sc.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
            re.setContext(sc);
            re.setExecMode(PyExecMode.EVAL);
            long value = ((PyObject) re.eval("x + y + z")).toLong();
            assertEquals(value, x + y + z);
        }
    }

    @Test
    public void testCompile() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.EVAL);
            var compiledScript = (PythonRemoteCompiledScript) re.compile("89*89");
            var evalValue = (PyObject) compiledScript.eval();
            assertEquals(evalValue.toLong(), 89*89);
            compiledScript.close();

            re.setExecMode(PyExecMode.SINGLE);
            compiledScript = (PythonRemoteCompiledScript) re.compile("import os; os.name");
            compiledScript.eval();

            re.setExecMode(PyExecMode.FILE);
            re.eval("""
                def save_sys_path(path):
                    global sys_path
                    sys_path = path
                """);

            compiledScript.close();
            compiledScript = (PythonRemoteCompiledScript) re.compile("import sys; save_sys_path(sys.path)");
            compiledScript.eval();
            re.setExecMode(PyExecMode.EVAL);
            assertTrue(re.eval("sys_path") instanceof PyList);
            compiledScript.close();
        }
    }

    @Test
    public void testCompileWithContext() throws ScriptException {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            var bindings = re.createBindings();
            var ctxt = new SimpleScriptContext();
            ctxt.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

            int x = -343;
            bindings.put("x", x);
            int y = 423;
            bindings.put("y", y);
            var func = engine.fromJava((PyJavaFunction.NoArgFunc)() -> engine.getNone());
            // 'func' is not picklable - but that should be ignored!
            bindings.put("func", func);
            re.setExecMode(PyExecMode.EVAL);
            var compiledScript = re.compile("x*y");
            var evalValue = (PyObject) compiledScript.eval(ctxt);
            assertEquals(evalValue.toLong(), x*y);
        }
    }

    @Test
    public void testGetInterface() throws Exception {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.FILE);
            re.eval("""
                def call():
                    return "hello"
                """);

            Callable<?> callable = re.getInterface(Callable.class);
            assertEquals(callable.call().toString(), "hello");
        }
    }

    @Test
    public void testGetInterfaceOnObject() throws Exception {
        try (var re = PythonRemoteScriptEngine.create(engine)) {
            re.setExecMode(PyExecMode.FILE);
            re.eval("""
                class Callback:
                    def __init__(self, x):
                        self.x = x
                    def call(self):
                        return self.x
                """);

            re.setExecMode(PyExecMode.EVAL);
            var remoteObj = re.eval("RemoteObjectManager.register(Callback(25))");
            Callable<?> callable = re.getInterface(remoteObj, Callable.class);
            assertEquals(callable.call().toString(), "25");

            remoteObj = re.eval("RemoteObjectManager.register(Callback('hello'))");
            callable = re.getInterface(remoteObj, Callable.class);
            assertEquals(callable.call().toString(), "hello");
        }
    }
}
