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

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

/**
 * ScriptEngineFactory implementation for the embedded CPython-based engine.
 * <p>
 * Exposes metadata (names, MIME types, extensions) and produces instances of
 * {@link PythonScriptEngine} on demand.
 */
public final class PythonScriptEngineFactory implements ScriptEngineFactory {
    private static final String ENGINE_VERSION_STRING;

    static {
        Properties props = new Properties();
        try (var in = PythonScriptEngineFactory.class.
                getClassLoader().
                getResourceAsStream("git.properties")) {
            props.load(in);
            ENGINE_VERSION_STRING = props.
                getProperty("git.build.version").
                strip();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    /**
     * The default constructor
     */
    public PythonScriptEngineFactory() {}

    /**
     * Creates a new ScriptEngine instance for executing Python code.
     *
     * @return a new PythonScriptEngine
     */
    @Override
    public synchronized ScriptEngine getScriptEngine() {
        return PythonScriptEngine.newEngine(this);
    }

    /**
     * Returns the engine name, as exposed through the standard key
     * {@link javax.script.ScriptEngine#ENGINE}.
     *
     * @return the engine name
     */
    @Override
    public String getEngineName() {
        return (String)getParameter(ScriptEngine.ENGINE);
    }

    /**
     * Returns the engine version, as exposed through the standard key
     * {@link javax.script.ScriptEngine#ENGINE_VERSION}.
     *
     * @return the engine version
     */
    @Override
    public String getEngineVersion() {
        return (String)getParameter(ScriptEngine.ENGINE_VERSION);
    }

    /**
     * File extensions associated with this scripting language.
     *
     * @return an unmodifiable list of supported extensions
     */
    @Override
    public List<String> getExtensions() {
       return Collections.unmodifiableList(EXTENSIONS);
    }

    /**
     * MIME types associated with this scripting language.
     *
     * @return an unmodifiable list of supported MIME types
     */
    @Override
    public List<String> getMimeTypes() {
        return Collections.unmodifiableList(MIME_TYPES);
    }

    /**
     * Language name exposed through {@link javax.script.ScriptEngine#LANGUAGE}.
     *
     * @return the language name
     */
    @Override
    public String getLanguageName() {
        return (String)getParameter(ScriptEngine.LANGUAGE);
    }

    /**
     * Language version exposed through {@link javax.script.ScriptEngine#LANGUAGE_VERSION}.
     *
     * @return the language version
     */
    @Override
    public String getLanguageVersion() {
        return (String)getParameter(ScriptEngine.LANGUAGE_VERSION);
    }

    /**
     * Returns factory parameters for the specified key.
     * Supported keys include:
     * <ul>
     *   <li>{@link javax.script.ScriptEngine#ENGINE_VERSION}</li>
     *   <li>{@link javax.script.ScriptEngine#LANGUAGE}</li>
     *   <li>{@link javax.script.ScriptEngine#LANGUAGE_VERSION}</li>
     *   <li>"THREADING" - reports engine threading capabilities</li>
     * </ul>
     *
     * @param key non-null parameter name
     * @return the parameter value, or null if unsupported
     * @throws IllegalArgumentException if key is null
     */
    @Override
    public Object getParameter(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must non-null");
        }

        return switch (key) {
            case ScriptEngine.ENGINE -> "OpenJDK Python Engine";
            case ScriptEngine.ENGINE_VERSION -> ENGINE_VERSION_STRING;
            case ScriptEngine.NAME -> "python";
            case ScriptEngine.LANGUAGE -> "Python";
            case ScriptEngine.LANGUAGE_VERSION -> "3.14";
            case "THREADING" -> "MULTITHREADED";
            default -> null;
        };
    }

    /**
     * Returns a string of the form "thiz.method(arg0, arg1, ...)" suitable for display.
     *
     * @param thiz   the target object expression (non-null)
     * @param method the method name (non-null)
     * @param args   zero or more argument expressions (non-null)
     * @return a string representing a method call expression
     * @throws IllegalArgumentException if any parameter is null
     */
    @Override
    public String getMethodCallSyntax(String thiz, String method, String... args) {
        if (thiz == null) {
            throw new IllegalArgumentException("Object must non-null");
        }

        if (method == null) {
            throw new IllegalArgumentException("Method name must non-null");
        }

        if (args == null) {
            throw new IllegalArgumentException("Arguments name must non-null");
        }

        final StringBuilder sb = new StringBuilder().append(thiz).append('.').append(method).append('(');
        final int len = args.length;

        if (len > 0) {
            sb.append(args[0]);
        }
        for (int i = 1; i < len; i++) {
            sb.append(',').append(args[i]);
        }
        sb.append(')');

        return sb.toString();
    }

    /**
     * Returns a snippet that prints the given expression using Python's print.
     *
     * @param toDisplay expression to print (non-null)
     * @return "print(" + toDisplay + ")"
     * @throws IllegalArgumentException if toDisplay is null
     */
    @Override
    public String getOutputStatement(String toDisplay) {
        if (toDisplay == null) {
            throw new IllegalArgumentException("Output must non-null");
        }

        return "print(" + toDisplay + ")";
    }

    /**
     * Returns a one-line program that executes the given statements in sequence,
     * separated by semicolons.
     *
     * @param statements program statements (non-null)
     * @return a single-line Python program
     * @throws IllegalArgumentException if statements is null
     */
    @Override
    public String getProgram(String... statements) {
        if (statements == null) {
            throw new IllegalArgumentException("Statements must non-null");
        }

        final StringBuilder sb = new StringBuilder();

        for (final String statement : statements) {
            sb.append(statement).append('\n');
        }

        return sb.toString();
    }

    /**
     * Returns the list of names usable to obtain this engine from a
     * {@link javax.script.ScriptEngineManager}.
     *
     * @return an unmodifiable list of engine names/aliases
     */
    @Override
    public List<String> getNames() {
        return Collections.unmodifiableList(NAMES);
    }

    // internals only below this point
    private static final List<String> MIME_TYPES;
    private static final List<String> EXTENSIONS;
    static {
        MIME_TYPES = immutableList(
                        "application/x-python-code",
                        "text/x-python"
                    );

        EXTENSIONS = immutableList("py");
    }

    private static final List<String> NAMES;
    static {
        NAMES = immutableList("python", "Python");
    }

    /**
     * Returns an unmodifiable list backed by the provided elements.
     *
     * @param elements items to expose in the list
     * @return an unmodifiable list view of the elements
     */
    private static List<String> immutableList(final String... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }
}
