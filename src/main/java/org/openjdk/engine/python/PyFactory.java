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
 * Factory methods for constructing common Python container type objects.
 */
public interface PyFactory {
    /**
     * Creates a new empty Python dict.
     *
     * @return a PyDictionary representing a new Python dict
     * @throws ScriptException if the dict creation fails in the Python runtime
     */
    public PyDictionary newPyDictionary() throws ScriptException;

    /**
     * Creates a new Python list and initializes it with the provided items.
     * All items must belong to the same engine as the factory.
     *
     * @param items zero or more PyObject elements to populate the list
     * @return a PyList representing the created Python list
     * @throws ScriptException if list creation or initialization fails
     * @throws IllegalArgumentException if any item belongs to a different engine
     */
    public PyList newPyList(PyObject... items) throws ScriptException;

    /**
     * Creates a new Python tuple and initializes it with the provided items.
     * All items must belong to the same engine as the factory.
     *
     * @param items zero or more PyObject elements to populate the tuple
     * @return a PyTuple representing the created Python tuple
     * @throws ScriptException if tuple creation or initialization fails
     * @throws IllegalArgumentException if any item belongs to a different engine
     */
    public PyTuple newPyTuple(PyObject... items) throws ScriptException;
}
