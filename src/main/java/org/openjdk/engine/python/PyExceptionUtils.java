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

import javax.script.ScriptException;
import static org.openjdk.engine.python.bindings.Python_h.*;

/**
 * Utility methods to interrogate and display Python exceptions raised by the
 * embedded CPython runtime. These helpers operate on the underlying PyObject
 * representing the Python exception and return wrapped values.
 */
public final class PyExceptionUtils {
    /**
     * Not instantiable.
     */
    private PyExceptionUtils() {}

    /**
     * Returns the traceback object associated with the given Python exception.
     *
     * @param pyException the Python exception object
     * @return a PyObject representing exception.__traceback__ (or Python None)
     * @throws ScriptException if retrieval fails in the Python runtime
     */
    public static PyObject getTraceBack(PyObject pyException) throws ScriptException {
        var engine = pyException.getEngine();
        return engine.withLock(() -> engine.wrap(PyException_GetTraceback(pyException.addr())));
    }

    /**
     * Returns the context associated with the given Python exception.
     *
     * @param pyException the Python exception object
     * @return a PyObject representing exception.__context__ (or Python None)
     * @throws ScriptException if retrieval fails in the Python runtime
     */
    public static PyObject getContext(PyObject pyException) throws ScriptException {
        var engine = pyException.getEngine();
        return engine.withLock(() -> engine.wrap(PyException_GetContext(pyException.addr())));
    }

    /**
     * Returns the cause associated with the given Python exception.
     *
     * @param pyException the Python exception object
     * @return a PyObject representing exception.__cause__ (or Python None)
     * @throws ScriptException if retrieval fails in the Python runtime
     */
    public static PyObject getCause(PyObject pyException) throws ScriptException {
        var engine = pyException.getEngine();
        return engine.withLock(() -> engine.wrap(PyException_GetCause(pyException.addr())));
    }

    /**
     * Prints the full Python exception (type, message, traceback) using
     * CPython's PyErr_DisplayException. Any ScriptException encountered is
     * wrapped and rethrown as a RuntimeException.
     *
     * @param pyException the Python exception object to print
     */
    public static void print(PyObject pyException) {
        try {
            pyException.getEngine().withLock(() -> {
                PyErr_DisplayException(pyException.addr());
                return (Void)null;
            });
        } catch (ScriptException se) {
            throw new RuntimeException(se);
        }
    }
}
