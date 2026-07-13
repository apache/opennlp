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

"""Distills the multilingual bge-m3 teacher into a static embedding table.

This is the worked example from TRAINING.md as a runnable script. It needs a Python
environment with model2vec's distill extra installed:

    uv venv .venv-distill
    uv pip install --python .venv-distill "model2vec[distill]"
    .venv-distill/bin/python distill_bge_m3.py [output-dir]

After it finishes, copy the teacher's trained SentencePiece file
(sentencepiece.bpe.model on the model hub) into the output directory and run the
AssembleModel command to verify the directory loads:

    opennlp-embeddings AssembleModel -modelDir <output-dir>

256 dimensions is the deliberate default: distilling the same teacher at 512 gives the
same cross-lingual similarity within noise while doubling the matrix and halving embed
throughput, because PCA to 256 already captures the useful variance.
"""

import os
import sys

from model2vec.distill import distill

out = sys.argv[1] if len(sys.argv) > 1 else "bge-m3-static"

static = distill("BAAI/bge-m3", pca_dims=256)
static.save_pretrained(out)
print("SAVED:", out, "dim:", static.dim)

print("=== output files ===")
for name in sorted(os.listdir(out)):
    path = os.path.join(out, name)
    print(f"  {os.path.getsize(path):>12}  {name}")
print("Now copy the teacher's sentencepiece.bpe.model into", out,
      "and run: opennlp-embeddings AssembleModel -modelDir", out)
