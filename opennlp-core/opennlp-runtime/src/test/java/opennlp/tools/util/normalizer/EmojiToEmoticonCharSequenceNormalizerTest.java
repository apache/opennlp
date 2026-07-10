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

public class EmojiToEmoticonCharSequenceNormalizerTest {

  private static EmojiToEmoticonCharSequenceNormalizer norm() {
    return EmojiToEmoticonCharSequenceNormalizer.getInstance();
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void foldsSupplementaryEmojiToEmoticon() {
    assertEquals(":)", norm().normalize(cp(0x1F642)).toString()); // SLIGHTLY SMILING FACE
    assertEquals(":D", norm().normalize(cp(0x1F600)).toString()); // GRINNING FACE
    assertEquals(":'(", norm().normalize(cp(0x1F622)).toString()); // CRYING FACE
  }

  @Test
  void foldsBmpPictographToEmoticon() {
    // U+263A is a one-character BMP pictograph expanding to a two-character emoticon, the case the
    // deprecated surrogate-block normalizer never matched at all.
    assertEquals(":)", norm().normalize(cp(0x263A)).toString());
    assertEquals("<3", norm().normalize(cp(0x2764)).toString());
  }

  @Test
  void emojiPresentationSequenceFoldsAsOneUnit() {
    // The base pictograph plus U+FE0F VARIATION SELECTOR-16 must fold together; a bare-pictograph
    // match that left the selector behind would emit a dangling invisible character.
    assertEquals("<3", norm().normalize(cp(0x2764) + cp(0xFE0F)).toString());
    assertEquals(":)", norm().normalize(cp(0x263A) + cp(0xFE0F)).toString());
  }

  @Test
  void manyToOneVariantsConvergeOnTheCanonicalEmoticon() {
    assertEquals(":D:D", norm().normalize(cp(0x1F603) + cp(0x1F604)).toString());
    assertEquals("<3<3", norm().normalize(cp(0x1F499) + cp(0x1F9E1)).toString());
  }

  @Test
  void unmappedContentPassesThrough() {
    final String rocket = cp(0x1F680); // no emoticon mapping
    assertEquals("go " + rocket + " now", norm().normalize("go " + rocket + " now").toString());
    assertEquals("plain text", norm().normalize("plain text").toString());
    assertEquals("", norm().normalize("").toString());
  }

  @Test
  void foldsInsideRunningTextWithoutBoundaries() {
    // Unlike the emoticon direction, the emoji direction needs no delimiter guard: a pictograph
    // glued to a word is still unambiguous.
    assertEquals("great:D news", norm().normalize("great" + cp(0x1F600) + " news").toString());
  }

  @Test
  void alignedNormalizedMatchesNormalize() {
    final String in = "a " + cp(0x1F622) + " b " + cp(0x2764) + cp(0xFE0F);
    assertEquals(norm().normalize(in).toString(), norm().normalizeAligned(in).normalizedString());
  }

  @Test
  void alignmentMapsExpansionBackToThePictograph() {
    // "x :'( y" from "x <crying face> y": the three-character emoticon (output 2..5) maps back to
    // the two-unit surrogate pair at input 2..4.
    final AlignedText at = norm().normalizeAligned("x " + cp(0x1F622) + " y");
    assertEquals("x :'( y", at.normalizedString());
    assertEquals(new Span(2, 4), at.toOriginalSpan(2, 5));
  }

  @Test
  void adjacentFoldsEachMapToTheirOwnSource() {
    // Two pictographs back to back must not blur into one block; each emoticon maps to its own.
    final AlignedText at = norm().normalizeAligned(cp(0x1F642) + cp(0x1F600));
    assertEquals(":):D", at.normalizedString());
    assertEquals(new Span(0, 2), at.toOriginalSpan(0, 2));
    assertEquals(new Span(2, 4), at.toOriginalSpan(2, 4));
  }

  @Test
  void composesIntoTheOffsetAwarePipeline() {
    // Collapse the double space (contracting), then fold the pictograph (expanding): a hit in the
    // output maps through both stages back to original document coordinates.
    final OffsetAwareNormalizer pipeline =
        TextNormalizer.builder().whitespace().emojiToEmoticon().buildAligned();
    final AlignedText at = pipeline.normalizeAligned("hi  " + cp(0x1F642));
    assertEquals("hi :)", at.normalizedString());
    assertEquals(new Span(4, 6), at.toOriginalSpan(3, 5)); // ":)" maps back to the pictograph
  }

  @Test
  void zwjSequenceIsNotCorrupted() {
    // HEART ON FIRE is HEAVY BLACK HEART + FE0F + ZWJ + FIRE: the embedded heart must not fold,
    // which would emit "<3" and leave a dangling joiner in front of the flame.
    final String heartOnFire = cp(0x2764) + cp(0xFE0F) + cp(0x200D) + cp(0x1F525);
    assertEquals(heartOnFire, norm().normalize(heartOnFire).toString());

    // COUPLE WITH HEART: the heart sits between two joiners; neither side may fold.
    final String couple = cp(0x1F469) + cp(0x200D) + cp(0x2764) + cp(0xFE0F) + cp(0x200D)
        + cp(0x1F48B) + cp(0x200D) + cp(0x1F468);
    assertEquals(couple, norm().normalize(couple).toString());
  }

  @Test
  void zwjSequenceNextToAFoldableEmojiOnlyFoldsTheStandaloneOne() {
    final String heartOnFire = cp(0x2764) + cp(0xFE0F) + cp(0x200D) + cp(0x1F525);
    final String input = cp(0x1F642) + " " + heartOnFire;
    assertEquals(":) " + heartOnFire, norm().normalize(input).toString());
  }

  @Test
  void textPresentationSelectorSuppressesTheFold() {
    // U+FE0E explicitly requests text presentation; folding would both misread the author's
    // intent and leave the selector dangling.
    final String textSmiley = cp(0x263A) + cp(0xFE0E);
    assertEquals(textSmiley, norm().normalize(textSmiley).toString());
  }

  @Test
  void trailingEmojiPresentationSelectorIsAbsorbedForEveryMappedPictograph() {
    // 1F642 has no explicit FE0F row; the selector must still fold with it, not dangle.
    assertEquals(":)", norm().normalize(cp(0x1F642) + cp(0xFE0F)).toString());
  }

  @Test
  void absorbedSelectorMapsBackToTheWholeSequence() {
    final AlignedText at = norm().normalizeAligned(cp(0x1F642) + cp(0xFE0F) + "!");
    assertEquals(":)!", at.normalizedString());
    // ":)" covers the pictograph plus the absorbed selector: three UTF-16 units.
    assertEquals(new Span(0, 3), at.toOriginalSpan(0, 2));
    assertEquals(at.normalizedString(),
        norm().normalize(cp(0x1F642) + cp(0xFE0F) + "!").toString());
  }
}
