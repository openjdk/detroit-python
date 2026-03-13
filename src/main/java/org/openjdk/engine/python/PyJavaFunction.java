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

import java.lang.foreign.MemorySegment;
import static java.lang.foreign.MemorySegment.NULL;

import static org.openjdk.engine.python.bindings.Python_h.*;

/**
 * Java wrapper for Python function implemented in Java.
 */
public final class PyJavaFunction extends PyObject {

    PyJavaFunction(/** PyObject* */
            MemorySegment pyAddr, PythonScriptEngine pyEngine) {
        super(pyAddr, pyEngine);
    }

    /**
     * Abstract marker interface for Python function implemented in Java.
     */
    public sealed interface Func permits
            NoArgFunc, OneArgFunc, VarArgsFunc {
    }

    /**
     * A no arg Python function implemented in Java.
     */
    public non-sealed interface NoArgFunc extends Func {

        /**
         * Callback from Python with no input arguments. The return value should
         * be a new reference.
         *
         * @return return value of the function
         */
        public PyObject call();
    }

    /**
     * A single arg Python function implemented in Java.
     */
    public non-sealed interface OneArgFunc extends Func {

        /**
         * Callback from Python with a single input argument. The input argument
         * is borrowed references. You should not decrement reference counter on
         * the input argument. If you use inside
         * PythonScriptEngine.withPyObjectManager, input arg is not tracked with
         * it to avoid decrementing reference counter when the object manager
         * goes out of scope. Also, the return value should be a new reference.
         *
         * @param arg single arg from Python
         * @return return value of the function
         */
        public PyObject call(PyObject arg);
    }

    /**
     * Mutliple args Python function implemented in Java.
     */
    public non-sealed interface VarArgsFunc extends Func {

        /**
         * Callback from Python with variable number of arguments. The input
         * arguments are borrowed references. You should not decrement reference
         * counter on the input arguments. If you use inside
         * PythonScriptEngine.withPyObjectManager, input arg is not tracked with
         * it to avoid decrementing reference counter when the object manager
         * goes out of scope. Also, the return value should be a new reference.
         *
         * @param args PyObject array that contains arguments passed from Python
         * @return return value of the function
         */
        public PyObject call(PyObject[] args);
    }

    // package-private helper to create PyCFunction object.
    static MemorySegment createPyCFunctionNoLock(PythonScriptEngine pyEngine,
            Func func, String name, String doc) {
        var pyMethodDef = pyEngine.getPyMethodDef(func, name, doc);
        return PyCFunction_New(pyMethodDef, NULL);
    }
}
