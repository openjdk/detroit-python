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
import java.lang.foreign.MemoryLayout;
import java.util.Arrays;

import javax.script.ScriptException;

import static org.openjdk.engine.python.bindings.Python_h.*;

/**
 * Java wrapper around a CPython tuple.
 * <p>
 * Unless stated otherwise, methods throw {@link ScriptException} when the
 * underlying Python runtime reports an error. All {@link PyObject} arguments must
 * belong to the same {@link PythonScriptEngine} instance as this tuple.
 */
public final class PyTuple extends PyObject {
    /**
     * Wraps an existing native tuple object.
     *
     * @param pyAddr   pointer to a CPython PyObject* known to be a tuple (non-null, non-NULL)
     * @param pyEngine the engine that owns the object
     */
    PyTuple(/** PyObject* */ MemorySegment pyAddr, PythonScriptEngine pyEngine) {
        super(pyAddr, pyEngine);
    }

    /**
     * Creates a new Python tuple and initializes it with the provided items.
     * All items must belong to the same engine as this tuple.
     *
     * @param pyEngine the engine that will own the created tuple
     * @param items    zero or more PyObject elements to populate the tuple
     * @throws ScriptException if tuple creation or initialization fails
     * @throws IllegalArgumentException if any item belongs to a different engine
     */
    PyTuple(PythonScriptEngine pyEngine, PyObject... items) throws ScriptException {
        this(newPyTuple(pyEngine, items), pyEngine);
    }

    /**
     * Returns the number of elements in this tuple.
     *
     * @return size of the tuple
     * @throws ScriptException if the operation fails
     */
    @Override
    public long size() throws ScriptException {
        return pyEngine.withLock(()-> PyTuple_Size(addr()));
    }

    /**
     * Returns the number of elements in this tuple (alias for {@link #size()}).
     *
     * @return length of the tuple
     * @throws ScriptException if the operation fails
     */
    @Override
    public long len() throws ScriptException {
        return size();
    }

    /**
     * Retrieves the element at the specified position.
     * The returned reference is a new reference independent of the tuple's storage.
     *
     * @param pos zero-based position
     * @return the element as a PyObject
     * @throws ScriptException if the operation fails
     * @throws IndexOutOfBoundsException if position is out of range (as thrown by CPython)
     */
    public PyObject getItem(long pos) throws ScriptException {
        return pyEngine.withLock(() -> {
            var pyObjPtr = PyTuple_GetItem(addr(), pos);
            pyEngine.checkAndThrowPyExceptionNoLock();
            // borrowed reference - let's create new ref so that
            // users won't have to deal with borrowed references.
            return pyEngine.wrap(Py_NewRef(pyObjPtr));
        });
    }

    /**
     * Returns a slice of this tuple in the interval [low, high).
     *
     * @param low  inclusive start index
     * @param high exclusive end index
     * @return a new PyTuple containing the slice
     * @throws ScriptException if the operation fails
     */
    public PyTuple slice(long low, long high) throws ScriptException {
        return pyEngine.withLock(() -> {
            var pyTuplePtr = PyTuple_GetSlice(addr(), low, high);
            pyEngine.checkAndThrowPyExceptionNoLock();
            return new PyTuple(pyTuplePtr, pyEngine);
        });
    }

    /**
     * Allocates a new Python tuple and populates it with the provided items.
     *
     * @param pyEngine the engine to use
     * @param items    elements to populate the tuple; all must belong to the same engine
     * @return a pointer to the new tuple PyObject*
     * @throws ScriptException if allocation or population fails
     * @throws IllegalArgumentException if any item belongs to a different engine
     */
    private static MemorySegment newPyTuple(PythonScriptEngine pyEngine, PyObject... items) throws ScriptException {
        for (var pyObj : items) {
            if (pyObj.getEngine() != pyEngine) {
                throw new IllegalArgumentException("PyObject from a different engine");
            }
        }
        return pyEngine.withLock(() -> {
            MemoryLayout[] layouts = new MemoryLayout[items.length];
            Arrays.fill(layouts,  C_POINTER);
            var invoker = PyTuple_Pack.makeInvoker(layouts);
            Object[] ptrs = asPointerArgs(items);
            var pyTuplePtr = invoker.apply(items.length, ptrs);
            pyEngine.checkAndThrowPyExceptionNoLock();
            return pyTuplePtr;
        });
    }

    private static Object[] asPointerArgs(PyObject[] args) {
        Object[] ptrArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            ptrArgs[i]  = args[i].addr();
        }
        return ptrArgs;
    }
}
