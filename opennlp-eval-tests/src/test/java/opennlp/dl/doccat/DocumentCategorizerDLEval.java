/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl.doccat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.dl.InferenceOptions;
import opennlp.dl.doccat.scoring.AverageClassificationScoringStrategy;
import opennlp.tools.eval.AbstractEvalTest;
import opennlp.tools.tokenize.WordpieceTokenizer;

public class DocumentCategorizerDLEval extends AbstractEvalTest {

  private static final Logger logger = LoggerFactory.getLogger(DocumentCategorizerDLEval.class);

  final String text = "We try hard to identify the sources and licenses of all media such as text," +
      " images or sounds used in our encyclopedia articles. Still, we cannot guarantee that all " +
      "media are used or marked correctly: for example, if an image description page states " +
      "that an image was in the public domain, you should still check yourself whether that claim " +
      "appears correct and decide for yourself whether your use of the image would be fine under " +
      "the laws applicable to you. Wikipedia is primarily subject to U.S. law; re-users outside " +
      "the U.S. should be aware that they are subject to the laws of their country, which almost " +
      "certainly are different. Images published under the GFDL or one of the Creative Commons " +
      "Licenses are unlikely to pose problems, as these are specific licenses with precise terms " +
      "worldwide. Public domain images may need to be re-evaluated by a re-user because it depends " +
      "on each country's copyright laws what is in the public domain there. There is no guarantee " +
      "that something in the public domain in the U.S. was also in the public domain in your country.";

  @Test
  public void categorize() throws Exception {

    final File model = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.onnx");
    final File vocab = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.vocab");

    try (final DocumentCategorizerDL documentCategorizerDL =
             new DocumentCategorizerDL(model, vocab, getCategories(),
                 new AverageClassificationScoringStrategy(),
                 new InferenceOptions())) {

      final double[] result = documentCategorizerDL.categorize(new String[] {text});

      // Sort the result for easier comparison.
      final double[] sortedResult = Arrays.stream(result)
          .boxed()
          .sorted(Collections.reverseOrder()).mapToDouble(Double::doubleValue).toArray();

      final double[] expected = new double[]
          {0.407059907913208,
              0.3602477014064789,
              0.14488528668880463,
              0.07669895142316818,
              0.011108151637017727};

      logger.debug("Actual: {}", Arrays.toString(sortedResult));
      logger.debug("Expected: {}", Arrays.toString(expected));

      Assertions.assertArrayEquals(expected, sortedResult, 0.000001);
      Assertions.assertEquals(5, result.length);

      final String category = documentCategorizerDL.getBestCategory(result);
      Assertions.assertEquals("bad", category);
    }

  }

