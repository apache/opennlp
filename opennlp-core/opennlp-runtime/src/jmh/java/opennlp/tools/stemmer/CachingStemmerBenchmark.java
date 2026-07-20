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

package opennlp.tools.stemmer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

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

import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmerFactory;

/**
 * JMH benchmark for {@link CachingStemmer} against an uncached shared {@link SnowballStemmer}.
 *
 * <p>Two workloads drive both strategies:</p>
 * <ul>
 *   <li>{@code zipf}: a 64k-token stream sampled with 1/rank weights from a 512-word
 *       vocabulary. This models real text, where a small vocabulary dominates; the default
 *       1024-entry cache holds the whole vocabulary.</li>
 *   <li>{@code diverse}: a 64k-token stream sampled uniformly from an 8192-word vocabulary,
 *       8x the cache capacity. This is the cache-hostile case: mostly misses plus constant
 *       eviction, so it bounds the overhead the cache can add.</li>
 * </ul>
 *
 * <p>One op stems 16 consecutive tokens from the stream; each benchmark thread walks the stream
 * from its own cursor.</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
public class CachingStemmerBenchmark {

  private static final int STREAM_LENGTH = 65536;
  private static final int WORDS_PER_OP = 16;

  private static final String[] ROOTS = {
      "run", "walk", "talk", "develop", "nation", "connect", "form", "create",
      "act", "direct", "govern", "manage", "operate", "organize", "present", "relate",
      "report", "state", "structure", "test", "train", "transform", "translate", "value",
      "view", "wonder", "yield", "zone", "note", "mark", "place", "point"
  };
  private static final String[] PREFIXES = {
      "", "re", "un", "over", "under", "out", "pre", "post",
      "non", "anti", "de", "dis", "mis", "sub", "super", "inter"
  };
  private static final String[] SUFFIXES = {
      "", "s", "ed", "ing", "er", "ers", "ation", "ations",
      "ly", "ness", "ment", "ments", "ize", "ized", "izing", "al"
  };

  @State(Scope.Benchmark)
  public static class WorkloadState {

    @Param({"zipf", "diverse"})
    String workload;

    String[] stream;

    @Setup(Level.Trial)
    public void build() {
      Random random = new Random(42);
      List<String> vocabulary = new ArrayList<>();
      if ("zipf".equals(workload)) {
        // 32 roots x 16 suffixes = 512 unique words, sampled with 1/rank weights.
        for (String root : ROOTS) {
          for (String suffix : SUFFIXES) {
            vocabulary.add(root + suffix);
          }
        }
        double[] cumulative = new double[vocabulary.size()];
        double sum = 0;
        for (int rank = 0; rank < vocabulary.size(); rank++) {
          sum += 1.0 / (rank + 1);
          cumulative[rank] = sum;
        }
        stream = new String[STREAM_LENGTH];
        for (int i = 0; i < STREAM_LENGTH; i++) {
          double r = random.nextDouble() * sum;
          int idx = 0;
          while (cumulative[idx] < r) {
            idx++;
          }
          stream[i] = vocabulary.get(idx);
        }
      } else {
        // 16 prefixes x 32 roots x 16 suffixes = 8192 unique words, sampled uniformly.
        for (String prefix : PREFIXES) {
          for (String root : ROOTS) {
            for (String suffix : SUFFIXES) {
              vocabulary.add(prefix + root + suffix);
            }
          }
        }
        stream = new String[STREAM_LENGTH];
        for (int i = 0; i < STREAM_LENGTH; i++) {
          stream[i] = vocabulary.get(random.nextInt(vocabulary.size()));
        }
      }
    }
  }

  @State(Scope.Benchmark)
  public static class UncachedState {
    Stemmer stemmer;

    @Setup(Level.Trial)
    public void create() {
      stemmer = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    }
  }

  @State(Scope.Benchmark)
  public static class CachedState {
    Stemmer stemmer;

    @Setup(Level.Trial)
    public void create() {
      stemmer = new CachingStemmer(
          new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH));
    }
  }

  @State(Scope.Thread)
  public static class Cursor {
    int position;

    @Setup(Level.Trial)
    public void randomize() {
      position = new Random().nextInt(STREAM_LENGTH);
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void uncachedShared(WorkloadState w, UncachedState st, Cursor cursor, Blackhole bh) {
    for (int i = 0; i < WORDS_PER_OP; i++) {
      bh.consume(st.stemmer.stem(w.stream[cursor.position++ & (STREAM_LENGTH - 1)]));
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void cachedShared(WorkloadState w, CachedState st, Cursor cursor, Blackhole bh) {
    for (int i = 0; i < WORDS_PER_OP; i++) {
      bh.consume(st.stemmer.stem(w.stream[cursor.position++ & (STREAM_LENGTH - 1)]));
    }
  }

  /**
   * Quick local iteration only: {@code forks(0)} disables JVM fork isolation
   * (unlike {@code mvn} with the {@code jmh} profile).
   * Use the Maven-invoked configuration for publishable numbers.
   */
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(CachingStemmerBenchmark.class.getSimpleName())
        .forks(0)
        .warmupIterations(3)
        .measurementIterations(5)
        .build();
    new Runner(opt).run();
  }
}
