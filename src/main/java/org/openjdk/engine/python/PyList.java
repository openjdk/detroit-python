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
import javax.script.ScriptException;
import static org.openjdk.engine.python.bindings.Python_h.*;
import static java.lang.foreign.MemorySegment.NULL;

/**
 * Java wrapper around a CPython list.
 * <p>
 * All {@link PyObject} arguments must belong to the same
 * {@link PythonScriptEngine} instance as this list.
 */
public final class PyList extends PyObject {
    /**
     * Wraps an existing native list object.
     *
     * @param pyAddr   pointer to a CPython PyObject* known to be a list (non-null, non-NULL)
     * @param pyEngine the engine that owns the object
     */
    PyList(/** PyObject* */ MemorySegment pyAddr, PythonScriptEngine pyEngine) {
        super(pyAddr, pyEngine);
    }

    /**
     * Creates a new Python list and initializes it with the provided items.
     * All items must belong to the same engine as this list.
     *
     * @param pyEngine the engine that will own the created list
     * @param items    zero or more PyObject elements to populate the list
     * @throws ScriptException if list creation or initialization fails
     * @throws IllegalArgumentException if any item belongs to a different engine
     */
    PyList(PythonScriptEngine pyEngine, PyObject... items) throws ScriptException {
        this(newPyList(pyEngine, items), pyEngine);
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return size of the list
     * @throws ScriptException if the operation fails
     */
    @Override
    public long size() throws ScriptException {
        return pyEngine.withLock(()-> PyList_Size(addr()));
    }

    /**
     * Returns the number of elements in this list (alias for {@link #size()}).
     *
     * @return length of the list
     * @throws ScriptException if the operation fails
     */
    @Override
    public long len() throws ScriptException {
        return size();
    }

    /**
     * Retrieves the element at the specified index.
     *
     * @param index zero-based index
     * @return the element as a PyObject
     * @throws ScriptException if the operation fails
     * @throws IndexOutOfBoundsException if index is out of range (as thrown by CPython)
     */
    public PyObject getItem(long index) throws ScriptException {
        return pyEngine.withLock(() -> {
            var pyObjPtr = PyList_GetItemRef(addr(), index);
            pyEngine.checkAndThrowPyExceptionNoLock();
            return pyEngine.wrap(pyObjPtr);
        });
    }

    /**
     * Sets the element at the specified index.
     *
     * @param index zero-based index
     * @param item  PyObject to set (must belong to the same engine)
     * @return true on success, false otherwise
     * @throws ScriptException if the operation fails
     * @throws IllegalArgumentException if the item belongs to a different engine
     */
    public boolean setItem(long index, PyObject item) throws ScriptException {
        checkEngine(item);
        return pyEngine.withLock(() -> {
            // PyList_SetItem steals the reference. Avoid confusion to the caller
            // by always incrementing the item's ref count! We avoid 'steal' and 'barrow'
            // of references for our Java APIs!
            return PyList_SetItem(addr(), index, Py_XNewRef(item.addr())) == 0;
        });
    }

    /**
     * Inserts the specified item at the given index, shifting subsequent elements to the right.
     *
     * @param index zero-based index where the item will be inserted
     * @param item  PyObject to insert (must belong to the same engine)
     * @return true on success, false otherwise
     * @throws ScriptException if the operation fails
     * @throws IllegalArgumentException if the item belongs to a different engine
     */
    public boolean insert(long index, PyObject item) throws ScriptException {
        checkEngine(item);
        return pyEngine.withLock(() -> {
            return PyList_Insert(addr(), index, item.addr()) == 0;
        });
    }

    /**
     * Appends the specified item to the end of this list.
     *
     * @param item PyObject to append (must belong to the same engine)
     * @return true on success, false otherwise
     * @throws ScriptException if the operation fails
     * @throws IllegalArgumentException if the item belongs to a different engine
     */
    public boolean append(PyObject item) throws ScriptException {
        checkEngine(item);
        return pyEngine.withLock(() -> {
            return PyList_Append(addr(), item.addr()) == 0;
        });
    }

    /**
     * Returns a slice of this list in the interval [low, high).
     *
     * @param low  inclusive start index
     * @param high exclusive end index
     * @return a new PyList containing the slice
     * @throws ScriptException if the operation fails
     */
    public PyList slice(long low, long high) throws ScriptException {
        return pyEngine.withLock(() -> {
            var pyListPtr = PyList_GetSlice(addr(), low, high);
            pyEngine.checkAndThrowPyExceptionNoLock();
            return new PyList(pyListPtr, pyEngine);
        });
    }

    /**
     * Replaces the slice [low, high) with the elements from itemlist.
     *
     * @param low      inclusive start index
     * @param high     exclusive end index
     * @param itemlist a PyObject iterable providing replacement elements, or null to delete the slice
     * @return true on success, false otherwise
     * @throws ScriptException if the operation fails
     * @throws IllegalArgumentException if itemlist belongs to a different engine
     */
    public boolean setSlice(long low, long high, PyObject itemlist) throws ScriptException {
        if (itemlist != null) {
            checkEngine(itemlist);
        }
        return pyEngine.withLock(() -> {
            return PyList_SetSlice(addr(), low, high, itemlist == null? NULL : itemlist.addr()) == 0;
        });
    }

    /**
     * Extends this list by appending all items from the given iterable.
     *
     * @param iterable PyObject iterable (must belong to the same engine)
     * @return true on success, false otherwise
     * @throws ScriptException if the operation fails
     * @throws IllegalArgumentException if the iterable belongs to a different engine
     */
    public boolean extend(PyObject iterable) throws ScriptException {
        checkEngine(iterable);
        return pyEngine.withLock(() -> {
            return PyList_Extend(addr(), iterable.addr()) == 0;
        });
    }

    /**
     * Removes all elements from this list.
     *
     * @return true on success, false otherwise
     * @throws ScriptException if the operation fails
     */
    public boolean clear() throws ScriptException {
        return pyEngine.withLock(()-> {
            return PyList_Clear(addr()) == 0;
        });
    }

    /**
     * Sorts the list in place using Python's default ordering.
     *
     * @return true on success, false otherwise
     * @throws ScriptException if the operation fails
     */
    public boolean sort() throws ScriptException {
        return pyEngine.withLock(()-> {
            return PyList_Sort(addr()) == 0;
        });
    }

    /**
     * Reverses the list in place.
     *
     * @return true on success, false otherwise
     * @throws ScriptException if the operation fails
     */
    public boolean reverse() throws ScriptException {
        return pyEngine.withLock(()-> {
            return PyList_Reverse(addr()) == 0;
        });
    }

    /**
     * Allocates a new Python list and populates it with the provided items.
     *
     * @param pyEngine the engine to use
     * @param items    elements to populate the list; all must belong to the same engine
     * @return a pointer to the new list PyObject*
     * @throws ScriptException if allocation or population fails
     * @throws IllegalArgumentException if any item belongs to a different engine
     */
    private static MemorySegment newPyList(PythonScriptEngine pyEngine, PyObject... items) throws ScriptException {
        for (var pyObj : items) {
            if (pyObj.getEngine() != pyEngine) {
                throw new IllegalArgumentException("PyObject from a different engine");
            }
        }
        return pyEngine.withLock(() -> {
            var pyListPtr = PyList_New(items.length);
            pyEngine.checkAndThrowPyExceptionNoLock();
            for (int index = 0; index < items.length; index++) {
                // PyList_SetItem steals the reference. Avoid confusion to the caller
                // by always incrementing the item's ref count! We avoid 'steal' and 'barrow'
                // of references for our Java APIs!
                if (PyList_SetItem(pyListPtr, index, Py_XNewRef(items[index].addr())) != 0) {
                    pyEngine.checkAndThrowPyExceptionNoLock();
                }
            }
            return pyListPtr;
        });
    }
}
