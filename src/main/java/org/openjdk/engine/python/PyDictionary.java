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
import java.util.Objects;
import javax.script.ScriptException;
import static org.openjdk.engine.python.bindings.Python_h.*;

/**
 * Java wrapper around a CPython dictionary.
 * <p>
 * All keys/values passed as {@link PyObject} must belong to the same
 * {@link PythonScriptEngine} instance as this dictionary.
 */
public final class PyDictionary extends PyObject {

    /**
     * Wraps an existing native dict object.
     *
     * @param pyAddr   pointer to a CPython PyObject* known to be a dict (non-null, non-NULL)
     * @param pyEngine the engine that owns the object
     */
    PyDictionary(/** PyObject* */ MemorySegment pyAddr, PythonScriptEngine pyEngine) {
        super(pyAddr, pyEngine);
    }

    /**
     * Creates a new empty Python dict object.
     *
     * @param pyEngine the engine that will own the created dictionary
     * @throws ScriptException if dict creation fails in the Python runtime
     */
    PyDictionary(PythonScriptEngine pyEngine) throws ScriptException {
        this(newPyDict(pyEngine), pyEngine);
    }

    /**
     * Removes all items from the dictionary.
     *
     * @throws ScriptException if the operation fails
     */
    public void clear() throws ScriptException {
        pyEngine.withLock(()-> {
            PyDict_Clear(addr());
            return (Void)null;
        });
    }

    /**
     * Returns whether the provided key exists in this dictionary.
     *
     * @param key a PyObject key belonging to the same engine
     * @return true if the key exists, false otherwise
     * @throws ScriptException if the operation fails
     * @throws IllegalArgumentException if the key belongs to a different engine
     */
    public boolean containsKey(PyObject key) throws ScriptException {
        checkEngine(key);
        return pyEngine.withLock(() -> {
            return PyDict_Contains(addr(), key.addr()) == 1;
        });
    }

