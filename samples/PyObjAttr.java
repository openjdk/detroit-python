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
import org.openjdk.engine.python.PythonException;
import org.openjdk.engine.python.PythonScriptEngine;
import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;

void main() {
    ScriptEngineManager m = new ScriptEngineManager();
    var e = (PythonScriptEngine) m.getEngineByName("python");
    try {
        var list = (PyObject) e.eval("[233, 444]");
        var listLen = list.getAttribute("__len__");
        IO.println(listLen.call());
        list.decRefCount();

        var map = (PyObject) e.eval("{ 'Java': 25, 'Python': 3.14 }");
        var mapGet = map.getAttribute("get");
        IO.println(mapGet.call("Java"));
        IO.println(mapGet.call("Python"));
        map.decRefCount();
        e.setExecMode(PyExecMode.SINGLE);
        e.eval("""
        class Person:
            def __init__(self, name, age):
                self.name = name
                self.age = age
        """);

        e.setExecMode(PyExecMode.EVAL);
        var personCls = (PyObject) e.eval("Person");
        IO.println(personCls);
        var personObj = (PyObject) personCls.call("Alice", 7);
        IO.println(personObj);
        IO.println("name = " + personObj.getAttribute("name"));
        IO.println("age = " + personObj.getAttribute("age"));
        personObj.deleteAttribute("name");
        IO.println(personObj.getAttributeOptional("name"));
        IO.println(personObj.setAttribute("name", "Alice Pleasance Liddell"));
        IO.println(personObj.getAttributeOptional("name"));
        personObj.deleteAttribute("age");
        // getAttribute on non-existent property throws exception
        IO.println(personObj.getAttribute("age"));
    } catch (ScriptException se) {
        if (se instanceof PythonException pe) {
            pe.print();
        } else {
            System.err.println(se);
        }
    }
}
