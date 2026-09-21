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

package opennlp.tools.util.featuregen;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

public class FeatureGeneratorUtilTest {

  @Test
  void test() {
    // digits
    Assertions.assertEquals("2d", FeatureGeneratorUtil.tokenFeature("12"));
    Assertions.assertEquals("4d", FeatureGeneratorUtil.tokenFeature("1234"));
    Assertions.assertEquals("an", FeatureGeneratorUtil.tokenFeature("abcd234"));
    Assertions.assertEquals("dd", FeatureGeneratorUtil.tokenFeature("1234-56"));
    Assertions.assertEquals("ds", FeatureGeneratorUtil.tokenFeature("4/6/2017"));
    Assertions.assertEquals("dc", FeatureGeneratorUtil.tokenFeature("1,234,567"));
    Assertions.assertEquals("dp", FeatureGeneratorUtil.tokenFeature("12.34567"));
    Assertions.assertEquals("num", FeatureGeneratorUtil.tokenFeature("123(456)7890"));

    // letters
    Assertions.assertEquals("lc", FeatureGeneratorUtil.tokenFeature("opennlp"));
    Assertions.assertEquals("sc", FeatureGeneratorUtil.tokenFeature("O"));
    Assertions.assertEquals("ac", FeatureGeneratorUtil.tokenFeature("OPENNLP"));
    Assertions.assertEquals("cp", FeatureGeneratorUtil.tokenFeature("A."));
    Assertions.assertEquals("ic", FeatureGeneratorUtil.tokenFeature("Mike"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("somethingStupid"));

    // symbols
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature(","));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("."));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("?"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("!"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("#"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("%"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("&"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("§"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("^"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("°"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("("));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature(")"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("/"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("\\"));
  }

  @Test
  void testGerman() {
    Assertions.assertEquals("ic", FeatureGeneratorUtil.tokenFeature("Änne"));
    Assertions.assertEquals("ic", FeatureGeneratorUtil.tokenFeature("Özlem"));
    Assertions.assertEquals("ic", FeatureGeneratorUtil.tokenFeature("Ümit"));
    Assertions.assertEquals("cp", FeatureGeneratorUtil.tokenFeature("Ä."));
    Assertions.assertEquals("cp", FeatureGeneratorUtil.tokenFeature("Ö."));
    Assertions.assertEquals("cp", FeatureGeneratorUtil.tokenFeature("Ü."));
    Assertions.assertEquals("sc", FeatureGeneratorUtil.tokenFeature("Ü"));
  }

  @ParameterizedTest
  @CsvSource({"A., cp", "Z., cp", "Ä., cp", "Ö., cp", "Ü., cp",
      // capitals of other scripts
      "É., cp", "Ω., cp", "Я., cp",
      // lower case initial, other second character, longer tokens
      "a., other", "é., other", "'A,', ic", "Ab., ic", "AB., ic"})
  void testCapPeriod(String token, String feature) {
    Assertions.assertEquals(feature, FeatureGeneratorUtil.tokenFeature(token));
  }

  @Test
  void testCapPeriodWithSupplementaryCapital() {
    // U+10400 DESERET CAPITAL LETTER LONG I is one code point of two chars
    final String deseretCapital = new String(Character.toChars(0x10400));
    Assertions.assertEquals("cp", FeatureGeneratorUtil.tokenFeature(deseretCapital + "."));
    final String deseretSmall = new String(Character.toChars(0x10428));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature(deseretSmall + "."));
  }

  private static Stream<Arguments> capPeriodLookalikes() {
    return Stream.of(
        Arguments.of("A.\n", "ic"), Arguments.of("A.\r", "ic"), Arguments.of("A.\r\n", "ic"),
        Arguments.of("A.\u0085", "ic"), Arguments.of("A.\u2028", "ic"),
        Arguments.of("A.\u2029", "ic"), Arguments.of("Ä.\n", "ic"), Arguments.of("A.\n\n", "ic"),
        Arguments.of("A. ", "ic"), Arguments.of("A.\nX", "ic"), Arguments.of("\nA.", "other"),
        Arguments.of(" A.", "other"));
  }

  @ParameterizedTest
  @MethodSource("capPeriodLookalikes")
  void testCapPeriodIsExactlyTwoCharacters(String token, String feature) {
    Assertions.assertEquals(feature, FeatureGeneratorUtil.tokenFeature(token));
  }

  @Test
  void testJapanese() {
    // Hiragana
    Assertions.assertEquals("jah", FeatureGeneratorUtil.tokenFeature("そういえば"));
    Assertions.assertEquals("jah", FeatureGeneratorUtil.tokenFeature("おーぷん・そ〜す・そふとうぇあ"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("あぱっち・そふとうぇあ財団"));

    // Katakana
    Assertions.assertEquals("jak", FeatureGeneratorUtil.tokenFeature("ジャパン"));
    Assertions.assertEquals("jak", FeatureGeneratorUtil.tokenFeature("オープン・ソ〜ス・ソフトウェア"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("アパッチ・ソフトウェア財団"));
  }
}
