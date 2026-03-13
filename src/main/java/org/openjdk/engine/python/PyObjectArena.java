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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import javax.script.ScriptException;

import static org.openjdk.engine.python.bindings.Python_h.Py_DecRef;

/**
 * Arena to track and manage the lifetime of PyObject instances created within a
 * scoped execution context. When the arena is closed, all tracked PyObjects have
 * their native references decremented and their internal addresses cleared to
 * prevent further use. Used via PythonScriptEngine.withPyObjectManager method.
 */
final class PyObjectArena implements AutoCloseable {

    private final PythonScriptEngine pyEngine;
    // Remove (called from unregister) has to be O(1) => we have to use a Set.
    // But, we can have more than one PyObject baked by same underlying native
    // PyObject*. So, we create a identity hash map baked set.
    private Set<PyObject> pyObjSet = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Creates a new arena bound to a specific PythonScriptEngine.
     *
     * @param pyEngine the engine that owns the tracked PyObjects
     */
    public PyObjectArena(PythonScriptEngine pyEngine) {
        this.pyEngine = pyEngine;
    }

    /**
     * Registers a PyObject with this arena for automatic cleanup on close.
     *
     * @param pyObj the object to register (non-null)
     * @return the same object for fluent usage
     */
    PyObject register(PyObject pyObj) {
        pyObjSet.add(Objects.requireNonNull(pyObj));
        return pyObj;
    }

    /**
     * Unregisters a previously registered PyObject. The object will not be
     * decref'd by this arena when it is closed.
     *
     * @param pyObj the object to unregister (non-null)
     */
    void unregister(PyObject pyObj) {
        pyObjSet.remove(Objects.requireNonNull(pyObj));
    }

    /**
     * Decrements the native reference count of all currently registered
     * PyObjects under the engine's GIL and clears their addresses to prevent
     * accidental reuse. Subsequent calls are no-ops.
     */
    @Override
    public void close() {
        if (pyObjSet != null) {
            try {
                pyEngine.withLock(() -> {
                    for (PyObject pyObj : pyObjSet) {
                        Py_DecRef(pyObj.addr());
                        pyObj.makeItNone();
                    }
                    return (Void)null;
                });
            } catch (ScriptException ignored) {
            }
            pyObjSet.clear();
            pyObjSet = null;
        }
    }
}
