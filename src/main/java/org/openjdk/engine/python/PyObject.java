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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.script.ScriptException;

import static java.lang.foreign.MemorySegment.NULL;
import static org.openjdk.engine.python.bindings.Python_h.*;
import org.openjdk.engine.python.bindings._object;
import org.openjdk.engine.python.bindings.Py_buffer;

/**
 * Base Java wrapper around a CPython PyObject*.
 * <p>
 * Instances of this class represent Python objects owned by a particular
 * {@link PythonScriptEngine}. Arguments of type {@link PyObject} passed to
 * methods of this class must originate from the same engine instance; otherwise
 * an{@link IllegalArgumentException} is thrown.
 */
public sealed class PyObject permits PyConstant, PyDictionary, PyList, PyTuple, PyJavaFunction {
    /**
     * The Python script engine from which this Python object is associated with.
     */
    protected final PythonScriptEngine pyEngine;
    // underlying PyObject*
    private volatile MemorySegment pyAddr;
    private volatile boolean engineManaged;

    /**
     * Constructs a wrapper around an existing native PyObject*.
     *
     * @param pyAddr   non-null, non-NULL pointer to a CPython object
     * @param pyEngine engine that owns the object
     * @throws NullPointerException if arguments are null or the pointer is NULL
     */
    PyObject(/* PyObject* */MemorySegment pyAddr, PythonScriptEngine pyEngine) {
        this.pyAddr = Objects.requireNonNull(pyAddr, "MemorySegment is null");
        this.pyEngine = Objects.requireNonNull(pyEngine, "PythonScriptEngine is null");
        if (NULL.equals(this.pyAddr)) {
            throw new NullPointerException("MemorySegment address is NULL");
        }
        this.engineManaged = register();
    }

    /**
     * Returns the {@link PythonScriptEngine} that owns this object.
     *
     * @return owning engine
     */
    public final PythonScriptEngine getEngine() {
        return pyEngine;
    }

    /**
     * Is this a well-known Python constant like True, None etc?
     *
     * @return true is this PyObject is a constant, false otherwise.
     */
    public boolean isConstant() {
        return false;
    }

    /**
     * Returns the current native reference count of the underlying PyObject.
     *
     * @return reference count
     * @throws RuntimeException if a ScriptException occurs (should not happen)
     */
    public final long getRefCount() {
        try {
            // !!!HACK!!! - internal + does not work when GIL disabled build
            return pyEngine.withLock(() -> _object.ob_refcnt(addr()));
        } catch (ScriptException se) {
            // cannot happen
            throw new RuntimeException(se);
        }
    }

    /**
     * Decrements the native reference count, potentially invalidating this wrapper
     * when it reaches zero. No-op for engine-managed objects.
     */
    public void decRefCount() {
        if (this.engineManaged) {
            // lifetime managed by the script engine
            return;
        }
        try {
            this.pyEngine.withLock(() -> {
                // !!!HACK!!! - internal + does not work when GIL disabled build
                long oldCount = _object.ob_refcnt(addr());
                if (oldCount == 0) {
                    throw new IllegalStateException("ref count is already zero!");
                }
                Py_DecRef(addr());
                if (oldCount == 1) {
                    this.makeItNone();
                }
                return (Void) null;
            });
        } catch (ScriptException ignored) {
            // cannot happen
        }
    }

    /**
     * Increments the native reference count. No-op for engine-managed objects.
     */
    public void incRefCount() {
        if (this.engineManaged) {
            // lifetime managed by the script engine
            return;
        }
        try {
            this.pyEngine.withLock(() -> {
                Py_IncRef(addr());
                return (Void) null;
            });
        } catch (ScriptException ignored) {
            // cannot happen
        }
    }

    /**
     * Object identity check equivalent to Python's {@code x is y}.
     *
     * @param other other PyObject to compare identity with
     * @return true if both wrap the same native address
     */
    public final boolean is(PyObject other) {
        return other != null && addr().equals(other.addr());
    }

