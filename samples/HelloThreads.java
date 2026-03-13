/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.engine.python.PythonException;
import org.openjdk.engine.python.PythonScriptEngine;
import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;

void main() throws Exception {
    // two engines runnings long tasks from 2 threads
    var m = new ScriptEngineManager();
    var e1 = (PythonScriptEngine) m.getEngineByName("python");
    IO.println("engine created " + e1 + "isMainEngine? " + e1.isMainEngine());
    e1.setExecMode(PyExecMode.FILE);
    var e2 = (PythonScriptEngine) m.getEngineByName("python");
    IO.println("engine created " + e2 + "isMainEngine? " + e2.isMainEngine());
    e2.setExecMode(PyExecMode.FILE);

    Thread t = new Thread(() -> {
        e2.withPyThreadState(() -> {
            try {
                e2.eval("""
                for i in range(1000):
                    print(f"square of {i} = {i*i}")
                """);
            } catch (ScriptException se) {
                if (se instanceof PythonException pe) {
                    pe.print();
                } else {
                    System.err.println(se);
                }
            }
        });
    });

    t.start();
    e1.eval("""
    for i in range(1000):
        print(f"cube of {i} = {i*i*i}")
    """);
    t.join();
    IO.println(e2.isClosed());
    e2.close();
    IO.println(e2.isClosed());

    e1.close();
}
