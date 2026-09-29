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


package opennlp.tools.stemmer.hunspell;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the parsing of the conversion tables, apart from the stemmer.
 */
class HunspellConversionTest {

  /**
   * Verifies that an underscore inside a pattern is a space in each table that
   * {@link HunspellConversion} parses, after the anchors are removed: the REP entry
   * {@code c_oat coat} offers {@code coat} for the text {@code c oat}.
   *
   * @param pattern The REP pattern as written in the affix file.
   * @throws IOException Thrown if the table fails to parse.
   */
  @ParameterizedTest
  @ValueSource(strings = {"c_oat", "^c_oat", "c_oat$", "^c_oat$"})
  void testReplacementPatternUnderscoreIsSpace(String pattern) throws IOException {
    final HunspellConversion table = HunspellConversion.parse(
        new String[][] {{"REP", "1"}, {"REP", pattern, "coat"}}, HunspellDictionary.REPLACEMENT_TAG);
    Assertions.assertTrue(table.anyReplacement("c oat", "coat"::equals));
    Assertions.assertFalse(table.anyReplacement("c_oat", "coat"::equals));
  }

  /**
   * Rejects a conversion pattern that is anchors only, since it has no text to match.
   *
   * @param directive The table.
   * @param pattern The pattern as written.
   */
  @ParameterizedTest
  @CsvSource({"ICONV, _", "ICONV, __", "OCONV, _", "REP, ^", "REP, ^$"})
  void testAnchorsOnlyPatternIsRejected(String directive, String pattern) {
    final IOException error = Assertions.assertThrows(IOException.class,
        () -> HunspellConversion.parse(
            new String[][] {{directive, "1"}, {directive, pattern, "x"}}, directive));
    Assertions.assertTrue(error.getMessage().contains("empty " + directive + " pattern"),
        error.getMessage());
  }
}
