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
package opennlp.tools.tokenize.uax29;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WordSegmenterTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static List<String> words(String text) {
    final List<String> out = new ArrayList<>();
    for (final Span span : WordSegmenter.segments(text)) {
      out.add(span.getCoveredText(text).toString());
    }
    return out;
  }

  @Test
  void testEnglishSentenceKeepsWordsAndSeparators() {
    assertEquals(List.of("The", " ", "quick", " ", "fox"), words("The quick fox"));
  }

  @Test
  void testContractionStaysOneWord() {
    assertEquals(List.of("don't"), words("don't"));        // WB6/WB7 over the apostrophe
  }

  @Test
  void testDecimalNumberStaysOneToken() {
    assertEquals(List.of("3.14"), words("3.14"));           // WB11/WB12
  }

  @Test
  void testAcronymWithInternalDotsStaysOneToken() {
    assertEquals(List.of("U.S.A"), words("U.S.A"));         // WB6/WB7
  }

  @Test
  void testLettersAndDigitsJoin() {
    assertEquals(List.of("a1b2"), words("a1b2"));           // WB9/WB10
  }

  @Test
  void testWhitespaceRunIsASingleSegment() {
    assertEquals(List.of("a", "  ", "b"), words("a  b"));   // WB3d
  }

  @Test
  void testNewlineBreaksOnBothSides() {
    assertEquals(List.of("a", "\n", "b"), words("a\nb"));   // WB3a/WB3b
  }

  @Test
  void testCarriageReturnLineFeedStayTogether() {
    assertEquals(List.of("a", "\r\n", "b"), words("a\r\nb")); // WB3
  }

  @Test
  void testIdeographsSplitPerCharacter() {
    assertEquals(List.of(cp(0x4E2D), cp(0x6587)), words(cp(0x4E2D) + cp(0x6587)));
  }

  @Test
  void testEmojiZwjSequenceStaysTogether() {
    final String family = cp(0x1F468) + cp(0x200D) + cp(0x1F469); // man + ZWJ + woman
    assertEquals(List.of(family), words(family));          // WB3c
  }

  @Test
  void testRegionalIndicatorFlagIsOneToken() {
    final String flag = cp(0x1F1FA) + cp(0x1F1F8);          // regional indicators U + S
    assertEquals(List.of(flag), words(flag));              // WB15/WB16
  }

  @Test
  void testEmptyText() {
    assertEquals(List.of(), words(""));
    assertArrayEquals(new int[] {0}, WordSegmenter.boundaries(""));
  }

  @Test
  void testBoundariesIncludeStartAndEnd() {
    assertArrayEquals(new int[] {0, 2, 3, 5}, WordSegmenter.boundaries("ab cd"));
  }
}