    /**
     * Returns whether the provided string key exists in this dictionary.
     *
     * @param key non-null String key
     * @return true if the key exists, false otherwise
     * @throws ScriptException if the operation fails
     */
    public boolean containsKey(String key) throws ScriptException {
        Objects.requireNonNull(key);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                var keyPtr = arena.allocateFrom(key);
                return PyDict_ContainsString(addr(), keyPtr) == 1;
            }
        });
    }

    /**
     * Returns whether the provided key exists in this dictionary.
     *
     * @param key the key to look up
     * @return true if the key exists, false otherwise
     * @throws ScriptException if conversion or lookup fails
     */
    public boolean containsKey(Object key) throws ScriptException {
        return switch (key) {
            case String str -> containsKey(str);
            case PyObject pyObj -> containsKey(pyObj);
            default -> {
                PyObject keyObj = pyEngine.fromJava(key).unregister();
                try {
                    yield containsKey(keyObj);
                } finally {
                    keyObj.destroy();
                }
            }
        };
    }

    /**
     * Returns a shallow copy of this dictionary.
     *
     * @return a new PyDictionary with the same key/value pairs
     * @throws ScriptException if the operation fails
     */
    public PyDictionary copy() throws ScriptException {
        return new PyDictionary(pyEngine.withLock(()-> PyDict_Copy(addr())), pyEngine);
    }

    /**
     * Sets the mapping for the given key to the specified value.
     *
     * @param key a PyObject key belonging to the same engine
     * @param val a PyObject value belonging to the same engine
     * @return true on success, false otherwise
     * @throws ScriptException if setting the item fails
     * @throws IllegalArgumentException if key or value belongs to a different engine
     */
    @Override
    public boolean setItem(PyObject key, PyObject val) throws ScriptException {
        checkEngine(key, val);
        return pyEngine.withLock(() -> {
            return PyDict_SetItem(addr(), key.addr(), val.addr()) == 0;
        });
    }

    /**
     * Sets the mapping for the given string key to the specified value.
     *
     * @param key non-null String key
     * @param val a PyObject value belonging to the same engine
     * @return true on success, false otherwise
     * @throws ScriptException if setting the item fails
     * @throws IllegalArgumentException if value belongs to a different engine
     */
    @Override
    public boolean setItem(String key, PyObject val) throws ScriptException {
        Objects.requireNonNull(key);
        checkEngine(val);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                var keyPtr = arena.allocateFrom(key);
                return PyDict_SetItemString(addr(), keyPtr,  val.addr()) == 0;
            }
        });
    }

    /**
     * Deletes the mapping for the given key.
     *
     * @param key a PyObject key belonging to the same engine
     * @return true on success, false otherwise
     * @throws ScriptException if deletion fails
     * @throws IllegalArgumentException if the key belongs to a different engine
     */
    @Override
    public boolean deleteItem(PyObject key) throws ScriptException {
        checkEngine(key);
        return pyEngine.withLock(() -> {
            return PyDict_DelItem(addr(), key.addr()) == 0;
        });
    }

    /**
     * Deletes the mapping for the given string key.
     *
     * @param key non-null String key
     * @return true on success, false otherwise
     * @throws ScriptException if deletion fails
     */
    @Override
    public boolean deleteItem(String key) throws ScriptException {
        Objects.requireNonNull(key);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                var keyPtr = arena.allocateFrom(key);
                return PyDict_DelItemString(addr(), keyPtr) == 0;
            }
        });
    }

    /**
     * Retrieves the value for the specified key.
     *
     * @param key a PyObject key belonging to the same engine
     * @return the value PyObject if present, or Python None if absent
     * @throws ScriptException if lookup fails
     * @throws IllegalArgumentException if the key belongs to a different engine
     */
    @Override
    public PyObject getItem(PyObject key) throws ScriptException {
        checkEngine(key);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                var resPtrPtr = arena.allocate(C_POINTER);
                int err = PyDict_GetItemRef(addr(), key.addr(), resPtrPtr);
                if (err != -1) {
                    return pyEngine.wrap(resPtrPtr.get(C_POINTER, 0));
                } else {
                    pyEngine.checkAndThrowPyExceptionNoLock();
                    return pyEngine.getNone();
                }
            }
        });
    }

    /**
     * Retrieves the value for the specified string key.
     *
     * @param key non-null String key
     * @return the value PyObject if present, or Python None if absent
     * @throws ScriptException if lookup fails
     */
    @Override
    public PyObject getItem(String key) throws ScriptException {
        Objects.requireNonNull(key);
        return pyEngine.withLock(() -> {
            try (var arena = Arena.ofConfined()) {
                var resPtrPtr = arena.allocate(C_POINTER);
                var keyPtr = arena.allocateFrom(key);
                int res = PyDict_GetItemStringRef(addr(), keyPtr, resPtrPtr);
                if (res != -1) {
                    return pyEngine.wrap(resPtrPtr.get(C_POINTER, 0));
                } else {
                    pyEngine.checkAndThrowPyExceptionNoLock();
                    return pyEngine.getNone();
                }
            }
        });
    }

    /**
     * Returns a list of (key, value) pairs contained in this dictionary.
     *
     * @return a PyList of PyTuple entries (key, value)
     * @throws ScriptException if the operation fails
     */
    public PyList items() throws ScriptException {
        return pyEngine.withLock(() -> {
            var pyListPtr = PyDict_Items(addr());
            pyEngine.checkAndThrowPyExceptionNoLock();
            return new PyList(pyListPtr, pyEngine);
        });
    }

    /**
     * Returns a list of the keys contained in this dictionary.
     *
     * @return a PyList of keys
     * @throws ScriptException if the operation fails
     */
    public PyList keys() throws ScriptException {
        return pyEngine.withLock(() -> {
            var pyListPtr = PyDict_Keys(addr());
            pyEngine.checkAndThrowPyExceptionNoLock();
            return new PyList(pyListPtr, pyEngine);
        });
    }

    /**
     * Returns a list of the values contained in this dictionary.
     *
     * @return a PyList of values
     * @throws ScriptException if the operation fails
     */
    public PyList values() throws ScriptException {
        return pyEngine.withLock(() -> {
            var pyListPtr = PyDict_Values(addr());
            pyEngine.checkAndThrowPyExceptionNoLock();
            return new PyList(pyListPtr, pyEngine);
        });
    }

    /**
     * Returns the number of mappings in this dictionary.
     *
     * @return the size of this dictionary
     * @throws ScriptException if the operation fails
     */
    @Override
    public long size() throws ScriptException {
        return pyEngine.withLock(()-> PyDict_Size(addr()));
    }

    /**
     * Returns the number of mappings in this dictionary (alias for {@link #size()}).
     *
     * @return the length of this dictionary
     * @throws ScriptException if the operation fails
     */
    @Override
    public long len() throws ScriptException {
        return size();
    }

    /**
     * Allocates a new empty Python dict through the engine.
     *
     * @param pyEngine the engine to use
     * @return a pointer to the new dict PyObject*
     * @throws ScriptException if allocation fails
     */
    private static MemorySegment newPyDict(PythonScriptEngine pyEngine) throws ScriptException {
        return pyEngine.withLock(() -> {
            var pyDictPtr = PyDict_New();
            pyEngine.checkAndThrowPyExceptionNoLock();
            return pyDictPtr;
        });
    }
}
