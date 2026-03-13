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

import sys

# Choose a Tool ID (0-5 are reserved for debuggers/profilers)
# sys.monitoring.DEBUGGER_ID = 0
# sys.monitoring.COVERAGE_ID = 1
# sys.monitoring.PROFILER_ID = 2
# sys.monitoring.OPTIMIZER_ID = 5

TOOL_ID = sys.monitoring.DEBUGGER_ID
sys.monitoring.use_tool_id(TOOL_ID, "MyTracer")

# The tracing callback
def on_line(code, line_number):
    print(f"Executing {code.co_name} at line {line_number}")
    # Returning sys.monitoring.DISABLE here would stop monitoring
    # this specific location for better performance.

# Register the callback for the LINE event
sys.monitoring.register_callback(TOOL_ID, sys.monitoring.events.LINE, on_line)

# Enable the events globally
sys.monitoring.set_events(TOOL_ID, sys.monitoring.events.LINE)

# --- actual program being monitored ---
def hello_world():
    x = 10
    y = 20
    return x + y

hello_world()

# Clean up tracing events
sys.monitoring.set_events(TOOL_ID, 0)
