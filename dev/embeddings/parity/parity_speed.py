# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""The Python half of the parity and speed comparison, plus the final parity check.

Loads the same static table with model2vec, writes one vector per sentence, measures
single-thread throughput with the same fixed-duration warmup-discarded loop the JVM side
uses, then loads the JVM's vectors (written first by run.sh) and reports the parity between
them.

The point is not to declare a winner; it is to show that both implementations produce the
same vectors, and to let anyone reproduce both numbers on their own hardware.

Usage: parity_speed.py <model-dir> [sentences-file] [jvm-vectors-file]
"""

import sys
import time

import numpy as np
from model2vec import StaticModel

WARMUP_SECONDS = 3
MEASURE_SECONDS = 5


def read_sentences(path):
    with open(path, encoding="utf-8") as handle:
        return [line.strip() for line in handle if line.strip()]


def main():
    model_dir = sys.argv[1]
    sentences_file = sys.argv[2] if len(sys.argv) > 2 else "sentences.txt"
    jvm_vectors_file = sys.argv[3] if len(sys.argv) > 3 else "jvm_vectors.tsv"
    sentences = read_sentences(sentences_file)

    load_start = time.time()
    model = StaticModel.from_pretrained(model_dir)
    load_ms = (time.time() - load_start) * 1000.0

    python_vectors = np.array([model.encode(s) for s in sentences], dtype=np.float32)

    end = time.time() + WARMUP_SECONDS
    i = 0
    while time.time() < end:
        model.encode(sentences[i % len(sentences)])
        i += 1

    embedded = 0
    i = 0
    start = time.time()
    end = start + MEASURE_SECONDS
    while time.time() < end:
        model.encode(sentences[i % len(sentences)])
        embedded += 1
        i += 1
    seconds = time.time() - start
    print(f"Python  load {load_ms:.0f} ms  |  {embedded / seconds:,.0f} texts/s single-thread  "
          f"({embedded} embeds in {seconds:.1f}s)")

    jvm_vectors = np.loadtxt(jvm_vectors_file, dtype=np.float32)
    if jvm_vectors.shape != python_vectors.shape:
        print(f"PARITY FAIL: shape mismatch {jvm_vectors.shape} vs {python_vectors.shape}")
        sys.exit(1)

    max_abs_diff = float(np.abs(python_vectors - jvm_vectors).max())
    cosines = [
        float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))
        for a, b in zip(python_vectors, jvm_vectors)
    ]
    print(f"Parity  max abs diff {max_abs_diff:.2e}  |  min cosine {min(cosines):.6f}  "
          f"over {len(sentences)} sentences in {python_vectors.shape[1]} dims")
    if min(cosines) < 0.9999:
        print("PARITY FAIL: vectors diverge")
        sys.exit(1)
    print("Parity  OK: the JVM and Python vectors are the same within float tolerance")


if __name__ == "__main__":
    main()
