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

package opennlp.dl.namefinder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.WordpieceTokenizer;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NameFinderDLTest {

  private static final Map<Integer, String> ID_TO_LABELS = Map.of(
      0, "O",
      1, "B-PER",
      2, "I-PER",
      3, "B-LOC",
      4, "I-LOC",
      5, "B-ORG",
      6, "I-ORG",
      7, "B-");

  private static Map<String, Integer> vocab() {
    final Map<String, Integer> vocab = new HashMap<>();
    vocab.put(WordpieceTokenizer.BERT_CLS_TOKEN, 0);
    vocab.put(WordpieceTokenizer.BERT_SEP_TOKEN, 1);
    vocab.put(WordpieceTokenizer.BERT_UNK_TOKEN, 2);
    vocab.put("hello", 3);
    vocab.put("world", 4);
    return vocab;
  }

  @Test
  void testTokenIdsMapsTokensToVocabularyIds() {
    final long[] ids = NameFinderDL.tokenIds(
        new String[] {WordpieceTokenizer.BERT_CLS_TOKEN, "hello", "world",
            WordpieceTokenizer.BERT_SEP_TOKEN}, vocab());

    assertArrayEquals(new long[] {0, 3, 4, 1}, ids);
  }

  @Test
  void testTokenIdsRejectsTokensMissingFromVocabulary() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        NameFinderDL.tokenIds(new String[] {"hello", "missing"}, vocab()));

    assertTrue(e.getMessage().contains("missing"),
        "the error message should name the missing token: " + e.getMessage());
  }

  @Test
  void testDecodeSpansUsesBioEntityTypesAndBoundedProbabilities() {
    final String text = "Alice visited New York City.";
    final String[] tokens = {"[CLS]", "Alice", "visited", "New", "York", "City", ".", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(1), scoresFor(0), scoresFor(3), scoresFor(4), scoresFor(4),
        scoresFor(0), scoresFor(0)
    };

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(2, spans.size());

    final Span person = spans.get(0);
    assertEquals("PER", person.getType());
    assertEquals("Alice", person.getCoveredText(text));
    assertProbability(person);

    final Span location = spans.get(1);
    assertEquals("LOC", location.getType());
    assertEquals("New York City", location.getCoveredText(text));
    assertProbability(location);
  }

  @Test
  void testDecodeSpansReconstructsWordpiecesAndEscapedPunctuation() {
    final String text = "Acme (UK) hired Sarah Connor.";
    final String[] tokens = {"[CLS]", "Acme", "(", "UK", ")", "hired", "Sarah", "Con",
        "##nor", ".", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(5), scoresFor(6), scoresFor(6), scoresFor(6), scoresFor(0),
        scoresFor(1), scoresFor(2), scoresFor(2), scoresFor(0), scoresFor(0)
    };

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(2, spans.size());
    assertEquals("ORG", spans.get(0).getType());
    assertEquals("Acme (UK)", spans.get(0).getCoveredText(text));
    assertEquals("PER", spans.get(1).getType());
    assertEquals("Sarah Connor", spans.get(1).getCoveredText(text));
  }

  @Test
  void testDecodeSpansIgnoresMalformedBeginLabels() {
    final String text = "Alice visited.";
    final String[] tokens = {"[CLS]", "Alice", "visited", ".", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(7), scoresFor(0), scoresFor(0), scoresFor(0)
    };

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertTrue(spans.isEmpty());
  }

  @Test
  void testDecodeSpansTreatsMissingPredictedLabelsAsOutside() {
    final String text = "Alice visited.";
    final String[] tokens = {"[CLS]", "Alice", "visited", ".", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(1), scoresFor(0), scoresFor(0), scoresFor(0)
    };
    final Map<Integer, String> incompleteLabels = Map.of(0, "O");

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, incompleteLabels);

    assertTrue(spans.isEmpty());
  }

  @Test
  void testDecodeSpansSearchStartLocatesNextOccurrence() {
    // "Paris" appears twice. Threading the cursor past the first occurrence (as find() does
    // across chunks/sentences) locates the second one instead of re-emitting the first, so a
    // repeated entity is not duplicated at the same offset.
    final String text = "Paris and Paris";
    final String[] tokens = {"[CLS]", "Paris", "[SEP]"};
    final float[][] scores = {scoresFor(0), scoresFor(3), scoresFor(0)};

    final List<Span> first = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS, 0);
    assertEquals(1, first.size());
    assertEquals(0, first.get(0).getStart());
    assertEquals(5, first.get(0).getEnd());

    final List<Span> next = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS,
        first.get(0).getEnd());
    assertEquals(1, next.size());
    assertEquals(10, next.get(0).getStart());
    assertEquals(15, next.get(0).getEnd());
    assertEquals("Paris", next.get(0).getCoveredText(text));
  }

  @Test
  void testDecodeSpansLocatesEntityWithInternalPunctuation() {
    // WordPiece splits "AT&T" into separate AT / & / T tokens, so the reconstructed span text
    // ("AT & T") must still be located in the contiguous source. Regression guard for the
    // flexible-whitespace (\s*) matching in findByRegex.
    final String text = "Buy AT&T stock";
    final String[] tokens = {"[CLS]", "Buy", "AT", "&", "T", "stock", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(0), scoresFor(5), scoresFor(6), scoresFor(6),
        scoresFor(0), scoresFor(0)
    };

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(1, spans.size());
    assertEquals("ORG", spans.get(0).getType());
    assertEquals("AT&T", spans.get(0).getCoveredText(text));
  }

  @Test
  void testDecodeSpansDoesNotMatchBeyondSearchEnd() {
    final String text = "London was quiet. Later Paris was loud.";
    final String[] tokens = {"[CLS]", "Paris", "[SEP]"};
    final float[][] scores = {scoresFor(0), scoresFor(3), scoresFor(0)};

    final List<Span> spans = NameFinderDL.decodeSpans(
        text, tokens, scores, ID_TO_LABELS, 0, text.indexOf(" Later"));

    assertTrue(spans.isEmpty());
  }

  @Test
  void testDecodeSpansMatchesSourceCaseInsensitively() {
    // The reconstructed span text may differ in case from the source (e.g. an uncased model);
    // findByRegex matches case-insensitively, so the span is still located at the source offsets.
    final String text = "Visit PARIS today";
    final String[] tokens = {"[CLS]", "Visit", "paris", "today", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(0), scoresFor(3), scoresFor(0), scoresFor(0)
    };

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(1, spans.size());
    assertEquals("LOC", spans.get(0).getType());
    assertEquals("PARIS", spans.get(0).getCoveredText(text));
  }

  @Test
  void testDecodeSpansSkipsNaNAndPicksLargestFinite() {
    final String text = "Alice visited.";
    final String[] tokens = {"[CLS]", "Alice", "visited", ".", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresWithNaN(1), scoresFor(0), scoresFor(0), scoresFor(0)
    };

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(1, spans.size());
    assertEquals("Alice", spans.get(0).getCoveredText(text));
  }

  @Test
  void testDecodeSpansRejectsAllNaNOrEmptyScores() {
    final String text = "Alice visited.";
    final String[] tokens = {"[CLS]", "Alice", "visited", ".", "[SEP]"};

    assertThrows(IllegalStateException.class, () -> NameFinderDL.decodeSpans(text, tokens,
        new float[][] {scoresFor(0), new float[] {Float.NaN, Float.NaN}, scoresFor(0),
            scoresFor(0), scoresFor(0)}, ID_TO_LABELS));
    assertThrows(IllegalStateException.class, () -> NameFinderDL.decodeSpans(text, tokens,
        new float[][] {scoresFor(0), new float[0], scoresFor(0), scoresFor(0), scoresFor(0)},
        ID_TO_LABELS));
  }

  @Test
  void testLabelProbabilityIsBoundedStableSoftmax() {
    // Reference (numpy): softmax([1,2,3])[2] = 0.66524096.
    final double p = NameFinderDL.labelProbability(new float[] {1f, 2f, 3f}, 2);
    assertEquals(0.66524096, p, 1e-6);
    assertBounded(p);
  }

  @Test
  void testLabelProbabilityHandlesPositiveInfinity() {
    // Two +Inf logits split the mass; a finite logit alongside them gets zero.
    final float[] scores = {Float.POSITIVE_INFINITY, 0f, Float.POSITIVE_INFINITY};
    assertEquals(0.5, NameFinderDL.labelProbability(scores, 0), 1e-9);
    assertEquals(0.0, NameFinderDL.labelProbability(scores, 1), 1e-9);
    assertBounded(NameFinderDL.labelProbability(scores, 0));
  }

  @Test
  void testLabelProbabilityHandlesAllNegativeInfinity() {
    // No finite score: fall back to a uniform distribution rather than producing NaN.
    final double p = NameFinderDL.labelProbability(
        new float[] {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY}, 0);
    assertEquals(0.5, p, 1e-9);
    assertBounded(p);
  }

  @Test
  void testLabelProbabilityIgnoresNaNInDenominator() {
    // A NaN logit must not poison the normalization of the finite ones.
    final double p = NameFinderDL.labelProbability(new float[] {0f, Float.NaN, 0f}, 0);
    assertEquals(0.5, p, 1e-9);
    assertBounded(p);
  }

  private static float[] scoresFor(int labelIndex) {
    final float[] scores = new float[ID_TO_LABELS.size()];
    for (int i = 0; i < scores.length; i++) {
      scores[i] = -5;
    }
    scores[labelIndex] = 5;
    return scores;
  }

  private static float[] scoresWithNaN(int labelIndex) {
    final float[] scores = scoresFor(labelIndex);
    scores[0] = Float.NaN;
    return scores;
  }

  private static void assertProbability(Span span) {
    assertTrue(span.getProb() > 0 && span.getProb() <= 1,
        "span probability should be normalized to (0, 1]: " + span.getProb());
  }

  private static void assertBounded(double probability) {
    assertTrue(probability >= 0 && probability <= 1,
        "probability must be within [0, 1]: " + probability);
  }
}
