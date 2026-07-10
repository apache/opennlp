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

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TextNormalizerTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void testRungsApplyInOrder() {
    final CharSequenceNormalizer n = TextNormalizer.builder().caseFold().accentFold().build();
    assertEquals("cafe", n.normalize("CAF" + cp(0x00C9)).toString()); // CAFE-acute -> cafe
  }

  @Test
  void testEmptyBuilderIsIdentity() {
    assertEquals("UnChanged", TextNormalizer.builder().build().normalize("UnChanged").toString());
  }

  @Test
  void testWhitespaceAndFoldChain() {
    final CharSequenceNormalizer n = TextNormalizer.builder()
        .nfc().whitespace().caseFold().accentFold().build();
    assertEquals("cafe", n.normalize("  CAF" + cp(0x00C9) + "  ").toString());
  }

  @Test
  void testWithCustomNormalizer() {
    final CharSequenceNormalizer up = s -> s.toString().toUpperCase(Locale.ROOT);
    assertEquals("AB", TextNormalizer.builder().with(up).build().normalize("ab").toString());
  }

  @Test
  void testWithRejectsNull() {
    assertThrows(NullPointerException.class, () -> TextNormalizer.builder().with(null));
  }

  @Test
  void testDefaultChainCleansMessyInput() {
    // BOM + curly-quoted, mixed-case, accented text -> stripped, ASCII-quoted, folded.
    final String input = cp(0xFEFF) + cp(0x201C) + "Caf" + cp(0x00C9) + cp(0x201D);
    assertEquals("\"cafe\"", TextNormalizer.defaultChain().normalize(input).toString());
  }

  @Test
  void testEveryRungIsInvokable() {
    final CharSequenceNormalizer n = TextNormalizer.builder()
        .stripInvisible().nfc().nfkc().whitespace().quotes().dashes().digits().ellipsis().bullets()
        .fullCaseFold().accentFold().build();
    // BOM stripped, Arabic-Indic 1 -> 1, full case + accent folded (sharp s expands to "ss").
    final String input = cp(0xFEFF) + "CAF" + cp(0x00C9) + cp(0x00DF) + " " + cp(0x0661);
    assertEquals("cafess 1", n.normalize(input).toString());
  }

  @Test
  void testCaseFoldRungIsInvokable() {
    final CharSequenceNormalizer n = TextNormalizer.builder().caseFold().build();
    assertEquals("cafe", n.normalize("CAFE").toString());
  }

  @Test
  void testCaseFoldWithFullCaseFoldIsPermittedButRedundant() {
    // This builder composes rungs freely and does not enforce the exclusion the TermAnalyzer
    // layer does; the combination is documented as redundant, and this pins that it changes
    // nothing over full case folding alone.
    final String input = "STRA" + cp(0x00DF) + "E";
    final CharSequenceNormalizer redundant = TextNormalizer.builder()
        .caseFold().fullCaseFold().build();
    final CharSequenceNormalizer fullOnly = TextNormalizer.builder().fullCaseFold().build();
    assertEquals(fullOnly.normalize(input).toString(), redundant.normalize(input).toString());
    assertEquals("strasse", redundant.normalize(input).toString());
  }
}
