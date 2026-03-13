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
import org.openjdk.engine.python.*;
import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;

void main() {
    ScriptEngineManager m = new ScriptEngineManager();
    var engine = (PythonScriptEngine) m.getEngineByName("python");
    engine.withPyObjectManager(() -> {
        try {
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("myvar = 233");
            IO.println("myvar = " + engine.get("myvar"));

            ScriptContext defaultContext = engine.getContext();

            // The variable "myvar" should not exist in a fresh context
            ScriptContext newContext = new SimpleScriptContext();
            newContext.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
            // set new context as engine context
            engine.setContext(newContext);
            try {
                engine.eval("myvar");
                throw new AssertionError("should not reach here");
            } catch (ScriptException se) {
                if (se instanceof PythonException pe) {
                    pe.print();
                } else {
                    System.err.print(se);
                }
            }

            // Let's try setting a different value
            engine.setExecMode(PyExecMode.SINGLE);
            engine.eval("myvar = 'hello'");
            IO.println("myvar = " + engine.get("myvar"));

            // in the default context, we should stll see old value
            // set engine context to be the default context
            engine.setContext(defaultContext);
            IO.println("myvar = " + engine.get("myvar"));
        } catch (ScriptException ex) {
                throw new RuntimeException(ex);
        }
    });
}
