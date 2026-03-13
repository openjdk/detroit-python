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

import java.util.concurrent.Callable;

// FIXME avoid java.base internal package access. Revisit this!
import jdk.internal.vm.Continuation;
import jdk.internal.misc.CarrierThreadLocal;

// Utility methods for working with Java virtual threads in contexts that require
// carrier-thread affinity.
final class VirtualThreadHelper {

    // Not instantiable. This is a utility class.
    private VirtualThreadHelper() {}

    /*
     * Executes the supplied callable while the current virtual thread is pinned
     * to its carrier thread.
     *
     * Pinning prevents the virtual thread from being unmounted, preempted, or
     * migrated to a different carrier during the execution of the callable.
     *
     * FIXME: revisit this!
     */
    public static <V> V invokeInCriticalSection(Callable<V> callable) throws Exception {
        assert Thread.currentThread().isVirtual():
            "VirtualThreadHelper used in non-virtual thread";

        if (PythonConfig.DEBUG) {
            IO.println("pinning " + Thread.currentThread());
        }
        Continuation.pin();
        try {
            return callable.call();
        } finally {
            if (PythonConfig.DEBUG) {
                IO.println("unpinning " + Thread.currentThread());
            }
            Continuation.unpin();
        }
    }

    /*
     * Creates a new thread-local whose value is associated with the underlying
     * carrier thread rather than the virtual thread.
     *
     * FIXME: revisit this!
     */
    public static <T> ThreadLocal<T> newCarrierThreadLocal() {
        assert Thread.currentThread().isVirtual():
            "VirtualThreadHelper used in non-virtual thread";

        if (PythonConfig.DEBUG) {
            IO.println("creating a new carrier thread local for " + Thread.currentThread());
        }
        return new CarrierThreadLocal<>();
    }
}
