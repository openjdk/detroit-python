
/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import javax.script.*;

import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;
import org.openjdk.engine.python.PythonRemoteScriptEngine;
import org.openjdk.engine.python.PythonScriptEngine;

// By default remote engine sends pickled objects. If you call
// any method on such objects, methods are executed locally.
// But, sometimes we may to access method(s) of a remote object
// so that methods will run on the remote process.
void main() throws Exception {
    var m = new ScriptEngineManager();
    var e = (PythonScriptEngine) m.getEngineByName("python");
    e.setExecMode(PyExecMode.FILE);

    var re = PythonRemoteScriptEngine.create(e);
    re.setExecMode(PyExecMode.FILE);
    re.eval("import os");
    re.eval("""
            class MyClass:
                def __init__(self, x):
                    self.x = x
                def add(self, y):
                    print("pid =", os.getpid())
                    print(self.x + y)
                def call(self):
                    print("pid =", os.getpid())
                    return self.x
            """);

    re.setExecMode(PyExecMode.EVAL);

    // script has to register an object as remote object!
    var remoteObj = re.eval("RemoteObjectManager.register(MyClass(25))");
    IO.println(remoteObj);

    // call "func" method on the remote object
    re.invokeMethod(remoteObj, "add", 233);

    // get Java interface object backed by methods of remote object
    var callable = re.getInterface(remoteObj, Callable.class);
    IO.println(callable.call());
}
