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
package opennlp.tools.sentdetect;

import java.io.IOException;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.dictionary.Dictionary;

/**
 * Characterization tests for the whitespace check in
 * {@link SentenceDetectorME#isAcceptableBreak(CharSequence, int, int)}: a candidate break is
 * rejected when a known abbreviation overlaps it and the character directly before the
 * abbreviation is a whitespace (or an apostrophe or an opening round bracket). These tests pin
 * which characters count as whitespace for that guard at the code points where the JVM
 * predicates and the Unicode {@code White_Space} property disagree.
 */
public class SentenceDetectorMEAbbreviationBreakTest extends AbstractSentenceDetectorTest {

  private static SentenceDetectorME detector;

  @BeforeAll
  static void prepareResources() throws IOException {
    Dictionary abb = loadAbbDictionary(Locale.ENGLISH);
    detector = new SentenceDetectorME(
        train(new SentenceDetectorFactory("eng", true, abb, null), Locale.ENGLISH));
  }

  private static boolean acceptable(String separator) {
    // "Mr." is in the abbreviation dictionary; the candidate break is its trailing period.
    String text = "x" + separator + "Mr. Smith";
    int candidate = text.indexOf('.');
    return detector.isAcceptableBreak(text, 0, candidate);
  }

  @Test
  void spaceBeforeAbbreviationRejectsTheBreak() {
    Assertions.assertFalse(acceptable(" "));
  }

  @Test
  void letterBeforeAbbreviationAllowsTheBreak() {
    Assertions.assertTrue(acceptable("x"));
  }

  @Test
  void noBreakSpaceBeforeAbbreviationRejectsTheBreak() {
    // NBSP, figure space and narrow no-break space carry the Unicode White_Space property,
    // so since 3.0 the abbreviation protects the break behind them (the previously used
    // Character.isWhitespace excludes the Zs no-break spaces).
    Assertions.assertFalse(acceptable(cp(0x00A0)));
    Assertions.assertFalse(acceptable(cp(0x2007)));
    Assertions.assertFalse(acceptable(cp(0x202F)));
  }

  @Test
  void lineAndParagraphSeparatorsBeforeAbbreviationRejectTheBreak() {
    // U+2028 and U+2029 are whitespace under both Character.isWhitespace and White_Space.
    Assertions.assertFalse(acceptable(cp(0x2028)));
    Assertions.assertFalse(acceptable(cp(0x2029)));
  }

  @Test
  void informationSeparatorsBeforeAbbreviationAllowTheBreak() {
    // The U+001C..U+001F information separators are not Unicode White_Space, so since 3.0
    // they no longer make the abbreviation protect the break (Character.isWhitespace
    // treated them as whitespace).
    for (int cp = 0x001C; cp <= 0x001F; cp++) {
      Assertions.assertTrue(acceptable(cp(cp)), "U+" + Integer.toHexString(cp));
    }
  }

  @Test
  void nextLineControlBeforeAbbreviationRejectsTheBreak() {
    // U+0085 NEL carries the Unicode White_Space property, so since 3.0 the abbreviation
    // protects the break behind it (Character.isWhitespace excludes NEL).
    Assertions.assertFalse(acceptable(cp(0x0085)));
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }
}
