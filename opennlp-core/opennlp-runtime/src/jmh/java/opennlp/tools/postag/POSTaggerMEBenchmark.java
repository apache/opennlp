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

package opennlp.tools.postag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.featuregen.CachedFeatureGenerator;
import opennlp.tools.util.model.ModelType;

/**
 * JMH benchmark comparing POS tagger instance allocation
 * strategies and cache configurations.
 * <p>
 * Measures 3 approaches x 2 cache configs = 6 combinations.
 * The {@code allCaches} parameter controls both the context
 * generator cache and the feature generator cache
 * simultaneously, isolating the total impact of caching.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
public class POSTaggerMEBenchmark {

  private static final String[][] SENTENCES = {
      {"The", "driver", "got", "badly", "injured",
       "by", "the", "accident", "."},
      {"I", "wrote", "him", "a", "letter",
       "right", "away", "."},
      {"She", "told", "me", "that", "he",
       "lived", "in", "Edinburgh", "."},
      {"The", "quick", "brown", "fox", "jumps",
       "over", "the", "lazy", "dog", "."},
      {"OpenNLP", "provides", "tools", "for",
       "natural", "language", "processing", "."}
  };

  @State(Scope.Benchmark)
  public static class ModelState {

    @Param({"true", "false"})
    boolean allCaches;

    POSModel posModel;

    @Setup(Level.Trial)
    public void train() throws IOException {
      // Toggle the CachedFeatureGenerator via
      // system property
      System.setProperty(
          CachedFeatureGenerator.DISABLE_CACHE_PROPERTY,
          String.valueOf(!allCaches));

      InputStreamFactory in = new ResourceAsStreamFactory(
          POSTaggerME.class,
          "/opennlp/tools/postag/"
              + "AnnotatedSentences.txt");
      ObjectStream<POSSample> samples =
          new WordTagSampleStream(
              new PlainTextByLineStream(
                  in, StandardCharsets.UTF_8));
      TrainingParameters p = new TrainingParameters();
      p.put(Parameters.ALGORITHM_PARAM,
          ModelType.MAXENT.toString());
      p.put(Parameters.ITERATIONS_PARAM, 100);
      p.put(Parameters.CUTOFF_PARAM, 5);
      posModel = POSTaggerME.train("eng", samples, p,
          new POSTaggerFactory());
    }

    int contextCacheSize() {
      return allCaches ? 3 : 0;
    }
  }

  @State(Scope.Thread)
  public static class PerThreadTagger {
    POSTaggerME tagger;

    @Setup(Level.Trial)
    public void create(ModelState ms) {
      tagger = new POSTaggerME(ms.posModel,
          POSTagFormat.UD, ms.contextCacheSize());
    }
  }

  @State(Scope.Benchmark)
  public static class SharedTagger {
    POSTaggerME tagger;

    @Setup(Level.Trial)
    public void create(ModelState ms) {
      tagger = new POSTaggerME(ms.posModel,
          POSTagFormat.UD, ms.contextCacheSize());
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void newInstancePerCall(ModelState ms, Blackhole bh) {
    for (String[] tokens : SENTENCES) {
      bh.consume(new POSTaggerME(ms.posModel, POSTagFormat.UD, ms.contextCacheSize()).tag(tokens));
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void instancePerThread(PerThreadTagger pt, Blackhole bh) {
    for (String[] tokens : SENTENCES) {
      bh.consume(pt.tagger.tag(tokens));
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void sharedInstance(SharedTagger st, Blackhole bh) {
    for (String[] tokens : SENTENCES) {
      bh.consume(st.tagger.tag(tokens));
    }
  }

  /**
   * Quick local iteration only: {@code forks(0)} disables JVM fork isolation
   * (unlike {@code mvn} with the {@code jmh} profile).
   * Use the Maven-invoked configuration for publishable numbers.
   */
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(POSTaggerMEBenchmark.class.getSimpleName())
        .forks(0)
        .warmupIterations(3)
        .measurementIterations(5)
        .build();
    new Runner(opt).run();
  }
}
