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

import opennlp.tools.util.normalizer.CodePoints.At;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises {@link CodePoints} directly: the decode of {@link CodePoints#at(CharSequence, int)} and
 * {@link CodePoints#before(CharSequence, int)} must agree with
 * {@link Character#codePointAt(CharSequence, int)} and
 * {@link Character#codePointBefore(CharSequence, int)} for BMP characters, paired surrogates, and
 * both kinds of unpaired surrogate.
 */
public class CodePointsTest {

  private static final int GRINNING_FACE = 0x1F600; // a supplementary code point (surrogate pair)
  private static final char HIGH = '\uD83D';         // the high surrogate of GRINNING_FACE
  private static final char LOW = '\uDE00';          // the low surrogate of GRINNING_FACE

  @Test
  void testAtDecodesBmpCharacter() {
    final At cp = CodePoints.at("abc", 1);
    assertEquals('b', cp.codePoint());
    assertEquals(1, cp.charCount());
    assertEquals(2, cp.nextIndex(1));
  }

  @Test
  void testAtDecodesSupplementaryPair() {
    final String text = "a" + new String(Character.toChars(GRINNING_FACE)) + "b";
    final At cp = CodePoints.at(text, 1);
    assertEquals(GRINNING_FACE, cp.codePoint());
    assertEquals(2, cp.charCount());
    assertEquals(3, cp.nextIndex(1));
  }

  @Test
  void testAtDecodesLoneHighSurrogateAsItself() {
    // A high surrogate with no low surrogate after it is its own (unpaired) code point.
    final String text = "a" + HIGH + "b";
    final At cp = CodePoints.at(text, 1);
    assertEquals(HIGH, cp.codePoint());
    assertEquals(1, cp.charCount());
    assertEquals(Character.codePointAt(text, 1), cp.codePoint());
    // A trailing high surrogate at the very end of the text must decode the same way.
    final String trailing = "a" + HIGH;
    final At last = CodePoints.at(trailing, 1);
    assertEquals(HIGH, last.codePoint());
    assertEquals(1, last.charCount());
  }

  @Test
  void testAtDecodesLoneLowSurrogateAsItself() {
    final String text = "a" + LOW + "b";
    final At cp = CodePoints.at(text, 1);
    assertEquals(LOW, cp.codePoint());
    assertEquals(1, cp.charCount());
    assertEquals(Character.codePointAt(text, 1), cp.codePoint());
  }

  @Test
  void testBeforeDecodesBmpCharacter() {
    final At cp = CodePoints.before("abc", 2);
    assertEquals('b', cp.codePoint());
    assertEquals(1, cp.charCount());
    assertEquals(1, cp.previousIndex(2));
  }

  @Test
  void testBeforeDecodesSupplementaryPair() {
    final String text = "a" + new String(Character.toChars(GRINNING_FACE)) + "b";
    final At cp = CodePoints.before(text, 3);
    assertEquals(GRINNING_FACE, cp.codePoint());
    assertEquals(2, cp.charCount());
    assertEquals(1, cp.previousIndex(3));
  }

  @Test
  void testBeforeDecodesLoneHighSurrogateAsItself() {
    final String text = "a" + HIGH + "b";
    final At cp = CodePoints.before(text, 2);
    assertEquals(HIGH, cp.codePoint());
    assertEquals(1, cp.charCount());
    assertEquals(Character.codePointBefore(text, 2), cp.codePoint());
  }

  @Test
  void testBeforeDecodesLoneLowSurrogateAsItself() {
    // A low surrogate with no high surrogate before it is its own (unpaired) code point.
    final String text = "a" + LOW + "b";
    final At cp = CodePoints.before(text, 2);
    assertEquals(LOW, cp.codePoint());
    assertEquals(1, cp.charCount());
    assertEquals(Character.codePointBefore(text, 2), cp.codePoint());
    // A leading low surrogate at the very start of the text must decode the same way.
    final String leading = LOW + "a";
    final At first = CodePoints.before(leading, 1);
    assertEquals(LOW, first.codePoint());
    assertEquals(1, first.charCount());
  }

  @Test
  void testEveryPositionAgreesWithCharacterCodePointAt() {
    // Reference check over a text mixing BMP, a pair, and both unpaired surrogate kinds.
    final String text = "a" + new String(Character.toChars(GRINNING_FACE)) + HIGH + "z" + LOW;
    int i = 0;
    while (i < text.length()) {
      final At cp = CodePoints.at(text, i);
      assertEquals(Character.codePointAt(text, i), cp.codePoint(), "at index " + i);
      assertEquals(Character.charCount(Character.codePointAt(text, i)), cp.charCount());
      i = cp.nextIndex(i);
    }
    int end = text.length();
    while (end > 0) {
      final At cp = CodePoints.before(text, end);
      assertEquals(Character.codePointBefore(text, end), cp.codePoint(), "before index " + end);
      end = cp.previousIndex(end);
    }
  }
}
