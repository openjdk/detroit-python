/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import static java.lang.foreign.MemorySegment.NULL;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.Supplier;

import org.openjdk.engine.python.bindings.PyConfig;
import org.openjdk.engine.python.bindings.PyCFunction;
import org.openjdk.engine.python.bindings._PyCFunctionFast;
import org.openjdk.engine.python.bindings.PyMemberDef;
import org.openjdk.engine.python.bindings.PyMethodDef;
import org.openjdk.engine.python.bindings.PyInterpreterConfig;
import org.openjdk.engine.python.bindings.PyThreadState;
import static org.openjdk.engine.python.bindings.Python_h.*;

/**
 * Concrete ScriptEngine implementation that embeds a CPython interpreter and
 * exposes it via the JSR-223 API. Each instance corresponds to either the main
 * Python interpreter or a sub-interpreter depending on lifecycle management.
 * <p>
 * This engine:
 * <ul>
 * <li>Initializes Python and configures GIL behavior based on
 * {@link PythonConfig}</li>
 * <li>Compiles and evaluates Python code under proper GIL discipline</li>
 * <li>Converts between Java and Python objects</li>
 * <li>Manages PyObject lifetimes using arenas and reference counting</li>
 * </ul>
 */
public final class PythonScriptEngine extends AbstractPythonScriptEngine {

    private static final int GIL_MODE;

    static {
        String mode = PythonConfig.GIL_MODE;
        GIL_MODE = switch (mode) {
            case "default" ->
                PyInterpreterConfig_DEFAULT_GIL();
            case "shared" ->
                PyInterpreterConfig_SHARED_GIL();
            case "own" ->
                PyInterpreterConfig_OWN_GIL();
            default -> {
                System.err.println("unknown gil mode: " + mode + ", assuming default");
                yield PyInterpreterConfig_DEFAULT_GIL();
            }
        };
        if (PythonConfig.DEBUG) {
            IO.println("GIL mode: " + mode);
        }
    }

    static interface ThrowingSupplier<T> {

        public T get() throws ScriptException;
    }

    static interface ThrowingFunction<T, R> {

        public R apply(T t) throws ScriptException;
    }

    // The factory that created this engine
    private final ScriptEngineFactory factory;
    // is this the main interpreter that called Py_Initialize?
    private final boolean mainInterpreter;
    // Auto Arena connected with the life-time of this engine.
    private Arena engineArena;
    private final ScopedValue<PyObjectArena> PY_OBJECTS_ARENA;
    // PyInterpreter* pyInterpreterState;
    // PyThreadState* for the current thread.
    private ThreadLocal<MemorySegment> pyPlatformThreadState;
    private ThreadLocal<MemorySegment> pyVirtualThreadState;
    // PyInterpreter* pyInterpreterState;
    private MemorySegment pyInterpreterState;
    // well-known Python constant singletons
    private final PyConstant NONE;
    private final PyConstant TRUE;
    private final PyConstant FALSE;
    private final PyConstant ELLIPSIS;
    private final PyConstant NOT_IMPLEMENTED;
    // well-known constants in a Map for faster conversion from address
    private final Map<MemorySegment, PyConstant> wellKnownConstants;
    // Python builtin objects/functions.
    private final PyObject builtins;
    // PyMethodDef objects are expensive because of lambda backed
    // C pointer to function implementation. Caching those is worthwhile.
    private final Map<PyJavaFunction.Func, MemorySegment> pyMethodDefMap;
    // If this is mainInterpreter, we track all the sub-interpreter backed ScriptEngines.
    private final ArrayList<WeakReference<AbstractPythonScriptEngine>> dependentEngines;

    private PythonScriptEngine(ScriptEngineFactory factory,
            /* PyTheadState* */ MemorySegment pyThreadState,
            boolean mainInterpreter) {
        this.factory = factory;
        this.mainInterpreter = mainInterpreter;
        this.engineArena = Arena.ofAuto();
        this.PY_OBJECTS_ARENA = ScopedValue.newInstance();
        if (Thread.currentThread().isVirtual()) {
            if (PythonConfig.DEBUG) {
                IO.println("Initializing pyVirtualThreadState in constructor");
            }
            this.pyVirtualThreadState = VirtualThreadHelper.newCarrierThreadLocal();
            this.pyVirtualThreadState.set(pyThreadState);
        } else {
            if (PythonConfig.DEBUG) {
                IO.println("Initializing pyPlatformThreadState in constructor");
            }
            this.pyPlatformThreadState = new PlatformThreadLocal();
            this.pyPlatformThreadState.set(pyThreadState);
        }
        this.pyInterpreterState = PyThreadState.interp(pyThreadState.reinterpret(PyThreadState.sizeof()));
        this.NONE = new PyConstant(getNoneAddr(), this);
        this.TRUE = new PyConstant(getTrueAddr(), this);
        this.FALSE = new PyConstant(getFalseAddr(), this);
        this.ELLIPSIS = new PyConstant(getEllipsisAddr(), this);
        this.NOT_IMPLEMENTED = new PyConstant(getNotImplementedAddr(), this);
        this.wellKnownConstants = Map.of(
                getNoneAddr(), NONE,
                getTrueAddr(), TRUE,
                getFalseAddr(), FALSE,
                getEllipsisAddr(), ELLIPSIS,
                getNotImplementedAddr(), NOT_IMPLEMENTED
        );
        try {
            this.builtins = withLock(() -> wrap(PyEval_GetBuiltins()));
            setSysPath();
            getContext().setBindings(createBindings(), ScriptContext.ENGINE_SCOPE);
        } catch (ScriptException se) {
            throw new RuntimeException(se);
        }
        this.pyMethodDefMap = Collections.synchronizedMap(new IdentityHashMap<>());
        this.dependentEngines = new ArrayList<>();
    }

    /**
     * Indicates whether this engine is backed by the main interpreter that
     * initialized Python via {@code Py_Initialize}.
     *
     * @return true if this engine is the main interpreter
     */
    @Override
    public boolean isMainEngine() {
        return mainInterpreter;
    }

