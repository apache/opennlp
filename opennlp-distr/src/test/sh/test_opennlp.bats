#!/usr/bin/env bats

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Setup the environment before running the tests
setup() {
  PATH="$OPENNLP_HOME/bin:$PATH"
}

# Test to check if the binary is accessible
@test "Binary 'opennlp' exists and is executable" {
  run command -v opennlp
  [ "$status" -eq 0 ]
  [ -x "$output" ]
}

# Test to validate the output of the SimpleTokenizer
@test "SimpleTokenizer produces correct output" {
  input="Hello, friends"
  expected_output="Hello , friends"

  # Run the command and capture output
  run echo "$input" | opennlp SimpleTokenizer

  # Debugging: Log the status and output
  echo "Status: $status"
  echo "Output: $output"

  # Validate the command executed successfully
  [ "$status" -eq 0 ] || echo "Error: opennlp SimpleTokenizer failed"

  # Validate the output matches the expected result
  [ "${output}" = "$expected_output" ] || echo "Unexpected output: ${output}"
}

# Teardown the environment after running the tests
teardown() {
  unset OPENNLP_HOME
}
