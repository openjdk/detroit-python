
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

// Added GPL copyright header for source files in a source directory recursively
class AddCopyright {

    private static final String COPYRIGHT_TEXT = """
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

    """;

    // post process generated java source file copyright addition
    private static boolean postProcessForCopyright(Path srcDir) throws IOException, InterruptedException {
        try (Stream<Path> paths = Files.walk(srcDir)) {
            boolean[] res = {true};
            paths.filter(Files::isRegularFile).
                    forEach(path -> {
                        try {
                            var origContent = Files.readString(path, StandardCharsets.UTF_8);
                            var newContent = COPYRIGHT_TEXT + origContent;
                            Files.writeString(path, newContent, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            res[0] = false;
                            System.err.println(e);
                        }
                    });
            return res[0];
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Directory missing");
            System.exit(1);
        }

        var srcDir = Paths.get(args[0]);
        if (!Files.isDirectory(srcDir)) {
            System.err.println(args[0] + " is not a directory");
            System.exit(2);
        }

        try {
            postProcessForCopyright(srcDir);
        } catch (IOException | InterruptedException ex) {
            System.err.println(ex);
            ex.printStackTrace(System.err);
            System.exit(3);
        }
    }
}
