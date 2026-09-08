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

package opennlp.tools.stemmer.light;

import java.text.Normalizer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.StringUtil;

/**
 * Checks the light-stemmer examples in the manual. Behavior changes must update both locations.
 */
class LightStemmerUsageExampleTest {

  /** German light stemming of a plural form. */
  @Test
  void testGermanLightStemmerStemsPlural() {
    final GermanLightStemmer light = new GermanLightStemmer();
    Assertions.assertEquals("haus", light.stem("h\u00E4usern").toString());
  }

  /**
   * Prepares uppercase or decomposed German text before stemming.
   *
   * @param input The input word.
   */
  @ParameterizedTest
  @ValueSource(strings = {"HA\u0308USERN", "H\u00C4USERN", "ha\u0308usern"})
  void testNormalizeAndLowercaseBeforeStemming(String input) {
    final String prepared = StringUtil.toLowerCase(
        Normalizer.normalize(input, Normalizer.Form.NFC));
    Assertions.assertEquals("h\u00E4usern", prepared);
    Assertions.assertEquals("haus", new GermanLightStemmer().stem(prepared).toString());
  }

  /** Spanish minimal stemming reduces a plural ending. */
  @Test
  void testSpanishMinimalStemmerReducesPlural() {
    Assertions.assertEquals("jersey",
        new SpanishMinimalStemmer().stem("jerseis").toString());
  }

  /** Norwegian stemming selects a written standard at construction. */
  @Test
  void testNorwegianLightStemmerSelectsBokmaal() {
    final NorwegianLightStemmer bokmaal =
        new NorwegianLightStemmer(NorwegianVariety.BOKMAAL);
    Assertions.assertEquals("hemmelig", bokmaal.stem("hemmeligheten").toString());
  }
}
