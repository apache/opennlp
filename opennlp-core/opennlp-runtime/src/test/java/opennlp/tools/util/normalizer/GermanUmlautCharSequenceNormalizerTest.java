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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GermanUmlautCharSequenceNormalizerTest {

  private static final GermanUmlautCharSequenceNormalizer FOLD =
      GermanUmlautCharSequenceNormalizer.getInstance();

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static String fold(String text) {
    return FOLD.normalize(text).toString();
  }

  @Test
  void testLowercaseUmlauts() {
    assertEquals("Mueller", fold("M" + cp(0x00FC) + "ller")); // Mueller
    assertEquals("schoen", fold("sch" + cp(0x00F6) + "n"));   // schoen
    assertEquals("aerger", fold(cp(0x00E4) + "rger"));        // aerger
  }

  @Test
  void testCapitalUmlauts() {
    assertEquals("Oel", fold(cp(0x00D6) + "l"));              // Oel
    assertEquals("Aerger", fold(cp(0x00C4) + "rger"));        // Aerger
    assertEquals("Ueber", fold(cp(0x00DC) + "ber"));          // Ueber
  }

  @Test
  void testEszett() {
    assertEquals("Strasse", fold("Stra" + cp(0x00DF) + "e")); // Strasse
  }

  @Test
  void testAsciiAndOtherCharactersUnchanged() {
    assertEquals("hello world 123", fold("hello world 123"));
  }

  @Test
  void testMixedSentence() {
    final String input = "M" + cp(0x00FC) + "ller Stra" + cp(0x00DF) + "e"; // Mueller Strasse
    assertEquals("Mueller Strasse", fold(input));
  }
}
