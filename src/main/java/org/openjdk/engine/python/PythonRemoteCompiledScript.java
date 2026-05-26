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

import java.util.Objects;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;

/**
 * A compiled Python code object evaluated by a remote Python interpreter and
 * coordinated via a local {@link PythonRemoteScriptEngine}.
 * <p>
 * The underlying code object lives in the remote interpreter; this wrapper
 * allows evaluation and lifecycle control from Java. Calling {@link #close()}
 * releases the remote resource and makes this instance unusable.
 */
public final class PythonRemoteCompiledScript extends CompiledScript implements AutoCloseable {

    private final PythonRemoteScriptEngine pyEngine;
    private PyObject pyScript;

    /**
     * Constructs a remote compiled script wrapper.
     *
     * @param pyEngine  the remote script engine coordinating evaluation (non-null)
     * @param scriptObj the remote CPython code object as a PyObject (non-null)
     * @throws NullPointerException if either argument is null
     */
    PythonRemoteCompiledScript(PythonRemoteScriptEngine pyEngine, PyObject scriptObj) {
        this.pyEngine = Objects.requireNonNull(pyEngine);
        this.pyScript = Objects.requireNonNull(scriptObj);
    }

    /**
     * Evaluates this compiled script using the provided script context in the
     * remote interpreter.
     *
     * @param ctxt the script context providing bindings and I/O
     * @return the result of evaluating the code object
     * @throws ScriptException if remote evaluation fails
     * @throws IllegalStateException if this compiled script has been closed
     */
    @Override
    public Object eval(ScriptContext ctxt) throws ScriptException {
        checkClosed();
        return pyEngine.evalCompiled(pyScript, ctxt);
    }

    /**
     * Evaluates this compiled script using the engine's current context in the
     * remote interpreter.
     *
     * @return the result of evaluating the code object
     * @throws ScriptException if remote evaluation fails
     * @throws IllegalStateException if this compiled script has been closed
     */
    @Override
    public synchronized Object eval() throws ScriptException {
        checkClosed();
        return pyEngine.evalCompiled(pyScript);
    }

    /**
     * Returns the remote engine that produced this compiled script.
     *
     * @return the owning PythonRemoteScriptEngine
     */
    @Override
    public PythonRemoteScriptEngine getEngine() {
        return pyEngine;
    }

    /**
     * Releases the remote code object and marks this instance as closed. Further
     * evaluation attempts will throw IllegalStateException. This method is idempotent.
     */
    @Override
    public synchronized void close() {
        if (pyScript != null) {
            try {
                pyEngine.closeCompiled(pyScript);
            } catch (ScriptException ex) {
                throw new RuntimeException(ex);
            } finally {
                pyScript.destroy();
                pyScript = null;
            }
        }
    }

    /**
     * Returns a debug-friendly string describing this compiled script.
     *
     * @return a string representation including the underlying remote code object
     */
    @Override
    public String toString() {
        return String.format("PythonRemoteCompiledScript(%s)", pyScript);
    }

    /**
     * Ensures this compiled script has not been closed.
     *
     * @throws IllegalStateException if the script has been closed
     */
    private void checkClosed() {
        if (pyScript == null) {
            throw new IllegalStateException("CompiledScript closed already");
        }
    }
}