    /**
     * Object non-identity check equivalent to Python's {@code x is not y}.
     *
     * @param other other PyObject to compare identity with
     * @return true if the native addresses differ
     */
    public final boolean isNot(PyObject other) {
        return !is(other);
    }

    /**
     * Returns the native address of the underlying PyObject (akin to Python id()).
     *
     * @return native address
     */
    public final long id() {
        return addr().address();
    }

    // conversion methods
    /**
     * Converts this object to a Java long using Python's int conversion.
     *
     * @return converted long value
     * @throws ScriptException if conversion fails
     */
    public final long toLong() throws ScriptException {
        return pyEngine.withLock(() -> {
            return PyLong_AsLongLong(addr());
        });
    }

    /**
     * Converts this object to a Java double using Python's float conversion.
     *
     * @return converted double value
     * @throws ScriptException if conversion fails
     */
    public final double toDouble() throws ScriptException {
        return pyEngine.withLock(() -> {
            return PyFloat_AsDouble(addr());
        });
    }

    /**
     * Converts this object to a Java char by converting to str and validating length 1.
     *
     * @return single character
     * @throws ScriptException if conversion fails or the string length is not 1
     */
    public final char toChar() throws ScriptException {
        var str = toString();
        if (str.length() != 1) {
            throw new ScriptException("string length is not 1");
        }
        return str.charAt(0);
    }


    /**
     * Returns whether this object is callable (Python's {@code callable(obj)}).
     *
     * @return true if callable
     * @throws ScriptException if the Python check fails
     */
    public final boolean isCallable() throws ScriptException {
        return pyEngine.withLock(() -> {
            return PyCallable_Check(addr()) != 0;
        });
    }

    /**
     * Calls this callable with the provided PyObject arguments.
     *
     * @param pyArgs positional arguments (must belong to same engine)
     * @return result as PyObject
     * @throws ScriptException if the call fails
     * @throws IllegalArgumentException if any argument belongs to a different engine
     */
    public final PyObject call(PyObject... pyArgs) throws ScriptException {
        checkEngine(pyArgs);
        return switch (pyArgs.length) {
            case 0 ->
                pyEngine.withLock(() -> {
                    return pyEngine.wrap(PyObject_CallNoArgs(this.addr()));
                });

            case 1 ->
                pyEngine.withLock(() -> {
                    return pyEngine.wrap(PyObject_CallOneArg(this.addr(), pyArgs[0].addr()));
                });

            default -> {
                var invoker = CallFunctionInvoker.get(pyArgs.length + 1);
                // convert arguments
                Object[] ptrArgs = asPointerArgs(pyArgs);
                yield pyEngine.withLock(() -> {
                    return pyEngine.wrap(invoker.apply(this.addr(), ptrArgs));
                });
            }
        };
    }

    /**
     * Calls this callable with arbitrary Java arguments, converting them to Python objects.
     *
     * @param args Java arguments
     * @return result as PyObject
     * @throws ScriptException if conversion or call fails
     */
    public final PyObject call(Object... args) throws ScriptException {
        PyObject[] pyArgs = toPyObjects(args);
        try {
            return call(pyArgs);
        } finally {
            decRefs(pyArgs);
        }
    }

    /**
     * Calls the named method on this object with PyObject arguments.
     *
     * @param name   method name
     * @param pyArgs positional arguments (must belong to same engine)
     * @return result as PyObject
     * @throws ScriptException if the call fails
     * @throws IllegalArgumentException if any argument belongs to a different engine
     */
    public final PyObject callMethod(String name, PyObject... pyArgs) throws ScriptException {
        checkEngine(pyArgs);
        var invoker = CallMethodInvoker.get(pyArgs.length + 1);
        // convert arguments
        Object[] ptrArgs = asPointerArgs(pyArgs);
        return pyEngine.withLock(() -> {
            MemorySegment nameAddr = pyEngine.toPyStringAddrNoLock(name);
            pyEngine.checkAndThrowPyExceptionNoLock();
            try {
                return pyEngine.wrap(invoker.apply(this.addr(), nameAddr, ptrArgs));
            } finally {
                Py_DecRef(nameAddr);
            }
        });
    }

