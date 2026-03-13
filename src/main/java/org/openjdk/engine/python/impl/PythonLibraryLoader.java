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

package org.openjdk.engine.python.impl;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

import org.openjdk.engine.python.PythonConfig;
import org.openjdk.engine.python.PythonConfig.OS;
import static org.openjdk.engine.python.PythonConfig.THE_OS;
import static java.lang.foreign.MemorySegment.NULL;

/*
 * Loads the CPython shared library and exposes a {@link SymbolLookup} for resolving
 * native symbols needed by the embedded runtime.
 *
 * On Linux, this loader uses dlopen with RTLD_GLOBAL to ensure symbols from libpython
 * are visible to subsequently loaded native extension modules. On other platforms, it
 * delegates to {@link System#load(String)} or {@link System#loadLibrary(String)}.
 */
public final class PythonLibraryLoader {
    private static final String PYTHON_LIB_NAME = THE_OS == OS.WINDOWS ? "python312" : "python3.12";

    /*
     * Loads the libpython shared library and returns a SymbolLookup for symbol resolution.
     *
     * Linux: Uses dlopen with RTLD_GLOBAL to make libpython symbols visible to
     *       subsequently loaded native modules. Returns a SymbolLookup that attempts
     *       lookup via dlopen handle first, then falls back to the default linker lookup.</li>
     * macOS/Windows: Uses System.load/System.loadLibrary and returns a combined
     *       loaderLookup with the default linker lookup.</li>
     *
     * @param libArena Arena to manage the lifetime of the dlopen-ed library handle on Linux
     * @return a SymbolLookup able to resolve symbols from libpython and the process
     * @throws UnsatisfiedLinkError if libpython cannot be loaded
     */
    public static SymbolLookup load(Arena libArena) {
        String pythonAbsLibPath = PythonConfig.SHARED_LIB_ABSPATH;
        if (THE_OS == OS.LINUX) {
            /*
             * Java System.load/.loadLibrary calls the 'dlopen' without the RTLD_GLOBAL
             * flag. On macOS, RTLD_GLOBAL flag is the default. On Linux, RTLD_GLOBAL
             * is not the default. Without RTLD_GLOBAL, other subsequent shared objects
             * loaded in the process will not be able to see the symbols. The object's
             * symbols aren not made available for the relocation processing of any other
             * shared object. We need this flag for libpython shared object so that the
             * native python modules can locate libpython symbols later on. For this
             * purpose, we implement a dlopen based SymbolLookup for libpython by
             * passing RTLD_GLOBAL when libpython is dlopen-ed.
             */
            String pythonLib = pythonAbsLibPath != null ?
                pythonAbsLibPath : System.mapLibraryName(PYTHON_LIB_NAME);
            return loadUsingDlopen(libArena, pythonLib)
                .or(Linker.nativeLinker().defaultLookup());
        } else {
            if (pythonAbsLibPath != null) {
                if (PythonConfig.DEBUG) {
                    System.out.println("Attempting System.load on " + pythonAbsLibPath);
                }
                System.load(pythonAbsLibPath);
                if (PythonConfig.DEBUG) {
                    System.out.println("Loaded using System.load: " + pythonAbsLibPath);
                }
            } else {
                if (PythonConfig.DEBUG) {
                    System.out.println("System.loadLibrary on " + PYTHON_LIB_NAME);
                }
                System.loadLibrary(PYTHON_LIB_NAME);
                if (PythonConfig.DEBUG) {
                    System.out.println("Loaded using System.loadLibrary: " + PYTHON_LIB_NAME);
                }
            }
            return SymbolLookup.loaderLookup()
                .or(Linker.nativeLinker().defaultLookup());
        }
    }

    /*
     * Resolves and returns a downcall handle for dlopen.
     *
     * @param lookup symbol lookup used to find dlopen
     * @param linker native linker to downcall into dlopen
     * @return a MethodHandle for dlopen(const char*, int) returning void*
     * @throws UnsatisfiedLinkError if dlopen cannot be found
     */
    private static MethodHandle getDlopenHandle(SymbolLookup lookup, Linker linker) {
        // Get the 'dlopen' symbol
        MemorySegment dlopenSymbol = lookup.find("dlopen")
            .orElseThrow(() -> new UnsatisfiedLinkError("dlopen not found"));

        // Prepare the function descriptor for dlopen
        FunctionDescriptor dlopenFd = FunctionDescriptor.of(
            ValueLayout.ADDRESS, // void* return type
            ValueLayout.ADDRESS, // const char* filename
            ValueLayout.JAVA_INT // int flag
        );

        return linker.downcallHandle(dlopenSymbol, dlopenFd);
    }

