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

`SentenceVectorsDLBenchmark` times `SentenceVectorsDL` on real models: `embedEach` calls
`embed` once per text, `embedAll` makes one call. A call holds `callSize` texts of 4 to 40
words, drawn with a fixed seed, so tokenized lengths are mixed as in natural text.

## Models

Each model is a directory with `model.onnx` and `vocab.txt` under `~/.opennlp/onnx` (set
another root with `-p modelRoot=<dir>`). By default the benchmark runs a small and a large
embedding dimension:

| `model`            | Dimension | Layers |
|--------------------|-----------|--------|
| `all-MiniLM-L6-v2` | 384       | 6      |
| `e5-large-v2`      | 1024      | 24     |

Export them as shown in [README.md](README.md), with `model_id` set to
`sentence-transformers/all-MiniLM-L6-v2` and `intfloat/e5-large-v2`. Pick other models with
`-p model=<dir>,<dir>`.

## Building and running

```bash
mvn test-compile -Pjmh -pl opennlp-core/opennlp-ml/opennlp-dl -am

mvn dependency:build-classpath -pl opennlp-core/opennlp-ml/opennlp-dl \
    -Pjmh -DincludeScope=test -Dmdep.outputFile=/tmp/cp.txt

CP="opennlp-core/opennlp-ml/opennlp-dl/target/classes:opennlp-core/opennlp-ml/opennlp-dl/target/test-classes:$(cat /tmp/cp.txt)"

java -cp "$CP" org.openjdk.jmh.Main SentenceVectorsDLBenchmark \
    -prof gc -prof opennlp.dl.vectors.NativeMemoryProfiler
```

The `jmh` profile is defined in the parent POM and compiles `src/jmh/java` of every module it is
built with.

For CUDA, replace the `onnxruntime` jar in `CP` with `onnxruntime_gpu` of the same version,
put the CUDA and cuDNN libraries that release needs on `LD_LIBRARY_PATH`, and add
`-p device=gpu`. Build only `opennlp-dl` with `-Pjmh`: the generated `*_jmhTest` classes land
in its test-jar, which `opennlp-dl-gpu` scans for tests.

## What it reports

| Result                    | Mode     | Meaning                                                   |
|---------------------------|----------|-----------------------------------------------------------|
| `embedAll`, `embedEach`   | `avgt`   | Average time per call                                     |
| `:texts`, `:tokens`       | `avgt`   | Time per text and per token position; invert for texts/s and tokens/s |
| `:p0.50` to `:p1.00`      | `sample` | Latency percentiles per call                              |
| `:gc.alloc.rate.norm`     | both     | Heap bytes allocated per call (`-prof gc`)                |
| `:direct.used`, `:direct.count` | both | Direct buffers held at the end of an iteration       |
| `:rss`                    | both     | Resident set size on Linux, which includes ONNX Runtime's native memory |

The memory results come from `-prof opennlp.dl.vectors.NativeMemoryProfiler`. A session keeps
one direct buffer for its largest inference, so `direct.count` and `direct.used` stay flat over
the iterations. To check that no direct memory piles up when explicit garbage collections are
disabled, add `-jvmArgsAppend "-XX:+DisableExplicitGC -XX:MaxDirectMemorySize=128m"`.

## Comparable CPU times

ONNX Runtime uses one intra-op thread per physical core by default, so CPU times move with the
machine's load and core count. Pin the benchmark JVM to a fixed set of cores, for example with
`taskset -c 0-3 java ...` on Linux, and run nothing else on them.
