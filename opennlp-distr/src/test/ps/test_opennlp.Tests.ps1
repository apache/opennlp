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

Describe "opennlp.bat Tests" {

    # Setup before all tests
    BeforeAll {
        # Ensure OPENNLP_HOME is added to PATH
        if (-not $env:OPENNLP_HOME) {
            Throw "OPENNLP_HOME environment variable is not set."
        }

        # Add the OPENNLP_HOME\bin directory to the PATH so opennlp.bat can be found
        $env:PATH = "$env:OPENNLP_HOME\bin;$env:PATH"
    }

    # Test if opennlp.bat is accessible and executable
    It "opennlp.bat exists and is executable" {
        $batFile = Join-Path $env:OPENNLP_HOME "bin\opennlp.bat"

        # Ensure the .bat file exists
        $batFile | Should -Exist

        # Check if the bat file runs by executing it with a help flag
        $result = & $batFile -h

        # Validate the result isn't null or empty
        $result | Should -Not -BeNullOrEmpty
    }

    # Test the output of SimpleTokenizer
    It "SimpleTokenizer produces correct output" {
        $input = "Hello, friends"
        $expected_output = "Hello , friends"

        # Run the opennlp.bat with SimpleTokenizer, using input redirection via echo
        $output = echo $input | & "$env:OPENNLP_HOME\bin\opennlp.bat" SimpleTokenizer

        # Debugging: Log the output
        Write-Host "Output: $output"

        # Validate the command executed successfully
        $output | Should -Not -BeNullOrEmpty

        # Check if the expected output is in the result
        $output | Should -Contain $expected_output
    }

    # Cleanup after tests
    AfterAll {
        # Remove OPENNLP_HOME from PATH after tests are done
        Remove-Item Env:\PATH -ErrorAction SilentlyContinue
    }
}
