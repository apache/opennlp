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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.dl.InferenceOptions;
import opennlp.dl.doccat.scoring.AverageClassificationScoringStrategy;
import opennlp.tools.tokenize.WordpieceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentCategorizerDLTest {

  private static Map<String, Integer> vocab() {
    final Map<String, Integer> vocab = new HashMap<>();
    vocab.put(WordpieceTokenizer.BERT_CLS_TOKEN, 0);
    vocab.put(WordpieceTokenizer.BERT_SEP_TOKEN, 1);
    vocab.put(WordpieceTokenizer.BERT_UNK_TOKEN, 2);
    vocab.put("hello", 3);
    vocab.put("world", 4);
    return vocab;
  }

  private static Map<Integer, String> categories() {
    final Map<Integer, String> categories = new HashMap<>();
    categories.put(0, "negative");
    categories.put(1, "positive");
    return categories;
  }

  private static DocumentCategorizerDL categorizerWithoutSession() {
    return new DocumentCategorizerDL(null, null, vocab(), categories(),
        new AverageClassificationScoringStrategy(), new InferenceOptions());
  }

  @Test
  void testCategorizeFailsLoudlyWhenInferenceFails() {
    final IllegalStateException e = assertThrows(IllegalStateException.class, () ->
        categorizerWithoutSession().categorize(new String[] {"hello world"}));

    assertTrue(e.getMessage().contains("document classification inference"));
    assertTrue(e.getCause() instanceof RuntimeException);
  }

  @Test
  void testScoreMapsFailLoudlyWhenInferenceFails() {
    final DocumentCategorizerDL categorizer = categorizerWithoutSession();

    assertThrows(IllegalStateException.class, () ->
        categorizer.scoreMap(new String[] {"hello world"}));
    assertThrows(IllegalStateException.class, () ->
        categorizer.sortedScoreMap(new String[] {"hello world"}));
  }

  @Test
  void testCategorizeRejectsMalformedInput() {
    // A caller-side input bug is distinguished from an inference failure: it is rejected up front
    // with IllegalArgumentException, not wrapped as "document classification inference" failure.
    final DocumentCategorizerDL categorizer = categorizerWithoutSession();

    assertThrows(IllegalArgumentException.class, () -> categorizer.categorize(null));
    assertThrows(IllegalArgumentException.class, () -> categorizer.categorize(new String[0]));
  }

  @Test
  void testCategorizeRejectsTokenlessContent() {
    // A non-empty array whose document has no tokens (empty or only whitespace, including Unicode
    // whitespace) is rejected up front rather than crashing downstream on an empty score list.
    final DocumentCategorizerDL categorizer = categorizerWithoutSession();
    final String nbsp = new String(Character.toChars(0x00A0));

    assertThrows(IllegalArgumentException.class, () -> categorizer.categorize(new String[] {""}));
    assertThrows(IllegalArgumentException.class, () -> categorizer.categorize(new String[] {"   "}));
    assertThrows(IllegalArgumentException.class, () -> categorizer.categorize(new String[] {nbsp}));
    assertThrows(IllegalArgumentException.class,
        () -> categorizer.categorize(new String[] {null}));
  }

  @Test
  void testConstructorRejectsNullInferenceOptions() {
    assertThrows(IllegalArgumentException.class, () ->
        new DocumentCategorizerDL(null, null, vocab(), categories(),
            new AverageClassificationScoringStrategy(), null));
  }

  @Test
  void testTokenIdsMapsTokensToVocabularyIds() {
    final long[] ids = DocumentCategorizerDL.tokenIds(
        new String[] {WordpieceTokenizer.BERT_CLS_TOKEN, "hello", "world",
            WordpieceTokenizer.BERT_SEP_TOKEN}, vocab());

    assertArrayEquals(new long[] {0, 3, 4, 1}, ids);
  }

  @Test
  void testTokenIdsRejectsTokensMissingFromVocabulary() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        DocumentCategorizerDL.tokenIds(new String[] {"hello", "missing"}, vocab()));

    assertTrue(e.getMessage().contains("missing"),
        "the error message should name the missing token: " + e.getMessage());
  }

  @Test
  void testSoftmaxRejectsNaNLogit() {
    // A NaN logit would otherwise poison the whole distribution into NaN scores; fail loudly instead.
    final IllegalStateException e = assertThrows(IllegalStateException.class, () ->
        DocumentCategorizerDL.softmax(new float[] {0f, Float.NaN, 0f}));
    assertTrue(e.getMessage().contains("NaN"), e.getMessage());
  }

  @Test
  void testSoftmaxRejectsInfiniteLogit() {
    // A +Infinity logit (not NaN, so it slips past an isNaN-only guard) poisons the distribution too:
    // max becomes +Inf, so value - max is Inf - Inf == NaN, every exp() is NaN, and categorize() would
    // silently return all-NaN scores. It must fail loud like the NaN case. -Infinity is non-finite too.
    final IllegalStateException pos = assertThrows(IllegalStateException.class, () ->
        DocumentCategorizerDL.softmax(new float[] {0f, Float.POSITIVE_INFINITY, 0f}));
    assertTrue(pos.getMessage().contains("non-finite") || pos.getMessage().contains("Infinity"),
        pos.getMessage());
    assertThrows(IllegalStateException.class, () ->
        DocumentCategorizerDL.softmax(new float[] {0f, Float.NEGATIVE_INFINITY, 0f}));
  }

  @Test
  void testSoftmaxIsUniformForEqualLogitsAndSumsToOne() {
    final double[] out = DocumentCategorizerDL.softmax(new float[] {0f, 0f, 0f});

    assertEquals(3, out.length);
    for (final double p : out) {
      assertEquals(1.0 / 3.0, p, 1e-12);
    }
    assertEquals(1.0, out[0] + out[1] + out[2], 1e-12);
  }

  @Test
  void testSoftmaxIsNumericallyStableForLargeLogits() {
    // The naive exp(logit) form overflows to +Infinity here and yields NaN; subtracting
    // the maximum keeps every value finite and the distribution uniform.
    final double[] out = DocumentCategorizerDL.softmax(new float[] {1000f, 1000f, 1000f});

    double sum = 0.0;
    for (final double p : out) {
      assertFalse(Double.isNaN(p) || Double.isInfinite(p),
          "softmax must stay finite for large logits");
      assertEquals(1.0 / 3.0, p, 1e-9);
      sum += p;
    }
    assertEquals(1.0, sum, 1e-12);
  }

  @Test
  void testSoftmaxIsNumericallyStable() {
    final double[] scores = DocumentCategorizerDL.softmax(new float[] {1000.0f, 1001.0f});

    assertArrayEquals(new double[] {0.2689414213699951, 0.7310585786300049}, scores, 1e-15);
  }

  @Test
  void testSoftmaxMatchesReferenceDistribution() {
    // Reference (numpy): softmax([1,2,3]) = [0.09003057, 0.24472847, 0.66524096].
    final double[] out = DocumentCategorizerDL.softmax(new float[] {1f, 2f, 3f});

    assertEquals(0.09003057, out[0], 1e-6);
    assertEquals(0.24472847, out[1], 1e-6);
    assertEquals(0.66524096, out[2], 1e-6);
  }

  @Test
  void testLogitsFromOutputDispatchesOnModelShape() {
    // A 2D output (BERT-style) takes row 0; a 1D output (RoBERTa-style) is taken as-is.
    assertArrayEquals(new float[] {1f, 2f},
        DocumentCategorizerDL.logitsFromOutput(new float[][] {{1f, 2f}}));
    assertArrayEquals(new float[] {3f, 4f},
        DocumentCategorizerDL.logitsFromOutput(new float[] {3f, 4f}));
  }

  @Test
  void testLogitsFromOutputFailsLoudlyOnNullAndUnexpectedType() {
    // A null or otherwise-shaped model output is a contract violation, not an "inference failed".
    final IllegalStateException onNull = assertThrows(IllegalStateException.class,
        () -> DocumentCategorizerDL.logitsFromOutput(null));
    assertTrue(onNull.getMessage().contains("null"), onNull.getMessage());
    assertThrows(IllegalStateException.class,
        () -> DocumentCategorizerDL.logitsFromOutput("not a tensor"));
  }

  @Test
  void testRequireMatchingCategoryCountFailsLoudlyOnMismatch() {
    // A distribution whose length differs from the configured category count means the model and
    // the categorizer configuration do not match; the matching case passes the array through.
    final double[] ok = {0.5, 0.5};
    assertArrayEquals(ok, DocumentCategorizerDL.requireMatchingCategoryCount(ok, 2));
    final IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> DocumentCategorizerDL.requireMatchingCategoryCount(new double[] {1.0}, 2));
    assertTrue(e.getMessage().contains("do not match"), e.getMessage());
  }
}
