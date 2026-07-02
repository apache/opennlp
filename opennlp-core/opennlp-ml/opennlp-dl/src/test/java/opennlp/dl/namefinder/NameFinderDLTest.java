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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.WordpieceTokenizer;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
  void testDecodeSpansRejectsMissingPredictedLabels() {
    final String text = "Alice visited.";
    final String[] tokens = {"[CLS]", "Alice", "visited", ".", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(1), scoresFor(0), scoresFor(0), scoresFor(0)
    };
    final Map<Integer, String> incompleteLabels = Map.of(0, "O");

    final IllegalStateException e = assertThrows(IllegalStateException.class, () ->
        NameFinderDL.decodeSpans(text, tokens, scores, incompleteLabels));

    assertTrue(e.getMessage().contains("1"),
        "the error message should name the missing label id: " + e.getMessage());
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
    // flexible-whitespace matching in findInSource (a span space matches zero source whitespace).
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
  void testDecodeSpansMatchesEntitySeparatedByNoBreakSpace() {
    // The source separates "New" and "York" with a no-break space (U+00A0). Java's \s does not
    // match it, so the previous regex matcher would have dropped this LOC span; the Unicode-aware
    // cursor matcher locates it and the covered text includes the no-break space.
    final String nbsp = new String(Character.toChars(0x00A0));
    final String text = "Visit New" + nbsp + "York today";
    final String[] tokens = {"[CLS]", "New", "York", "[SEP]"};
    final float[][] scores = {scoresFor(0), scoresFor(3), scoresFor(4), scoresFor(0)};

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(1, spans.size());
    assertEquals("LOC", spans.get(0).getType());
    assertEquals("New" + nbsp + "York", spans.get(0).getCoveredText(text));
  }

  @Test
  void testDecodeSpansMatchesEntitySeparatedByIdeographicSpace() {
    // Same idea with the CJK ideographic space (U+3000), another character outside Java's \s.
    final String ideographic = new String(Character.toChars(0x3000));
    final String text = "from New" + ideographic + "York city";
    final String[] tokens = {"[CLS]", "New", "York", "[SEP]"};
    final float[][] scores = {scoresFor(0), scoresFor(3), scoresFor(4), scoresFor(0)};

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(1, spans.size());
    assertEquals("New" + ideographic + "York", spans.get(0).getCoveredText(text));
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
    // findInSource matches case-insensitively, so the span is still located at the source offsets.
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
  void testDecodeSpansRejectsTokenScoreCountMismatch() {
    // Fewer score rows than tokens is a model/tokenizer contract violation; the message must name
    // both counts so the mismatch is debuggable.
    final String text = "Alice visited.";
    final String[] tokens = {"[CLS]", "Alice", "visited", ".", "[SEP]"};
    final float[][] scores = {scoresFor(0), scoresFor(1)};

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS));

    assertTrue(e.getMessage().contains("5") && e.getMessage().contains("2"),
        "the error message should name both counts: " + e.getMessage());
  }

  @Test
  void testDecodeSpansIgnoresInsideLabelWithoutBegin() {
    // An I-LOC with no preceding B-LOC is not a valid span start and must not emit an entity.
    final String text = "Visit Paris today";
    final String[] tokens = {"[CLS]", "Visit", "Paris", "today", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(0), scoresFor(4), scoresFor(0), scoresFor(0)
    };

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertTrue(spans.isEmpty());
  }

  @Test
  void testDecodeSpansSeparatesAdjacentEntitiesOfDifferentTypes() {
    // B-PER directly followed by B-LOC must yield two distinct single-token spans, not one merged
    // span: findEntityEnd stops at the type change and the outer loop resumes at the next begin.
    final String text = "Alice Paris";
    final String[] tokens = {"[CLS]", "Alice", "Paris", "[SEP]"};
    final float[][] scores = {scoresFor(0), scoresFor(1), scoresFor(3), scoresFor(0)};

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(2, spans.size());
    assertEquals("PER", spans.get(0).getType());
    assertEquals("Alice", spans.get(0).getCoveredText(text));
    assertEquals("LOC", spans.get(1).getType());
    assertEquals("Paris", spans.get(1).getCoveredText(text));
  }

  @Test
  void testMultiTokenSpanProbabilityIsWeakestTokenProbability() {
    // The probability of a multi-token entity is the minimum across its tokens, so a confident
    // begin followed by a weak continuation reports the weak continuation's probability.
    final String text = "New York";
    final String[] tokens = {"[CLS]", "New", "York", "[SEP]"};
    final float[] strongBegin = scoresFor(3);
    final float[] weakInside = weakScoresFor(4);
    final float[][] scores = {scoresFor(0), strongBegin, weakInside, scoresFor(0)};

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(1, spans.size());
    assertEquals("New York", spans.get(0).getCoveredText(text));
    assertEquals(NameFinderDL.labelProbability(weakInside, 4), spans.get(0).getProb(), 1e-9);
    assertTrue(spans.get(0).getProb() < NameFinderDL.labelProbability(strongBegin, 3),
        "multi-token span should reflect its weakest continuation");
  }

  @Test
  void testDecodeSpansEmitsRepeatedEntityAtDistinctOffsets() {
    // Two identical surface forms within a single call must resolve to distinct, non-overlapping
    // spans via the internal monotonic cursor rather than both matching the first occurrence.
    final String text = "Paris and Paris";
    final String[] tokens = {"[CLS]", "Paris", "and", "Paris", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(3), scoresFor(0), scoresFor(3), scoresFor(0)
    };

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(2, spans.size());
    assertEquals(0, spans.get(0).getStart());
    assertEquals(5, spans.get(0).getEnd());
    assertEquals(10, spans.get(1).getStart());
    assertEquals(15, spans.get(1).getEnd());
  }

  @Test
  void testMergeOverlappingSpansKeepsLongestAndPreservesDisjoint() {
    // Containment: the longer span absorbs the shorter overlapping one.
    final List<Span> contained = NameFinderDL.mergeOverlappingSpans(new ArrayList<>(List.of(
        new Span(0, 8, "LOC", 0.9), new Span(0, 13, "LOC", 0.8))));
    assertEquals(1, contained.size());
    assertEquals(0, contained.get(0).getStart());
    assertEquals(13, contained.get(0).getEnd());

    // Partial overlap: the longer span wins, the shorter overlapping one is dropped.
    final List<Span> partial = NameFinderDL.mergeOverlappingSpans(new ArrayList<>(List.of(
        new Span(0, 5, "LOC", 0.7), new Span(3, 12, "LOC", 0.6))));
    assertEquals(1, partial.size());
    assertEquals(3, partial.get(0).getStart());
    assertEquals(12, partial.get(0).getEnd());

    // Equal length overlap: the higher probability wins the tie.
    final List<Span> tie = NameFinderDL.mergeOverlappingSpans(new ArrayList<>(List.of(
        new Span(0, 5, "PER", 0.6), new Span(2, 7, "PER", 0.9))));
    assertEquals(1, tie.size());
    assertEquals(2, tie.get(0).getStart());
    assertEquals(7, tie.get(0).getEnd());

    // Adjacent but disjoint spans are both kept and returned in document order.
    final List<Span> disjoint = NameFinderDL.mergeOverlappingSpans(new ArrayList<>(List.of(
        new Span(5, 10, "LOC", 0.9), new Span(0, 5, "PER", 0.9))));
    assertEquals(2, disjoint.size());
    assertEquals(0, disjoint.get(0).getStart());
    assertEquals(5, disjoint.get(1).getStart());
  }

  @Test
  void testMergeOverlappingSpansReturnsAFreshListForTrivialInput() {
    // The size < 2 fast path must hand back a new list, not the caller's, so the result is always
    // owned by the caller -- the same ownership contract as the merging path.
    final List<Span> single = new ArrayList<>(List.of(new Span(0, 5, "PER", 0.9)));
    final List<Span> merged = NameFinderDL.mergeOverlappingSpans(single);
    assertEquals(1, merged.size());
    assertNotSame(single, merged);
    assertTrue(NameFinderDL.mergeOverlappingSpans(new ArrayList<Span>()).isEmpty());
  }

  @Test
  void testChunkAssemblyKeepsFullerOverlappingSpan() {
    // Mirrors how locate() decodes two overlapping chunks bounded to their own character regions.
    // Chunk 1 covers up to "York" and labels "New York"; the overlapping chunk 2 covers "New York
    // City" and labels it. Both candidates are produced and mergeOverlappingSpans keeps the fuller
    // "New York City" rather than dropping it, which is the chunk-boundary case a single forward
    // cursor mishandled.
    final String text = "Alice visited New York City."; // "New York" = [14,22), "City" ends at 27
    final String[] chunk1 = {"[CLS]", "New", "York", "[SEP]"};
    final float[][] scores1 = {scoresFor(0), scoresFor(3), scoresFor(4), scoresFor(0)};
    final String[] chunk2 = {"[CLS]", "New", "York", "City", "[SEP]"};
    final float[][] scores2 =
        {scoresFor(0), scoresFor(3), scoresFor(4), scoresFor(4), scoresFor(0)};

    final List<Span> candidates = new ArrayList<>();
    candidates.addAll(NameFinderDL.decodeSpans(text, chunk1, scores1, ID_TO_LABELS, 14, 22));
    candidates.addAll(NameFinderDL.decodeSpans(text, chunk2, scores2, ID_TO_LABELS, 14, 27));
    assertEquals(2, candidates.size(), "both chunks emit a candidate for the boundary entity");

    final List<Span> merged = NameFinderDL.mergeOverlappingSpans(candidates);
    assertEquals(1, merged.size());
    assertEquals("New York City", merged.get(0).getCoveredText(text));
  }

  @Test
  void testDecodeSpansLocatesEntityWithRegexMetacharacters() {
    // WordPiece splits "C++" into C / + / + tokens, so the reconstructed span text contains regex
    // metacharacters. Pattern.quote must treat them literally (not as quantifiers) for the entity
    // to be located in the source.
    final String text = "Love C++ today";
    final String[] tokens = {"[CLS]", "Love", "C", "+", "+", "today", "[SEP]"};
    final float[][] scores = {
        scoresFor(0), scoresFor(0), scoresFor(5), scoresFor(6), scoresFor(6),
        scoresFor(0), scoresFor(0)
    };

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS);

    assertEquals(1, spans.size());
    assertEquals("ORG", spans.get(0).getType());
    assertEquals("C++", spans.get(0).getCoveredText(text));
  }

  @Test
  void testDecodeSpansClampsSearchStartBeyondText() {
    // A searchStart past the end of the text must clamp to an empty region and yield no match
    // rather than throwing an out-of-bounds error.
    final String text = "Paris";
    final String[] tokens = {"[CLS]", "Paris", "[SEP]"};
    final float[][] scores = {scoresFor(0), scoresFor(3), scoresFor(0)};

    final List<Span> spans = NameFinderDL.decodeSpans(text, tokens, scores, ID_TO_LABELS, 999);

    assertTrue(spans.isEmpty());
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

  @Test
  void testBuildSpanTextSkipsRobertaSpecialTokens() {
    // RoBERTa markers (<s>, </s>) must be skipped during span reconstruction, the same way the
    // BERT [CLS]/[SEP] markers are, so they never leak into a reconstructed entity span.
    assertEquals("New York",
        NameFinderDL.buildSpanText(new String[] {"<s>", "New", "York", "</s>"}, 0, 3));
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

  // Lower-margin scores than scoresFor, so the chosen label's softmax probability is well below 1
  // and a multi-token span's minimum-probability behavior is observable.
  private static float[] weakScoresFor(int labelIndex) {
    final float[] scores = new float[ID_TO_LABELS.size()];
    for (int i = 0; i < scores.length; i++) {
      scores[i] = -1;
    }
    scores[labelIndex] = 1;
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

  @Test
  void testConstructorRejectsNullArgumentsWithIllegalArgumentException() {
    // The guards run in the constructor-argument expression, before any model file is touched,
    // so they are testable without an ONNX session; null parameters report
    // IllegalArgumentException, matching the engine and tokenizer layers.
    assertThrows(IllegalArgumentException.class,
        () -> new NameFinderDL(null, null, null, null));
  }
}
