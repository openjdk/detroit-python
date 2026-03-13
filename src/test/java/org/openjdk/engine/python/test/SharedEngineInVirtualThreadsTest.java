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
import static org.testng.Assert.assertEquals;

public class SharedEngineInVirtualThreadsTest {

    private static final int NUM_THREADS = 25;

    @Test
    public void testSharedEngineInManyVirtualThreads() throws ScriptException, InterruptedException {
        var m = new ScriptEngineManager();
        try (var e = (PythonScriptEngine) m.getEngineByName("python")) {
            IO.println(Thread.currentThread());
            IO.println("shared engine created " + e + ", isMainEngine? " + e.isMainEngine());
            e.eval("print('Hello main ')");
            assertEquals(((PyObject) e.eval("225 + 26")).toLong(), 251);
            var failureCount = new AtomicInteger();
            var countDownLatch = new CountDownLatch(NUM_THREADS);
            for (int i = 0; i < NUM_THREADS; i++) {
                final int j = i;
                Thread.startVirtualThread(() -> {
                    try {
                        e.withPyThreadState(() -> {
                            try {
                                IO.println("shared main engine used in virtual thread " + j);
                                e.setExecMode(AbstractPythonScriptEngine.PyExecMode.EVAL);
                                e.eval("print('in thread " + Thread.currentThread() + "')");
                                assertEquals(((PyObject) e.eval("225 + 26 + " + j)).toLong(), 251 + j);
                                IO.println("leaving main engine usage in virtual thread " + j);
                            } catch (Throwable th) {
                                failureCount.getAndIncrement();
                                throw new RuntimeException(th);
                            }
                        });
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