    /*
     * Resolves and returns a downcall handle for dlsym.
     *
     * @param lookup symbol lookup used to find dlsym
     * @param linker native linker to downcall into dlsym
     * @return a MethodHandle for dlsym(void*, const char*) returning void*
     * @throws UnsatisfiedLinkError if dlsym cannot be found
     */
    private static MethodHandle getDlsymHandle(SymbolLookup lookup, Linker linker) {
        // Get the 'dlsym' symbol
        MemorySegment dlsymSymbol = lookup.find("dlsym")
            .orElseThrow(() -> new UnsatisfiedLinkError("dlsym not found"));

        // Prepare the function descriptor for dlsym
        FunctionDescriptor dlsymFd = FunctionDescriptor.of(
            ValueLayout.ADDRESS, // void* return type
            ValueLayout.ADDRESS, // library handle
            ValueLayout.ADDRESS); // const char* filename
        return linker.downcallHandle(dlsymSymbol, dlsymFd);
    }

    /*
     * Resolves and returns a downcall handle for dlclose.
     *
     * @param lookup symbol lookup used to find dlclose
     * @param linker native linker to downcall into dlclose
     * @return a MethodHandle for dlclose(void*) returning int
     * @throws UnsatisfiedLinkError if dlclose cannot be found
     */
    private static MethodHandle getDlcloseHandle(SymbolLookup lookup, Linker linker) {
        // Get the 'dlclose' symbol
        MemorySegment dlcloseSymbol = lookup.find("dlclose")
            .orElseThrow(() -> new UnsatisfiedLinkError("dlclose not found"));

        // Prepare the function descriptor for dlclose
        FunctionDescriptor dlcloseFd = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, // int return type
            ValueLayout.ADDRESS); // library handle

        return linker.downcallHandle(dlcloseSymbol, dlcloseFd);
    }

    // dlopen flags: resolve symbols lazily.
    private static final int RTLD_LAZY = 0x00001;
    // dlopen flags: make symbols globally available for subsequent relocations.
    private static final int RTLD_GLOBAL = 0x00100;

    /*
     * Loads libpython using dlopen with RTLD_GLOBAL and returns a SymbolLookup which resolves
     * symbols against that handle, falling back to the default linker if needed.
     * The handle is associated with the provided libArena so its lifetime is scoped
     * to the arena.
     *
     * @param libArena Arena whose lifetime governs the dlopen handle
     * @param pythonLib the library name or absolute path to load
     * @return a SymbolLookup backed by dlsym on the returned handle
     * @throws RuntimeException if loading or symbol resolution helper setup fails
     */
    private static SymbolLookup loadUsingDlopen(Arena libArena, String pythonLib) {
        if (PythonConfig.DEBUG) {
            System.out.println("Attempting to load using dlopen: " + pythonLib);
        }
        Linker linker = Linker.nativeLinker();
        SymbolLookup defaultLookup = linker.defaultLookup();
        MethodHandle dlopen = getDlopenHandle(defaultLookup, linker);
        MethodHandle dlsym = getDlsymHandle(defaultLookup, linker);
        MethodHandle dlclose = getDlcloseHandle(defaultLookup, linker);

        try (Arena arena = Arena.ofShared()) {
            MemorySegment pythonLibNameAddr = arena.allocateFrom(pythonLib);
            try {
                var pythonHandle = (MemorySegment) dlopen.invoke(pythonLibNameAddr, RTLD_LAZY | RTLD_GLOBAL);
                if (pythonHandle.equals(MemorySegment.NULL)) {
                    throw new UnsatisfiedLinkError("dlopen failed to load " + pythonLib);
                }

                if (PythonConfig.DEBUG) {
                    System.out.println("Loaded using dlopen: " + pythonLib);
                }

                pythonHandle.reinterpret(0L, libArena, (newSeg) -> {
                    try {
                        var res = (int) dlclose.invoke(newSeg);
                        if (PythonConfig.DEBUG) {
                            System.out.println("dlclose on " + pythonLib + "completed. Result code: " + res);
                        }
                    } catch (Throwable th) {
                        if (PythonConfig.DEBUG) {
                            System.out.println("dlclose on " + pythonLib + " failed");
                            th.printStackTrace(System.out);
                        }
                    }
                });

                return (name) -> {
                    try (var temp = Arena.ofConfined()) {
                        var namePtr = temp.allocateFrom(name);
                        try {
                            if (PythonConfig.DEBUG) {
                                System.out.println("Attempting dlsym on " + name);
                            }
                            var seg = (MemorySegment) dlsym.invoke(pythonHandle, namePtr);
                            Optional<MemorySegment> optSeg = seg.equals(NULL) ? Optional.empty() : Optional.ofNullable(seg);
                            if (PythonConfig.DEBUG) {
                                if (optSeg.isEmpty()) {
                                    System.out.println("dlsym failed on " + name);
                                } else {
                                    System.out.println("dlsym succeeded on " + name);
                                }
                            }
                            return optSeg;
                        } catch (Throwable tx) {
                            throw new RuntimeException("Symbol attempt failed: " + name, tx);
                        }
                    }
                };
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }
}
