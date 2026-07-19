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
package opennlp.tools.util.normalizer;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link OffsetAwareNormalizer} and {@code TextNormalizer.Builder.buildAligned()}: the
 * cursor-based rungs report alignments, an aligned pipeline composes them with
 * {@link Alignment#andThen(Alignment)} so a span found in the fully normalized text maps back to the
 * original input, and a non-alignable rung is rejected loudly.
 */
public class AlignedNormalizerPipelineTest {

  private static final int ZERO_WIDTH_SPACE = 0x200B;
  private static final int EM_DASH = 0x2014;
  private static final int YEZIDI_HYPHEN = 0x10EAD; // a supplementary (non-BMP) dash
  private static final int MATH_BOLD_DIGIT_ZERO = 0x1D7CE; // a supplementary decimal digit

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static String covered(AlignedText aligned, int normalizedStart, int normalizedEnd) {
    final Span span = aligned.toOriginalSpan(normalizedStart, normalizedEnd);
    return aligned.original().subSequence(span.getStart(), span.getEnd()).toString();
  }

  // The aligned form must always reproduce exactly what the plain form produces.
  @Test
  void alignedNormalizedTextMatchesPlainForEveryRung() {
    final OffsetAwareNormalizer[] rungs = {
        WhitespaceCharSequenceNormalizer.getInstance(),
        LineBreakPreservingWhitespaceCharSequenceNormalizer.getInstance(),
        DashCharSequenceNormalizer.getInstance(),
        InvisibleCharSequenceNormalizer.getInstance(),
        QuoteCharSequenceNormalizer.getInstance(),
        DigitCharSequenceNormalizer.getInstance(),
        EllipsisCharSequenceNormalizer.getInstance(),
        BulletCharSequenceNormalizer.getInstance(),
        GermanUmlautCharSequenceNormalizer.getInstance(),
        FullCaseFoldCharSequenceNormalizer.getInstance()
    };
    final String[] inputs = {
        "",
        "plain",
        "  lots   of\tspace  ",
        "\n\n  para   one\n\n\tpara two  \n",
        "a" + cp(ZERO_WIDTH_SPACE) + "b" + cp(YEZIDI_HYPHEN) + "c" + cp(EM_DASH) + "d",
        cp(ZERO_WIDTH_SPACE) + "  " + cp(ZERO_WIDTH_SPACE),
        // quotes, ellipsis, eszett, bullet, fullwidth and supplementary digits in one string
        cp(0x201C) + "don" + cp(0x2019) + "t " + cp(0x2026) + " Stra" + cp(0x00DF) + "e "
            + cp(0x2022) + " " + cp(0xFF15) + cp(MATH_BOLD_DIGIT_ZERO)
    };
    for (final OffsetAwareNormalizer rung : rungs) {
      for (final String input : inputs) {
        assertEquals(rung.normalize(input).toString(), rung.normalizeAligned(input).normalized(),
            rung.getClass().getSimpleName() + " on [" + input + "]");
      }
    }
  }

  @Test
  void whitespaceCollapseAndTrimMapsSpanBackToOriginal() {
    final String original = "  hello   world  ";
    final AlignedText aligned = WhitespaceCharSequenceNormalizer.getInstance()
        .normalizeAligned(original);
    assertEquals("hello world", aligned.normalized());
    // "world" sits at [6, 11) in the collapsed/trimmed form.
    final Span span = aligned.toOriginalSpan(6, 11);
    assertEquals(original.indexOf("world"), span.getStart());
    assertEquals("world", covered(aligned, 6, 11));
  }

  @Test
  void dashFoldOfSupplementaryDashMapsSpanBackToOriginal() {
    final String original = "a" + cp(YEZIDI_HYPHEN) + "b";
    final AlignedText aligned = DashCharSequenceNormalizer.getInstance().normalizeAligned(original);
    assertEquals("a-b", aligned.normalized());
    // The two-unit supplementary dash folded to one ASCII hyphen, so 'b' moved from 3 to 2.
    assertEquals("b", covered(aligned, 2, 3));
    assertEquals(cp(YEZIDI_HYPHEN), covered(aligned, 1, 2));
  }

  @Test
  void invisibleStripMapsSpanBackAcrossDeletion() {
    final String original = "a" + cp(ZERO_WIDTH_SPACE) + "b";
    final AlignedText aligned = InvisibleCharSequenceNormalizer.getInstance()
        .normalizeAligned(original);
    assertEquals("ab", aligned.normalized());
    // 'b' is at index 1 in "ab" but index 2 in the original; the deleted ZWSP must not be covered.
    assertEquals("b", covered(aligned, 1, 2));
    assertEquals(2, aligned.toOriginalSpan(1, 2).getStart());
  }

  @Test
  void pipelineComposesStripInvisibleWhitespaceAndDashesBackToOriginal() {
    // 'a', zero-width space, two spaces, 'b', em dash, 'c'.
    final String original = "a" + cp(ZERO_WIDTH_SPACE) + "  b" + cp(EM_DASH) + "c";
    final OffsetAwareNormalizer pipeline = TextNormalizer.builder()
        .stripInvisible().whitespace().dashes().buildAligned();

    final AlignedText aligned = pipeline.normalizeAligned(original);
    assertEquals("a b-c", aligned.normalized());
    assertEquals(pipeline.normalize(original).toString(), aligned.normalized());
    // "b-c" at [2, 5) maps back across a deletion, a collapse, and a dash fold to "b<em-dash>c".
    assertEquals("b" + cp(EM_DASH) + "c", covered(aligned, 2, 5));
  }

  @Test
  void emptyAlignedPipelineIsIdentity() {
    final AlignedText aligned = TextNormalizer.builder().buildAligned().normalizeAligned("Hello");
    assertEquals("Hello", aligned.normalized());
    assertEquals("Hello", covered(aligned, 0, 5));
  }

  @Test
  void buildAlignedRejectsNonAlignableRungLoudly() {
    final IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> TextNormalizer.builder().nfc().whitespace().buildAligned());
    assertTrue(ex.getMessage().contains("Nfc"), ex.getMessage());
    assertTrue(ex.getMessage().contains("offset-aware"), ex.getMessage());
  }

  @Test
  void buildAlignedReportsTheOffendingRungIndexWhenItIsNotFirst() {
    // A non-alignable step after several offset-aware ones must still be rejected, and the message
    // must name its 0-based position (index 2) and type so the failure points at the right fold.
    final IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> TextNormalizer.builder().whitespace().dashes().caseFold().buildAligned());
    assertTrue(ex.getMessage().contains("step at 0-based index 2"), ex.getMessage());
    assertTrue(ex.getMessage().contains("CaseFold"), ex.getMessage());
  }

  @Test
  void buildAlignedRejectsEachKindOfNonAlignableRung() {
    // Every fold that routes through java.text.Normalizer or JDK case mapping is rejected, named.
    assertThrows(IllegalStateException.class,
        () -> TextNormalizer.builder().nfkc().buildAligned());
    assertThrows(IllegalStateException.class,
        () -> TextNormalizer.builder().accentFold().buildAligned());
    assertThrows(IllegalStateException.class,
        () -> TextNormalizer.builder().caseFold().buildAligned());
  }

  @Test
  void capabilityIsDetectableByInstanceOf() {
    assertTrue(WhitespaceCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertTrue(DashCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertTrue(InvisibleCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertFalse(NfcCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertTrue(TextNormalizer.builder().whitespace().dashes().buildAligned()
        instanceof OffsetAwareNormalizer);
    // The per-code-point substitution folds are offset-aware too.
    assertTrue(QuoteCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertTrue(DigitCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertTrue(EllipsisCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertTrue(BulletCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertTrue(GermanUmlautCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertTrue(FullCaseFoldCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    // The folds that route through java.text.Normalizer or JDK case mapping cannot, by design.
    assertFalse(NfkcCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertFalse(CaseFoldCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertFalse(AccentFoldCharSequenceNormalizer.getInstance() instanceof OffsetAwareNormalizer);
    assertFalse(ConfusableSkeletonCharSequenceNormalizer.getInstance()
        instanceof OffsetAwareNormalizer);
  }

  @Test
  void roundTripOfAFullySpanningMatchReturnsTheWholeOriginal() {
    final String original = "  the   quick  ";
    final AlignedText aligned = WhitespaceCharSequenceNormalizer.getInstance()
        .normalizeAligned(original);
    final String normalized = aligned.normalizedString();
    assertEquals("the quick", normalized);
    final Span whole = aligned.toOriginalSpan(0, normalized.length());
    assertSame(original, aligned.original());
    // The match spanning the whole normalized text covers the original from first to last kept char.
    assertEquals("the   quick", original.subSequence(whole.getStart(), whole.getEnd()).toString());
  }

  @Test
  void lineBreakPreservingCollapsesHorizontalRunsButKeepsBreaks() {
    final LineBreakPreservingWhitespaceCharSequenceNormalizer rung =
        LineBreakPreservingWhitespaceCharSequenceNormalizer.getInstance();
    final String original = "Hello   world\n\n\tfoo  bar";
    assertEquals("Hello world\nfoo bar", rung.normalize(original).toString());

    // The plain whitespace rung instead flattens the blank line into a single space.
    assertEquals("Hello world foo bar",
        WhitespaceCharSequenceNormalizer.getInstance().normalize(original).toString());

    final AlignedText aligned = rung.normalizeAligned(original);
    assertEquals(rung.normalize(original).toString(), aligned.normalized());
    // "bar" sits at [16, 19) in the collapsed form and at [21, 24) in the original.
    assertEquals(original.indexOf("bar"), aligned.toOriginalSpan(16, 19).getStart());
    assertEquals("bar", covered(aligned, 16, 19));
    // The preserved newline at index 11 maps back to the whole "\n\n\t" run it came from.
    assertEquals("\n\n\t", covered(aligned, 11, 12));
  }

  @Test
  void lineBreakPreservingTrimsLeadingAndTrailingBreaks() {
    final LineBreakPreservingWhitespaceCharSequenceNormalizer rung =
        LineBreakPreservingWhitespaceCharSequenceNormalizer.getInstance();
    final String original = "\n\nHello\n\n";
    final AlignedText aligned = rung.normalizeAligned(original);
    assertEquals("Hello", aligned.normalized());
    assertEquals("Hello", covered(aligned, 0, 5));
    assertEquals(original.indexOf("Hello"), aligned.toOriginalSpan(0, 5).getStart());
  }

  @Test
  void lineBreakPreservingComposesInAnAlignedPipeline() {
    assertTrue(LineBreakPreservingWhitespaceCharSequenceNormalizer.getInstance()
        instanceof OffsetAwareNormalizer);
    final String original = "a" + cp(ZERO_WIDTH_SPACE) + "  b\n\nc" + cp(EM_DASH) + "d";
    final OffsetAwareNormalizer pipeline = TextNormalizer.builder()
        .stripInvisible().whitespacePreservingLineBreaks().dashes().buildAligned();

    final AlignedText aligned = pipeline.normalizeAligned(original);
    assertEquals("a b\nc-d", aligned.normalized());
    assertEquals(pipeline.normalize(original).toString(), aligned.normalized());
    // "c-d" at [4, 7) maps back across a deletion, a break-preserving collapse, and a dash fold.
    assertEquals("c" + cp(EM_DASH) + "d", covered(aligned, 4, 7));
  }

  @Test
  void pipelineMapsAnOriginalSpanForwardToTheNormalizedText() {
    final String original = "a" + cp(ZERO_WIDTH_SPACE) + "  b" + cp(EM_DASH) + "c";
    final AlignedText aligned = TextNormalizer.builder()
        .stripInvisible().whitespace().dashes().buildAligned().normalizeAligned(original);
    assertEquals("a b-c", aligned.normalized());
    // 'b' is at original index 4 and normalized index 2; the forward mapping must agree.
    final Span forward = aligned.toNormalizedSpan(4, 5);
    assertEquals(2, forward.getStart());
    assertEquals("b", aligned.normalizedString().substring(forward.getStart(), forward.getEnd()));
  }

  @Test
  void lineBreakPreservingNormalizesCrLfAndUnicodeSeparators() {
    final LineBreakPreservingWhitespaceCharSequenceNormalizer rung =
        LineBreakPreservingWhitespaceCharSequenceNormalizer.getInstance();
    assertEquals("a\nb", rung.normalize("a\r\nb").toString());            // CRLF -> one newline
    assertEquals("a\nb", rung.normalize("a\n\n\n\nb").toString());        // blank lines -> one newline
    assertEquals("x\ny", rung.normalize("x" + cp(0x2028) + "y").toString()); // line separator
    assertEquals("p\nq", rung.normalize("p" + cp(0x2029) + "q").toString()); // paragraph separator
    // A horizontal run still collapses to a space even when mixed with a break-bearing run.
    assertEquals("a b\nc", rung.normalize("a  b \n c").toString());
  }

  @Test
  void whitespaceRungCollapsesAllWhitespaceToEmptyWithAValidSpan() {
    final AlignedText aligned =
        WhitespaceCharSequenceNormalizer.getInstance().normalizeAligned("   ");
    assertEquals("", aligned.normalized());
    // Mapping the empty match must yield a valid empty span rather than throwing.
    final Span empty = aligned.toOriginalSpan(0, 0);
    assertEquals(empty.getStart(), empty.getEnd());
  }

  @Test
  void ellipsisExpansionMapsSpanBackToOriginal() {
    final String original = "a" + cp(0x2026) + "b";
    final AlignedText aligned = EllipsisCharSequenceNormalizer.getInstance()
        .normalizeAligned(original);
    assertEquals("a...b", aligned.normalized());
    // The single ellipsis expanded to three dots, so 'b' moved from index 2 to index 4.
    assertEquals("b", covered(aligned, 4, 5));
    // The whole expansion, and any sub-span of it, maps back to the one source ellipsis.
    assertEquals(cp(0x2026), covered(aligned, 1, 4));
    assertEquals(cp(0x2026), covered(aligned, 2, 3));
  }

  @Test
  void germanUmlautExpansionMapsSpanBackToOriginal() {
    final String original = "Stra" + cp(0x00DF) + "e";   // "Strasse" from the eszett form
    final AlignedText aligned = GermanUmlautCharSequenceNormalizer.getInstance()
        .normalizeAligned(original);
    assertEquals("Strasse", aligned.normalized());
    // The eszett expanded to "ss", so the trailing 'e' moved from index 5 to index 6.
    assertEquals("e", covered(aligned, 6, 7));
    // Both halves of "ss" map back to the single source eszett.
    assertEquals(cp(0x00DF), covered(aligned, 4, 6));
    assertEquals(cp(0x00DF), covered(aligned, 5, 6));
  }

  @Test
  void digitFoldOfSupplementaryDigitMapsSpanBackToOriginal() {
    final String original = "a" + cp(MATH_BOLD_DIGIT_ZERO) + "b";
    final AlignedText aligned = DigitCharSequenceNormalizer.getInstance()
        .normalizeAligned(original);
    assertEquals("a0b", aligned.normalized());
    // The two-unit supplementary digit folded to one ASCII '0', so 'b' moved from 3 to 2.
    assertEquals("b", covered(aligned, 2, 3));
    assertEquals(cp(MATH_BOLD_DIGIT_ZERO), covered(aligned, 1, 2));
  }

  @Test
  void digitFoldOfMixedAsciiAndNonAsciiDigitsMapsSpansBackToOriginal() {
    // ASCII digits take the copy-through path (recorded as equal runs), non-ASCII digits are
    // replaced; the resulting alignment must be indistinguishable from a full replace pass.
    final String original = "4" + cp(0x0665) + "2" + cp(0xFF11); // '4', arabic-indic 5, '2', fullwidth 1
    final AlignedText aligned = DigitCharSequenceNormalizer.getInstance()
        .normalizeAligned(original);
    assertEquals("4521", aligned.normalized());
    // Every position folds one for one, so each normalized digit maps back to its own source.
    assertEquals("4", covered(aligned, 0, 1));
    assertEquals(cp(0x0665), covered(aligned, 1, 2));
    assertEquals("2", covered(aligned, 2, 3));
    assertEquals(cp(0xFF11), covered(aligned, 3, 4));
    // And the reverse direction: the ASCII '2' at original index 2 maps to normalized index 2.
    assertEquals(new Span(2, 3), aligned.toNormalizedSpan(2, 3));
  }

  @Test
  void quoteFoldMapsSpanBackToOriginal() {
    final String original = cp(0x201C) + "hi" + cp(0x201D);   // curly double quotes
    final AlignedText aligned = QuoteCharSequenceNormalizer.getInstance()
        .normalizeAligned(original);
    assertEquals("\"hi\"", aligned.normalized());
    assertEquals("hi", covered(aligned, 1, 3));
    // A one-for-one fold, so the opening quote maps straight back to the curly source quote.
    assertEquals(cp(0x201C), covered(aligned, 0, 1));
  }

  @Test
  void substitutionFoldsComposeInAnAlignedPipeline() {
    final String original = "say " + cp(0x201C) + "hi" + cp(0x201D) + cp(0x2026);
    final OffsetAwareNormalizer pipeline = TextNormalizer.builder()
        .quotes().ellipsis().buildAligned();
    final AlignedText aligned = pipeline.normalizeAligned(original);
    assertEquals("say \"hi\"...", aligned.normalized());
    assertEquals(pipeline.normalize(original).toString(), aligned.normalized());
    // The expanded "..." maps back across the quote fold to the single source ellipsis.
    assertEquals(cp(0x2026), covered(aligned, 8, 11));
    assertEquals("hi", covered(aligned, 5, 7));
  }
}
