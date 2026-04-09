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

package opennlp.tools.tokenize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
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

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * JMH benchmark for {@link TokenizerME} thread-safety and
 * instance allocation strategies.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
public class TokenizerMEBenchmark {

  private static final String[] INPUT = {
      "The driver got badly injured by the accident.",
      "She told me he lived in Edinburgh.",
      "I wrote him a letter right away.",
      "The quick brown fox jumps over the lazy dog.",
      "OpenNLP provides tools for NLP."
  };

  @State(Scope.Benchmark)
  public static class ModelState {
    TokenizerModel model;

    @Setup(Level.Trial)
    public void train() throws IOException {
      InputStreamFactory in = new ResourceAsStreamFactory(
          TokenizerModel.class,
          "/opennlp/tools/tokenize/token.train");
      ObjectStream<TokenSample> samples =
          new TokenSampleStream(
              new PlainTextByLineStream(
                  in, StandardCharsets.UTF_8));
      TrainingParameters p = new TrainingParameters();
      p.put(Parameters.ITERATIONS_PARAM, 100);
      p.put(Parameters.CUTOFF_PARAM, 0);
      model = TokenizerME.train(samples,
          TokenizerFactory.create(
              null, "eng", null, true, null), p);
    }
  }

  @State(Scope.Thread)
  public static class PerThreadState {
    TokenizerME tokenizer;

    @Setup(Level.Trial)
    public void create(ModelState ms) {
      tokenizer = new TokenizerME(ms.model);
    }
  }

  @State(Scope.Benchmark)
  public static class SharedState {
    TokenizerME tokenizer;

    @Setup(Level.Trial)
    public void create(ModelState ms) {
      tokenizer = new TokenizerME(ms.model);
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void newInstancePerCall(ModelState ms, Blackhole bh) {
    for (String s : INPUT) {
      bh.consume(new TokenizerME(ms.model).tokenize(s));
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void instancePerThread(PerThreadState pt, Blackhole bh) {
    for (String s : INPUT) {
      bh.consume(pt.tokenizer.tokenize(s));
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void sharedInstance(SharedState st, Blackhole bh) {
    for (String s : INPUT) {
      bh.consume(st.tokenizer.tokenize(s));
    }
  }

  /**
   * Quick local iteration only: {@code forks(0)} disables JVM fork isolation
   * (unlike {@code mvn} with the {@code jmh} profile).
   * Use the Maven-invoked configuration for publishable numbers.
   */
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(TokenizerMEBenchmark.class.getSimpleName())
        .forks(0)
        .warmupIterations(3)
        .measurementIterations(5)
        .build();
    new Runner(opt).run();
  }
}
