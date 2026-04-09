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

package opennlp.tools.sentdetect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
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

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * JMH benchmark for {@link SentenceDetectorME} thread-safety
 * and instance allocation strategies.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
public class SentenceDetectorMEBenchmark {

  private static final String INPUT =
      ("The driver got badly injured. "
          + "She told me he lived in Edinburgh. "
          + "I wrote him a letter right away. "
          + "The quick brown fox jumps over the dog. "
          + "OpenNLP provides tools for NLP. ")
          .repeat(5);

  @State(Scope.Benchmark)
  public static class ModelState {
    SentenceModel model;

    @Setup(Level.Trial)
    public void train() throws IOException {
      InputStreamFactory in = new ResourceAsStreamFactory(
          SentenceDetectorMEBenchmark.class,
          "/opennlp/tools/sentdetect/Sentences.txt");
      ObjectStream<SentenceSample> samples =
          new SentenceSampleStream(
              new PlainTextByLineStream(
                  in, StandardCharsets.UTF_8));
      model = SentenceDetectorME.train("eng", samples,
          new SentenceDetectorFactory(
              "eng", true, null, null),
          TrainingParameters.defaultParams());
    }
  }

  @State(Scope.Thread)
  public static class PerThreadState {
    SentenceDetectorME detector;

    @Setup(Level.Trial)
    public void create(ModelState ms) {
      detector = new SentenceDetectorME(ms.model);
    }
  }

  @State(Scope.Benchmark)
  public static class SharedState {
    SentenceDetectorME detector;

    @Setup(Level.Trial)
    public void create(ModelState ms) {
      detector = new SentenceDetectorME(ms.model);
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void newInstancePerCall(ModelState ms, Blackhole bh) {
    bh.consume(new SentenceDetectorME(ms.model).sentDetect(INPUT));
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void instancePerThread(PerThreadState pt, Blackhole bh) {
    bh.consume(pt.detector.sentDetect(INPUT));
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void sharedInstance(SharedState st, Blackhole bh) {
    bh.consume(st.detector.sentDetect(INPUT));
  }

  /**
   * Quick local iteration only: {@code forks(0)} disables JVM fork isolation
   * (unlike {@code mvn} with the {@code jmh} profile).
   * Use the Maven-invoked configuration for publishable numbers.
   */
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(SentenceDetectorMEBenchmark.class.getSimpleName())
        .forks(0)
        .warmupIterations(3)
        .measurementIterations(5)
        .build();
    new Runner(opt).run();
  }
}
