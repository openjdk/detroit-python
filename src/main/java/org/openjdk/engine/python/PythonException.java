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

import java.util.Objects;
import javax.script.ScriptException;

/**
 * ScriptException that wraps a Python exception raised by the embedded CPython runtime.
 * <p>
 * This exception carries the underlying PyObject representing the Python exception,
 * allowing callers to inspect traceback, context and cause, or to print the full
 * Python error using {@link #print()}.
 */
public final class PythonException extends ScriptException {
    /**
     * Underlying Python exception object.
     */
    private final PyObject pyException;

    /**
     * Constructs a PythonException with a message.
     *
     * @param pyException the underlying Python exception object (non-null)
     * @param message     the detail message
     */
    public PythonException(PyObject pyException, String message) {
        super(message);
        this.pyException = Objects.requireNonNull(pyException);
    }

    /**
     * Constructs a PythonException with a cause.
     *
     * @param pyException the underlying Python exception object (non-null)
     * @param cause       the causing exception
     */
    public PythonException(PyObject pyException, Exception cause) {
        super(cause);
        this.pyException = Objects.requireNonNull(pyException);
    }

    /**
     * Returns the underlying Python exception object.
     *
     * The returned object is owned by this exception and belongs to the same engine
     * that raised it.
     *
     * @return the PyObject representing the Python exception
     */
    public PyObject getPyException() {
        return pyException;
    }

    /**
     * Returns the Python traceback object (exception.__traceback__) if available.
     *
     * @return a PyObject representing the traceback, or Python None if not present
     * @throws ScriptException if retrieval fails in the Python runtime
     */
    public PyObject getPyTraceBack() throws ScriptException {
        return PyExceptionUtils.getTraceBack(pyException);
    }

    /**
     * Returns the Python exception context (exception.__context__) if available.
     *
     * @return a PyObject representing the context, or Python None if not present
     * @throws ScriptException if retrieval fails in the Python runtime
     */
    public PyObject getPyContext() throws ScriptException {
        return PyExceptionUtils.getContext(pyException);
    }

    /**
     * Returns the Python exception cause (exception.__cause__) if available.
     *
     * @return a PyObject representing the cause, or Python None if not present
     * @throws ScriptException if retrieval fails in the Python runtime
     */
    public PyObject getPyCause() throws ScriptException {
        return PyExceptionUtils.getCause(pyException);
    }

    /**
     * Prints the full Python exception (type, value, traceback) using CPython's
     * PyErr_DisplayException. This mirrors Python's standard exception printing.
     * <p>
     * Any ScriptException raised during printing is wrapped into a RuntimeException.
     */
    public void print() {
        PyExceptionUtils.print(pyException);
    }
}
