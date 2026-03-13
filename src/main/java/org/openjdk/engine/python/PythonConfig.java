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

/**
 * Manages various Python engine related System properties and other config items.
 */
public final class PythonConfig {

    private PythonConfig() {
    }

    /**
     * Enumeration of supported operating systems used for environment
     * detection.
     */
    public enum OS {
        /**
         * Windows OS variants.
         */
        WINDOWS,
        /**
         * Linux OS variants.
         */
        LINUX,
        /**
         * macOS variants.
         */
        MAC,
        /**
         * Unknown OS.
         */
        UNKNOWN
    }

    /**
     * Detects the current operating system from the "os.name" system property.
     *
     * @return the detected OS value
     */
    private static OS getCurrentOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("linux")) {
            return OS.LINUX;
        } else if (osName.contains("mac")) {
            return OS.MAC;
        } else if (osName.contains("win")) {
            return OS.WINDOWS;
        } else {
            return OS.UNKNOWN;
        }
    }

    /**
     * OS enum value for the current operating system.
     */
    public static final OS THE_OS = getCurrentOS();

    /**
     * Debug mode on or off. Initialised from the System property
     * "java.python.debug". Default is empty => false. Set it to "true" to turn
     * on DEBUG mode.
     */
    public static final boolean DEBUG = Boolean.getBoolean("org.openjdk.engine.python.debug");

    /**
     * Python GIL mode. Initialised from the System property
     * "java.python.gil.mode". Possible values are "own", "shared", "default".
     * The default is "own". That is, each Python engine (backed by separate
     * Python interpreter) gets its own GIL.
     */
    public static final String GIL_MODE = System.getProperty("org.openjdk.engine.python.gil.mode", "own");

    /**
     * The name of the Python program to use. Initialised from the System
     * property "java.python.program.name". Default is null.
     */
    public static final String PROGRAM_NAME = System.getProperty("org.openjdk.engine.python.program.name");

    /**
     * Absolute path of the Python shared object/DLL. Initialised from the
     * System property "java.python.library.abspath". Default is null. In most
     * scenarios, you do not have to set this. Setting env. var LD_LIBRARY_PATH
     * or setting the System property "java.library.path" should work in most
     * scenarios.
     */
    public static final String SHARED_LIB_ABSPATH = System.getProperty("org.openjdk.engine.python.library.abspath");

    /**
     * Path to prepend to the Python's module search path "sys.path". Default is
     * null.
     */
    public static final String SYS_PREPEND_PATH = System.getProperty("org.openjdk.engine.python.sys.prepend.path");

    /**
     * Path to append to the Python's module search path "sys.path". Default is
     * null.
     */
    public static final String SYS_APPEND_PATH = System.getProperty("org.openjdk.engine.python.sys.append.path");

    /**
     * Flag to tell whether java stack trace has to be captured in Python
     * exception message or not. Default value is true.
     */
    public static final boolean JAVASTACK_IN_PYEXCEPTION;

    static {
        String propVal = System.getProperty("org.openjdk.engine.python.javastack_in_pyexception", "true");
        JAVASTACK_IN_PYEXCEPTION = Boolean.parseBoolean(propVal);
    }

    /**
     * Returns the configured Python program name if explicitly set via the
     * "java.python.program.name" system property, otherwise null.
     *
     * @return the Python program name or null
     */
    public static String getProgramName() {
        return PROGRAM_NAME;
    }
}
