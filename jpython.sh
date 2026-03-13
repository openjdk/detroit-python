#!/usr/bin/env bash

# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
#   - Redistributions of source code must retain the above copyright
#     notice, this list of conditions and the following disclaimer.
#
#   - Redistributions in binary form must reproduce the above copyright
#     notice, this list of conditions and the following disclaimer in the
#     documentation and/or other materials provided with the distribution.
#
#   - Neither the name of Oracle nor the names of its
#     contributors may be used to endorse or promote products derived
#     from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
# THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
# PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
# CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

set -e

PYTHON_BIN=$(command -v python || command -v python3 || command -v python3.14 )
if [ -z "$PYTHON_BIN" ]; then
    echo "❌ Python not found."
    exit 1
fi

bash "$(dirname "$0")/checkversion.sh" "$PYTHON_BIN"

# Use Python to locate the shared library
PYTHON_LIBDIR=$($PYTHON_BIN -c "import sysconfig; print(sysconfig.get_config_var('LIBDIR'))")
if [ ! -d "$PYTHON_LIBDIR" ]; then
    echo "⚠️ Could not determine Python shared object path."
    exit 1
fi

# Use Python to locate the path of the executable
PYTHON_EXECUTABLE=$($PYTHON_BIN -c "import sys; print(sys.executable)")

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
case "$(uname -s)" in
  Darwin) PLAT="macos" ;;
  Linux)  PLAT="linux" ;;
  CYGWIN*|MINGW*|MSYS*) PLAT="windows" ;;
  *)      echo "Unsupported OS: $(uname -s)"; exit 1 ;;
esac
JAR="$SCRIPT_DIR/target/org.openjdk.engine.python-3.14-$PLAT.jar"
[ -f "$JAR" ] || { echo "JAR not found: $JAR"; exit 1; }

LD_LIBRARY_PATH="$PYTHON_LIBDIR":$LD_LIBRARY_PATH java \
  -Djava.library.path="$PYTHON_LIBDIR" \
  -Dorg.openjdk.engine.python.program.name="$PYTHON_EXECUTABLE" \
  --module-path "$JAR" \
  --add-modules org.openjdk.engine.python \
  --enable-native-access=org.openjdk.engine.python \
  --add-exports java.base/jdk.internal.vm=org.openjdk.engine.python \
  --add-exports java.base/jdk.internal.misc=org.openjdk.engine.python \
  -m org.openjdk.engine.python/org.openjdk.engine.python.PythonMain "$@"

