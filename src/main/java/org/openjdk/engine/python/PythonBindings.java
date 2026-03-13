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
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.script.Bindings;
import javax.script.ScriptException;

/**
 * Bindings implementation backed by a Python dictionary (globals) for a specific
 * {@link PythonScriptEngine}. Values inserted into this map are converted to and
 * from Python objects as needed.
 * <p>
 * All operations ultimately delegate to the underlying {@link PyDictionary} and
 * may wrap {@link ScriptException}s as {@link RuntimeException}s for the
 * Bindings interface contract.
 */
final class PythonBindings implements Bindings, AutoCloseable {

    private final PyDictionary pyDict;

    /**
     * Creates a new Python-backed Bindings wrapper.
     *
     * @param pyDict the underlying Python dictionary (non-null)
     */
    PythonBindings(PyDictionary pyDict) {
        this.pyDict = Objects.requireNonNull(pyDict);
    }

    /**
     * Returns the underlying Python dictionary backing these bindings.
     *
     * @return the PyDictionary used as globals
     */
    PyDictionary getPyDictionary() {
        return pyDict;
    }

    /**
     * Associates the specified value with the specified key in these bindings.
     * The value is converted to a Python object and stored in the underlying dict.
     *
     * @param name  non-null key
     * @param value value to associate (may be converted to a PyObject)
     * @return the previous value associated with the key, or null if there was none
     * @throws RuntimeException wrapping ScriptException on failure
     */
    @Override
    public Object put(String name, Object value) {
        checkKey(name);
        try {
            var oldVal = pyDict.getItem(name);
            pyDict.setItem(name, value);
            return oldVal;
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Copies all of the mappings from the specified map to these bindings.
     * Each value is converted to a Python object before insertion.
     *
     * @param toMerge the mappings to store
     * @throws RuntimeException wrapping ScriptException on failure
     */
    @Override
    public void putAll(Map<? extends String, ? extends Object> toMerge) {
        toMerge.entrySet().
                stream().
                filter(e -> e.getKey() instanceof String).
                forEach(e -> {
                    try {
                        pyDict.setItem((String) e.getKey(), e.getValue());
                    } catch (ScriptException ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }

    /**
     * Returns true if these bindings contain a mapping for the specified key.
     *
     * @param key non-null key (must be a String)
     * @return true if a mapping exists
     * @throws ClassCastException/IllegalArgumentException if key invalid
     * @throws RuntimeException wrapping ScriptException on failure
     */
    @Override
    public boolean containsKey(Object key) {
        checkKey(key);
        try {
            return pyDict.containsKey((String) key);
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns the value to which the specified key is mapped, or null if none.
     *
     * @param key non-null key (must be a String)
     * @return the mapped value (as a PyObject) or null
     * @throws ClassCastException/IllegalArgumentException if key invalid
     * @throws RuntimeException wrapping ScriptException on failure
     */
    @Override
    public Object get(Object key) {
        checkKey(key);
        try {
            return pyDict.getItem((String) key);
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Removes the mapping for a key if present.
     *
     * @param key non-null key (must be a String)
     * @return the previous value associated with key, or null
     * @throws ClassCastException/IllegalArgumentException if key invalid
     * @throws RuntimeException wrapping ScriptException on failure
     */
    @Override
    public Object remove(Object key) {
        checkKey(key);
        try {
            var oldVal = pyDict.getItem((String) key);
            pyDict.deleteItem((String) key);
            return oldVal;
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns the number of key-value mappings in these bindings.
     *
     * @return the number of entries
     * @throws RuntimeException wrapping ScriptException on failure
     */
    @Override
    public int size() {
        try {
            return (int) pyDict.size();
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns true if these bindings contain no key-value mappings.
     *
     * @return true if empty
     */
    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns true if these bindings map one or more keys to the specified value.
     * Note: this materializes values via {@link #values()}.
     *
     * @param value value whose presence is to be tested
     * @return true if at least one mapping to value exists
     */
    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    /**
     * Removes all of the mappings from these bindings.
     *
     * @throws RuntimeException wrapping ScriptException on failure
     */
    @Override
    public void clear() {
        try {
            pyDict.clear();
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns a set view of the keys contained in these bindings.
     * The keys are converted to Java strings.
     *
     * @return a set of keys
     * @throws RuntimeException wrapping ScriptException on failure
     */
    @Override
    public Set<String> keySet() {
        Set<String> stringKeySet = new HashSet<>();
        pyDict.getEngine().withPyObjectManager(() -> {
            try {
                PyList keyList = pyDict.keys();
                final int size = (int) keyList.size();
                for (int i = 0; i < size; i++) {
                    stringKeySet.add(keyList.getItem(i).toString());
                }
            } catch (ScriptException ex) {
                throw new RuntimeException(ex);
            }
        });
        return stringKeySet;
    }

    /**
     * Returns a collection view of the values contained in these bindings.
     *
     * @return a collection of values
     */
    @Override
    public Collection<Object> values() {
        return entrySet().stream().map(e -> e.getValue()).toList();
    }

    /**
     * Returns a set view of the mappings contained in these bindings.
     * Keys are Java strings; values are {@link PyObject} instances wrapping native pointers.
     *
     * @return a set of entries
     * @throws RuntimeException wrapping ScriptException on failure
     */
    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, MemorySegment>> stringEntrySet = new HashSet<>();
        pyDict.getEngine().withPyObjectManager(() -> {
            try {
                PyList items = pyDict.items();
                final int size = (int) items.size();
                for (int i = 0; i < size; i++) {
                    PyTuple item = (PyTuple) items.getItem(i);
                    PyObject key = item.getItem(0);
                    PyObject value = item.getItem(1);
                    stringEntrySet.add(new AbstractMap.SimpleImmutableEntry<>(key.toString(), value.addr()));
                }
            } catch (ScriptException ex) {
                throw new RuntimeException(ex);
            }
        });
        return stringEntrySet.
            stream().
            map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), (Object)pyDict.pyEngine.wrap(e.getValue()))).
            collect(Collectors.toSet());
    }

    @Override
    public synchronized void close() {
        if (pyDict.isNone()) {
            // already closed
            return;
        }
        try {
            pyDict.clear();
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
        pyDict.destroy();
    }

    /**
     * Validates that a key is a non-empty String.
     *
     * @param key the key to validate (non-null)
     * @throws ClassCastException if not a String
     * @throws IllegalArgumentException if empty string
     */
    private void checkKey(Object key) {
        Objects.requireNonNull(key);
        if (!(key instanceof String)) {
            throw new ClassCastException("String key expected");
        }
        if ("".equals(key)) {
            throw new IllegalArgumentException("empty String as key");
        }
    }
}
