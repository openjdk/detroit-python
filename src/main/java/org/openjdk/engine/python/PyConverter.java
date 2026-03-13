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

/**
 * Bidirectional conversion between Java objects and Python objects for a specific
 * PythonScriptEngine instance.
 * <p>
 * Implementations must ensure:
 * <ul>
 *   <li>Created PyObject instances belong to (and are compatible with) the originating engine.</li>
 *   <li>Type-safety is enforced where possible; otherwise ScriptException is thrown.</li>
 * </ul>
 * All methods may throw ScriptException if conversion fails.
 */
public interface PyConverter {
    /**
     * Converts a Java String into a Python String.
     *
     * @param str non-null Java string to convert
     * @return a PyObject representing the Python str
     * @throws ScriptException if the conversion fails
     */
    public PyObject fromJava(String str) throws ScriptException;

    /**
     * Converts a Java long (64-bit) into a Python int.
     *
     * @param l the Java long value
     * @return a PyObject representing the Python int
     * @throws ScriptException if the conversion fails
     */
    public PyObject fromJava(long l) throws ScriptException;

    /**
     * Converts a Java double into a Python float.
     *
     * @param d the Java double value
     * @return a PyObject representing the Python float
     * @throws ScriptException if the conversion fails
     */
    public PyObject fromJava(double d) throws ScriptException;

    /**
     * Converts a Java boolean into a Python bool.
     *
     * @param b the Java boolean value
     * @return a PyObject representing Python's True or False
     * @throws ScriptException if the conversion fails
     */
    public PyObject fromJava(boolean b) throws ScriptException;

    /**
     * Converts an arbitrary supported Java object into a corresponding Python object.
     *
     * @param obj the Java object to convert; may be null (mapped to Python None)
     * @return a PyObject representing the converted value
     * @throws ScriptException if the type is unsupported or conversion fails
     */
    public PyObject fromJava(Object obj) throws ScriptException;

    /**
     * Converts a Java function as a Python function object.
     *
     * @param func the Java function implementation
     * @param name the name of the Java function in Python code
     * @param doc the doc String for the Java function
     * @return a PyJavaFunction representing the converted Python function object
     * @throws ScriptException if the type is unsupported or conversion fails
     */
    public PyJavaFunction fromJava(PyJavaFunction.Func func,
        String name, String doc) throws ScriptException;

    /**
     * Converts a Python-backed value to the requested Java type.
     *
     * @param obj the value to convert; may be a PyObject or already a Java object
     * @param cls the target Java class to convert to; must be non-null
     * @return the converted Java value (or null if obj is null)
     * @throws ScriptException if the conversion is not possible or fails
     */
    public Object toJava(Object obj, Class<?> cls) throws ScriptException;
}
