
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

# Set error action to stop on errors (equivalent to set -e)
$ErrorActionPreference = 'Stop'

# Try to find python, then python3, then python3.13. Select the first one found.
$PythonBin = Get-Command python, python3, python3.13 -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $PythonBin) {
    Write-Host "Python not found." -ForegroundColor Red
    exit 1
}

# Get the actual executable path
$PythonExe = $PythonBin.Source

# 2. Run the version check script
$CheckScriptPath = Join-Path $PSScriptRoot "checkversion.ps1"

if (Test-Path $CheckScriptPath) {
    & $CheckScriptPath -PythonBinPath $PythonExe
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
else {
    Write-Warning "checkversion.ps1 not found, skipping version check."
}

# 3. Use Python to locate the library directory
try {
    $PythonLibDir = & $PythonExe -c "import sys; print(sys.base_prefix)"
    $PythonLibDir = $PythonLibDir.Trim()
}
catch {
    Write-Host "Could not determine Python shared object path." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $PythonLibDir)) {
    Write-Host "Python library directory does not exist: $PythonLibDir" -ForegroundColor Yellow
    exit 1
}


# 4. Use Python to locate the executable
try {
    $PythonExecutable = (& $PythonBin -c "import sys; print(sys.executable)").Trim()
}
catch {
    Write-Host "Could not determine Python executable path." -ForegroundColor Yellow
    exit 1
}

# 5. Locate the JAR
$ModuleJarPath = Join-Path $PSScriptRoot "target\org.openjdk.engine.python-3.13-windows.jar"
if (-not (Test-Path $ModuleJarPath)) { Write-Host "JAR not found. Run 'mvn package'." -F Red; exit 1 }
$ModuleJarPath = (Resolve-Path $ModuleJarPath).Path

# 6. Run Java
$OriginalPath = $env:Path
$env:Path = "$PythonLibDir;$env:Path"

# Build arguments array for cleaner execution
$JavaArgs = @(
    "-Djava.library.path=$PythonLibDir",
    "-Dorg.openjdk.engine.python.program.name=$PythonExecutable",
    "--module-path", "$ModuleJarPath",
    "--add-modules", "org.openjdk.engine.python",
    "--enable-native-access=org.openjdk.engine.python",
    "--add-exports", "java.base/jdk.internal.vm=org.openjdk.engine.python",
    "--add-exports", "java.base/jdk.internal.misc=org.openjdk.engine.python",
    "-Djextract.trace.downcalls=true",
    "-Dorg.openjdk.engine.python.debug=true",
    "-ea:org.openjdk.engine.python..."
)

# Append any arguments passed to this script ($args) to the Java command
$JavaArgs += $args

try {
    & java $JavaArgs
}
catch {
    Write-Host "Error running Java: $_" -ForegroundColor Red
    exit $LASTEXITCODE
}
finally {
    $env:Path = $OriginalPath
}