    // ScriptEngine methods
    /**
     * Evaluates the provided Python source using the supplied ScriptContext.
     *
     * @param script Python source code (non-null)
     * @param sc ScriptContext providing globals and I/O
     * @return evaluation result wrapped as a PyObject
     * @throws ScriptException if compilation or evaluation fails
     * @throws IllegalStateException if the engine has been closed
     */
    @Override
    public synchronized Object eval(String script, ScriptContext sc) throws ScriptException {
        checkClosed();
        Objects.requireNonNull(script);
        Objects.requireNonNull(sc);

        CompilationState cs = newCompilationState(script, sc);
        return withLock(() -> {
            MemorySegment compiled = compileUnlocked(cs);
            try {
                return evalUnlocked(compiled, sc);
            } finally {
                Py_DecRef(compiled);
            }
        });
    }

    /**
     * Reads Python source from the given Reader and evaluates it using the
     * supplied ScriptContext.
     *
     * @param reader Reader providing Python source
     * @param sc ScriptContext providing globals and I/O
     * @return evaluation result wrapped as a PyObject
     * @throws ScriptException if reading, compilation, or evaluation fails
     * @throws IllegalStateException if the engine has been closed
     */
    @Override
    public synchronized Object eval(Reader reader, ScriptContext sc) throws ScriptException {
        checkClosed();
        String str;
        try {
            str = readAll(reader);
        } catch (IOException ioExp) {
            throw new ScriptException(ioExp);
        }
        return eval(str, sc);
    }

