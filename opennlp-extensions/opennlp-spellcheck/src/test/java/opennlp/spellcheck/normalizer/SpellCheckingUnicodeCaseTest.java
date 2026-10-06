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

package opennlp.spellcheck.normalizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import opennlp.spellcheck.symspell.SymSpell;
import opennlp.tools.util.StringUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Checks spelling corrections for Unicode letters, including supplementary code points. */
class SpellCheckingUnicodeCaseTest {

  private static final int FREQUENCY = 1000;
  private static final String PUNCTUATION_PREFIX = "\"";
  private static final String PUNCTUATION_SUFFIX = "!\"";
  private static final String WHITESPACE_PREFIX = "\t";
  private static final String WHITESPACE_SUFFIX = "\u00a0";

  /**
   * Corrects a known misspelling using the source token's capitalization.
   *
   * @param dictionary The dictionary entry.
   * @param input The misspelled token.
   * @param expected The corrected token.
   */
  @ParameterizedTest
  @CsvSource({
      "world, wrold, world",
      "world, Wrold, World",
      "world, WROLD, WORLD",
      "𐐨𐐩𐐪𐐫, 𐐨𐐩𐐪𐐭, 𐐨𐐩𐐪𐐫",
      "𐐨𐐩𐐪𐐫, 𐐀𐐩𐐪𐐭, 𐐀𐐩𐐪𐐫",
      "𐐨𐐩𐐪𐐫, 𐐀𐐁𐐂𐐅, 𐐀𐐁𐐂𐐃",
      "𐐨world, 𐐨WROLD, 𐐨world",
      "𐐨world, 𐐀WROLD, 𐐀WORLD",
      "a𐐩cda, A𐐩CDE, A𐐩cda",
      "a𐐩cda, A𐐁CDE, A𐐁CDA",
      "word𐐨, WRLD𐐨, Word𐐨",
      "word𐐨, WRLD𐐀, WORD𐐀",
      "𠮷𠮸𠮹𠮻, 𠮷𠮸𠮹𠮺, 𠮷𠮸𠮹𠮻",
      "ab, A, Ab",
      "ab, 𐐀, Ab",
      "ab, 𐐨, ab"
  })
  void testCasing(String dictionary, String input, String expected) {
    final SymSpell engine = new SymSpell();
    engine.add(dictionary, FREQUENCY);
    final var suggestions = engine.lookup(StringUtil.toLowerCase(input));
    assertFalse(suggestions.isEmpty(),
        "The dictionary must provide a correction for this input");
    assertEquals(dictionary, suggestions.getFirst().term());
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(engine)
        .minTokenLength(1).build();
    assertEquals(expected, normalizer.normalize(input).toString());
    assertEquals(PUNCTUATION_PREFIX + expected + PUNCTUATION_SUFFIX,
        normalizer.normalize(PUNCTUATION_PREFIX + input + PUNCTUATION_SUFFIX).toString());
    assertEquals(WHITESPACE_PREFIX + expected + WHITESPACE_SUFFIX,
        normalizer.normalize(WHITESPACE_PREFIX + input + WHITESPACE_SUFFIX).toString());
    final StringBuilder mutable = new StringBuilder(input);
    assertEquals(expected, normalizer.normalize(mutable).toString());
    assertEquals(input, mutable.toString());
  }

  /**
   * Preserves known words and tokens excluded by the normalizer's guards.
   *
   * @param dictionary The dictionary entry.
   * @param input The token to preserve.
   */
  @ParameterizedTest
  @CsvSource({
      "𐐨𐐩𐐪𐐫, 𐐨𐐩𐐪𐐫",
      "𐐨𐐩𐐪𐐫, 𐐀𐐁𐐂𐐃",
      "𐐨𐐩𐐪𐐫, 𐐀𐐩𐐂𐐫",
      "word𐐨, WORD𐐨",
      "12346, 12345",
      "😀😀😀😁, 😀😀😀😀",
      "www.world.com, www.wrold.com"
  })
  void testUnchangedTokens(String dictionary, String input) {
    final SymSpell engine = new SymSpell();
    engine.add(dictionary, FREQUENCY);
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(engine)
        .minTokenLength(1).build();
    assertEquals(input, normalizer.normalize(input).toString());
  }

  /** Runs the manual example with the default normalizer settings. */
  @Test
  void testManualExample() {
    final SymSpell engine = new SymSpell();
    engine.add("\uD801\uDC28word", FREQUENCY);
    final var normalizer = new SpellCheckingCharSequenceNormalizer(engine);
    assertEquals("\uD801\uDC28word", normalizer.normalize("\uD801\uDC28WROLD").toString());
  }
}
