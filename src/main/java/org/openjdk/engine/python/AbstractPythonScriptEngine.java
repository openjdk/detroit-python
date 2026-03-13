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

import java.lang.reflect.Modifier;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;

import static org.openjdk.engine.python.bindings.Python_h.Py_single_input;
import static org.openjdk.engine.python.bindings.Python_h.Py_file_input;
import static org.openjdk.engine.python.bindings.Python_h.Py_eval_input;

/**
 * Provides common functionality for Python engines.
 */
public abstract class AbstractPythonScriptEngine extends AbstractScriptEngine
    implements AutoCloseable, Compilable, Invocable, PyConverter, PyConstants, PyFactory {
    /**
     * Key to be used to get Python exec mode from current the ScriptContext
     */
    public static final String PYTHON_EXEC_MODE_KEY = "python-exec-mode";

    /**
     * Current default Python exec mode (used when the ScriptContext does not override it)
     */
    protected PyExecMode execMode;

    /**
     * Is this engine closed by AutoCloseable.close call?
     */
    protected boolean closed;

    // various Python execution modes.
    private static final int PY_SINGLE_INPUT;
    private static final int PY_FILE_INPUT;
    private static final int PY_EVAL_INPUT;
    static {
        // These are just macros in C header
        PY_SINGLE_INPUT = Py_single_input();
        PY_FILE_INPUT = Py_file_input();
        PY_EVAL_INPUT = Py_eval_input();
    }

    /**
     * Python compile/eval modes mapping to CPython's compile flags.
     *
     * SINGLE is analogous to interactive mode, FILE to exec, and EVAL to expression evaluation.
     */
    public enum PyExecMode {
        /**
         * The script consists of a single interactive statement.
         * (expression statements that evaluate to something other than None will be printed).
         */
        SINGLE(PY_SINGLE_INPUT),
        /**
         * The script consists of a sequence of statements.
         */

        FILE(PY_FILE_INPUT),
        /**
         * The script consists of a single Python expression.
         */
        EVAL(PY_EVAL_INPUT);

        private final int code;

        /**
         * Constructs a mode with the given CPython compile code.
         *
         * @param code native compile mode constant
         */
        private PyExecMode(int code) {
            this.code = code;
        }

        /**
         * Returns the native CPython compile code for this mode.
         *
         * @return native compile flag
         */
        int code() {
            return code;
        }
    };

    /**
     * Constructs a new engine with default execution mode {@link PyExecMode#EVAL}.
     */
    public AbstractPythonScriptEngine() {
        this.execMode = PyExecMode.EVAL;
    }

    /**
     * Returns the engine's default execution mode used when the ScriptContext does not override it.
     *
     * @return current default execution mode
     * @throws IllegalStateException if the engine has been closed
     */
    public synchronized PyExecMode getExecMode() {
        checkClosed();
        return this.execMode;
    }

    /**
     * Sets the engine's default execution mode used when the ScriptContext does not override it.
     *
     * @param mode new execution mode (non-null)
     * @throws IllegalStateException if the engine has been closed
     */
    public synchronized void setExecMode(PyExecMode mode) {
        checkClosed();
        this.execMode = mode;
    }

    /**
     * Indicates whether this engine is backed by the main Python interpreter.
     *
     * @return true if this engine is the main engine, false otherwise
     */
    public abstract boolean isMainEngine();


    /**
     * Closes this engine and releases all underlying native resources.
     * After this call, further operations will throw IllegalStateException.
     * This method is idempotent.
     * <p>
     * When the main interpreter backed ScriptEngine is closed, all sub-interpreter
     * backed ScriptEngine instances are also closed.
     */
    @Override
    public abstract void close();

    /**
     * Returns whether this engine has been closed.
     *
     * @return true if closed, false otherwise
     */
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Ensures ENGINE_SCOPE bindings are of type {@link PythonBindings}.
     *
     * @param bindings the supplied ENGINE_SCOPE bindings
     * @return a ScriptContext using the given bindings
     * @throws IllegalArgumentException if bindings are not an instance of PythonBindings
     */
    @Override
    protected ScriptContext getScriptContext(Bindings bindings) {
        if (! (bindings instanceof PythonBindings)) {
            throw new IllegalArgumentException("call engine.createBindings() to create ENGINE_SCOPE bindings");
        }
        return super.getScriptContext(bindings);
    }

    // package-private helpers below this point
    record CompilationState(String script, String name, PyExecMode mode) {};

    /**
     * Builds a new immutable snapshot of the state needed to compile a script.
     *
     * @param script the script source (non-null)
     * @param sc     the script context providing mode and filename
     * @return a new CompilationState
     */
    final CompilationState newCompilationState(String script, ScriptContext sc) {
        PyExecMode mode = getExecMode(sc);
        Object name = sc.getAttribute(ScriptEngine.FILENAME);
        if (name == null) {
            name = "<eval>";
        }

        return new CompilationState(script, name.toString(), mode);
    }

    /**
     * Throws IllegalStateException if the engine has been closed.
     *
     * @throws IllegalStateException if closed
     */
    final synchronized void checkClosed() {
        if (closed) {
            throw new IllegalStateException("engine already closed");
        }
    }

    /**
     * Validates that the given Class object represents a public interface.
     *
     * @param iface the interface type to validate
     * @throws IllegalArgumentException if the class is null, not an interface, or not public
     */
    final void checkInterface(Class<?> iface) {
        if (iface == null || !iface.isInterface() || !Modifier.isPublic(iface.getModifiers())) {
            throw new IllegalArgumentException("public interface Class expected");
        }
    }

    /**
     * Returns the execution mode from the provided context, defaulting to the engine's mode.
     *
     * @param sc the script context to query
     * @return the execution mode to use
     */
    final PyExecMode getExecMode(ScriptContext sc) {
        Object obj = sc.getAttribute(PYTHON_EXEC_MODE_KEY);
        return obj != null ? PyExecMode.valueOf(obj.toString()) : this.getExecMode();
    }

    /**
     * Returns the ENGINE_SCOPE Python globals dictionary from the given context.
     *
     * @param sc the script context
     * @return the backing PyDictionary representing globals
     * @throws IllegalArgumentException if ENGINE_SCOPE bindings are not PythonBindings
     */
    static PyDictionary getPyDictionary(ScriptContext sc) {
        PythonBindings pb = getPythonBindings(sc);
        return pb.getPyDictionary();
    }

    /**
     * Reads the entire content from the given Reader, closing it afterwards.
     *
     * @param reader the source reader
     * @return the accumulated string content
     * @throws IOException if an I/O error occurs
     */
    static String readAll(Reader reader) throws IOException {
        try (Reader r = reader) {
            StringWriter writer = new StringWriter();
            r.transferTo(writer);
            return writer.toString();
        }
    }

    /**
     * Returns the ENGINE_SCOPE bindings as {@link PythonBindings}.
     *
     * @param sc the script context to inspect
     * @return PythonBindings backing the ENGINE_SCOPE
     * @throws IllegalArgumentException if ENGINE_SCOPE is not PythonBindings
     */
    static PythonBindings getPythonBindings(ScriptContext sc) {
        Bindings bindings = sc.getBindings(ScriptContext.ENGINE_SCOPE);
        // FIXME: for now, ENGINE_SCOPE must a PythonBindings object.
        if (bindings instanceof PythonBindings pyBindings) {
            return pyBindings;
        } else {
            throw new IllegalArgumentException("call engine.createBindings() to create ENGINE_SCOPE bindings");
        }
    }
}
