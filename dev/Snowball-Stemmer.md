<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Building and Integrating Snowball Stemmer for OpenNLP

This guide outlines the steps to build the Snowball compiler, generate stemmer classes, and integrate them into OpenNLP.

---

## Prerequisites

- A Unix-like environment with `make` installed.
- Access to the [Snowball repository](https://github.com/snowballstem/snowball).
- The OpenNLP repository checked out locally.

---

## Procedure

### 1. Clone and Build the Snowball Compiler

Clone the Snowball repository and build the compiler using `make`:

```bash
git clone https://github.com/snowballstem/snowball.git
cd snowball
make
```

This will generate the snowball compiler in the root directory of the repository.

# Run the Snowball Compiler

Run the Snowball compiler to generate the stemmer code. 

```bash
#!/bin/bash

# Define an array of languages
languages=("arabic" "catalan" "danish" "dutch" "english" "finnish" "french" "german" "greek" "hungarian" "indonesian" "irish" "italian" "norwegian" "porter" "portuguese" "romanian" "russian" "spanish" "swedish" "turkish")

# Base paths
snowball_exec_path="../snowball"
output_base="../../../../IdeaProjects/opennlp/opennlp-tools/src/main/java/opennlp/tools/stemmer/snowball"

# Loop through the languages and execute the command
for lang in "${languages[@]}"; do
  "${snowball_exec_path}" "${lang}.sbl" -java -o "${output_base}/${lang}Stemmer"
done
```

Usage:
1. Save this script as `generate_stemmers.sh` at the appropriate location.
2. Make it executable with `chmod +x generate_stemmers.sh`.
3. Run it using `./generate_stemmers.sh`.

# Manually Reformat Code to Match OpenNLP Style

- Open the generated Java files in your preferred IDE or text editor.
- Reformat the code to match the OpenNLP code style. This may include:
- Adjusting indentation.
- Renaming variables or methods as needed.
- Ensuring proper spacing and alignment.

# Add License Information

- Ensure each generated file includes the appropriate license information for both Snowball and OpenNLP.

