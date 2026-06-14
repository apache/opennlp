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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.dl.InferenceOptions;
import opennlp.dl.doccat.scoring.AverageClassificationScoringStrategy;
import opennlp.tools.eval.AbstractEvalTest;

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

  @Test
  public void categorizeReturnsSizedArrayOnFailure() throws Exception {

    final File model = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.onnx");
    final File vocab = new File(getOpennlpDataDir(),
        "onnx/doccat/nlptown_bert-base-multilingual-uncased-sentiment.vocab");

    try (final DocumentCategorizerDL documentCategorizerDL =
             new DocumentCategorizerDL(model, vocab, getCategories(),
                 new AverageClassificationScoringStrategy(), new InferenceOptions())) {

      // Empty input drives categorize() down its failure path (strings[0] throws) before any
      // inference; it must return zeros sized to the category count, not an empty array.
      final double[] scores = documentCategorizerDL.categorize(new String[0]);
      Assertions.assertEquals(getCategories().size(), scores.length);
      for (final double score : scores) {
        Assertions.assertEquals(0.0, score);
      }

      // The dependent API must stay safe to call on that result rather than indexing past an
      // empty array.
      Assertions.assertEquals(getCategories().size(),
          documentCategorizerDL.scoreMap(new String[0]).size());
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
                 new InferenceOptions())) {

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

}
