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

cd target
mkdir -p org.openjdk.engine.python-3.13
mkdir -p org.openjdk.engine.python-3.13/target
mkdir -p org.openjdk.engine.python-3.13/samples
mkdir -p org.openjdk.engine.python-3.13/docs
cat > org.openjdk.engine.python-3.13/README.md <<"EOF"

# alpha early access binary for CPython engine for Java.

## Prerequisites

* Install JDK-25 and put it in PATH
* Install Python 3.13 and put it in PATH.
You can be inside a Python virtual environment.

## Simple REPL
```sh
sh ./jpython.sh
```

## Run java code that uses Python engine

```sh
cd samples
sh ../run.sh Hello.java
```

## Python virtual enviroments

Python developers often create a [virtual environments](https://docs.python.org/3/library/venv.html) and activate it.

```bash
python3 -m venv ~/bin/my_env
source ~/bin/myenv/bin/activate
```

Once activated, 'pip install' commands will install packages into the
active virtual environment.

org.openjdk.engine.python engine supports virtual environments. You can import
modules from packages installed in the currently active environment.
EOF

cp org.openjdk.engine.python-3.13*.jar org.openjdk.engine.python-3.13/target/
# copy license files
cp ../ADDITIONAL_LICENSE_INFO org.openjdk.engine.python-3.13
cp -r ../legal org.openjdk.engine.python-3.13
cp ../LICENSE org.openjdk.engine.python-3.13
cp -r reports/apidocs/* org.openjdk.engine.python-3.13/docs
cp -r ../samples org.openjdk.engine.python-3.13
cp -r ../checkversion.sh org.openjdk.engine.python-3.13
cp -r ../run.sh org.openjdk.engine.python-3.13
cp -r ../jpython.sh org.openjdk.engine.python-3.13
cp -r ../checkversion.ps1 org.openjdk.engine.python-3.13
cp -r ../run.ps1 org.openjdk.engine.python-3.13
cp -r ../jpython.ps1 org.openjdk.engine.python-3.13
zip org.openjdk.engine.python-3.13.zip -r org.openjdk.engine.python-3.13