    /**
     * Calls the named method on this object with Java arguments (converted to PyObjects).
     *
     * @param name method name
     * @param args Java arguments
     * @return result as PyObject
     * @throws ScriptException if conversion or call fails
     */
    public final PyObject callMethod(String name, Object... args) throws ScriptException {
        PyObject[] pyArgs = toPyObjects(args);
        try {
            return callMethod(name, pyArgs);
        } finally {
            decRefs(pyArgs);
        }
    }

    // attribute management - has, get, set and delete
    /**
     * Returns whether this object has the given attribute.
     *
     * @param name attribute name as PyObject (must belong to same engine)
     * @return true if attribute exists, false otherwise
     * @throws ScriptException if the check fails
     */
    public final boolean hasAttribute(PyObject name) throws ScriptException {
        checkEngine(name);
        return pyEngine.withLock(() -> {
            return PyObject_HasAttrWithError(this.addr(), name.addr()) == 1;
        });
    }

    /**
     * Returns whether this object has the given attribute.
     *
     * @param name attribute name (non-null)
     * @return true if attribute exists, false otherwise
     * @throws ScriptException if the check fails
     */
    public final boolean hasAttribute(String name) throws ScriptException {
        Objects.requireNonNull(name);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                return PyObject_HasAttrWithError(this.addr(), arena.allocateFrom(name)) == 1;
            }
        });
    }

    /**
     * Gets the value of the named attribute.
     *
     * @param name attribute name as PyObject (must belong to same engine)
     * @return attribute value as PyObject
     * @throws ScriptException if retrieval fails
     */
    public final PyObject getAttribute(PyObject name) throws ScriptException {
        checkEngine(name);
        return pyEngine.withLock(() -> {
            return pyEngine.wrap(PyObject_GetAttr(this.addr(), name.addr()));
        });
    }

    /**
     * Gets the value of the named attribute.
     *
     * @param name attribute name (non-null)
     * @return attribute value as PyObject
     * @throws ScriptException if retrieval fails
     */
    public final PyObject getAttribute(String name) throws ScriptException {
        Objects.requireNonNull(name);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                return pyEngine.wrap(PyObject_GetAttrString(this.addr(), arena.allocateFrom(name)));
            }
        });
    }

    /**
     * Gets the value of the named attribute, returning Python None if absent.
     *
     * @param name attribute name as PyObject (must belong to same engine)
     * @return attribute value or Python None
     * @throws ScriptException if retrieval fails
     */
    public final PyObject getAttributeOptional(PyObject name) throws ScriptException {
        checkEngine(name);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                MemorySegment pyObjPtrPtr = arena.allocate(C_POINTER);
                PyObject_GetOptionalAttr(this.addr(), name.addr(), pyObjPtrPtr);
                pyEngine.checkAndThrowPyExceptionNoLock();
                return pyEngine.wrap(pyObjPtrPtr.get(C_POINTER, 0));
            }
        });
    }

    /**
     * Gets the value of the named attribute, returning Python None if absent.
     *
     * @param name attribute name (non-null)
     * @return attribute value or Python None
     * @throws ScriptException if retrieval fails
     */
    public final PyObject getAttributeOptional(String name) throws ScriptException {
        Objects.requireNonNull(name);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                MemorySegment pyObjPtrPtr = arena.allocate(C_POINTER);
                PyObject_GetOptionalAttrString(this.addr(), arena.allocateFrom(name), pyObjPtrPtr);
                pyEngine.checkAndThrowPyExceptionNoLock();
                return pyEngine.wrap(pyObjPtrPtr.get(C_POINTER, 0));
            }
        });
    }

    /**
     * Sets the named attribute to the specified value.
     *
     * @param name  attribute name as PyObject (same engine)
     * @param pyObj value as PyObject (same engine)
     * @return true on success
     * @throws ScriptException if setting fails
     */
    public final boolean setAttribute(PyObject name, PyObject pyObj) throws ScriptException {
        checkEngine(name, pyObj);
        return pyEngine.withLock(() -> {
            boolean result = PyObject_SetAttr(this.addr(), name.addr(), pyObj.addr()) == 0;
            pyEngine.checkAndThrowPyExceptionNoLock();
            return result;
        });
    }

    /**
     * Sets the named attribute to the specified Java value (converted to PyObject).
     *
     * @param name attribute name as PyObject (same engine)
     * @param obj  Java value to convert
     * @return true on success
     * @throws ScriptException if conversion or setting fails
     */
    public final boolean setAttribute(PyObject name, Object obj) throws ScriptException {
        if (obj instanceof PyObject pyObj) {
            return setAttribute(name, pyObj);
        }
        Objects.requireNonNull(name);
        PyObject pyObj = pyEngine.fromJava(obj).unregister();
        try {
            return setAttribute(name, pyObj);
        } finally {
            pyObj.destroy();
        }
    }

    /**
     * Sets the named attribute to the specified value.
     *
     * @param name  attribute name (non-null)
     * @param pyObj value as PyObject (same engine)
     * @return true on success
     * @throws ScriptException if setting fails
     */
    public final boolean setAttribute(String name, PyObject pyObj) throws ScriptException {
        Objects.requireNonNull(name);
        checkEngine(pyObj);
        return pyEngine.withLock(() -> {
            var namePtr = pyEngine.toPyStringAddrNoLock(name);
            pyEngine.checkAndThrowPyExceptionNoLock();
            try {
                boolean result = PyObject_SetAttr(this.addr(), namePtr, pyObj.addr()) == 0;
                pyEngine.checkAndThrowPyExceptionNoLock();
                return result;
            } finally {
                Py_DecRef(namePtr);
            }
        });
    }

    /**
     * Sets the named attribute to the specified Java value (converted to PyObject).
     *
     * @param name attribute name (non-null)
     * @param obj  Java value to convert
     * @return true on success
     * @throws ScriptException if conversion or setting fails
     */
    public final boolean setAttribute(String name, Object obj) throws ScriptException {
        if (obj instanceof PyObject pyObj) {
            return setAttribute(name, pyObj);
        }
        Objects.requireNonNull(name);
        PyObject pyObj = pyEngine.fromJava(obj).unregister();
        try {
            return setAttribute(name, pyObj);
        } finally {
            pyObj.destroy();
        }
    }

    /**
     * Deletes the named attribute.
     *
     * @param name attribute name as PyObject (same engine)
     * @return true on success
     * @throws ScriptException if deletion fails
     */
    public final boolean deleteAttribute(PyObject name) throws ScriptException {
        checkEngine(name);
        return pyEngine.withLock(() -> {
            return PyObject_DelAttr(this.addr(), name.addr()) == 0;
        });
    }

    /**
     * Deletes the named attribute.
     *
     * @param name attribute name (non-null)
     * @return true on success
     * @throws ScriptException if deletion fails
     */
    public final boolean deleteAttribute(String name) throws ScriptException {
        Objects.requireNonNull(name);
        return pyEngine.withLock(() -> {
            var namePtr = pyEngine.toPyStringAddrNoLock(name);
            pyEngine.checkAndThrowPyExceptionNoLock();
            return PyObject_DelAttr(this.addr(), namePtr) == 0;
        });
    }

    // get, set and delete items
    /**
     * Gets the value mapped to the given key (Python {@code obj[key]}).
     *
     * @param key key as PyObject (same engine)
     * @return mapped value as PyObject
     * @throws ScriptException if retrieval fails
     */
    public PyObject getItem(PyObject key) throws ScriptException {
        checkEngine(key);
        return pyEngine.withLock(() -> {
            return pyEngine.wrap(PyObject_GetItem(this.addr(), key.addr()));
        });
    }

    /**
     * Gets the value mapped to the given string key.
     *
     * @param key key string (non-null)
     * @return mapped value as PyObject
     * @throws ScriptException if retrieval or conversion fails
     */
    public PyObject getItem(String key) throws ScriptException {
        Objects.requireNonNull(key);
        PyObject keyObj = pyEngine.fromJava(key).unregister();
        try {
            return getItem(keyObj);
        } finally {
            keyObj.destroy();
        }
    }

    /**
     * Sets the mapping {@code this[key] = val}.
     *
     * @param key key as PyObject (same engine)
     * @param val value as PyObject (same engine)
     * @return true on success
     * @throws ScriptException if setting fails
     */
    public boolean setItem(PyObject key, PyObject val) throws ScriptException {
        checkEngine(key, val);
        return pyEngine.withLock(() -> {
            boolean result = PyObject_SetItem(this.addr(), key.addr(), val.addr()) == 0;
            pyEngine.checkAndThrowPyExceptionNoLock();
            return result;
        });
    }

    /**
     * Sets the mapping {@code this[key] = val} for a string key.
     *
     * @param key key string (non-null)
     * @param val value as PyObject (same engine)
     * @return true on success
     * @throws ScriptException if setting fails
     */
    public boolean setItem(String key, PyObject val) throws ScriptException {
        Objects.requireNonNull(key);
        PyObject keyObj = pyEngine.fromJava(key).unregister();
        try {
            return setItem(keyObj, val);
        } finally {
            keyObj.destroy();
        }
    }

    /**
     * Sets the mapping {@code this[key] = val} for a string key, converting the Java value.
     *
     * @param key key string (non-null)
     * @param val Java value to convert
     * @return true on success
     * @throws ScriptException if conversion or setting fails
     */
    public boolean setItem(String key, Object val) throws ScriptException {
        if (val instanceof PyObject pyVal) {
            return setItem(key, pyVal);
        }
        Objects.requireNonNull(key);
        PyObject valObj = pyEngine.fromJava(val).unregister();
        try {
            return setItem(key, valObj);
        } finally {
            valObj.destroy();
        }
    }

    /**
     * Deletes the mapping for the given key.
     *
     * @param key key as PyObject (same engine)
     * @return true on success
     * @throws ScriptException if deletion fails
     */
    public boolean deleteItem(PyObject key) throws ScriptException {
        checkEngine(key);
        return pyEngine.withLock(() -> {
            return PyObject_DelItem(this.addr(), key.addr()) == 0;
        });
    }

    /**
     * Deletes the mapping for the given string key.
     *
     * @param key key string (non-null)
     * @return true on success
     * @throws ScriptException if deletion fails
     */
    public boolean deleteItem(String key) throws ScriptException {
        Objects.requireNonNull(key);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                var keyPtr = arena.allocateFrom(key);
                return PyObject_DelItemString(this.addr(), keyPtr) == 0;
            }
        });
    }

    /**
     * Deletes the mapping for the given key (String, PyObject, or convertible Java type).
     *
     * @param key key to delete
     * @return true on success
     * @throws ScriptException if conversion or deletion fails
     */
    public boolean deleteItem(Object key) throws ScriptException {
        return switch (key) {
            case String str -> deleteItem(str);
            case PyObject pyObj -> deleteItem(pyObj);
            default -> {
                PyObject keyObj = pyEngine.fromJava(key).unregister();
                try {
                    yield deleteItem(keyObj);
                } finally {
                    keyObj.destroy();
                }
            }
        };
    }

    /**
     * Returns the size of this object if it supports Python's sequence/mapping protocol.
     *
     * @return size value
     * @throws ScriptException if the query fails
     */
    public long size() throws ScriptException {
        return pyEngine.withLock(() -> PyObject_Size(addr()));
    }

    /**
     * Returns the length of this object (alias of {@link #size()}).
     *
     * @return length value
     * @throws ScriptException if the query fails
     */
    public long len() throws ScriptException {
        return pyEngine.withLock(() -> PyObject_Length(addr()));
    }

    /**
     * Returns the result of Python's {@code repr(obj)}.
     *
     * @return PyObject representing the repr string
     * @throws ScriptException if the call fails
     */
    public final PyObject repr() throws ScriptException {
        return pyEngine.withLock(() -> {
            return pyEngine.wrap(PyObject_Repr(this.addr()));
        });
    }

    /**
     * Returns the result of Python's {@code str(obj)}.
     *
     * @return PyObject representing the string
     * @throws ScriptException if the call fails
     */
    public final PyObject str() throws ScriptException {
        return pyEngine.withLock(() -> {
            return pyEngine.wrap(PyObject_Str(this.addr()));
        });
    }

    // type queries
    /**
     * Returns whether this object is an instance of the given class or tuple of classes.
     *
     * @param pyCls class or tuple of classes (same engine)
     * @return true if instance
     * @throws ScriptException if the check fails
     */
    public final boolean isInstance(PyObject pyCls) throws ScriptException {
        checkEngine(pyCls);
        return pyEngine.withLock(() -> {
            return PyObject_IsInstance(this.addr(), pyCls.addr()) == 1;
        });
    }

    /**
     * Returns whether this object's type is a subclass of the given class.
     *
     * @param pyCls class object (same engine)
     * @return true if subclass
     * @throws ScriptException if the check fails
     */
    public final boolean isSubclassOf(PyObject pyCls) throws ScriptException {
        checkEngine(pyCls);
        return pyEngine.withLock(() -> {
            return PyObject_IsSubclass(this.addr(), pyCls.addr()) != 0;
        });
    }

    /**
     * Returns true if this object is Python's None.
     *
     * @return true if None
     */
    public final boolean isNone() {
        return addr().equals(PythonScriptEngine.getNoneAddr());
    }

    /**
     * Returns true if this object is logically False (negation of {@link #isTrue()}).
     *
     * @return true if falsey
     * @throws ScriptException if truthiness check fails
     */
    public final boolean isFalse() throws ScriptException {
        return !isTrue();
    }

    /**
     * Returns true if this object is logically True.
     *
     * @return true if truthy
     * @throws ScriptException if truthiness check fails
     */
    public final boolean isTrue() throws ScriptException {
        return pyEngine.withLock(() -> {
            return PyObject_IsTrue(this.addr()) == 1;
        });
    }

    /**
     * Returns true if this object is Python's Ellipsis.
     *
     * @return true if Ellipsis
     */
    public final boolean isEllipsis() {
        return addr().equals(PythonScriptEngine.getEllipsisAddr());
    }

    /**
     * Returns true if this object is Python's NotImplemented.
     *
     * @return true if NotImplemented
     */
    public final boolean isNotImplemented() {
        return addr().equals(PythonScriptEngine.getNotImplementedAddr());
    }

    /**
     * Returns this object's type (Python {@code type(obj)}).
     *
     * @return PyObject representing the type
     * @throws ScriptException if the call fails
     */
    public final PyObject type() throws ScriptException {
        return pyEngine.withLock(() -> {
            return pyEngine.wrap(PyObject_Type(this.addr()));
        });
    }

    /**
     * Returns the result of Python's {@code dir(obj)}.
     *
     * @return PyObject list of attribute names
     * @throws ScriptException if the call fails
     */
    public final PyObject dir() throws ScriptException {
        return pyEngine.withLock(() -> pyEngine.wrap(PyObject_Dir(addr())));
    }

    /**
     * Returns an iterator for this object (Python {@code iter(obj)}).
     *
     * @return PyObject iterator
     * @throws ScriptException if the call fails
     */
    public final PyObject iter() throws ScriptException {
        return pyEngine.withLock(() -> pyEngine.wrap(PyObject_GetIter(addr())));
    }

    /**
     * Returns an asynchronous iterator for this object (Python {@code aiter(obj)}).
     *
     * @return PyObject async iterator
     * @throws ScriptException if the call fails
     */
    public final PyObject aiter() throws ScriptException {
        return pyEngine.withLock(() -> pyEngine.wrap(PyObject_GetAIter(addr())));
    }

    /**
     * Equality based on native address equality of the wrapped PyObject*.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof PyObject otherPyObj) {
            return addr().equals(otherPyObj.addr());
        }
        return false;
    }

    /**
     * Hash based on native address of the wrapped PyObject*.
     */
    @Override
    public final int hashCode() {
        return addr().hashCode();
    }

    /**
     * Returns the string representation of this object using Python's {@code str(obj)}.
     *
     * @return Java String obtained from PyUnicode UTF-8 data
     * @throws IllegalStateException if a ScriptException occurs
     */
    @Override
    public final String toString() {
        try {
            return pyEngine.withLock(() -> {
                var pyStr = PyObject_Str(this.addr());
                pyEngine.checkAndThrowPyExceptionNoLock();
                try {
                    return PyUnicode_AsUTF8(pyStr).getString(0);
                } finally {
                    Py_DecRef(pyStr);
                }
            });
        } catch (ScriptException se) {
            throw new IllegalStateException(se);
        }
    }

    // Internals only below this point
    /*
     * Returns the underlying native pointer for this PyObject wrapper.
     * Package-private for use by wrapper/container classes that must pass
     * raw PyObject* addresses to CPython C-API downcalls.
     *
     * Thread-safety: callers must ensure they are under the engine's GIL
     * via PythonScriptEngine.withLock when dereferencing or passing this pointer
     * to native code.
     *
     * @return the non-NULL MemorySegment representing PyObject*
     */
    final MemorySegment addr() {
        return pyAddr;
    }

    /*
     * Resets the native address to Python None after the object reaches
     * a native refcount of zero. This prevents accidental future use of
     * a freed object by ensuring subsequent operations raise Python-level
     * exceptions rather than causing invalid memory access.
     *
     * Intended to be called by PyObjectArena post-cleanup hooks and destroy
     * method. Not thread-safe; caller must coordinate with engine GIL discipline.
     */
    void makeItNone() {
        // Ref count just now reached zero. No longer safe to access
        // the object. Let's make it None! That way, user will get
        // Python exceptions later when they pass this PyObject to
        // Python code.
        this.pyAddr = PythonScriptEngine.getNoneAddr();
    }

    // package-private method to destroy temporary references faster
    // than waiting for engine managed PyObjectArena to be closed.
    void destroy() {
        this.unregister();
        try {
            pyEngine.withLock(() -> {
                Py_DecRef(addr());
                this.makeItNone();
                return null;
            });
        } catch (ScriptException ex) {
        }
    }

    // package-private helper to register/unregister this
    // object to/from engine management
    boolean register() {
        return pyEngine.register(this);
    }

    PyObject unregister() {
        if (this.engineManaged) {
            this.pyEngine.unregister(this);
            this.engineManaged = false;
        }
        return this;
    }

    /**
     * Validates that the provided PyObject belongs to the same engine
     * as this object. Many CPython C-API functions require objects to
     * be from the same interpreter/engine.
     *
     * @param pyObj object to validate
     * @throws IllegalArgumentException if engines differ
     */
    protected final void checkEngine(PyObject pyObj) {
        if (this.getEngine() != pyObj.getEngine()) {
            throw new IllegalArgumentException("PyObject from a different engine");
        }
    }

    /**
     * Validates that all provided PyObject instances belong to the same
     * engine as this object.
     *
     * @param pyObjs objects to validate
     * @throws IllegalArgumentException if any object has a different engine
     */
    protected final void checkEngine(PyObject... pyObjs) {
        for (PyObject pyObj : pyObjs) {
            if (this.getEngine() != pyObj.getEngine()) {
                throw new IllegalArgumentException("PyObject from a different engine");
            }
        }
    }

    /**
     * Converts Java arguments into engine-owned PyObject wrappers.
     * null maps to Python None; existing PyObject values are reused (and have
     * their native refcount incremented if not engine-managed); other Java values
     * are converted via the engine. Executes under the engine's GIL.
     *
     * @param args Java arguments to convert
     * @return array of PyObject wrappers belonging to this engine
     * @throws ScriptException if conversion fails
     */
    private PyObject[] toPyObjects(Object[] args) throws ScriptException {
        PyObject[] pyArgs = new PyObject[args.length];
        return pyEngine.withLock(() -> {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    pyArgs[i] = pyEngine.getNone();
                } else {
                    if (args[i] instanceof PyObject pyObj) {
                        checkEngine(pyObj);
                        if (!pyObj.engineManaged && !isConstant()) {
                            Py_IncRef(pyObj.addr());
                        }
                        pyArgs[i] = pyObj;
                    } else {
                        var objPtr = pyEngine.toPyObjectAddrNoLock(args[i]);
                        pyEngine.checkAndThrowPyExceptionNoLock();
                        pyArgs[i] = pyEngine.wrap(objPtr).unregister();
                    }
                }
            }
            return pyArgs;
        });
    }

    /**
     * Balances temporary references acquired in {@link #toPyObjects(Object[])}.
     * For non engine-managed PyObject values, decrements the native refcount.
     * Engine-managed objects are left untouched. Executes under the engine's GIL.
     *
     * @param pyArgs PyObject arguments to release if needed
     * @throws ScriptException if a release fails
     */
    private void decRefs(PyObject[] pyArgs) throws ScriptException {
        pyEngine.withLock(() -> {
            for (PyObject pyArg : pyArgs) {
                // Elements guaranteed to be non-null by toPyObjects.
                if (!pyArg.engineManaged && !pyArg.isConstant()) {
                    Py_DecRef(pyArg.addr());
                }
            }
            return (Void) null;
        });
    }

    /**
     * Builds an array of native arguments for CPython varargs call helpers,
     * consisting of each argument's PyObject* address followed by a trailing
     * NULL sentinel as required by the C-API.
     *
     * @param args PyObject arguments
     * @return an Object[] containing MemorySegment pointers and a final NULL
     */
    private static Object[] asPointerArgs(PyObject[] args) {
        // Call a callable Python object callable, with a variable number of PyObject* arguments.
        // The arguments are provided as a variable number of parameters followed by NULL.
        Object[] ptrArgs = new Object[args.length + 1];
        for (int i = 0; i < args.length; i++) {
            ptrArgs[i] = args[i].addr();
        }
        ptrArgs[args.length] = MemorySegment.NULL;
        return ptrArgs;
    }

    /**
     * Produces an array of C pointer layouts sized for a variadic CPython
     * invoker signature. Used to construct downcall handles for functions
     * that receive PyObject* arguments.
     *
     * @param num number of pointer arguments expected (including NULL sentinel)
     * @return an array filled with C_POINTER layouts
     */
    private static MemoryLayout[] pointerLayouts(int num) {
        // all are C_POINTER args
        MemoryLayout[] layouts = new MemoryLayout[num];
        Arrays.fill(layouts, C_POINTER);
        return layouts;
    }

    // cache management for PyObject_CallFunctionObjArgs objects
    private static class CallFunctionInvoker {
        private CallFunctionInvoker() {}
        private static final ConcurrentHashMap<Integer, PyObject_CallFunctionObjArgs> CACHE =
            new ConcurrentHashMap<>();

        static PyObject_CallFunctionObjArgs get(int n) {
            return CACHE.computeIfAbsent(n, (numArgs) -> {
                if (PythonConfig.DEBUG) {
                    IO.println("creating PyObject_CallFunctionObjArgs for " + n + " args");
                }
                MemoryLayout[] layouts = pointerLayouts(numArgs);
                // create invoker
                return PyObject_CallFunctionObjArgs.makeInvoker(layouts);
            });
        }
    }

    // cache management for PyObject_CallMethodObjArgs objects
    private static class CallMethodInvoker {
        private CallMethodInvoker() {}
        private static final ConcurrentHashMap<Integer, PyObject_CallMethodObjArgs> CACHE =
            new ConcurrentHashMap<>();

        static PyObject_CallMethodObjArgs get(int n) {
            return CACHE.computeIfAbsent(n, (numArgs) -> {
                if (PythonConfig.DEBUG) {
                    IO.println("creating PyObject_CallMethodObjArgs for " + n + " args");
                }
                MemoryLayout[] layouts = pointerLayouts(numArgs);
                // create invoker
                return PyObject_CallMethodObjArgs.makeInvoker(layouts);
            });
        }
    }
}
