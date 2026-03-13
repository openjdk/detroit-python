/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Paths;
import javax.script.*;
import org.openjdk.engine.python.*;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;

public class SysPathEmptyPrependTest extends AbstractSysPathTest {

    @BeforeClass
    public void createEngine() {
        // empty in sys.path means current directory
        System.setProperty("org.openjdk.engine.python.sys.prepend.path", "");
        ScriptEngineManager m = new ScriptEngineManager();
        this.engine = (PythonScriptEngine) m.getEngineByName("python");
        // use current directory as the module directory
        this.moduleDir = Paths.get(".");
    }

    @AfterClass
    public void closeEngine() {
        this.engine.close();
        // make sure to clean up __pycache__ that is generated because
        // of module import from current directory
        deletePath(Paths.get("__pycache__"));
    }

    @Test
    public void testDefaultSysPathPrepend() throws IOException, ScriptException {
        // check that the default sys.path prepend path is empty.
        // empty entry in sys.path means the current directory.
        engine.setExecMode(PythonScriptEngine.PyExecMode.FILE);
        engine.eval("import sys");
        engine.setExecMode(PythonScriptEngine.PyExecMode.EVAL);
        var sysPathFirstEntry = engine.eval("sys.path[0]").toString();
        assertTrue(sysPathFirstEntry.isEmpty());

        testModuleUsage();
    }
}
