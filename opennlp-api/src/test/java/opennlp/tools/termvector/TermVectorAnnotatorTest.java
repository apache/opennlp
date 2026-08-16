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

package opennlp.tools.termvector;

import java.io.Serial;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;
import opennlp.tools.util.normalizer.AlignedText;
import opennlp.tools.util.normalizer.Alignment;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;
import opennlp.tools.util.normalizer.OffsetAwareNormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link TermVectorAnnotator} roll-up: term identity with and without a
 * normalizer, both recording modes, and the graceful-degradation rules of the document
 * pipeline. The token layer is built directly here; the wiring through a
 * {@code DocumentAnalyzer} is covered by {@code TermVectorPipelineTest}.
 */
public class TermVectorAnnotatorTest {

  /**
   * A deterministic {@link OffsetAwareNormalizer} that collapses every run of
   * whitespace to one space and applies a full case fold, including the German eszett
   * expansion {@code ß -> ss}. Every edit is recorded in an {@link Alignment}, so the
   * expected spans below follow directly from the input text.
   */
  private static final class WhitespaceCaseFoldNormalizer implements OffsetAwareNormalizer {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public CharSequence normalize(CharSequence text) {
      return normalizeAligned(text).normalized();
    }

    @Override
    public AlignedText normalizeAligned(CharSequence text) {
      final String source = text.toString();
      final StringBuilder out = new StringBuilder(source.length());
      final Alignment.Builder alignment = new Alignment.Builder(source.length());
      int i = 0;
      while (i < source.length()) {
        final char c = source.charAt(i);
        if (Character.isWhitespace(c)) {
          int end = i;
          while (end < source.length() && Character.isWhitespace(source.charAt(end))) {
            end++;
          }
          out.append(' ');
          alignment.replace(end - i, 1);
          i = end;
        } else if (c == 'ß') {
          out.append("ss");
          alignment.replace(1, 2);
          i++;
        } else {
          final String folded = String.valueOf(c).toLowerCase(Locale.ROOT);
          out.append(folded);
          if (folded.length() == 1 && folded.charAt(0) == c) {
            alignment.equal(1);
          } else {
            alignment.replace(1, folded.length());
          }
          i++;
        }
      }
      return new AlignedText(source, out.toString(), alignment.build(source.length()));
    }
  }

  /**
   * A deterministic {@link OffsetAwareNormalizer} that deletes every digit and copies
   * every other character unchanged, so a token made up of digits alone normalizes to
   * the empty string.
   */
  private static final class DigitDeletingNormalizer implements OffsetAwareNormalizer {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public CharSequence normalize(CharSequence text) {
      return normalizeAligned(text).normalized();
    }

    @Override
    public AlignedText normalizeAligned(CharSequence text) {
      final String source = text.toString();
      final StringBuilder out = new StringBuilder(source.length());
      final Alignment.Builder alignment = new Alignment.Builder(source.length());
      for (int i = 0; i < source.length(); i++) {
        final char c = source.charAt(i);
        if (Character.isDigit(c)) {
          alignment.replace(1, 0);
        } else {
          out.append(c);
          alignment.equal(1);
        }
      }
      return new AlignedText(source, out.toString(), alignment.build(source.length()));
    }
  }

  private static final OffsetAwareNormalizer FOLD = new WhitespaceCaseFoldNormalizer();

  private static final OffsetAwareNormalizer DROP_DIGITS = new DigitDeletingNormalizer();

  /**
   * A plain, alignment-free case folder for the per-token path: it stands in for the
   * shipped normalizers (case fold, NFC, accent fold) that cannot report offsets and
   * therefore cannot implement {@link OffsetAwareNormalizer}.
   */
  private static final CharSequenceNormalizer PLAIN_LOWER =
      text -> text.toString().toLowerCase(Locale.ROOT);

