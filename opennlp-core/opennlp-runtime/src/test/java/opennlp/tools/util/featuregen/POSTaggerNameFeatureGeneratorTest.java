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

package opennlp.tools.util.featuregen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

public class POSTaggerNameFeatureGeneratorTest {

  private static ObjectStream<POSSample> createSampleStream() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(POSTaggerNameFeatureGeneratorTest.class,
            "/opennlp/tools/postag/AnnotatedSentences.txt"); //PENN FORMAT

    return new WordTagSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  /**
   * Trains a POSModel from the annotated test data.
   *
   * @return {@link POSModel}
   */
  static POSModel trainPennFormatPOSModel(ModelType type) throws IOException {
    TrainingParameters params = new TrainingParameters();
    params.put(Parameters.ALGORITHM_PARAM, type.toString());
    params.put(Parameters.ITERATIONS_PARAM, 100);
    params.put(Parameters.CUTOFF_PARAM, 5);

    return POSTaggerME.train("eng", createSampleStream(), params,
            new POSTaggerFactory());
  }

  @Test
  void testFeatureGeneration() throws IOException {
    POSTaggerNameFeatureGenerator fg = new POSTaggerNameFeatureGenerator(
            trainPennFormatPOSModel(ModelType.MAXENT));

    String[] tokens = {"Hi", "Mike", ",", "it", "'s", "Stefanie", "Schmidt", "."};
    for (int i = 0; i < tokens.length; i++) {
      List<String> feats = new ArrayList<>();
      fg.createFeatures(feats, tokens, i, null);
      Assertions.assertTrue(feats.get(0).startsWith("pos="));
    }
  }

  /**
   * Stress-tests concurrent {@link POSTaggerNameFeatureGenerator#createFeatures} calls on a single
   * shared instance with sentences of differing lengths. Before {@code OPENNLP-1816} the
   * {@code cachedTokens} / {@code cachedTags} fields were plain instance fields, which raced under
   * concurrent {@code NameFinderME.find()} calls — a thread that tagged a longer sentence could
   * stash a long {@code cachedTags} and another thread tagging a shorter sentence would either read
   * a stale tag or, with the right interleaving, throw {@link ArrayIndexOutOfBoundsException} when
   * indexing past its own sentence's bounds.
   *
   * <p>This test exercises that exact pattern: many threads, sentences of differing lengths,
   * many iterations. Any {@code AIOOBE} or wrong-length cache hit fails the test.
   */
  @Test
  void testConcurrentCreateFeaturesIsThreadSafe() throws Exception {
    final POSTaggerNameFeatureGenerator fg = new POSTaggerNameFeatureGenerator(
            trainPennFormatPOSModel(ModelType.MAXENT));

    final String[][] sentences = {
        {"Hi", "Mike", "."},
        {"Hello", "Stefanie", "Schmidt", ",", "how", "are", "you", "today", "?"},
        {"OpenNLP", "is", "thread-safe", "."},
        {"This", "is", "a", "longer", "sentence", "with", "more", "tokens", "for", "the", "tagger", "."},
        {"Short", "."}
    };

    final int threadCount = 16;
    final int iterationsPerThread = 200;

    final ExecutorService exec = Executors.newFixedThreadPool(threadCount);
    try {
      final CyclicBarrier startGate = new CyclicBarrier(threadCount);
      final AtomicInteger failures = new AtomicInteger();
      final List<Future<?>> futures = new ArrayList<>(threadCount);

      for (int t = 0; t < threadCount; t++) {
        final int seed = t;
        futures.add(exec.submit(() -> {
          try {
            startGate.await();
            for (int it = 0; it < iterationsPerThread; it++) {
              String[] toks = sentences[(seed + it) % sentences.length];
              for (int i = 0; i < toks.length; i++) {
                List<String> feats = new ArrayList<>();
                fg.createFeatures(feats, toks, i, null);
                if (feats.isEmpty() || !feats.get(0).startsWith("pos=")) {
                  failures.incrementAndGet();
                }
              }
            }
          } catch (Throwable th) {
            failures.incrementAndGet();
            throw new RuntimeException(th);
          }
        }));
      }

      for (Future<?> f : futures) {
        f.get(60, TimeUnit.SECONDS);
      }
      Assertions.assertEquals(0, failures.get(),
          "concurrent createFeatures must not race or throw");
    } finally {
      exec.shutdownNow();
    }
  }
}
