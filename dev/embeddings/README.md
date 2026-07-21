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

# Embeddings scripts

Developer scripts around the static embeddings module. None of them are part of the build; they
make the module's numbers and its worked example reproducible from a checkout.

## `distill_bge_m3.py`

The runnable form of the TRAINING.md worked example: distills the multilingual bge-m3 teacher
into a 256-dimension static table with Model2Vec. Needs a Python environment with
`model2vec[distill]` installed; the script's header shows the setup. After it finishes, copy the
teacher's `sentencepiece.bpe.model` next to the output and verify with the `AssembleModel`
command.

## `parity/`

The parity and single-thread speed comparison between this module and the model2vec Python
reference: the same model and the same multilingual sentences on both sides, the two vector sets
checked against each other, and both throughputs measured with the same fixed-duration
methodology. `sh run.sh` after building the project; see the script header for the environment
overrides. A run passes only when the vectors agree within float tolerance, so the two speeds it
prints are for implementations producing the same answer.