  /**
   * A plain normalizer that deletes every digit, so a token made up of digits alone
   * normalizes to the empty string on the per-token path.
   */
  private static final CharSequenceNormalizer PLAIN_DROP_DIGITS = text -> {
    final StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isDigit(text.charAt(i))) {
        out.append(text.charAt(i));
      }
    }
    return out.toString();
  };

  private static Document documentWithTokens(String text) {
    return Document.of(text).with(Layers.TOKENS, SingleSpaceTokens.tokens(text));
  }

  @Test
  void testFullModeGroupsByCoveredTextAsIs() {
    final Document document = new TermVectorAnnotator()
        .annotate(documentWithTokens("The dog barks. The dog naps."));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(4, vectors.size());
    // The layer preserves first-occurrence order.
    assertEquals(new TermVector("The", 2, List.of(new Span(0, 3), new Span(15, 18))),
        vectors.get(0).value());
    assertEquals(new TermVector("dog", 2, List.of(new Span(4, 7), new Span(19, 22))),
        vectors.get(1).value());
    assertEquals(new TermVector("barks.", 1, List.of(new Span(8, 14))),
        vectors.get(2).value());
    assertEquals(new TermVector("naps.", 1, List.of(new Span(23, 28))),
        vectors.get(3).value());
  }

  @Test
  void testLayerIsDocumentScopedAndCarriesNoAnnotationSpans() {
    final Document document = new TermVectorAnnotator()
        .annotate(documentWithTokens("The dog barks."));
    assertEquals(LayerKey.Scope.DOCUMENT, TermVectorAnnotator.TERM_VECTORS.scope());
    for (final Annotation<TermVector> vector : document.get(TermVectorAnnotator.TERM_VECTORS)) {
      assertNull(vector.span());
    }
  }

  @Test
  void testScoringOnlyModeOmitsSpans() {
    final Document document = new TermVectorAnnotator(TermVectorAnnotator.Mode.SCORING_ONLY)
        .annotate(documentWithTokens("The dog barks. The dog naps."));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(4, vectors.size());
    assertEquals(TermVector.count("The", 2), vectors.get(0).value());
    assertEquals(TermVector.count("dog", 2), vectors.get(1).value());
    assertEquals(TermVector.count("barks.", 1), vectors.get(2).value());
    assertEquals(TermVector.count("naps.", 1), vectors.get(3).value());
    for (final Annotation<TermVector> vector : vectors) {
      assertTrue(vector.value().spans().isEmpty());
    }
  }

  /**
   * The offset-fidelity case: whitespace collapse shifts offsets and the eszett case
   * fold grows the text, yet every emitted occurrence span must land on the original
   * text. {@code "Groß  groß  GROSS"} normalizes to {@code "gross gross gross"}, so all
   * three tokens group under one term while their spans keep pointing at the original
   * surface forms.
   */
  @Test
  void testNormalizationGroupsFoldedTokensWithOriginalOffsets() {
    final String text = "Groß  groß  GROSS";
    final Document document = new TermVectorAnnotator(FOLD)
        .annotate(documentWithTokens(text));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(1, vectors.size());
    final TermVector vector = vectors.get(0).value();
    assertEquals("gross", vector.term());
    assertEquals(3, vector.frequency());
    assertEquals(List.of(new Span(0, 4), new Span(6, 10), new Span(12, 17)), vector.spans());

    // Every occurrence span covers the original surface form, not the normalized one.
    final List<String> surfaceForms =
        vector.spans().stream().map(s -> s.getCoveredText(text).toString()).toList();
    assertEquals(List.of("Groß", "groß", "GROSS"), surfaceForms);

    // The same spans round-trip through the alignment: a token's normalized span maps
    // back to exactly its original span, through both edits.
    final AlignedText aligned = FOLD.normalizeAligned(text);
    assertEquals("gross gross gross", aligned.normalizedString());
    assertEquals(new Span(0, 4), aligned.toOriginalSpan(0, 5));
    assertEquals(new Span(6, 10), aligned.toOriginalSpan(6, 11));
    assertEquals(new Span(12, 17), aligned.toOriginalSpan(12, 17));
  }

  @Test
  void testNormalizationKeepsDistinctTermsApart() {
    final String text = "Das  große  Haus  ist  groß";
    final Document document = new TermVectorAnnotator(FOLD)
        .annotate(documentWithTokens(text));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(5, vectors.size());
    assertEquals(new TermVector("das", 1, List.of(new Span(0, 3))), vectors.get(0).value());
    assertEquals(new TermVector("grosse", 1, List.of(new Span(5, 10))), vectors.get(1).value());
    assertEquals(new TermVector("haus", 1, List.of(new Span(12, 16))), vectors.get(2).value());
    assertEquals(new TermVector("ist", 1, List.of(new Span(18, 21))), vectors.get(3).value());
    assertEquals(new TermVector("gross", 1, List.of(new Span(23, 27))), vectors.get(4).value());
  }

  @Test
  void testScoringOnlyModeWithNormalizerCountsWithoutOffsets() {
    final Document document = new TermVectorAnnotator(FOLD,
        TermVectorAnnotator.Mode.SCORING_ONLY).annotate(documentWithTokens("Groß  groß  GROSS"));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(1, vectors.size());
    assertEquals(TermVector.count("gross", 3), vectors.get(0).value());
  }

  /**
   * The general per-token path: a plain {@link CharSequenceNormalizer} defines term
   * identity by folding each token's covered text, no alignment involved, while every
   * occurrence span stays the token's own span in the original text. This admits the
   * folds {@code buildAligned()} rejects (case fold, NFC, accent fold).
   */
  @Test
  void testPlainNormalizerGroupsFoldedTokensWithOriginalSpans() {
    final String text = "Word word WORD";
    final Document document = new TermVectorAnnotator(PLAIN_LOWER)
        .annotate(documentWithTokens(text));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(1, vectors.size());
    assertEquals(new TermVector("word", 3,
        List.of(new Span(0, 4), new Span(5, 9), new Span(10, 14))), vectors.get(0).value());

    // Every occurrence span covers the original surface form, not the folded one.
    final List<String> surfaceForms = vectors.get(0).value().spans().stream()
        .map(s -> s.getCoveredText(text).toString()).toList();
    assertEquals(List.of("Word", "word", "WORD"), surfaceForms);
  }

  @Test
  void testPlainNormalizerScoringOnlyModeCountsWithoutOffsets() {
    final Document document = new TermVectorAnnotator(PLAIN_LOWER,
        TermVectorAnnotator.Mode.SCORING_ONLY).annotate(documentWithTokens("Word word WORD"));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(1, vectors.size());
    assertEquals(TermVector.count("word", 3), vectors.get(0).value());
  }

  /**
   * Empty-term omission behaves identically on the per-token path: tokens a plain
   * normalizer folds to the empty string are left out of the layer, matching
   * {@link #testTokensNormalizedAwayAreOmitted()}.
   */
  @Test
  void testPlainNormalizerOmitsDeletedTokens() {
    final Document document = new TermVectorAnnotator(PLAIN_DROP_DIGITS)
        .annotate(documentWithTokens("dog 42 dog 7"));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(1, vectors.size());
    assertEquals(new TermVector("dog", 2, List.of(new Span(0, 3), new Span(7, 10))),
        vectors.get(0).value());
  }

  @Test
  void testNullPlainNormalizerIsRejected() {
    final CharSequenceNormalizer noNormalizer = null;
    assertThrows(IllegalArgumentException.class, () -> new TermVectorAnnotator(noNormalizer));
    assertThrows(IllegalArgumentException.class,
        () -> new TermVectorAnnotator(noNormalizer, TermVectorAnnotator.Mode.FULL));
    assertThrows(IllegalArgumentException.class,
        () -> new TermVectorAnnotator(PLAIN_LOWER, null));
  }

  /**
   * A token the normalizer deletes entirely is omitted from the layer: an empty string
   * is no term, and the token layer still accounts for the token itself.
   */
  @Test
  void testTokensNormalizedAwayAreOmitted() {
    final Document document = new TermVectorAnnotator(DROP_DIGITS)
        .annotate(documentWithTokens("dog 42 dog 7"));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(1, vectors.size());
    assertEquals(new TermVector("dog", 2, List.of(new Span(0, 3), new Span(7, 10))),
        vectors.get(0).value());
  }

  /**
   * Spans are UTF-16 offsets, so a supplementary-plane character occupies two positions:
   * {@code "𝕏 x 𝕏"} tokenizes to spans of width two around the surrogate pairs, and both
   * occurrences group under one term whose spans still cover the original text exactly.
   */
  @Test
  void testSupplementaryPlaneTokensKeepUtf16Offsets() {
    final String text = "𝕏 x 𝕏";
    final Document document = new TermVectorAnnotator()
        .annotate(documentWithTokens(text));

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(2, vectors.size());
    assertEquals(new TermVector("𝕏", 2, List.of(new Span(0, 2), new Span(5, 7))),
        vectors.get(0).value());
    assertEquals(new TermVector("x", 1, List.of(new Span(3, 4))), vectors.get(1).value());
    for (final Annotation<TermVector> vector : vectors) {
      for (final Span span : vector.value().spans()) {
        assertEquals(vector.value().term(), span.getCoveredText(text).toString());
      }
    }
  }

  /**
   * A document whose every token normalizes to the empty string yields the layer
   * present but empty, the same graceful degradation as an empty token layer.
   */
  @Test
  void testAllTokensNormalizedAwayYieldPresentButEmptyLayer() {
    final Document document = new TermVectorAnnotator(DROP_DIGITS)
        .annotate(documentWithTokens("42 7"));
    assertTrue(document.layers().contains(TermVectorAnnotator.TERM_VECTORS));
    assertTrue(document.get(TermVectorAnnotator.TERM_VECTORS).isEmpty());
  }

  @Test
  void testEmptyTokenLayerYieldsPresentButEmptyLayer() {
    final Document document = new TermVectorAnnotator()
        .annotate(Document.of("").with(Layers.TOKENS, List.of()));
    assertTrue(document.layers().contains(TermVectorAnnotator.TERM_VECTORS));
    assertTrue(document.get(TermVectorAnnotator.TERM_VECTORS).isEmpty());
  }

  @Test
  void testMissingTokenLayerIsRejected() {
    final TermVectorAnnotator annotator = new TermVectorAnnotator();
    final Document bare = Document.of("The dog barks.");
    assertThrows(IllegalArgumentException.class, () -> annotator.annotate(bare));
  }

  @Test
  void testNullDocumentIsRejected() {
    final TermVectorAnnotator annotator = new TermVectorAnnotator();
    assertThrows(IllegalArgumentException.class, () -> annotator.annotate(null));
  }

  @Test
  void testNullConstructorArgumentsAreRejected() {
    final TermVectorAnnotator.Mode noMode = null;
    final OffsetAwareNormalizer noNormalizer = null;
    assertThrows(IllegalArgumentException.class, () -> new TermVectorAnnotator(noMode));
    assertThrows(IllegalArgumentException.class, () -> new TermVectorAnnotator(noNormalizer));
    assertThrows(IllegalArgumentException.class, () -> new TermVectorAnnotator(FOLD, null));
    assertThrows(IllegalArgumentException.class,
        () -> new TermVectorAnnotator(null, TermVectorAnnotator.Mode.FULL));
  }

  @Test
  void testRequiresAndProvides() {
    final TermVectorAnnotator annotator = new TermVectorAnnotator();
    assertEquals(Set.of(Layers.TOKENS), annotator.requires());
    assertEquals(Set.of(TermVectorAnnotator.TERM_VECTORS), annotator.provides());
  }
}
