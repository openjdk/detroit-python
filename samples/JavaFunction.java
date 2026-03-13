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

import org.openjdk.engine.python.PythonScriptEngine;
import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;
import org.openjdk.engine.python.PyJavaFunction;

void main() throws ScriptException {
    ScriptEngineManager m = new ScriptEngineManager();
    var e = (PythonScriptEngine) m.getEngineByName("python");

    PyJavaFunction.OneArgFunc pyFunc = (arg) -> {
        System.out.println("one arg Java function called with: " + arg);
        try {
            return e.fromJava(System.getProperty(arg.toString()));
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    };

    PyJavaFunction.VarArgsFunc pyVarArgsFunc = (args) -> {
        System.out.println("varargs Java function called with: " + args.length  + " args");
        var buf = new StringBuilder();
        for (var arg : args) {
            buf.append(arg.toString());
            buf.append(':');
        }
        try {
            return e.fromJava(buf.toString());
        } catch (ScriptException ex) {
            throw new RuntimeException(ex);
        }
    };


    e.put("getJavaProp", pyFunc);
    e.eval("print('func __name__ =', getJavaProp.__name__)");
    e.eval("print(getJavaProp('java.home'))");

    e.put("concat", pyVarArgsFunc);
    e.eval("print('func __name__ =', concat.__name__)");
    e.eval("print(concat('hello', 233, { 'x' : 42 }))");

    PyJavaFunction.NoArgFunc thrower = ()-> {
        System.out.println("Going to throw exception!");
        throw new RuntimeException("Java Error!!");
    };
    e.put("jexceptionFunc", thrower);
    e.setExecMode(PyExecMode.FILE);
    e.eval("""
    try:
        jexceptionFunc()
    except Exception as e:
        print("Caught exception from Java function call")
        print(e)
    """);
}
