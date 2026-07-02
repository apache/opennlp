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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.uax29.WordTokenizer;
import opennlp.tools.tokenize.uax29.WordType;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EmoticonToEmojiCharSequenceNormalizerTest {

  private static EmoticonToEmojiCharSequenceNormalizer norm() {
    return EmoticonToEmojiCharSequenceNormalizer.getInstance();
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void foldsDelimitedEmoticons() {
    assertEquals(cp(0x1F642), norm().normalize(":)").toString());
    assertEquals("a " + cp(0x1F641) + " b", norm().normalize("a :( b").toString());
    assertEquals("love <3".replace("<3", cp(0x2764) + cp(0xFE0F)),
        norm().normalize("love <3").toString());
  }

  @Test
  void longestMatchWinsAtAPosition() {
    // ":-)" must fold as one unit; a shortest-first scan would never reach it, and a prefix match
    // of a shorter source would leave stray characters.
    assertEquals("hi " + cp(0x1F642), norm().normalize("hi :-)").toString());
    assertEquals(cp(0x1F494), norm().normalize("</3").toString());
  }

  @Test
  void embeddedSequencesAreLeftAlone() {
    // The reason for the whitespace-delimited guard: emoticon character sequences occur inside
    // ordinary text, where a fold would corrupt it irreversibly.
    assertEquals("see https://example.org now",
        norm().normalize("see https://example.org now").toString());
    assertEquals("nice:)", norm().normalize("nice:)").toString());
    assertEquals("a ratio of 1:3 today", norm().normalize("a ratio of 1:3 today").toString());
  }

  @Test
  void trailingPunctuationBlocksTheFoldByDesign() {
    // Deliberate conservatism: "fun :)." keeps the emoticon because ')' is followed by '.', not
    // whitespace or the boundary. A missed fold costs nothing; loosening the guard is a data-driven
    // follow-up, not a default.
    assertEquals("fun :).", norm().normalize("fun :).").toString());
    assertEquals(":):(", norm().normalize(":):(").toString());
  }

  @Test
  void unicodeWhiteSpaceDelimits() {
    // The guard is Unicode White_Space, not Character.isWhitespace: NBSP delimits too.
    assertEquals("a" + cp(0x00A0) + cp(0x1F642), norm().normalize("a" + cp(0x00A0) + ":)").toString());
  }

  @Test
  void alignedNormalizedMatchesNormalize() {
    final String in = "ok :-) and :'( done";
    assertEquals(norm().normalize(in).toString(), norm().normalizeAligned(in).normalizedString());
  }

  @Test
  void alignmentMapsContractionBackToTheEmoticon() {
    // "a <slightly smiling face> b" from "a :-) b": the surrogate pair (output 2..4) maps back to
    // the three-character emoticon at input 2..5.
    final AlignedText at = norm().normalizeAligned("a :-) b");
    assertEquals("a " + cp(0x1F642) + " b", at.normalizedString());
    assertEquals(new Span(2, 5), at.toOriginalSpan(2, 4));
  }

  @Test
  void composesIntoTheOffsetAwarePipeline() {
    // Collapse the whitespace run (contracting), then fold the emoticon (also contracting): a hit
    // on the emoji in the output maps through both stages back to the original coordinates.
    final OffsetAwareNormalizer pipeline =
        TextNormalizer.builder().whitespace().emoticonToEmoji().buildAligned();
    final AlignedText at = pipeline.normalizeAligned("ok   :-)");
    assertEquals("ok " + cp(0x1F642), at.normalizedString());
    assertEquals(new Span(5, 8), at.toOriginalSpan(3, 5));
  }

  @Test
  void roundTripThroughBothDirectionsConvergesOnCanonicalForms() {
    // :-) folds to the pictograph; folding back yields the canonical short emoticon :), not the
    // original long variant. Convergence, not restoration, is the documented contract.
    final CharSequence folded = norm().normalize("well :-) then");
    assertEquals("well :) then",
        EmojiToEmoticonCharSequenceNormalizer.getInstance().normalize(folded).toString());
  }

  @Test
  void foldingBeforeTokenizationMakesEmoticonsAndEmojiOneClass() {
    // The UAX #29 word tokenizer drops an unfolded emoticon as punctuation but keeps a pictograph
    // as an EMOJI token, so this rung applied before tokenization is exactly what lets the signal
    // survive; this is the settled answer to the epic's WordType question, with no tokenizer change.
    final WordTokenizer tokenizer = new WordTokenizer();
    assertArrayEquals(new String[] {"great"}, tokenizer.tokenize("great :)"));
    final String folded = norm().normalize("great :)").toString();
    assertArrayEquals(new String[] {"great", cp(0x1F642)}, tokenizer.tokenize(folded));
    final List<WordType> types = new ArrayList<>();
    tokenizer.tokenize(folded, (start, end, type) -> types.add(type));
    assertEquals(List.of(WordType.ALPHANUMERIC, WordType.EMOJI), types);
  }
}