  /**
   * Verifies that a single {@link DocumentCategorizerDL} instance is safe to share across
   * threads: concurrent {@link DocumentCategorizerDL#categorize(String[])} calls on one
   * instance must all return the same scores as the single-threaded baseline.
   */
  @Test
  public void categorizeConcurrentTest() throws Exception {

    final File model = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.onnx");
    final File vocab = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.vocab");

    final int threads = 8;
    final int iterationsPerThread = 10;

    try (final DocumentCategorizerDL documentCategorizerDL =
             new DocumentCategorizerDL(model, vocab, getCategories(),
                 new AverageClassificationScoringStrategy(), new InferenceOptions())) {

      final double[] baseline = documentCategorizerDL.categorize(new String[] {text});

      final ExecutorService executor = Executors.newFixedThreadPool(threads);
      try {
        final CountDownLatch startGate = new CountDownLatch(1);
        final List<Future<Boolean>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
          futures.add(executor.submit(() -> {
            startGate.await();
            for (int i = 0; i < iterationsPerThread; i++) {
              final double[] result = documentCategorizerDL.categorize(new String[] {text});
              if (result.length != baseline.length) {
                return false;
              }
              for (int c = 0; c < baseline.length; c++) {
                if (Math.abs(result[c] - baseline[c]) > 0.000001) {
                  return false;
                }
              }
            }
            return true;
          }));
        }

        startGate.countDown();
        for (Future<Boolean> future : futures) {
          Assertions.assertTrue(future.get(),
              "a concurrent categorize() returned scores inconsistent with the single-threaded case");
        }
      } finally {
        executor.shutdownNow();
      }
    }

  }

  @Test
  public void categorizeFailsLoudlyOnFailure() throws Exception {

    try (final DocumentCategorizerDL documentCategorizerDL =
             categorizerWithoutSession()) {

      // Malformed input is rejected up front with IllegalArgumentException.
      Assertions.assertThrows(IllegalArgumentException.class, () ->
          documentCategorizerDL.categorize(null));
      Assertions.assertThrows(IllegalArgumentException.class, () ->
          documentCategorizerDL.categorize(new String[0]));

      // Valid input with no session must fail loudly rather than return an invalid all-zero
      // distribution.
      final IllegalStateException e = Assertions.assertThrows(IllegalStateException.class, () ->
          documentCategorizerDL.categorize(new String[] {"hello world"}));
      Assertions.assertTrue(e.getMessage().contains("document classification inference"));

      // The dependent API must not mask that inference failure with all-zero scores.
      Assertions.assertThrows(IllegalStateException.class, () ->
          documentCategorizerDL.scoreMap(new String[] {"hello world"}));
      Assertions.assertThrows(IllegalStateException.class, () ->
          documentCategorizerDL.sortedScoreMap(new String[] {"hello world"}));
    }

  }

  @Test
  public void categorizeWithAutomaticLabels() throws Exception {

    final File model = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.onnx");
    final File vocab = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.vocab");
    final File config = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.json");

    try (final DocumentCategorizerDL documentCategorizerDL =
             new DocumentCategorizerDL(model, vocab, config,
                 new AverageClassificationScoringStrategy(),
                 new InferenceOptions())) {

      final double[] result = documentCategorizerDL.categorize(new String[] {text});

      // Sort the result for easier comparison.
      final double[] sortedResult = Arrays.stream(result)
          .boxed()
          .sorted(Collections.reverseOrder()).mapToDouble(Double::doubleValue).toArray();

      final double[] expected = new double[]
          {0.407059907913208,
              0.3602477014064789,
              0.14488528668880463,
              0.07669895142316818,
              0.011108151637017727};

      logger.debug("Actual: {}", Arrays.toString(sortedResult));
      logger.debug("Expected: {}", Arrays.toString(expected));

      Assertions.assertArrayEquals(expected, sortedResult, 0.000001);
      Assertions.assertEquals(5, result.length);

      final String category = documentCategorizerDL.getBestCategory(result);
      Assertions.assertEquals("2 stars", category);
    }

  }

  @Disabled("This test should only be run if a GPU device is present.")
  @Test
  public void categorizeWithGpu() throws Exception {

    final File model = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.onnx");
    final File vocab = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.vocab");

    final InferenceOptions inferenceOptions = new InferenceOptions();
    inferenceOptions.setGpu(true);
    inferenceOptions.setGpuDeviceId(0);

    try (final DocumentCategorizerDL documentCategorizerDL =
             new DocumentCategorizerDL(model, vocab, getCategories(),
                 new AverageClassificationScoringStrategy(),
                 inferenceOptions)) {

      final double[] result = documentCategorizerDL.categorize(new String[] {"I am happy"});
      logger.debug(Arrays.toString(result));

      final double[] expected = new double[]
          {0.00752239441499114,
              0.0074586994014680386,
              0.05470007658004761,
              0.3344593346118927,
              0.5958595275878906};

      Assertions.assertArrayEquals(expected, result, 0.000001);
      Assertions.assertEquals(5, result.length);

      final String category = documentCategorizerDL.getBestCategory(result);
      Assertions.assertEquals("very good", category);
    }

  }

  @Test
  public void categorizeWithInferenceOptions() throws Exception {

    final File model = new File(getOpennlpDataDir(),
        "onnx/doccat/lvwerra_distilbert-imdb.onnx");
    final File vocab = new File(getOpennlpDataDir(),
        "onnx/doccat/lvwerra_distilbert-imdb.vocab");

    final InferenceOptions inferenceOptions = new InferenceOptions();
    inferenceOptions.setIncludeTokenTypeIds(false);

    final Map<Integer, String> categories = new HashMap<>();
    categories.put(0, "negative");
    categories.put(1, "positive");

    try (final DocumentCategorizerDL documentCategorizerDL =
             new DocumentCategorizerDL(model, vocab, categories,
                 new AverageClassificationScoringStrategy(),
                 inferenceOptions)) {

      final double[] result = documentCategorizerDL.categorize(new String[] {"I am angry"});

      final double[] expected = new double[] {0.9072678089141846, 0.09273219853639603};

      Assertions.assertArrayEquals(expected, result, 0.000001);
      Assertions.assertEquals(2, result.length);

      final String category = documentCategorizerDL.getBestCategory(result);
      Assertions.assertEquals("negative", category);
    }
  }

  @Test
  public void scoreMap() throws Exception {

    final File model = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.onnx");
    final File vocab = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.vocab");

    try (final DocumentCategorizerDL documentCategorizerDL =
             new DocumentCategorizerDL(model, vocab, getCategories(),
                 new AverageClassificationScoringStrategy(),
                 new InferenceOptions())) {

      final Map<String, Double> result = documentCategorizerDL.scoreMap(new String[] {"I am happy"});

      Assertions.assertEquals(0.5958595275878906, result.get("very good"), 0.000001);
      Assertions.assertEquals(0.3344593346118927, result.get("good"), 0.000001);
      Assertions.assertEquals(0.05470007658004761, result.get("neutral"), 0.000001);
      Assertions.assertEquals(0.0074586994014680386, result.get("bad"), 0.000001);
      Assertions.assertEquals(0.00752239441499114, result.get("very bad"), 0.000001);
    }

  }

  @Test
  public void sortedScoreMap() throws Exception {

    final File model = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.onnx");
    final File vocab = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.vocab");

    try (final DocumentCategorizerDL documentCategorizerDL =
             new DocumentCategorizerDL(model, vocab, getCategories(),
                 new AverageClassificationScoringStrategy(),
                 new InferenceOptions())) {

      final Map<Double, Set<String>> result =
          documentCategorizerDL.sortedScoreMap(new String[] {"I am happy"});

      Assertions.assertNotNull(result, "Result must not be NULL.");
      Assertions.assertEquals(5, result.size());

      final Iterator<Map.Entry<Double, Set<String>>> it = result.entrySet().iterator();

      // we assume a sorted map here, so lets check in sorted order (lower values first).
      Map.Entry<Double, Set<String>> e = it.next();
      Assertions.assertEquals(0.0074586994014680386, e.getKey(), 0.000001);
      Assertions.assertEquals(e.getValue().size(), 1);

      e = it.next();
      Assertions.assertEquals(0.00752239441499114, e.getKey(), 0.000001);
      Assertions.assertEquals(e.getValue().size(), 1);

      e = it.next();
      Assertions.assertEquals(0.05470007658004761, e.getKey(), 0.000001);
      Assertions.assertEquals(e.getValue().size(), 1);

      e = it.next();
      Assertions.assertEquals(0.3344593346118927, e.getKey(), 0.000001);
      Assertions.assertEquals(e.getValue().size(), 1);

      e = it.next();
      Assertions.assertEquals(0.5958595275878906, e.getKey(), 0.000001);
      Assertions.assertEquals(e.getValue().size(), 1);
    }

  }

  @Test
  public void doccat() throws Exception {

    final File model = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.onnx");
    final File vocab = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.vocab");

    try (final DocumentCategorizerDL documentCategorizerDL =
             new DocumentCategorizerDL(model, vocab, getCategories(),
                 new AverageClassificationScoringStrategy(),
                 new InferenceOptions())) {

      final int index = documentCategorizerDL.getIndex("bad");
      Assertions.assertEquals(1, index);

      final String category = documentCategorizerDL.getCategory(3);
      Assertions.assertEquals("good", category);

      final int number = documentCategorizerDL.getNumberOfCategories();
      Assertions.assertEquals(5, number);
    }

  }

  private Map<Integer, String> getCategories() {

    final Map<Integer, String> categories = new HashMap<>();

    categories.put(0, "very bad");
    categories.put(1, "bad");
    categories.put(2, "neutral");
    categories.put(3, "good");
    categories.put(4, "very good");

    return categories;

  }

  private DocumentCategorizerDL categorizerWithoutSession() {
    return new DocumentCategorizerDL(null, null, vocab(), getCategories(),
        new AverageClassificationScoringStrategy(), new InferenceOptions());
  }

  private Map<String, Integer> vocab() {
    final Map<String, Integer> vocab = new HashMap<>();
    vocab.put(WordpieceTokenizer.BERT_CLS_TOKEN, 0);
    vocab.put(WordpieceTokenizer.BERT_SEP_TOKEN, 1);
    vocab.put(WordpieceTokenizer.BERT_UNK_TOKEN, 2);
    vocab.put("hello", 3);
    vocab.put("world", 4);
    return vocab;
  }

}
