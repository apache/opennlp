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

# Run all ME benchmarks
mvn exec:java -pl opennlp-core/opennlp-runtime \
    -Pjmh -Dexec.classpathScope=test \
    -Dexec.mainClass=org.openjdk.jmh.Main \
    -Dexec.args="opennlp.tools.*.ME*"

# Run POSTagger only (includes cacheSize param)
mvn exec:java -pl opennlp-core/opennlp-runtime \
    -Pjmh -Dexec.classpathScope=test \
    -Dexec.mainClass=org.openjdk.jmh.Main \
    -Dexec.args="POSTaggerMEBenchmark"
```

### Regression testing (stock vs patched)

Run the `newInstancePerCall` benchmark on both stock and patched
builds. The throughput numbers should be within JMH's error margin.

```bash
# On stock (upstream/main):
# ... build and run as above, save output

# On patched (feature/thread-safe-me):
# ... build and run as above, compare
```

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
