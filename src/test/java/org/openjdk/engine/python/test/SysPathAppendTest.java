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
import javax.script.*;
import org.openjdk.engine.python.*;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class SysPathAppendTest extends AbstractSysPathTest {

    @BeforeClass
    public void init() throws IOException {
        super.init("org.openjdk.engine.python.sys.append.path");
    }

    @Override
    @AfterClass
    public void destroy() throws IOException {
        super.destroy();
    }

    @Test
    public void testSysPathAppend() throws IOException, ScriptException {
        // check the sys.path last entry
        engine.setExecMode(PythonScriptEngine.PyExecMode.FILE);
        engine.eval("import sys");
        engine.setExecMode(PythonScriptEngine.PyExecMode.EVAL);
        var sysPathFirstEntry = engine.eval("sys.path[-1]").toString();
        assertEquals(sysPathFirstEntry, moduleDir.toString());

        testModuleUsage();
    }
}
