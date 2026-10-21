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

# Embedding Benchmarks

`SentenceVectorsDLBenchmark` times `SentenceVectorsDL` on a real model: `embedEach` calls
`embed` once per text, `embedAll` makes one call. A call holds `callSize` texts of 4 to 40
words, drawn with a fixed seed, so tokenized lengths are mixed as in natural text.

The model directory holds `model.onnx` and `vocab.txt` (for example all-MiniLM-L6-v2) and
defaults to `~/.opennlp/onnx/sentence-transformers`; set it with `-p modelDir=<dir>`.

## Building and running

```bash
mvn test-compile -Pjmh -pl opennlp-core/opennlp-ml/opennlp-dl -am

mvn dependency:build-classpath -pl opennlp-core/opennlp-ml/opennlp-dl \
    -Pjmh -DincludeScope=test -Dmdep.outputFile=/tmp/cp.txt

CP="opennlp-core/opennlp-ml/opennlp-dl/target/classes:opennlp-core/opennlp-ml/opennlp-dl/target/test-classes:$(cat /tmp/cp.txt)"

# CPU, with the bytes allocated per call
java -cp "$CP" org.openjdk.jmh.Main SentenceVectorsDLBenchmark -prof gc
```

For CUDA, replace the `onnxruntime` jar in `CP` with `onnxruntime_gpu` of the same version,
put the CUDA and cuDNN libraries that release needs on `LD_LIBRARY_PATH`, and add
`-p device=gpu`. Build only `opennlp-dl` with `-Pjmh`: the generated `*_jmhTest` classes land
in its test-jar, which `opennlp-dl-gpu` scans for tests.

## Allocation per call

Moving the inference inputs and outputs to direct buffers cut the allocation per call
(`gc.alloc.rate.norm`) about threefold on the CPU and the GPU, without changing the average
time per call. The measurements are in the pull request for GH-1471.
