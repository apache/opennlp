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

package opennlp.tools.stemmer.snowball;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import opennlp.tools.stemmer.Stemmer;

/**
 * JMH benchmark for the thread-safe {@link SnowballStemmer} versus the pre-patch implementation
 * that held its generated engine in a plain field.
 *
 * <p>Three strategies are measured, all at {@link Threads#MAX}:</p>
 * <ul>
 *   <li>{@code sharedInstance} — one thread-safe {@link SnowballStemmer} shared by every thread
 *       (one thread runs on the owner fast path, the rest on {@link ThreadLocal} state)</li>
 *   <li>{@code instancePerThread} — one thread-safe {@link SnowballStemmer} per thread (every
 *       thread is the owner of its own instance, so this isolates the owner-fast-path cost)</li>
 *   <li>{@code legacyInstancePerThread} — the old non-thread-safe implementation, one per thread
 *       (the pre-patch baseline; sharing it across threads would be a correctness bug)</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
public class SnowballStemmerBenchmark {

  private static final String[] INPUT = {
      "running", "accompanying", "malediction", "softeners", "declining",
      "conspiracies", "monotonically", "annotations", "internationalization",
      "denormalization", "photographers", "responsibilities", "acknowledgement",
      "this", "cat", "querying"
  };

  /**
   * Replica of the pre-patch {@code SnowballStemmer}: the generated engine lives in a plain
   * field, so an instance must not be shared across threads.
   */
  static final class LegacySnowballStemmer implements Stemmer {

    private final AbstractSnowballStemmer stemmer = new englishStemmer();

    @Override
    public CharSequence stem(CharSequence word) {
      stemmer.setCurrent(word.toString());
      stemmer.stem();
      return stemmer.getCurrent();
    }
  }

  @State(Scope.Benchmark)
  public static class SharedState {
    Stemmer stemmer;

    @Setup(Level.Trial)
    public void create() {
      stemmer = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    }
  }

  @State(Scope.Thread)
  public static class PerThreadState {
    Stemmer stemmer;

    @Setup(Level.Trial)
    public void create() {
      stemmer = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    }
  }

  @State(Scope.Thread)
  public static class LegacyPerThreadState {
    Stemmer stemmer;

    @Setup(Level.Trial)
    public void create() {
      stemmer = new LegacySnowballStemmer();
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void sharedInstance(SharedState st, Blackhole bh) {
    for (String s : INPUT) {
      bh.consume(st.stemmer.stem(s));
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void instancePerThread(PerThreadState pt, Blackhole bh) {
    for (String s : INPUT) {
      bh.consume(pt.stemmer.stem(s));
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void legacyInstancePerThread(LegacyPerThreadState pt, Blackhole bh) {
    for (String s : INPUT) {
      bh.consume(pt.stemmer.stem(s));
    }
  }

  /**
   * Quick local iteration only: {@code forks(0)} disables JVM fork isolation
   * (unlike {@code mvn} with the {@code jmh} profile).
   * Use the Maven-invoked configuration for publishable numbers.
   */
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(SnowballStemmerBenchmark.class.getSimpleName())
        .forks(0)
        .warmupIterations(3)
        .measurementIterations(5)
        .build();
    new Runner(opt).run();
  }
}
