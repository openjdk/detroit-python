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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.script.*;

import org.openjdk.engine.python.*;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

public class ManyEnginesManyVirtualThreadsTest {

    private static final int NUM_ENGINES = 25;

    @Test
    public void testManyEnginesInManyVirtualThreads() throws ScriptException, InterruptedException {
        var m = new ScriptEngineManager();
        try (var e = (PythonScriptEngine) m.getEngineByName("python")) {
            IO.println(Thread.currentThread());
            assertTrue(e.isMainEngine());
            e.eval("print('main thread " + Thread.currentThread() + "')");
            assertEquals(((PyObject) e.eval("225 + 26")).toLong(), 251);

            var failureCount = new AtomicInteger();
            var countDownLatch = new CountDownLatch(NUM_ENGINES);
            for (int i = 0; i < NUM_ENGINES; i++) {
                final int j = i;
                Thread.startVirtualThread(() -> {
                    IO.println(Thread.currentThread());
                    try (var e1 = (PythonScriptEngine) m.getEngineByName("python")) {
                        IO.println("new engine " + e1 + " in virtual thread " + j);
                        e1.eval("print('in thread " + Thread.currentThread() + "')");
                        assertEquals(((PyObject) e1.eval("225 + 26 + " + j)).toLong(), 251 + j);
                        IO.println("closing engine " + e1 + " in virtual thread " + j);
                    } catch (Throwable th) {
                        failureCount.getAndIncrement();
                        throw new RuntimeException(th);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }

            countDownLatch.await();
            assertEquals(failureCount.get(), 0);
            IO.println("closing main engine");
        }
    }
}