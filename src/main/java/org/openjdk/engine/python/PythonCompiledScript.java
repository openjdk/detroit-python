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
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;

/**
 * A compiled Python code object bound to a specific {@link PythonScriptEngine}.
 * <p>
 * Instances wrap a native CPython code object and can be evaluated multiple
 * times against different {@link ScriptContext}s. The underlying PyObject is
 * reference-counted; invoking {@link #close()} releases the native resource and
 * makes this compiled script unusable.
 */
public final class PythonCompiledScript extends CompiledScript implements AutoCloseable {
    private final PythonScriptEngine pyEngine;
    private PyObject pyScript;

    /**
     * Creates a compiled script wrapper.
     *
     * @param scriptObj the CPython code object as a PyObject (non-null)
     * @throws NullPointerException if scriptObj is null
     */
    PythonCompiledScript(PyObject scriptObj) {
        this.pyScript = Objects.requireNonNull(scriptObj);
        this.pyEngine = scriptObj.getEngine();
    }

    /**
     * Evaluates this compiled script using the provided script context.
     *
     * @param sc the script context providing bindings and I/O
     * @return the result of evaluating the code object (may be a PyObject or a Java value)
     * @throws ScriptException if evaluation fails in the Python runtime
     * @throws IllegalStateException if this compiled script has been closed
     */
    @Override
    public synchronized Object eval(ScriptContext sc) throws ScriptException {
        if (pyScript == null) {
            throw new IllegalStateException("CompiledScript closed already");
        }
        return pyEngine.eval(pyScript, sc);
    }

    /**
     * Returns the engine that produced this compiled script.
     *
     * @return the owning PythonScriptEngine
     */
    @Override
    public PythonScriptEngine getEngine() {
        return pyEngine;
    }

    /**
     * Returns a debug-friendly string describing this compiled script.
     *
     * @return a string representation including the underlying code object
     */
    @Override
    public String toString() {
        return String.format("PythonCompiledScript(%s)", pyScript);
    }

    /**
     * Releases the underlying native code object. After this call, attempts to
     * {@link #eval(ScriptContext)} will result in an {@link IllegalStateException}.
     * This method is idempotent.
     */
    @Override
    public synchronized void close() {
        if (pyScript != null) {
            pyScript.destroy();
            pyScript = null;
        }
    }
}
