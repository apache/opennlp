# Thread Safety Benchmarks

## JMH Benchmarks

JMH benchmarks compare three instance allocation strategies under
concurrent load, with proper fork isolation, warmup, and statistical
variance reporting.

### Benchmark classes

| Class | Component | Parameters |
|-------|-----------|------------|
| `TokenizerMEBenchmark` | TokenizerME | 3 approaches |
| `SentenceDetectorMEBenchmark` | SentenceDetectorME | 3 approaches |
| `POSTaggerMEBenchmark` | POSTaggerME | 3 approaches x 2 cache configs |
| `SnowballStemmerBenchmark` | SnowballStemmer | 3 approaches (incl. plain-field baseline) |
| `CachingStemmerBenchmark` | CachingStemmer | cached vs uncached x 2 workloads |

### Approaches measured

| Approach | Description |
|----------|-------------|
| `newInstancePerCall` | Fresh ME per operation (traditional pattern, backward-compat baseline) |
| `instancePerThread` | One ME per thread, reused across operations |
| `sharedInstance` | Single ME shared by all threads |

### Building and running

```bash
# Build with JMH profile
mvn test-compile -Pjmh \
    -pl opennlp-core/opennlp-runtime -am \
    -Dforbiddenapis.skip=true -Dcheckstyle.skip=true

# Materialize the test classpath once (JMH's forked JVMs inherit
# java.class.path, which mvn exec:java does not populate, running
# through exec:java fails with ClassNotFoundException: ForkedMain)
mvn dependency:build-classpath -pl opennlp-core/opennlp-runtime \
    -Pjmh -DincludeScope=test -Dmdep.outputFile=/tmp/cp.txt

CP="opennlp-core/opennlp-runtime/target/classes:opennlp-core/opennlp-runtime/target/test-classes:$(cat /tmp/cp.txt)"

# Run all ME benchmarks
java -cp "$CP" org.openjdk.jmh.Main 'opennlp.tools.*.ME*'

# Run POSTagger only (includes cacheSize param)
java -cp "$CP" org.openjdk.jmh.Main POSTaggerMEBenchmark
```

### Baseline comparison

Run the `newInstancePerCall` benchmark on both `main` and this
branch. The throughput numbers should be within JMH's error margin.

```bash
# On main:
# ... build and run as above, save output

# On this branch:
# ... build and run as above, compare
```

### SnowballStemmer results (Linux, JDK 25, 32 cores, 2 forks x 10 iterations)

`SnowballStemmerBenchmark` compares the thread-safe `SnowballStemmer`
(engine behind `OwnerOrPerThreadState`) against a baseline replica
that keeps the engine in a plain field (not shareable).
One op = stemming 16 English words.

| Strategy | 1 thread | 8 threads | 32 threads |
|----------|---------:|----------:|-----------:|
| `sharedInstance` (one shared stemmer) | 560k ± 3k ops/s | 1.55M ± 0.17M | 3.16M ± 0.34M |
| `instancePerThread` (stemmer per thread) | 509k ± 26k ops/s | 1.60M ± 0.17M | 2.94M ± 0.11M |
| `legacyInstancePerThread` (plain-field baseline, stemmer per thread) | 544k ± 19k ops/s | 1.46M ± 0.16M | 4.77M ± 0.39M |

At 1 and 8 threads the three strategies are within (or nearly within)
each other's error bars: the `OwnerOrPerThreadState` lookup is not
measurable against the cost of stemming itself. Only at full
saturation (32 threads, hyperthreaded) does the legacy plain-field
baseline pull ahead (~1.5x): with every hardware thread busy, the
per-call owner check plus `ThreadLocal` lookup is no longer hidden by
memory-level parallelism. Real pipelines stem as one stage among many,
so the saturated-microbenchmark gap is an upper bound, and the legacy
strategy was not shareable across threads in the first place.

### CachingStemmer results (same environment)

`CachingStemmerBenchmark` compares a `CachingStemmer` (per-thread LRU,
default 1024 entries, wrapping the English Snowball stemmer) against
the uncached shared stemmer. One op = 16 tokens from a 64k-token
stream. The `zipf` workload samples a 512-word vocabulary with 1/rank
weights (real-text repetition; the cache holds the whole vocabulary);
`diverse` samples an 8192-word vocabulary uniformly (8x cache
capacity: mostly misses plus constant eviction).

| Workload | Strategy | 8 threads | 32 threads |
|----------|----------|----------:|-----------:|
| `zipf` | `cachedShared` | 48.5M ± 0.7M ops/s | 95.4M ± 0.9M |
| `zipf` | `uncachedShared` | 1.43M ± 0.13M | 2.75M ± 0.34M |
| `diverse` | `cachedShared` | 1.81M ± 0.51M | 3.45M ± 0.18M |
| `diverse` | `uncachedShared` | 1.08M ± 0.09M | 3.13M ± 0.12M |

On the Zipf workload the cache is a ~34x throughput multiplier (raw
stemming becomes a hash lookup for the dominant vocabulary). On the
cache-hostile workload it still does not lose: the ~12% residual hit
rate pays for the eviction overhead. The cache more than recovers the
`OwnerOrPerThreadState` lookup cost observed in
`SnowballStemmerBenchmark` at full saturation.

The cache is keyed to the physical thread, and these runs use a fixed
platform-thread pool whose threads live for the whole measurement. On
a virtual-thread-per-task executor every task starts with an empty
cache, so the multiplier only applies to repeats within one task;
workloads that stem a handful of words per task should expect
uncached-level throughput there.

### POSTagger cache impact

The `POSTaggerMEBenchmark` uses `@Param({"0", "3"})` for cache
size, producing a matrix of 3 approaches x 2 cache configs = 6
benchmark runs. This quantifies whether the context generator
cache provides measurable benefit.

## JUnit Correctness Test

`ThreadSafetyBenchmarkIT` is a Failsafe integration test (`*IT.java`). It
verifies that a shared ME instance produces identical results to a
single-threaded baseline for all 7 ME classes under barrier-synchronized
concurrent access. Run it with `mvn verify` (not `mvn test`, which excludes
`*IT.java`).

```bash
mvn verify -pl opennlp-core/opennlp-runtime -am \
    -Dforbiddenapis.skip=true \
    -Dit.test=ThreadSafetyBenchmarkIT
```
