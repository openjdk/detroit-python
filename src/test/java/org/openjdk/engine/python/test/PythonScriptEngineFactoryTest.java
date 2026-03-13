/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.openjdk.engine.python.test;

import java.util.HashSet;
import javax.script.ScriptEngine;

import org.openjdk.engine.python.PythonScriptEngine;
import org.openjdk.engine.python.PythonScriptEngineFactory;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import static org.testng.Assert.*;

public class PythonScriptEngineFactoryTest {
    private static final String ENGINE = "OpenJDK Python Engine";
    private static final String LANGUAGE_VERSION = "3.14";
    private static final String EXPECTED_VERSION_PREFIX = "3.14";
    private PythonScriptEngineFactory factory;

    @BeforeClass
    public void createEngineFactory() {
        this.factory = new PythonScriptEngineFactory();
    }

    @Test
    public void createEngineAndClose() {
        var engine = (PythonScriptEngine) factory.getScriptEngine();
        assertTrue(engine.isMainEngine());
        var engine2 = (PythonScriptEngine) factory.getScriptEngine();
        assertFalse(engine2.isMainEngine());
        engine2.close();
        assertTrue(engine2.isClosed());
        engine.close();
        assertTrue(engine.isClosed());
    }

    @Test
    public void testEngineVersionMatchesGitProperties() {
        var engineVersion = factory.getEngineVersion();
        assertTrue(engineVersion.startsWith(EXPECTED_VERSION_PREFIX),
                "Engine version should start with "
                + EXPECTED_VERSION_PREFIX);
    }

    @Test
    public void testEngineVersionParameterConsistency() {
        var versionParam = factory.
                getParameter(ScriptEngine.ENGINE_VERSION).
                toString();
        assertTrue(versionParam.startsWith(EXPECTED_VERSION_PREFIX),
                "getParameter(ENGINE_VERSION) should start with the prefix "
                + EXPECTED_VERSION_PREFIX);
    }

    @Test
    public void testLanguageName() {
        var langName = factory.
                getParameter(ScriptEngine.LANGUAGE).
                toString();
        assertEquals("Python", langName);
    }

    @Test
    public void testLanguageVersion() {
        var langVersion = factory.
                getParameter(ScriptEngine.LANGUAGE_VERSION).
                toString();
        assertEquals(LANGUAGE_VERSION, langVersion);
    }

    @Test
    public void testEngine() {
        var engine = factory.
                getParameter(ScriptEngine.ENGINE).
                toString();
        assertEquals(ENGINE, engine);
    }

    @Test
    public void testThreading() {
        var threading = factory.
                getParameter("THREADING").
                toString();
        assertEquals("MULTITHREADED", threading);
    }

    @Test
    public void testOutputStatement() {
        var statement = factory.getOutputStatement("'hello'");
        assertEquals("print('hello')", statement);
        statement = factory.getOutputStatement("True");
        assertEquals("print(True)", statement);
        statement = factory.getOutputStatement("False");
        assertEquals("print(False)", statement);
        statement = factory.getOutputStatement("None");
        assertEquals("print(None)", statement);
    }

    @Test
    public void testMimeTypes() {
        var mimeTypes = new HashSet<String>(factory.getMimeTypes());
        assertTrue(mimeTypes.contains("application/x-python-code"));
        assertTrue(mimeTypes.contains("text/x-python"));
    }

    @Test
    public void testNames() {
        var names = new HashSet<String>(factory.getNames());
        assertTrue(names.contains("python"));
        assertTrue(names.contains("Python"));
    }

    @Test
    public void testExtensions() {
        var extensions = new HashSet<String>(factory.getExtensions());
        assertTrue(extensions.contains("py"));
    }
}
