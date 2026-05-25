
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
import org.openjdk.engine.python.PythonException;
import org.openjdk.engine.python.PythonRemoteCompiledScript;
import org.openjdk.engine.python.PythonRemoteScriptEngine;
import org.openjdk.engine.python.PythonScriptEngine;


void main() throws IOException {
    System.setProperty("org.openjdk.engine.python.sys.prepend.path", "");
    var m = new ScriptEngineManager();
    var e = (PythonScriptEngine) m.getEngineByName("python");
    e.setExecMode(PyExecMode.FILE);

    try {
        e.eval("import os");
        e.setExecMode(PyExecMode.EVAL);
        IO.println("local pid = " + e.eval("os.getpid()"));

        e.setExecMode(PyExecMode.FILE);
        e.eval("from squaring import *");
        IO.println(e.invokeFunction("square", 27));
        e.setExecMode(PyExecMode.EVAL);
        var numbers = e.eval("[23, 44, 12]");

        var re = PythonRemoteScriptEngine.create(e);
        re.setExecMode(PyExecMode.FILE);
        re.eval("import os");
        re.setExecMode(PyExecMode.EVAL);
        IO.println("remote pid = " + re.eval("os.getpid()"));
        re.setExecMode(PyExecMode.FILE);
        re.eval("from squaring import *");
        // we can call global functions on the remote engine
        IO.println(re.invokeFunction("square", 27));
        // we can call global functions on the remote engine by passing
        // arbitaray Python picklable arguments from the local engine
        IO.println("sum of " + numbers + " is " + re.invokeFunction("sum", numbers));

        // we can compile script remotely and eval it many times
        var reCompiled = (PythonRemoteCompiledScript) re.compile("""
           for i in range(10):
                print(i*i)
           """);

        IO.println(reCompiled);
        reCompiled.eval();

        // closed the compiled script
        reCompiled.close();

        IO.println(reCompiled);
        // cannot eval after close!
        reCompiled.eval();
    } catch (ScriptException se) {
        if (se instanceof PythonException pe) {
            pe.print();
        } else {
            se.printStackTrace();
        }
    } catch (NoSuchMethodException nsme) {
        nsme.printStackTrace();
    }
}
