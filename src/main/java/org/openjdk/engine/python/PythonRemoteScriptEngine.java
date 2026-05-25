/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Proxy;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

/**
 * ScriptEngine implementation that evaluates Python code in a remote Python interpreter
 * while exposing a local Java API. A local {@link PythonScriptEngine} is used
 * for conversions and as a bridge; the actual execution happens remotely via
 * a remote client server protocol.
 */
public final class PythonRemoteScriptEngine extends AbstractPythonScriptEngine {

    static final String REMOTE_ENGINE_MODULE_NAME = "remote_script_engine";
    static final String REMOTE_ENGINE_MODULE_PATH;

    // Create a temporaray directory and file for the remote engine
    // support module file remote_script_engine.py.
    static {
        try {
            String moduleFileName = REMOTE_ENGINE_MODULE_NAME + ".py";

            // create a temporary directory for new module
            Path tempDir = Files.createTempDirectory("python-script-engine");

            //  create a new module .py file with the content from the .py resource bundled
            try (var resIs = PythonScriptEngine.class.getResourceAsStream(moduleFileName)) {
                Path tempFile = tempDir.resolve(moduleFileName);
                tempFile.toFile().deleteOnExit();
                Files.write(tempFile, resIs.readAllBytes());
            }
            tempDir.toFile().deleteOnExit();

            // On Windows, Python infers \U as some unicode character and throws error
            // for paths that contain \U (example: C:\User\tmp). So use / as file
            // separator for all platforms.
            REMOTE_ENGINE_MODULE_PATH = tempDir.
                    toAbsolutePath().
                    toString().
                    replace(File.separatorChar, '/');
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private final PythonScriptEngine localPyEngine;
    private final PyObject remoteClient;

    PythonRemoteScriptEngine(PythonScriptEngine localPyEnigne) throws ScriptException {
        this.localPyEngine = Objects.requireNonNull(localPyEnigne);
        PyObject remoteEngineConstr = initRemoteEngineClientConstructor().unregister();
        this.remoteClient = remoteEngineConstr.call().unregister();
    }

    // remote support
    /**
     * Creates a new remote sctipt engine backed by a remote Python interpreter,
     * using the given engine as the local bridge for conversions and coordination.
     * <p>
     * When the local ScriptEngine is closed, all remote script engines created using
     * that ScriptEngine are also closed.
     *
     * @param pyEngine local PythonScriptEngine associated with the new remote engine.
     * @return a new AbstractPythonScriptEngine that executes remotely
     * @throws ScriptException if initialization of the remote client fails
     */
    public static AbstractPythonScriptEngine create(PythonScriptEngine pyEngine) throws ScriptException {
        // The remote client object is managed by PythonRemoteScriptEngine object.
        final var remoteEngine = new PythonRemoteScriptEngine(pyEngine);
        pyEngine.addDependentEngine(remoteEngine);
        return remoteEngine;
    }

    /**
     * Evaluates the provided script in the remote interpreter using the supplied context.
     *
     * @param script the Python source code to execute (non-null)
     * @param ctxt   the ScriptContext providing globals and I/O
     * @return the result of evaluation as a PyObject or converted Java value
     * @throws ScriptException if remote evaluation fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized Object eval(String script, ScriptContext ctxt) throws ScriptException {
        checkClosed();
        CompilationState cs = newCompilationState(script, ctxt);
        return remoteClient.callMethod("eval_command",
                script, cs.name(), getCompileMode(cs.mode()), getPyDictionary(ctxt));
    }

    /**
     * Evaluates the script read from the given Reader in the remote interpreter
     * using the supplied context.
     *
     * @param reader Reader supplying Python source (will be fully read)
     * @param ctxt   the ScriptContext providing globals and I/O
     * @return the result of evaluation
     * @throws ScriptException if reading or remote evaluation fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized Object eval(Reader reader, ScriptContext ctxt) throws ScriptException {
        checkClosed();
        try {
            return eval(readAll(reader), ctxt);
        } catch (IOException ex) {
            throw new ScriptException(ex);
        }
    }

    /**
     * Evaluates the provided script in the remote interpreter using this engine's current context.
     *
     * @param script the Python source code to execute (non-null)
     * @return the result of evaluation
     * @throws ScriptException if remote evaluation fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized Object eval(String script) throws ScriptException {
        checkClosed();
        CompilationState cs = newCompilationState(script, context);
        return remoteClient.callMethod("eval_command",
                script, cs.name(), getCompileMode(cs.mode()));
    }

    /**
     * Evaluates the script read from the given Reader in the remote interpreter
     * using this engine's current context.
     *
     * @param reader Reader supplying Python source (will be fully read)
     * @return the result of evaluation
     * @throws ScriptException if reading or remote evaluation fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized Object eval(Reader reader) throws ScriptException {
        checkClosed();
        try {
            return eval(readAll(reader));
        } catch (IOException ioExp) {
            throw new ScriptException(ioExp);
        }
    }

    /**
     * Sets a global variable in the remote interpreter's globals dictionary.
     *
     * @param key   variable name
     * @param value Java value to store (converted to PyObject remotely)
     * @throws RuntimeException wrapping ScriptException if the call fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized void put(String key, Object value) {
        checkClosed();
        try {
            remoteClient.callMethod("put_var_command", key, value);
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Retrieves a global variable from the remote interpreter's globals dictionary.
     *
     * @param key variable name
     * @return the value as a PyObject or converted Java value
     * @throws RuntimeException wrapping ScriptException if the call fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized Object get(String key) {
        checkClosed();
        try {
            return remoteClient.callMethod("get_var_command", key);
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Creates new ENGINE_SCOPE bindings. The returned bindings are local but
     * compatible with the remote engine.
     *
     * @return new PythonBindings instance
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized Bindings createBindings() {
        checkClosed();
        return localPyEngine.createBindings();
    }

    /**
     * Sets the ScriptContext and synchronizes the remote interpreter's globals.
     *
     * @param ctxt new ScriptContext whose ENGINE_SCOPE must be PythonBindings
     * @throws RuntimeException wrapping ScriptException if synchronization fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized void setContext(ScriptContext ctxt) {
        checkClosed();
        Objects.requireNonNull(ctxt);
        // check that ENGINE_SCOPE is set acceptable value.
        PythonBindings pyBindings = getPythonBindings(ctxt);
        try {
            remoteClient.callMethod("put_globals_command", pyBindings.getPyDictionary());
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
        super.setContext(ctxt);
    }

    /**
     * Returns the ScriptContext after synchronizing its ENGINE_SCOPE with the
     * remote interpreter's globals.
     *
     * @return current ScriptContext with up-to-date globals
     * @throws RuntimeException wrapping ScriptException if synchronization fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized ScriptContext getContext() {
        checkClosed();
        try {
            var pyDict = (PyDictionary) remoteClient.callMethod("get_globals_command");
            context.setBindings(new PythonBindings(pyDict), ScriptContext.ENGINE_SCOPE);
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
        return context;
    }

    /**
     * Returns the ScriptEngineFactory associated with this engine (delegated to the local engine).
     *
     * @return factory instance
     */
    @Override
    public ScriptEngineFactory getFactory() {
        return localPyEngine.getFactory();
    }

    /**
     * Closes this engine and releases remote resources. Idempotent.
     *
     * @throws RuntimeException wrapping ScriptException if remote close fails
     */
    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        try {
            remoteClient.callMethod("engine_close_command");
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        } finally {
            remoteClient.destroy();
            if (PythonConfig.PYTHON_GC_ON_CLOSE) {
                localPyEngine.gc();
            }
            this.closed = true;
        }
        if (PythonConfig.JAVA_GC_ON_CLOSE) {
            System.gc();
        }
    }

    /**
     * Remote engines are never main engines.
     *
     * @return false always
     */
    @Override
    public boolean isMainEngine() {
        return false;
    }

    /**
     * Compiles the given script in the remote interpreter.
     *
     * @param script Python source (non-null)
     * @return a compiled script wrapper executing remotely
     * @throws ScriptException if compilation fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized CompiledScript compile(String script) throws ScriptException {
        checkClosed();
        CompilationState cs = newCompilationState(script, context);
        try {
            var pyScript = remoteClient.callMethod("compile_command",
                    script, cs.name(), getCompileMode(cs.mode()));
            pyScript.unregister();
            return new PythonRemoteCompiledScript(this, pyScript);
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Compiles the given Reader content in the remote interpreter.
     *
     * @param reader Reader supplying Python source
     * @return a compiled script wrapper executing remotely
     * @throws ScriptException if reading or compilation fails
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized CompiledScript compile(Reader reader) throws ScriptException {
        checkClosed();
        try {
            return compile(readAll(reader));
        } catch (IOException ioExp) {
            throw new ScriptException(ioExp);
        }
    }

    /**
     * Invokes a method on a Python object in the remote interpreter.
     *
     * @param thiz  target object (converted if needed)
     * @param name  method name
     * @param args  arguments to pass
     * @return the invocation result
     * @throws ScriptException if the invocation fails remotely
     * @throws NoSuchMethodException if the method cannot be resolved
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized Object invokeMethod(Object thiz, String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        checkClosed();
        var pyAddr = localPyEngine.withPyObjectManager(() -> {
            PyObject pyObj = remoteClient.callMethod("call_object_method",
                    fromJava(thiz), name, newPyList(toPyObjects(args)));
            // make the result is not confined to the current PyObjectArena!
            pyObj.unregister();
            // This transfers the object address!
            return pyObj.addr();
        });
        return localPyEngine.wrap(pyAddr);
    }

    /**
     * Invokes a global function in the remote interpreter.
     *
     * @param name function name
     * @param args arguments to pass
     * @return the invocation result
     * @throws ScriptException if the invocation fails remotely
     * @throws NoSuchMethodException if the function cannot be resolved
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized Object invokeFunction(String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        checkClosed();

        var pyAddr = localPyEngine.withPyObjectManager(() -> {
            PyObject pyObj = remoteClient.callMethod("call_global_function",
                    name, newPyList(toPyObjects(args)));
            // make the result is not confined to the current PyObjectArena!
            pyObj.unregister();
            // This transfers the object address!
            return pyObj.addr();
        });
        return localPyEngine.wrap(pyAddr);
    }

    /**
     * Returns a Java proxy implementing the given interface by delegating to
     * functions in the remote interpreter's globals.
     *
     * @param <T>   interface type
     * @param iface public interface to implement
     * @return a proxy instance
     * @throws IllegalArgumentException if iface is not a public interface
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized <T> T getInterface(Class<T> iface) {
        checkClosed();
        checkInterface(iface);
        return implementInterface(iface);
    }

    /**
     * Returns a Java proxy implementing the given interface by delegating to
     * methods on the specified Python object in the remote interpreter.
     *
     * @param <T>   interface type
     * @param thiz  Python object to dispatch calls to
     * @param iface public interface to implement
     * @return a proxy instance
     * @throws IllegalArgumentException if iface is not a public interface
     * @throws IllegalStateException if this engine is closed
     */
    @Override
    public synchronized <T> T getInterface(Object thiz, Class<T> iface) {
        checkClosed();
        checkInterface(iface);
        return implementInterface(thiz, iface);
    }

    /**
     * Converts a Java String to a remote Python str (delegated to local engine).
     */
    @Override
    public PyObject fromJava(String str) throws ScriptException {
        return localPyEngine.fromJava(str);
    }

    /**
     * Converts a Java long to a remote Python int (delegated to local engine).
     */
    @Override
    public PyObject fromJava(long l) throws ScriptException {
        return localPyEngine.fromJava(l);
    }

    /**
     * Converts a Java double to a remote Python float (delegated to local engine).
     */
    @Override
    public PyObject fromJava(double d) throws ScriptException {
        return localPyEngine.fromJava(d);
    }

    /**
     * Converts a Java boolean to a remote Python bool (delegated to local engine).
     */
    @Override
    public PyObject fromJava(boolean b) throws ScriptException {
        return localPyEngine.fromJava(b);
    }

    /**
     * Converts a supported Java object to a remote Python object (delegated to local engine).
     */
    @Override
    public PyObject fromJava(Object obj) throws ScriptException {
        if (obj instanceof PyJavaFunction.Func pyFunc) {
            return fromJava(pyFunc, pyFunc.toString(), null);
        } else {
            return localPyEngine.fromJava(obj);
        }
    }

    /**
     * Converts a Java function as a Python function object.
     */
    @Override
    public PyJavaFunction fromJava(PyJavaFunction.Func func, String name, String doc) {
        throw new UnsupportedOperationException("not implemented for remote engine");
    }

    /**
     * Converts a Python-backed value to the requested Java type (delegated to local engine).
     */
    @Override
    public Object toJava(Object obj, Class<?> cls) throws ScriptException {
        return localPyEngine.toJava(obj, cls);
    }

    /**
     * Returns the Python None singleton (delegated to local engine).
     */
    @Override
    public PyConstant getNone() {
        return localPyEngine.getNone();
    }

    /**
     * Returns the Python False singleton (delegated to local engine).
     */
    @Override
    public PyConstant getFalse() {
        return localPyEngine.getFalse();
    }

    /**
     * Returns the Python True singleton (delegated to local engine).
     */
    @Override
    public PyConstant getTrue() {
        return localPyEngine.getTrue();
    }

    /**
     * Returns the Python Ellipsis singleton (delegated to local engine).
     */
    @Override
    public PyConstant getEllipsis() {
        return localPyEngine.getEllipsis();
    }

    /**
     * Returns the Python NotImplemented singleton (delegated to local engine).
     */
    @Override
    public PyConstant getNotImplemented() {
        return localPyEngine.getNotImplemented();
    }

    /**
     * Creates a new Python dict (delegated to local engine).
     */
    @Override
    public PyDictionary newPyDictionary() throws ScriptException {
        return localPyEngine.newPyDictionary();
    }

    /**
     * Creates a new Python list with the provided items (delegated to local engine).
     */
    @Override
    public PyList newPyList(PyObject... items) throws ScriptException {
        return localPyEngine.newPyList(items);
    }

    /**
     * Creates a new Python tuple with the provided items (delegated to local engine).
     */
    @Override
    public PyTuple newPyTuple(PyObject... items) throws ScriptException {
        return localPyEngine.newPyTuple(items);
    }

    // package private helpers below this point
    Object evalCompiled(PyObject pyScript, ScriptContext ctxt) throws ScriptException {
        return remoteClient.callMethod("eval_codeobject_command", pyScript, getPyDictionary(ctxt));
    }

    Object evalCompiled(PyObject pyScript) throws ScriptException {
        return remoteClient.callMethod("eval_codeobject_command", pyScript);
    }

    Object closeCompiled(PyObject pyScript) throws ScriptException {
        return remoteClient.callMethod("close_codeobject_command", pyScript);
    }

    // internals only below this point
    private PyObject initRemoteEngineClientConstructor() throws ScriptException {
        // create new ScriptContext to avoid polluting the global scope.
        try (var pyBindings = (PythonBindings) localPyEngine.createBindings()) {
            var newContext = localPyEngine.getScriptContext(pyBindings);
            PyExecMode oldMode = execMode;
            try {
                localPyEngine.setExecMode(PyExecMode.FILE);

                // append the temporary directory to module search path
                localPyEngine.eval(String.format("""
                    import sys
                    sys.path.append("%s")
                    """, PythonRemoteScriptEngine.REMOTE_ENGINE_MODULE_PATH), newContext);

                // import RemoteEngineClient class
                localPyEngine.eval(String.format("from %s import RemoteEngineClient",
                        PythonRemoteScriptEngine.REMOTE_ENGINE_MODULE_NAME), newContext);

                localPyEngine.setExecMode(PyExecMode.EVAL);
                // return the RemoteEngineClass class to use as constructor later
                return (PyObject) localPyEngine.eval("RemoteEngineClient", newContext);
            } finally {
                localPyEngine.setExecMode(oldMode);
            }
        }
    }

    private <T> T implementInterface(Class<T> iface) {
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(),
                new Class[]{iface},
                (proxy, method, args) -> {
                    if (args == null) {
                        args = new Object[0];
                    }

                    var value = invokeFunction(method.getName(), args);
                    return toJava(value, method.getReturnType());
                }
        ));
    }

    private <T> T implementInterface(Object thiz, Class<T> iface) {
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(),
                new Class[]{iface},
                (proxy, method, args) -> {
                    if (args == null) {
                        args = new Object[0];
                    }

                    var value = invokeMethod(thiz, method.getName(), args);
                    return toJava(value, method.getReturnType());
                }
        ));
    }

    /**
     * Converts arbitrary Java arguments into PyObject values using the local engine,
     * suitable for transmission/usage on the remote interpreter.
     *
     * Ownership semantics follow the local engine rules; callers must ensure appropriate
     * lifetimes for any temporary values on the remote side.
     *
     * @param args Java arguments to convert
     * @return array of PyObject values suitable for remote calls
     * @throws ScriptException if any conversion fails
     */
    private PyObject[] toPyObjects(Object... args) throws ScriptException {
        PyObject[] res = new PyObject[args.length];
        for (int i = 0; i < args.length; i++) {
            res[i] = fromJava(args[i]);
        }
        return res;
    }

    // code mode string as passed to Python builtin function "compile"
    /**
     * Maps the engine's compile mode to the string accepted by Python's builtin
     * compile() function on the remote interpreter.
     *
     * @param mode engine execution/compile mode
     * @return "single" for SINGLE, "exec" for FILE, "eval" for EVAL
     */
    private String getCompileMode(PyExecMode mode) {
        return switch (mode) {
            case SINGLE ->
                "single";
            case FILE ->
                "exec";
            case EVAL ->
                "eval";
        };
    }
}
