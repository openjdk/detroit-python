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

import javax.script.*;
import org.openjdk.engine.python.*;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;

public class ManyEnginesOneThreadTest {

    private static final int NUM_ENGINES = 25;

    @Test
    public void testManyEnginesInOneThread() throws ScriptException {
        var m = new ScriptEngineManager();
        try (var e = (PythonScriptEngine) m.getEngineByName("python")) {
            IO.println(Thread.currentThread());
            assertTrue(e.isMainEngine());
            e.eval("print('Hello main')");
            assertEquals(((PyObject) e.eval("225 + 26")).toLong(), 251);

            for (int i = 0; i < NUM_ENGINES; i++) {
                try (var e1 = (PythonScriptEngine) m.getEngineByName("python")) {
                    assertFalse(e1.isMainEngine());
                    try {
                        e1.eval("print('Hello-" + i + "')");
                        assertEquals(((PyObject) e1.eval("225 + 26 + " + i)).toLong(), 251 + i);
                    } catch (ScriptException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
            IO.println("closing main engine");
        }
    }
}
