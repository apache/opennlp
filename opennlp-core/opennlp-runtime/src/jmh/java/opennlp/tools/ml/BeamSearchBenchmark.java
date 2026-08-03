/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.ml;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import opennlp.tools.util.BeamSearchContextGenerator;
import opennlp.tools.util.SequenceValidator;

/**
 * JMH benchmark for {@link BeamSearch} on long input sequences.
 * <p>
 * One op = one {@code bestSequence} call on a synthetic token sequence of
 * {@code sequenceLength} tokens. Long sequences are what expose the quadratic
 * per-candidate outcome-list copying removed with OPENNLP-1903; sentences of
 * 5-10 tokens, as used by the ME benchmarks, show no measurable difference.
 * Only public API that predates OPENNLP-1903 is used
 * ({@code BeamSearch(int, MaxentModel, int)} and
 * {@code bestSequence(T[], Object[], BeamSearchContextGenerator, SequenceValidator)}),
 * so the same compiled class exercises both implementations: to produce a
 * baseline, place an {@code opennlp-ml-commons} jar built before OPENNLP-1903
 * on the classpath ahead of the freshly built classes and rerun.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
public class BeamSearchBenchmark {

  private static final int BEAM_SIZE = 3;
  private static final int NUM_INPUTS = 64;
  private static final int VOCAB_SIZE = 17;
  /** Seed for both the input generator and the {@link SeededMaxentModel}. */
  private static final long SEED = 0x5eedL;
  private static final String[] MODEL_OUTCOMES = {"start", "cont", "other"};

  private static final SequenceValidator<String> ACCEPT_ALL =
      (i, input, outcomes, outcome) -> true;

  @State(Scope.Benchmark)
  public static class SearchState {

    @Param({"8", "64", "256"})
    int sequenceLength;

    @Param({"0", "64"})
    int cacheSize;

    BeamSearch beamSearch;
    TokenContextGenerator contextGenerator;
    String[][] inputs;
    final AtomicInteger cursor = new AtomicInteger();

    @Setup(Level.Trial)
    public void create() {
      beamSearch = new BeamSearch(BEAM_SIZE,
          new SeededMaxentModel(MODEL_OUTCOMES, SEED), cacheSize);
      contextGenerator = new TokenContextGenerator();
      inputs = new String[NUM_INPUTS][];
      Random rnd = new Random(SEED);
      for (int n = 0; n < NUM_INPUTS; n++) {
        String[] input = new String[sequenceLength];
        for (int i = 0; i < sequenceLength; i++) {
          input[i] = "t" + rnd.nextInt(VOCAB_SIZE);
        }
        inputs[n] = input;
      }
    }

    String[] nextInput() {
      // Rotate through the input pool so repeated invocations decode different
      // sequences instead of hammering one fully cache-warm input.
      return inputs[Math.floorMod(cursor.getAndIncrement(), NUM_INPUTS)];
    }
  }

  /**
   * Derives contexts from the current token and the previous outcome, interned
   * so identical context content maps to the same {@code String[]} instance
   * and the identity-keyed contexts cache in {@link BeamSearch} produces hits.
   * Thread-safe.
   */
  static final class TokenContextGenerator implements BeamSearchContextGenerator<String> {

    private final ConcurrentHashMap<String, String[]> intern = new ConcurrentHashMap<>();

    @Override
    public String[] getContext(int index, String[] sequence,
                               String[] priorDecisions, Object[] additionalContext) {
      String prev = index > 0 ? priorDecisions[index - 1] : "<s>";
      String[] ctx = {"tok=" + sequence[index], "prev=" + prev};
      String key = ctx[0] + '|' + ctx[1];
      String[] existing = intern.putIfAbsent(key, ctx);
      return existing != null ? existing : ctx;
    }
  }

  @Benchmark
  @Threads(1)
  public void bestSequenceSingle(SearchState state, Blackhole bh) {
    bh.consume(state.beamSearch.bestSequence(state.nextInput(), null,
        state.contextGenerator, ACCEPT_ALL));
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void bestSequenceConcurrent(SearchState state, Blackhole bh) {
    bh.consume(state.beamSearch.bestSequence(state.nextInput(), null,
        state.contextGenerator, ACCEPT_ALL));
  }

  /**
   * Quick local iteration only: {@code forks(0)} disables JVM fork isolation
   * (unlike {@code mvn} with the {@code jmh} profile).
   * Use the Maven-invoked configuration for publishable numbers.
   */
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(BeamSearchBenchmark.class.getSimpleName())
        .forks(0)
        .warmupIterations(1)
        .warmupTime(TimeValue.seconds(1))
        .measurementIterations(1)
        .measurementTime(TimeValue.seconds(1))
        .build();
    new Runner(opt).run();
  }
}
