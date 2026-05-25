##
# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
##


import builtins
import multiprocessing
import pickle
import sys
import typing

from enum import Enum
from multiprocessing import Queue
from typing import NamedTuple

multiprocessing.set_start_method("spawn", force=True)

def is_picklable(value):
  """Checks if a value is picklable."""
  try:
    pickle.dumps(value)
    return True
  except (pickle.PicklingError, TypeError, AttributeError):
    # Catches general pickling errors and TypeErrors (e.g., for lambda functions)
    return False

def picklable_dict(original_dict: dict):
    if is_picklable(original_dict):
        return original_dict
    else:
        return {
            key: value
            for key, value in original_dict.items()
                if key != "__builtins__" and is_picklable(value)
        }

def picklable_list(original_list: list):
    if is_picklable(original_list):
        return original_list
    else:
        return [ picklable_value(value) for value in original_list ]

def picklable_tuple(original_tuple: tuple):
    if is_picklable(original_tuple):
        return original_tuple
    else:
        temp_list = [ picklable_value(value) for value in original_tuple ]
        # handle tuple subclasses like NamedTuple
        return type(original_tuple)(temp_list)

def picklable_value(value):
    if isinstance(value, dict):
        return picklable_dict(value)
    elif isinstance(value, list):
        return picklable_list(value)
    elif isinstance(value, tuple):
        return picklable_tuple(value)
    else:
        return value if is_picklable(value) else None

# command type codes
class CommandType(Enum):
    DONE = 0
    # command data tuple (file_name, mode, scope_dict)
    EVAL = 1
    # command data tuple (file_name, mode)
    COMPILE = 2
    # (optional) command data is scope dictionary
    EVAL_COMPILED = 3
    CLOSE_COMPILED = 4
    GET_VAR = 5
    # command data is value of the variable assigned
    PUT_VAR = 6
    GET_GLOBALS = 7
    # command data is scope dictionary
    PUT_GLOBALS = 8
    # command data is the list of args
    CALL_FUNCTION = 9
    # command data is the list of args including self
    CALL_METHOD = 10

# remote command details
class Command(NamedTuple):
    # type of the command
    type: CommandType
    # message string associated with the command
    message: str
    # command specific additional data, if any. can be None
    data: object | None

# result from a remote command
class Result(NamedTuple):
    # result value
    value: object | None
    # exception value
    error: Exception | None

# result from eval commands that accept input dictionary
class EvalCommandResult(NamedTuple):
    # eval result
    eval_value: object | None
    # output dictionary
    scope_dict: dict | None

# This is the token sent to client for a remote object
class RemoteObject(NamedTuple):
    object_id: str

# simple dictionary based remote object manager
class RemoteObjectManager:
    # RemoteObject -> (local) object
    objectMap = dict()

    @classmethod
    def find(cls, remoteObj: RemoteObject) -> object:
        return cls.objectMap.get(remoteObj, None)

    @classmethod
    def register(cls, obj: object) -> RemoteObject:
        remoteObj = RemoteObject(hex(id(obj)));
        cls.objectMap[remoteObj] = obj;
        return remoteObj

    @classmethod
    def unregister(cls, remoteObj: RemoteObject) -> None:
        cls.objectMap.pop(remoteObj, None)

