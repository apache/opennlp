#!/bin/sh
#   Licensed to the Apache Software Foundation (ASF) under one
#   or more contributor license agreements.  See the NOTICE file
#   distributed with this work for additional information
#   regarding copyright ownership.  The ASF licenses this file
#   to you under the Apache License, Version 2.0 (the
#   "License"); you may not use this file except in compliance
#   with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing,
#   software distributed under the License is distributed on an
#   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#   KIND, either express or implied.  See the License for the
#   specific language governing permissions and limitations
#   under the License.

# Reproduces the parity and single-thread speed comparison between opennlp-embeddings and the
# model2vec Python reference: the same model and the same sentences on both sides, with the
# vector sets checked against each other. Run from this directory after building the project
# (mvn install, or at least mvn compile from the repository root).
#
# Environment overrides:
#   MODEL_DIR  the static model directory (default: bge-m3-static in this directory;
#              see ../distill_bge_m3.py and opennlp-extensions/opennlp-embeddings/TRAINING.md
#              to produce one)
#   PYTHON     a Python interpreter with model2vec installed (default: python3)
set -e

MODEL_DIR="${MODEL_DIR:-bge-m3-static}"
PYTHON="${PYTHON:-python3}"

# The repository root is three levels above this script.
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
CP="$ROOT/opennlp-api/target/classes:$ROOT/opennlp-core/opennlp-runtime/target/classes:$ROOT/opennlp-extensions/opennlp-subword/target/classes:$ROOT/opennlp-extensions/opennlp-embeddings/target/classes"

echo "Model: $MODEL_DIR"
echo "Sentences: $(grep -c . sentences.txt) lines, multilingual"
echo

# JVM side first: it writes jvm_vectors.tsv, which the Python side then diffs.
javac -cp "$CP" -d . EmbedBenchM3.java
java -cp "$CP:." EmbedBenchM3 "$MODEL_DIR" sentences.txt jvm_vectors.tsv 3 5

# Python side: prints its own rate, then reports parity against the JVM's vectors.
"$PYTHON" parity_speed.py "$MODEL_DIR"