    /**
     * Creates ENGINE_SCOPE bindings backed by a Python dictionary. The returned
     * bindings include __builtins__ and __name__ = "__main__".
     *
     * @return new PythonBindings instance
     * @throws IllegalStateException if the engine has been closed
     */
    @Override
    public synchronized Bindings createBindings() {
        checkClosed();
        try {
            var pyDict = new PyDictionary(this);
            pyDict.setItem("__builtins__", builtins);
            pyDict.setItem("__name__", "__main__");
            // PyDictionary is owned by PythonBindinges object
            pyDict.unregister();
            return new PythonBindings(pyDict);
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Sets the engine's ScriptContext. The ENGINE_SCOPE bindings must be a
     * {@link PythonBindings} instance.
     *
     * @param ctxt new context (non-null)
     * @throws IllegalArgumentException if ENGINE_SCOPE is not PythonBindings
     */
    @Override
    public synchronized void setContext(ScriptContext ctxt) {
        Objects.requireNonNull(ctxt);
        // check that ENGINE_SCOPE is set acceptable value.
        getPythonBindings(ctxt);
        super.setContext(ctxt);
    }

    /**
     * Returns the ScriptEngineFactory that produced this engine.
     *
     * @return engine factory
     */
    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    // Compilable methods
    /**
     * Compiles the given Python source using the current ScriptContext.
     *
     * @param script Python source code (non-null)
     * @return a compiled script handle which must be closed to release
     * resources
     * @throws ScriptException if compilation fails
     */
    @Override
    public synchronized PythonCompiledScript compile(String script) throws ScriptException {
        checkClosed();
        Objects.requireNonNull(script);
        // PyObject for compiled script is owned & managed by PythonCompiledScript object!
        PyObject pyScript = compile(script, getContext()).unregister();
        return new PythonCompiledScript(pyScript);
    }

    /**
     * Compiles Python source read from the given Reader using the current
     * ScriptContext.
     *
     * @param reader Reader providing Python source
     * @return a compiled script handle which must be closed to release
     * resources
     * @throws ScriptException if reading or compilation fails
     */
    @Override
    public synchronized PythonCompiledScript compile(Reader reader) throws ScriptException {
        checkClosed();
        String str;
        try {
            str = readAll(reader);
        } catch (IOException ioExp) {
            throw new ScriptException(ioExp);
        }
        return compile(str);
    }

    // Invocable methods
    /**
     * Invokes a method on a target Python object in this engine.
     *
     * @param thiz target object (must be a PyObject owned by this engine)
     * @param name method name (non-null)
     * @param args Java arguments to convert and pass
     * @return invocation result as a PyObject
     * @throws ScriptException if conversion or invocation fails
     * @throws NoSuchMethodException if the method cannot be resolved
     * @throws IllegalArgumentException if thiz is not a PyObject for this
     * engine
     */
    @Override
    public synchronized Object invokeMethod(Object thiz, String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        checkClosed();
        Objects.requireNonNull(name);
        if (!(thiz instanceof PyObject)) {
            throw new IllegalArgumentException("'this' is not a PyObject");
        }

        PyObject pyObj = (PyObject) thiz;
        if (pyObj.getEngine() != this) {
            throw new IllegalArgumentException("PyObject from a different engine");
        }

        return ((PyObject) thiz).callMethod(name, args);
    }

    /**
     * Invokes a global function available in the engine's globals or in
     * builtins.
     *
     * @param name function name (non-null)
     * @param args Java arguments to convert and pass
     * @return invocation result as a PyObject
     * @throws ScriptException if conversion or invocation fails
     * @throws NoSuchMethodException if no function by that name is found
     */
    @Override
    public synchronized Object invokeFunction(String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        checkClosed();
        Objects.requireNonNull(name);
        PyObject func = getPyDictionary(context).getItem(name);
        // try builtin
        if (!isCallable(func)) {
            func = builtins.getItem(name);
        }
        if (!isCallable(func)) {
            throw new NoSuchMethodException(name);
        }
        return func.call(args);
    }

    /**
     * Returns a proxy implementing the given public interface by dispatching
     * calls to Python global functions of the same name.
     *
     * @param <T> interface type parameter
     * @param iface public interface to implement (non-null)
     * @return proxy instance
     * @throws IllegalArgumentException if iface is not a public interface
     */
    @Override
    public synchronized <T> T getInterface(Class<T> iface) {
        checkClosed();
        checkInterface(iface);
        return implementInterface(iface, getPyDictionary(context));
    }

    /**
     * Returns a proxy implementing the given public interface by dispatching
     * calls to methods on the specified Python object.
     *
     * @param <T> interface type parameter
     * @param thiz PyObject to dispatch to (owned by this engine)
     * @param iface public interface to implement (non-null)
     * @return proxy instance
     * @throws IllegalArgumentException if iface is not a public interface or
     * thiz is invalid
     */
    @Override
    public synchronized <T> T getInterface(Object thiz, Class<T> iface) {
        checkClosed();
        checkInterface(iface);
        if (!(thiz instanceof PyObject)) {
            throw new IllegalArgumentException("'this' is not a PyObject");
        }

        PyObject pyObj = (PyObject) thiz;
        if (pyObj.getEngine() != this) {
            throw new IllegalArgumentException("PyObject from a different engine");
        }
        return implementInterface(iface, pyObj);
    }

    // PyConverter methods
    /**
     * Converts a Java String into a Python str belonging to this engine.
     *
     * @param str Java string (non-null)
     * @return PyObject representing the Python str
     * @throws ScriptException if conversion fails
     */
    @Override
    public synchronized PyObject fromJava(String str) throws ScriptException {
        return withLock(() -> wrap(fromJavaNoLock(str)));
    }

    /**
     * Converts a Java long into a Python int belonging to this engine.
     *
     * @param l Java long value
     * @return PyObject representing the Python int
     * @throws ScriptException if conversion fails
     */
    @Override
    public synchronized PyObject fromJava(long l) throws ScriptException {
        return withLock(() -> wrap(fromJavaNoLock(l)));
    }

    /**
     * Converts a Java double into a Python float belonging to this engine.
     *
     * @param d Java double value
     * @return PyObject representing the Python float
     * @throws ScriptException if conversion fails
     */
    @Override
    public synchronized PyObject fromJava(double d) throws ScriptException {
        return withLock(() -> wrap(fromJavaNoLock(d)));
    }

    /**
     * Converts a Java boolean into a Python bool belonging to this engine.
     *
     * @param b Java boolean value
     * @return PyObject representing Python True/False
     * @throws ScriptException if conversion fails
     */
    @Override
    public synchronized PyObject fromJava(boolean b) throws ScriptException {
        return b ? TRUE : FALSE;
    }

    /**
     * Converts a supported Java object into a Python object belonging to this
     * engine.
     *
     * @param obj Java object to convert; null maps to Python None
     * @return PyObject representing the converted value
     * @throws ScriptException if conversion fails or type unsupported
     */
    @Override
    public synchronized PyObject fromJava(Object obj) throws ScriptException {
        switch (obj) {
            case PyObject pyObj -> {
                if (pyObj.getEngine() != this) {
                    throw new IllegalArgumentException("PyObject from a different engine!");
                }
                return pyObj;
            }
            case PyJavaFunction.Func pyFunc -> {
                return fromJava(pyFunc, pyFunc.toString(), null);
            }
            default -> {
                return withLock(() -> wrap(fromJavaNoLock(obj)));
            }
        }
    }

    /**
     * Converts a Java function as a Python function object.
     *
     * @param func the Java function implementation
     * @param name the name of the Java function in Python code
     * @param doc the doc String for the Java function
     * @return a PyJavaFunction representing the converted Python function
     * object
     * @throws ScriptException if the type is unsupported or conversion fails
     */
    @Override
    public PyJavaFunction fromJava(PyJavaFunction.Func func,
            String name, String doc) throws ScriptException {
        return withLock(() -> new PyJavaFunction(
                PyJavaFunction.createPyCFunctionNoLock(this, func, name, doc), this));
    }

    /**
     * Converts a Python-backed value to the requested Java type.
     *
     * @param obj value to convert (may already be a Java value)
     * @param cls target class (non-null)
     * @return converted value (or null)
     * @throws ScriptException if conversion fails
     */
    @Override
    public synchronized Object toJava(Object obj, Class<?> cls) throws ScriptException {
        Objects.requireNonNull(cls);
        if (obj instanceof PyObject pyObj) {
            if (pyObj.getEngine() != this) {
                throw new IllegalArgumentException("PyObject from a different engine!");
            }
            if (cls.isInstance(obj)) {
                return cls.cast(pyObj);
            }
            var converter = pyToJavaConverters.get(cls);
            if (converter == null) {
                // FIXME: Not all target java types are supported yet.
                throw new IllegalArgumentException("cannot convert to " + cls);
            } else {
                return converter.apply(pyObj);
            }
        } else if (obj == null) {
            return null;
        } else if (cls.isInstance(obj)) {
            return cls.cast(obj);
        } else {
            throw new IllegalArgumentException("cannot convert to " + cls);
        }
    }

    // Memory managed execution of the given Runnable
    /**
     * Executes the given Runnable while tracking any PyObject instances created
     * during its execution. On exit, decref/cleanup is performed for the
     * tracked objects in a deterministic manner.
     *
     * @param r Runnable to run within a managed PyObject arena
     */
    public final void withPyObjectManager(Runnable r) {
        try (var pyArena = new PyObjectArena(this)) {
            ScopedValue.where(PY_OBJECTS_ARENA, pyArena).run(r);
        }
    }

    /**
     * Executes the given Runnable with potentially a new Python thread state.
     * On return from Runnable's run method call, Python thread state is
     * cleared. This is used to run Python script in a new thread and
     * clear Python thread state after completing the runnable. Without
     * this, Python thread state could be leaded by a Java thread till the
     * engine is closed. This can be used to clear Python thread state
     * sooner than engine closure.
     *
     * @param r Runnable to run with Python engine thread state
     */
    public final void withPyThreadState(Runnable r) {
        final boolean isVirtual = Thread.currentThread().isVirtual();
        if (isVirtual) {
            try {
                VirtualThreadHelper.invokeInCriticalSection(() -> {
                    final MemorySegment pyState;
                    synchronized (this) {
                        checkClosed();
                        pyState = getVirtualThreadPyThreadState();
                    }
                    withPyThreadStateInternal(pyState, r);
                    synchronized (this) {
                        if (! isClosed()) {
                            pyVirtualThreadState.remove();
                        }
                    }
                    return null;
                });
            } catch (Exception ex) {
                if (ex instanceof RuntimeException re) {
                    throw re;
                } else {
                    throw new RuntimeException(ex);
                }
            }
        } else {
            final MemorySegment pyState;
            synchronized (this) {
                checkClosed();
                pyState = getPlatformThreadPyThreadState();
            }
            withPyThreadStateInternal(pyState, r);
            synchronized (this) {
                if (! isClosed()) {
                    pyPlatformThreadState.remove();
                }
            }
        }
    }

    /**
     * Calls the given ScopedValue.CallableOp while tracking any PyObject
     * instances created during its execution. On exit, decref/cleanup is
     * performed for the tracked objects in a deterministic manner.
     *
     * @param <R> the type of the result of the operation
     * @param <X> the type of the exception that may be thrown by the operation
     * @param op ScopedValue.CallableOp to call within a managed PyObject arena
     * @return the return value from the ScopedValue.CallableOp call
     * @throws X the exception thrown by ScopedValue.Callable is propagated
     */
    public final <R, X extends Throwable> R withPyObjectManager(
            ScopedValue.CallableOp<? extends R, X> op) throws X {
        try (var pyArena = new PyObjectArena(this)) {
            return ScopedValue.where(PY_OBJECTS_ARENA, pyArena).call(op);
        }
    }

    // PyConstants methods
    @Override
    public PyConstant getNone() {
        return NONE;
    }

    @Override
    public PyConstant getFalse() {
        return FALSE;
    }

    @Override
    public PyConstant getTrue() {
        return TRUE;
    }

    @Override
    public PyConstant getEllipsis() {
        return ELLIPSIS;
    }

    @Override
    public PyConstant getNotImplemented() {
        return NOT_IMPLEMENTED;
    }

    // PyFactory methods
    @Override
    public PyDictionary newPyDictionary() throws ScriptException {
        return new PyDictionary(this);
    }

    @Override
    public PyList newPyList(PyObject... items) throws ScriptException {
        return new PyList(this, items);
    }

    @Override
    public PyTuple newPyTuple(PyObject... items) throws ScriptException {
        return new PyTuple(this, items);
    }

    // AutoClosable
    @Override
    public synchronized void close() {
        if (this.closed) {
            // already closed. Nothing to do!
            return;
        }
        closeAllDependentEngines();
        EngineLifeCycleManager.close(this);
        this.closed = true;
        this.pyInterpreterState = null;
        if (this.pyPlatformThreadState != null) {
            this.pyPlatformThreadState.remove();
        }
        this.pyPlatformThreadState = null;
        if (this.pyVirtualThreadState != null) {
            this.pyVirtualThreadState.remove();
        }
        this.pyVirtualThreadState = null;
        this.pyMethodDefMap.clear();
        this.engineArena = null;
        this.dependentEngines.clear();
    }

    // package-private helpers below this point

    /*
     * Create (or get from cache) PyMethodDef* for the given
     * PyJavaFunction.PyFunc callback (Python native function
     * implemented in Java).
     */
    synchronized MemorySegment getPyMethodDef(
            PyJavaFunction.Func func, String name, String doc) {
        // Cache management for CMethodDef* object for a given
        // PyJavaFunction.Func lambda
        return pyMethodDefMap.computeIfAbsent(func, (fn)
                -> createMethodDef(fn, name, doc));
    }

    boolean register(PyObject pyObj) {
        if (PY_OBJECTS_ARENA.isBound()) {
            PY_OBJECTS_ARENA.get().register(pyObj);
            return true;
        } else {
            return false;
        }
    }

    boolean unregister(PyObject pyObj) {
        if (PY_OBJECTS_ARENA.isBound()) {
            PY_OBJECTS_ARENA.get().unregister(pyObj);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Creates a new PythonScriptEngine. The first engine becomes the main
     * engine bound to the process-wide interpreter; subsequent calls create
     * sub-interpreters.
     *
     * @param fac factory that owns the engine
     * @return a new PythonScriptEngine instance
     */
    static PythonScriptEngine newEngine(PythonScriptEngineFactory fac) {
        return EngineLifeCycleManager.newEngine(fac);
    }

    synchronized PyObject wrap(MemorySegment pyObjPtr) {
        checkClosed();
        if (NULL.equals(pyObjPtr)) {
            return getNone();
        } else {
            // conservatively resize MemorySegment so that our code will
            // work even if jextract generates stricter mapping (zero size
            // memory segments for native pointers)
            pyObjPtr = pyObjPtr.reinterpret(org.openjdk.engine.python.bindings.PyObject.sizeof());
            // read the PyTypeObject* of this PyObject*
            var typePtr = org.openjdk.engine.python.bindings.PyObject.ob_type(pyObjPtr);

            // compare against the well-known PyTypeObject* values.
            if (typePtr.equals(EngineLifeCycleManager.getDictTypeAddr())) {
                return new PyDictionary(pyObjPtr, this);
            } else if (typePtr.equals(EngineLifeCycleManager.getListTypeAddr())) {
                return new PyList(pyObjPtr, this);
            } else if (typePtr.equals(EngineLifeCycleManager.getTupleTypeAddr())) {
                return new PyTuple(pyObjPtr, this);
            } else {
                // check for well known constant address - if not fallback
                // to creating a generic PyObject.
                PyObject constant = wellKnownConstants.get(pyObjPtr);
                return constant != null ? constant : new PyObject(pyObjPtr, this);
            }
        }
    }

    synchronized MemorySegment toPyObjectAddrNoLock(Object obj) {
        checkClosed();
        return fromJavaNoLock(obj);
    }

    synchronized MemorySegment toPyStringAddrNoLock(String str) {
        checkClosed();
        return fromJavaNoLock(str);
    }

    synchronized <T> T withLock(ThrowingSupplier<T> supplier) throws ScriptException {
        checkClosed();
        if (Thread.currentThread().isVirtual()) {
            try {
                if (PythonConfig.DEBUG) {
                    IO.println("VirtualThread.invokeInCriticalSection from " + Thread.currentThread());
                }
                return VirtualThreadHelper.invokeInCriticalSection(() -> {
                    var curThreadState = getVirtualThreadPyThreadState();
                    return withLockInternal(curThreadState, supplier);
                });
            } catch (Exception ex) {
                switch (ex) {
                    case ScriptException sx ->
                        throw sx;
                    case RuntimeException rx ->
                        throw rx;
                    default ->
                        throw new RuntimeException(ex);
                }
            }
        } else {
            return withLockInternal(getPlatformThreadPyThreadState(), supplier);
        }
    }

    synchronized PyObject eval(PyObject scriptObj, ScriptContext sc) throws ScriptException {
        return withLock(() -> evalUnlocked(scriptObj.addr(), sc));
    }

    static MemorySegment getNoneAddr() {
        return EngineLifeCycleManager.getNoneAddr();
    }

    static MemorySegment getFalseAddr() {
        return EngineLifeCycleManager.getFalseAddr();
    }

    static MemorySegment getTrueAddr() {
        return EngineLifeCycleManager.getTrueAddr();
    }

    static MemorySegment getEllipsisAddr() {
        return EngineLifeCycleManager.getEllipsisAddr();
    }

    static MemorySegment getNotImplementedAddr() {
        return EngineLifeCycleManager.getNotImplementedAddr();
    }

    void checkAndThrowPyExceptionNoLock() throws ScriptException {
        var pyExpAddr = PyErr_GetRaisedException();
        if (!NULL.equals(pyExpAddr)) {
            throw new PythonException(
                    wrap(pyExpAddr),
                    PyUnicode_AsUTF8(PyObject_Str(pyExpAddr)).getString(0));
        }
    }

    // add a dependent script engine to this engine
    synchronized void addDependentEngine(AbstractPythonScriptEngine engine) {
        dependentEngines.add(new WeakReference<>(engine));
    }

    // close all engines dependent on this engine
    synchronized void closeAllDependentEngines() {
        for (var depEngineRef : dependentEngines) {
            final var depEngine = depEngineRef.get();
            if (depEngine != null) {
                try {
                    depEngine.close();
                } catch (Throwable ignored) {
                }
            }
        }
        dependentEngines.clear();
    }

    // Internals only below this point
    private void withPyThreadStateInternal(MemorySegment curThreadState,  Runnable r) {
        try {
            r.run();
        } finally {
            synchronized (this) {
                if (! isClosed()) {
                    PyEval_AcquireThread(curThreadState);
                    PyThreadState_Clear(curThreadState);
                    PyThreadState_DeleteCurrent();
                }
            }
        }
    }

    // Helpers for getPyMethodDef package-private entry point

    // This is to ensure that we don't unnecessarily create PyThreadState*
    // for native threads from Python.
    private synchronized void setPyThreadState(MemorySegment tstate) {
        if (Thread.currentThread().isVirtual()) {
            if (this.pyVirtualThreadState == null) {
                if (PythonConfig.DEBUG) {
                    IO.println("initializing pyVirtualThreadState in setPyThreadState");
                }
                this.pyVirtualThreadState = VirtualThreadHelper.newCarrierThreadLocal();
            }
            if (PythonConfig.DEBUG) {
                IO.println("Setting pyVirtualThreadState in setPyThreadState");
            }
            this.pyVirtualThreadState.set(tstate);
        } else {
            if (this.pyPlatformThreadState == null) {
                if (PythonConfig.DEBUG) {
                    IO.println("initializing pyPlatformThreadState in getPlatformThreadPyThreadState");
                }
                this.pyPlatformThreadState = new PlatformThreadLocal();
            }
            if (PythonConfig.DEBUG) {
                IO.println("Setting pyPlatformThreadState in setPyThreadState");
            }
            this.pyPlatformThreadState.set(tstate);
        }
    }

    // Run the given callback with detached Python thread state and
    // restore it after the callback ends.
    private MemorySegment withPyThreadStateDetached(Supplier<MemorySegment> callback) {
        var tstate = PyEval_SaveThread();
        // If we are entering from a Python thread into Java callback
        // make sure that we save the thread state to avoid re-creating
        // PyThreaState object afresh.
        synchronized (this) {
            if (!tstate.equals(NULL)
                    && PyThreadState.interp(tstate).equals(pyInterpreterState)) {
                setPyThreadState(tstate);
            }
        }
        try {
            var resAddr = callback.get();
            // going back into Python. Restore thread state.
            PyEval_RestoreThread(tstate);
            return resAddr;
        } catch (Throwable th) {
            try (var arena = Arena.ofConfined()) {
                String msg;
                if (PythonConfig.JAVASTACK_IN_PYEXCEPTION) {
                    var sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    th.printStackTrace(pw);
                    msg = sw.toString();
                } else {
                    msg = th.toString();
                }
                var msgPtr = arena.allocateFrom(msg);
                PyEval_RestoreThread(tstate);
                // we should create Python exception object
                // only after thread state is restored!
                PyErr_SetString(PyExc_RuntimeError(), msgPtr);
                // return NULL to signal the interpreter that
                // we have set an exception
                return NULL;
            }
        }
    }

    // return address PyObject* from a java callback from Python
    private static MemorySegment returnAddress(PyObject pyRes) {
        if (pyRes == null) {
            return PythonScriptEngine.getNoneAddr();
        } else {
            // do not track the return value by possible
            // PyObjectArena in context
            return pyRes.unregister().addr();
        }
    }

    // create a new PyMethodDef* and initialise it with the callback,
    // name and doc string. The doc string may be null.
    private MemorySegment createMethodDef(
            PyJavaFunction.Func func, String name, String doc) {
        Objects.requireNonNull(func);
        Objects.requireNonNull(name);

        Arena allocator = this.engineArena;
        var pyMethodDef = PyMemberDef.allocate(allocator);
        PyMemberDef.name(pyMethodDef, allocator.allocateFrom(name));
        if (doc != null) {
            PyMethodDef.ml_doc(pyMethodDef, allocator.allocateFrom(doc));
        }

        MemorySegment pyCFuncPtr;
        switch (func) {
            case PyJavaFunction.NoArgFunc noArgFunc -> {
                PyMethodDef.ml_flags(pyMethodDef, METH_STATIC() | METH_NOARGS());
                pyCFuncPtr = PyCFunction.allocate((selfPtr, argPtr)
                        -> withPyThreadStateDetached(
                                () -> returnAddress(noArgFunc.call())),
                        allocator);
            }
            case PyJavaFunction.OneArgFunc oneArgFunc -> {
                PyMethodDef.ml_flags(pyMethodDef, METH_STATIC() | METH_O());
                pyCFuncPtr = PyCFunction.allocate((selfPtr, argPtr)
                        -> withPyThreadStateDetached(
                                () -> {
                                    // do not track the argument by possible
                                    // PyObjectArena in context. This is borrowed refs
                                    // and so should not be decremented.
                                    final PyObject pyArg = wrap(argPtr).unregister();
                                    return returnAddress(oneArgFunc.call(pyArg));
                                }),
                        allocator);
            }
            case PyJavaFunction.VarArgsFunc varArgsFunc -> {
                PyMethodDef.ml_flags(pyMethodDef, METH_STATIC() | METH_FASTCALL());
                pyCFuncPtr = _PyCFunctionFast.allocate((selfPtr, argsPtr, count)
                        -> withPyThreadStateDetached(
                                () -> {
                                    final PyObject[] pyArgs = new PyObject[(int) count];
                                    for (int i = 0; i < pyArgs.length; i++) {
                                        // do not track the arguments by possible
                                        // PyObjectArena in context. These are borrowed refs
                                        // and so should not be decremented.
                                        pyArgs[i] = wrap(argsPtr.getAtIndex(C_POINTER, i)).
                                                unregister();
                                    }
                                    return returnAddress(varArgsFunc.call(pyArgs));
                                }),
                        allocator);
            }

        }

        PyMethodDef.ml_meth(pyMethodDef, pyCFuncPtr);
        return pyMethodDef;
    }

    private class PlatformThreadLocal extends ThreadLocal<MemorySegment> {

        @Override
        protected MemorySegment initialValue() {
            if (PythonConfig.DEBUG) {
                IO.println("PyThreadState_New in PlatformThreadLocal.initialValue");
            }
            return PyThreadState_New(PythonScriptEngine.this.pyInterpreterState);
        }
    }

    private <T> T withLockInternal(MemorySegment curThreadState, ThrowingSupplier<T> supplier)
            throws ScriptException {
        PyEval_AcquireThread(curThreadState);
        try {
            T res = supplier.get();
            checkAndThrowPyExceptionNoLock();
            return res;
        } finally {
            PyEval_ReleaseThread(curThreadState);
        }
    }

    private synchronized MemorySegment getVirtualThreadPyThreadState() {
        assert Thread.currentThread().isVirtual() :
                "getVirtualThreadPyThreadState used in non-virtual thread";

        if (this.pyVirtualThreadState == null) {
            if (PythonConfig.DEBUG) {
                IO.println("Initializing pyVirtualThreadState in getVirtualThreadPyThreadState");
            }
            this.pyVirtualThreadState = VirtualThreadHelper.newCarrierThreadLocal();
        }
        var pyState = this.pyVirtualThreadState.get();
        if (pyState == null) {
            if (PythonConfig.DEBUG) {
                IO.println("PyThreadState_New in getVirtualThreadPyThreadState");
            }
            pyState = PyThreadState_New(this.pyInterpreterState);
            this.pyVirtualThreadState.set(pyState);
        }
        return pyState;
    }

    private synchronized MemorySegment getPlatformThreadPyThreadState() {
        assert !Thread.currentThread().isVirtual() :
                "getPlatformThreadPyThreadState used in virtual thread";

        if (this.pyPlatformThreadState == null) {
            if (PythonConfig.DEBUG) {
                IO.println("initializing pyPlatformThreadState in getPlatformThreadPyThreadState");
            }
            this.pyPlatformThreadState = new PlatformThreadLocal();
        }
        return this.pyPlatformThreadState.get();
    }

    private void setSysPath() throws ScriptException {
        String prependPath = PythonConfig.SYS_PREPEND_PATH;
        String appendPath = PythonConfig.SYS_APPEND_PATH;
        if (prependPath == null && appendPath == null) {
            // nothing to do
            return;
        }

        // prepend empty string sys.path
        withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                var sysPath = PySys_GetObject(arena.allocateFrom("path"));  // Borrowed
                if (sysPath.equals(NULL)) {
                    throw new RuntimeException("sys.path does not exist??");
                }

                if (prependPath != null) {
                    var prepend = PyUnicode_FromString(arena.allocateFrom(prependPath));
                    checkAndThrowPyExceptionNoLock();
                    PyList_Insert(sysPath, 0, prepend);
                    checkAndThrowPyExceptionNoLock();
                    Py_DecRef(prepend);
                }

                if (appendPath != null) {
                    var append = PyUnicode_FromString(arena.allocateFrom(appendPath));
                    checkAndThrowPyExceptionNoLock();
                    PyList_Append(sysPath, append);
                    checkAndThrowPyExceptionNoLock();
                    Py_DecRef(append);
                }

                return (Void) null;
            }
        });
    }

    private <T> T implementInterface(Class<T> iface, PyObject pyObj) {
        Objects.requireNonNull(pyObj);
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(),
                new Class[]{iface},
                (proxy, method, args) -> {
                    if (args == null) {
                        args = new Object[0];
                    }
                    Object res = pyObj.callMethod(method.getName(), args);
                    return toJava(res, method.getReturnType());
                }
        ));
    }

    private <T> T implementInterface(Class<T> iface, PyDictionary pyDict) {
        Objects.requireNonNull(pyDict);
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(),
                new Class[]{iface},
                (proxy, method, args) -> {
                    if (args == null) {
                        args = new Object[0];
                    }
                    PyObject func = pyDict.getItem(method.getName()).unregister();
                    try {
                        Object res = func.call(args);
                        return toJava(res, method.getReturnType());
                    } finally {
                        func.destroy();
                    }
                }
        ));
    }

    // create the engine backed by the main interpreter
    private final class EngineLifeCycleManager {

        private static PythonScriptEngine theEngine;

        // only a subset of immortal constants
        private static MemorySegment NONE;
        private static MemorySegment FALSE;
        private static MemorySegment TRUE;
        private static MemorySegment ELLIPSIS;
        private static MemorySegment NOT_IMPLEMENTED;

        // few well-nown Type objects
        private static MemorySegment DICT_TYPE;
        private static MemorySegment LIST_TYPE;
        private static MemorySegment TUPLE_TYPE;

        private static PythonScriptEngine newMainEngineInternal(PythonScriptEngineFactory fac) {
            String pythonProgram = PythonConfig.getProgramName();
            if (pythonProgram != null) {
                if (PythonConfig.DEBUG) {
                    IO.println("Setting python program name to be " + pythonProgram);
                }
                try (var arena = Arena.ofConfined()) {
                    var config = PyConfig.allocate(arena);
                    try {
                        // default initialize the config
                        PyConfig_InitPythonConfig(config);
                        // don't install signal handlers
                        PyConfig.install_signal_handlers(config, 0);
                        // set PyConfig's program_name
                        var progNamePtr = arena.allocateFrom(pythonProgram);
                        PyConfig_SetBytesString(arena, config,
                                config.asSlice(PyConfig.program_name$offset()), progNamePtr);

                        // Let python auto-discover the rest
                        PyConfig.module_search_paths_set(config, 0);

                        // Initialize main interpreter from config
                        var status = Py_InitializeFromConfig(arena, config);
                        if (PyStatus_Exception(status) != 0) {
                            System.err.println("Failed to initialize Python from config");
                            throw new RuntimeException("cannot initialize Python from config");
                        }
                    } finally {
                        PyConfig_Clear(config);
                    }
                }
            } else {
                // can't find python program path. use the default init.
                // 0 is passed to tell python not to install signal handlers.
                Py_InitializeEx(0);
            }

            NONE = _Py_NoneStruct();
            FALSE = _Py_FalseStruct();
            TRUE = _Py_TrueStruct();
            ELLIPSIS = _Py_EllipsisObject();
            NOT_IMPLEMENTED = _Py_NotImplementedStruct();

            DICT_TYPE = PyDict_Type();
            LIST_TYPE = PyList_Type();
            TUPLE_TYPE = PyTuple_Type();

            return new PythonScriptEngine(fac, PyEval_SaveThread(), true);
        }

        // create a new engine backed by a sub-interpreter
        private static PythonScriptEngine newSubEngineInternal(ScriptEngineFactory fac) {
            try (var arena = Arena.ofConfined()) {
                var config = PyInterpreterConfig.allocate(arena);
                // own GIL or not
                PyInterpreterConfig.gil(config, GIL_MODE);
                // few settings set as true
                PyInterpreterConfig.check_multi_interp_extensions(config, 1);
                if (GIL_MODE == PyInterpreterConfig_OWN_GIL()) {
                    // Do not use obmalloc with own-GIL mode. Interpreter
                    // crashes when trying to eval script.
                    PyInterpreterConfig.use_main_obmalloc(config, 0);
                } else {
                    PyInterpreterConfig.use_main_obmalloc(config, 1);
                }
                PyInterpreterConfig.allow_threads(config, 1);
                PyInterpreterConfig.allow_daemon_threads(config, 1);

                // PyThreadState** threadStatePtrPtr;
                var threadStatePtrPtr = arena.allocate(C_POINTER);
                // PyStatus status;
                var status = Py_NewInterpreterFromConfig(arena, threadStatePtrPtr, config);
                if (PyStatus_Exception(status) != 0) {
                    throw new RuntimeException("cannot create new interpreter from config");
                }

                // PyThreadState* threadStatePtr = *ThreadStatePtrPtr;
                var threadStatePtr = threadStatePtrPtr.get(C_POINTER, 0);
                if (NULL.equals(threadStatePtr)) {
                    // should not happen as we have checked error state?!
                    throw new IllegalStateException("null new thread state!");
                }
                return new PythonScriptEngine(fac, PyEval_SaveThread(), false);
            }
        }

        private static PythonScriptEngine newEngineInternal(PythonScriptEngineFactory fac) {
            synchronized (EngineLifeCycleManager.class) {
                if (theEngine == null) {
                    theEngine = newMainEngineInternal(fac);
                    return theEngine;
                }
            }
            final PythonScriptEngine subEngine = newSubEngineInternal(fac);
            theEngine.addDependentEngine(subEngine);
            return subEngine;
        }

        private static void closeInternal(PythonScriptEngine engine,
                MemorySegment curThreadState) {
            PyEval_AcquireThread(curThreadState);
            synchronized (EngineLifeCycleManager.class) {
                if (theEngine == engine) {
                    if (Py_FinalizeEx() != 0) {
                        throw new IllegalStateException("Main engine close failed. Py_FinalizeEx failed!");
                    }

                    NONE = MemorySegment.NULL;
                    FALSE = MemorySegment.NULL;
                    TRUE = MemorySegment.NULL;
                    ELLIPSIS = MemorySegment.NULL;
                    NOT_IMPLEMENTED = MemorySegment.NULL;

                    DICT_TYPE = MemorySegment.NULL;
                    LIST_TYPE = MemorySegment.NULL;
                    TUPLE_TYPE = MemorySegment.NULL;

                    theEngine = null;
                } else {
                    Py_EndInterpreter(curThreadState);
                }
            }
        }

        static void close(PythonScriptEngine engine) {
            final var isVirtual = Thread.currentThread().isVirtual();
            if (isVirtual) {
                try {
                    if (PythonConfig.DEBUG) {
                        IO.println("VirtualThread.invokeInCriticalSection from " + Thread.currentThread());
                    }
                    VirtualThreadHelper.invokeInCriticalSection(() -> {
                        var curThreadState = engine.getVirtualThreadPyThreadState();
                        closeInternal(engine, curThreadState);
                        return null;
                    });
                } catch (Exception ex) {
                    switch (ex) {
                        case RuntimeException rx ->
                            throw rx;
                        default ->
                            throw new RuntimeException(ex);
                    }
                }
            } else {
                closeInternal(engine, engine.getPlatformThreadPyThreadState());
            }
        }

        static PythonScriptEngine newEngine(PythonScriptEngineFactory fac) {
            final var isVirtual = Thread.currentThread().isVirtual();
            if (isVirtual) {
                try {
                    if (PythonConfig.DEBUG) {
                        IO.println("VirtualThread.invokeInCriticalSection from " + Thread.currentThread());
                    }
                    return VirtualThreadHelper.invokeInCriticalSection(() -> {
                        return newEngineInternal(fac);
                    });
                } catch (Exception ex) {
                    switch (ex) {
                        case RuntimeException rx ->
                            throw rx;
                        default ->
                            throw new RuntimeException(ex);
                    }
                }
            } else {
                return newEngineInternal(fac);
            }
        }

        static synchronized MemorySegment getNoneAddr() {
            return NONE;
        }

        static synchronized MemorySegment getFalseAddr() {
            return FALSE;
        }

        static synchronized MemorySegment getTrueAddr() {
            return TRUE;
        }

        static synchronized MemorySegment getEllipsisAddr() {
            return ELLIPSIS;
        }

        static synchronized MemorySegment getNotImplementedAddr() {
            return NOT_IMPLEMENTED;
        }

        static synchronized MemorySegment getDictTypeAddr() {
            return DICT_TYPE;
        }

        static synchronized MemorySegment getListTypeAddr() {
            return LIST_TYPE;
        }

        static synchronized MemorySegment getTupleTypeAddr() {
            return TUPLE_TYPE;
        }
    }

    private static MemorySegment fromJavaNoLock(String str) {
        Objects.requireNonNull(str);
        try (var arena = Arena.ofConfined()) {
            return PyUnicode_FromString(arena.allocateFrom(str));
        }
    }

    private static MemorySegment fromJavaNoLock(long l) {
        return PyLong_FromLongLong(l);
    }

    private static MemorySegment fromJavaNoLock(double d) {
        return PyFloat_FromDouble(d);
    }

    private static MemorySegment fromJavaNoLock(boolean b) {
        return b ? getTrueAddr() : getFalseAddr();
    }

    /**
     * Converts a supported Java object to a Python PyObject* for this engine.
     * Existing PyObject instances must belong to this engine, otherwise an
     * IllegalArgumentException is thrown.
     *
     * Ownership: for newly created objects, the caller owns a new reference.
     * For singletons (None/True/False), a borrowed singleton address is
     * returned.
     *
     * GIL: Must be called under withLock.
     *
     * @param obj Java value or PyObject
     * @return PyObject* address representing the value
     * @throws IllegalArgumentException if a PyObject belongs to a different
     * engine
     * @throws RuntimeException if the type is not supported
     */
    private MemorySegment fromJavaNoLock(Object obj) {
        if (obj == null) {
            return getNoneAddr();
        } else if (obj instanceof PyObject) {
            throw new AssertionError("PyObject not expected here");
        } else if (obj instanceof PyJavaFunction.Func func) {
            return PyJavaFunction.createPyCFunctionNoLock(this, func, func.toString(), null);
        } else {
            var converter = javaToPyConverters.get(obj.getClass());
            if (converter == null) {
                // FIXME: Not all Java objects are supported yet.
                throw new IllegalArgumentException("cannot convert: " + obj);
            } else {
                return converter.apply(obj);
            }
        }
    }

    private static final Map<Class<?>, Function<Object, MemorySegment>> javaToPyConverters = new HashMap<>();

    static {
        javaToPyConverters.put(Byte.class, obj -> fromJavaNoLock(((Number) obj).longValue()));
        javaToPyConverters.put(Short.class, obj -> fromJavaNoLock(((Number) obj).longValue()));
        javaToPyConverters.put(Integer.class, obj -> fromJavaNoLock(((Number) obj).longValue()));
        javaToPyConverters.put(Long.class, obj -> fromJavaNoLock(((Number) obj).longValue()));
        javaToPyConverters.put(Float.class, obj -> fromJavaNoLock(((Number) obj).doubleValue()));
        javaToPyConverters.put(Double.class, obj -> fromJavaNoLock(((Number) obj).doubleValue()));
        javaToPyConverters.put(Boolean.class, obj -> fromJavaNoLock((boolean) obj));
        javaToPyConverters.put(Character.class, obj -> fromJavaNoLock(((Character) obj).toString()));
        javaToPyConverters.put(String.class, obj -> fromJavaNoLock((String) obj));
    }

    private static final Map<Class<?>, ThrowingFunction<PyObject, Object>> pyToJavaConverters = new HashMap<>();

    static {
        pyToJavaConverters.put(Byte.class, pyObj -> (byte) pyObj.toLong());
        pyToJavaConverters.put(byte.class, pyObj -> (byte) pyObj.toLong());
        pyToJavaConverters.put(Short.class, pyObj -> (short) pyObj.toLong());
        pyToJavaConverters.put(short.class, pyObj -> (short) pyObj.toLong());
        pyToJavaConverters.put(Integer.class, pyObj -> (int) pyObj.toLong());
        pyToJavaConverters.put(int.class, pyObj -> (int) pyObj.toLong());
        pyToJavaConverters.put(Long.class, PyObject::toLong);
        pyToJavaConverters.put(long.class, PyObject::toLong);
        pyToJavaConverters.put(Float.class, pyObj -> (float) pyObj.toDouble());
        pyToJavaConverters.put(float.class, pyObj -> (float) pyObj.toDouble());
        pyToJavaConverters.put(Double.class, PyObject::toDouble);
        pyToJavaConverters.put(double.class, PyObject::toDouble);
        pyToJavaConverters.put(Boolean.class, PyObject::isTrue);
        pyToJavaConverters.put(boolean.class, PyObject::isTrue);
        pyToJavaConverters.put(Character.class, PyObject::toChar);
        pyToJavaConverters.put(char.class, PyObject::toChar);
        pyToJavaConverters.put(String.class, PyObject::toString);
        pyToJavaConverters.put(Void.class, pyObj -> null);
        pyToJavaConverters.put(void.class, pyObj -> null);
    }

    private boolean isCallable(PyObject pyObj) {
        try {
            return pyObj != null && !pyObj.isNone() && pyObj.isCallable();
        } catch (ScriptException se) {
            if (PythonConfig.DEBUG) {
                IO.println("isCallable failed!");
                se.printStackTrace(System.out);
            }
            return false;
        }
    }

    private PyObject compile(String script, ScriptContext sc) throws ScriptException {
        CompilationState cs = newCompilationState(script, sc);
        return withLock(() -> wrap(compileUnlocked(cs)));
    }

    /**
     * Evaluates a compiled Python code object against the provided globals.
     * Caller must have acquired the GIL via withLock. On error, raises a
     * ScriptException by inspecting the Python exception state.
     *
     * @param compiled PyObject* address for the compiled code object
     * @param sc ScriptContext providing globals/builtins
     * @return wrapped evaluation result
     * @throws ScriptException if evaluation fails
     */
    private PyObject evalUnlocked(MemorySegment compiled, ScriptContext sc) throws ScriptException {
        var res = PyEval_EvalCode(compiled, getPyDictionary(sc).addr(), NULL);
        checkAndThrowPyExceptionNoLock();
        return wrap(res);
    }

    /**
     * Compiles Python source to a code object using CPython C-API without
     * acquiring the GIL (caller must already hold it). Returns the raw
     * PyObject* address of the code object; the caller owns the reference and
     * must decref when done.
     *
     * @param cs immutable compilation state (script, name, mode)
     * @return PyObject* address of compiled code
     * @throws ScriptException if compilation fails
     */
    private MemorySegment compileUnlocked(CompilationState cs) throws ScriptException {
        try (var arena = Arena.ofConfined()) {
            var scriptptr = arena.allocateFrom(cs.script());
            var nameptr = arena.allocateFrom(cs.name());

            var pyObjAddr = Py_CompileString(scriptptr, nameptr, cs.mode().code());
            if (NULL.equals(pyObjAddr)) {
                checkAndThrowPyExceptionNoLock();
            }
            return pyObjAddr;
        }
    }
}