class RemoteEngineServer:
    def __init__(self, command_queue: Queue, result_queue: Queue):
        self.command_queue = command_queue
        self.result_queue = result_queue
        self.set_current_globals(dict())
        # string id -> code_object
        self.code_objects = dict()

    def set_current_globals(self, d: dict):
        d["__builtins__"] = builtins.__dict__
        # expose remote object manager for convenience
        d["RemoteObjectManager"] = RemoteObjectManager
        self.current_globals = d

    def get_global_function(self, func_name: str):
        if func_name in self.current_globals:
            return self.current_globals[func_name]
        elif hasattr(builtins, func_name):
            return getattr(builtins, func_name)
        else:
            return None

    def execute_command(self, command: Command):
        match command.type:
            case CommandType.EVAL:
                # command.data tuple (file_name, mode, scope_dict)
                (file_name, mode, scope_dict) = typing.cast(tuple, command.data)
                if not isinstance(command.data, tuple):
                    raise TypeError("command.data is not a tuple")
                code_object = compile(command.message, file_name, mode);
                if isinstance(scope_dict, dict):
                    eval_value = eval(code_object, scope_dict, None)
                    # return a named tuple with eval value and the updated dictionary
                    return EvalCommandResult(picklable_value(eval_value), picklable_dict(scope_dict))
                else:
                    return picklable_value(eval(code_object, self.current_globals, None))
            case CommandType.COMPILE:
                # command.data tuple (file_name, mode)
                (file_name, mode) = typing.cast(tuple, command.data)
                if not isinstance(command.data, tuple):
                    raise TypeError("command.data is not a tuple")
                code_object = compile(command.message, file_name, mode);
                code_object_id = hex(id(code_object))
                self.code_objects[code_object_id] = code_object
                return code_object_id
            case CommandType.EVAL_COMPILED:
                # optional command data is scope dictionary
                code_object = self.code_objects[command.message]
                if isinstance(command.data, dict):
                    scope_dict = command.data
                    eval_value = eval(code_object, scope_dict, None)
                    # return a named tuple with eval value and the updated dictionary
                    return EvalCommandResult(picklable_value(eval_value), picklable_dict(scope_dict))
                else:
                    return picklable_value(eval(code_object, self.current_globals, None))
            case CommandType.CLOSE_COMPILED:
                self.code_objects.pop(command.message, None)
                return command.message
            case CommandType.GET_VAR:
                return picklable_value(self.current_globals[command.message])
            case CommandType.PUT_VAR:
                # command data is value of the variable assigned
                # do not allow __builtins__ to be overwritten!
                if command.message != "__builtins__":
                    self.current_globals[command.message] = command.data
                else:
                    raise Exception("cannot overwritte __builtins__")
                return None
            case CommandType.GET_GLOBALS:
                return picklable_dict(self.current_globals)
            case CommandType.PUT_GLOBALS:
                # command data is scope dictionary
                if isinstance(command.data, dict):
                    self.set_current_globals(command.data)
                    return None
                else:
                    raise TypeError("command.data is not a dictionary")
            case CommandType.CALL_FUNCTION:
                # command data is the list of args
                args = typing.cast(list, command.data)
                if isinstance(args, list):
                    func = self.get_global_function(command.message)
                    if not callable(func):
                        raise TypeError("not a callable: " + command.message)
                    return picklable_value(func(*args))
                else:
                    raise TypeError("command.data is not a list")
            case CommandType.CALL_METHOD:
                # command data is the list of args including self
                args = typing.cast(list, command.data)
                remoteObj = args[0]
                if not isinstance(remoteObj, RemoteObject):
                    raise TypeError("expected a RemoteObject in command.data[0]")
                if isinstance(args, list):
                    localObj = RemoteObjectManager.find(remoteObj)
                    if localObj is None:
                        raise Exception("No such remote object: " + str(remoteObj))
                    method = getattr(localObj, command.message)
                    if not callable(method):
                        raise TypeError("not a callable: " + command.message)
                    return picklable_value(method(*args[1:]))
                else:
                    raise TypeError("command.data is not a list")
            case _:
                print(f"Unknown command: {command}")
                return None

    def run(self):
        exit_code = 0
        while True:
            command = self.command_queue.get()
            if command.type == CommandType.DONE:
                self.result_queue.put(Result("DONE", None))
                if isinstance(command.data, int):
                    exit_code = command.data
                break
            try:
                result = Result(self.execute_command(command), None);
            except Exception as e:
                result = Result(None, e)
            self.result_queue.put(result)
        self.command_queue.close()
        self.result_queue.close()
        sys.exit(exit_code)



def server(command_queue: Queue, result_queue: Queue):
    remote_engine_server = RemoteEngineServer(command_queue, result_queue)
    remote_engine_server.run()

class RemoteEngineClient:
    def __init__(self):
        self.command_queue = multiprocessing.Queue()
        self.result_queue = multiprocessing.Queue()

        server_proc = multiprocessing.Process(target=server, args=(self.command_queue, self.result_queue))
        server_proc.start()

    def send_command(self, cmd: Command):
        if not is_picklable(cmd.data):
            raise Exception("cannot pickle command data")
        self.command_queue.put(cmd)
        result = self.result_queue.get()
        if result.error is None:
            return result.value
        else:
            raise result.error

    def eval_command(self, code: str, file_name: str, mode: str, scope_dict: dict | None = None):
        if isinstance(scope_dict, dict):
            eval_result = self.send_command(Command(CommandType.EVAL, code, (file_name, mode, picklable_dict(scope_dict))))
            scope_dict.update(eval_result.scope_dict)
            return eval_result.eval_value;
        else:
            return self.send_command(Command(CommandType.EVAL, code, (file_name, mode, None)))

    def compile_command(self, code: str, file_name: str, mode: str = 'eval'):
        return self.send_command(Command(CommandType.COMPILE, code, (file_name, mode)))

    def eval_codeobject_command(self, code: str, scope_dict: dict | None = None):
        if isinstance(scope_dict, dict):
            eval_result = self.send_command(Command(CommandType.EVAL_COMPILED, code, picklable_dict(scope_dict)))
            scope_dict.update(eval_result.scope_dict)
            return eval_result.eval_value
        else:
            return self.send_command(Command(CommandType.EVAL_COMPILED, code, None))

    def close_codeobject_command(self, code: str):
        return self.send_command(Command(CommandType.CLOSE_COMPILED, code, None))

    def get_var_command(self, name: str):
        return self.send_command(Command(CommandType.GET_VAR, name, None))

    def put_var_command(self, name: str, value: object):
        return self.send_command(Command(CommandType.PUT_VAR, name, picklable_value(value)))

    def get_globals_command(self):
        return self.send_command(Command(CommandType.GET_GLOBALS, "", None))

    def put_globals_command(self, scope_dict: dict):
        if not isinstance(scope_dict, dict):
            raise TypeError("expected a dict object for scope_dict")
        return self.send_command(Command(CommandType.PUT_GLOBALS, "", picklable_dict(scope_dict)))

    def call_global_function(self, func_name: str, args: list):
        if not isinstance(args, list):
            raise TypeError("expected a list for args")
        return self.send_command(Command(CommandType.CALL_FUNCTION, func_name, picklable_list(args)))

    def call_object_method(self, obj: RemoteObject, method_name: str, args: list):
        if not isinstance(obj, RemoteObject):
            raise TypeError("expected a RemoteObject")
        if not isinstance(args, list):
            raise TypeError("expected a list for args")
        return self.send_command(Command(CommandType.CALL_METHOD, method_name, picklable_list([ obj, *args ])))

    def engine_close_command(self):
        self.send_command(Command(CommandType.DONE, "", None))
        self.command_queue.close()
        self.result_queue.close()
