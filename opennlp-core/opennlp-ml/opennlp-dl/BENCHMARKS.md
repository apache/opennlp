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

The memory results come from `-prof opennlp.dl.vectors.NativeMemoryProfiler`. A session runs
up to `InferenceOptions.setMaxConcurrentInferences` inferences at once, by default one per
available processor, and keeps one direct buffer per running inference until it is closed. A
caller beyond the limit waits, so `direct.count` never exceeds the limit and `direct.used`
stays flat over the iterations. Set the limit with `-p maxConcurrentInferences=<n>`. To check
that no direct memory piles up when explicit garbage collections are disabled, add
`-jvmArgsAppend "-XX:+DisableExplicitGC -XX:MaxDirectMemorySize=128m"`.

## Comparable CPU times

ONNX Runtime uses one intra-op thread per physical core by default, so CPU times move with the
machine's core count and load. The benchmark fixes the session to 4 intra-op threads through
`InferenceOptions`, so CPU times are comparable across machines. For lower noise, pin the JVM
to dedicated cores with `taskset`, for example `taskset -c 0-3 java ...` on Linux, and run
nothing else on them.

## Results

`embedAll` with `-bm sample`, 4 intra-op threads, 3 warmup and 5 measurement iterations of
2 seconds, 1 fork. Threads are concurrent callers of one shared instance (`-t`).

| Machine | CPU                                  | GPU                            | Memory | JDK         | ONNX Runtime |
|---------|--------------------------------------|--------------------------------|-------:|-------------|--------------|
| krick   | AMD Ryzen 9 9950X3D, 16 cores        | RTX 4080 SUPER 16 GB, CUDA 13  | 128 GB | Temurin 25  | 1.31.0       |
| nano1   | Jetson Orin Nano Super, 6 x A78AE    | not run (no aarch64 GPU build) | 8 GB   | Temurin 25  | 1.31.0       |

### Direct buffers under concurrent callers

krick, 8 callers. The rows compare how an inference gets the memory of its tensors: ONNX
Runtime allocating the output from heap inputs, a pool of 2 buffers where a busier call
allocates its own, a pool of 2 where a busier call waits, and one buffer per caller, which is
what the default limit gives up to 32 callers on this machine.

| Model, texts/call, device | Tensor memory          | Mean     | p99      | Direct MB | Heap/call | RSS MB |
|---------------------------|------------------------|---------:|---------:|----------:|----------:|-------:|
| e5-large-v2, 128, CPU     | ONNX Runtime output    | 10.07 s  | 12.23 s  | 4.5       | 15,960 KB | 3,658  |
|                           | pool of 2, allocate    | 9.96 s   | 10.85 s  | 194.8     | 3,047 KB  | 3,089  |
|                           | pool of 2, wait        | 19.03 s  | 182.0 s  | 2.4       | 3,124 KB  | 3,033  |
|                           | one buffer per caller  | 9.73 s   | 10.75 s  | 9.5       | 3,164 KB  | 3,014  |
| e5-large-v2, 128, GPU     | ONNX Runtime output    | 761 ms   | 800 ms   | 5.3       | 16,036 KB | 3,191  |
|                           | pool of 2, allocate    | 777 ms   | 813 ms   | 301.0     | 3,040 KB  | 3,017  |
|                           | pool of 2, wait        | 985 ms   | 4,396 ms | 2.4       | 3,124 KB  | 2,869  |
|                           | one buffer per caller  | 769 ms   | 798 ms   | 9.5       | 3,123 KB  | 2,800  |
| all-MiniLM-L6-v2, 1, CPU  | ONNX Runtime output    | 2.4 ms   | 3.6 ms   | 8.5       | 53 KB     | 1,472  |
|                           | pool of 2, allocate    | 2.3 ms   | 3.3 ms   | 249.0     | 20 KB     | 1,423  |
|                           | pool of 2, wait        | 6.5 ms   | 10.0 ms  | 0.1       | 20 KB     | 641    |
|                           | one buffer per caller  | 2.3 ms   | 3.2 ms   | 0.3       | 20 KB     | 1,191  |

One buffer per caller is as fast as the ONNX Runtime output, keeps direct memory flat, and
allocates 3 to 5 times less heap per call. A pool smaller than the number of callers either
lets direct memory grow or makes callers queue.

### CPU and GPU, one caller

| texts/call | MiniLM, krick CPU | MiniLM, krick GPU | e5-large, krick CPU | e5-large, krick GPU | MiniLM, nano1 CPU | e5-large, nano1 CPU |
|-----------:|------------------:|------------------:|--------------------:|--------------------:|------------------:|--------------------:|
| 1          | 0.75 ms           | 0.5 ms            | 30 ms               | 3.7 ms              | 9.7 ms            | 207 ms              |
| 8          | 5.5 ms            | 3.6 ms            | 228 ms              | 25 ms               | 69 ms             | 1.56 s              |
| 32         | 32 ms             | 11.5 ms           | 837 ms              | 79 ms               | 300 ms            | not run             |
| 128        | 89 ms             | 24 ms             | 2.45 s              | 172 ms              | 1.0 s             | not run             |

`embedAll` here runs one inference per tokenized length; a GPU gains more once texts of mixed
lengths are padded into one inference.

