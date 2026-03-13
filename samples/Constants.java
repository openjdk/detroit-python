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
import org.openjdk.engine.python.PyObject;
import org.openjdk.engine.python.PythonScriptEngine;

void main() throws ScriptException {
    var m = new ScriptEngineManager();
    var e = (PythonScriptEngine) m.getEngineByName("python");
    var pyNone = e.getNone();
    var pyTrue = e.getTrue();
    var pyFalse = e.getFalse();
    var pyEllipsis = e.getEllipsis();
    var pyNotImplemented = e.getNotImplemented();

    IO.println("isNone? " + pyNone.isNone());
    IO.println("isTrue? " + pyTrue.isTrue());
    IO.println("isFalse? " + pyFalse.isFalse());
    IO.println("isEllipsis? " + pyEllipsis.isEllipsis());
    IO.println("isNotImplemented? " + pyNotImplemented.isNotImplemented());

    PyObject typeFunc = (PyObject) e.eval("type");
    IO.println(typeFunc.call(pyNone));
    IO.println(typeFunc.call(pyTrue));
    IO.println(typeFunc.call(pyFalse));
    IO.println(typeFunc.call(pyEllipsis));
    IO.println(typeFunc.call(pyNotImplemented));

    PyObject printFunc = (PyObject) e.eval("print");
    printFunc.call(pyNone);
    printFunc.call(pyTrue);
    printFunc.call(pyFalse);
    printFunc.call(pyEllipsis);
    printFunc.call(pyNotImplemented);
}
