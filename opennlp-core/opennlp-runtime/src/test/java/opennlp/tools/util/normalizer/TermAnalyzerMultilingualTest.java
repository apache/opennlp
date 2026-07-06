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

/**
 * Normalization behavior of {@link TermAnalyzer} across a range of scripts and diacritics: German
 * umlauts (the language-specific digraph fold), Romance-language accents (the generic accent fold),
 * Cyrillic case folding, and CJK (Japanese) canonical and compatibility forms. Source strings are
 * built from code points to keep this file ASCII-only.
 */
public class TermAnalyzerMultilingualTest {

  private static String cp(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  private static String normalized(TermAnalyzer analyzer, String text) {
    return analyzer.analyze(text).get(0).normalized();
  }

  @Test
  void germanUmlautsFoldToTheirDigraphs() {
    // The German-specific fold expands the umlauts and eszett (ue/oe/ae/ss), where the generic
    // accent fold would merely strip the diaeresis.
    final TermAnalyzer analyzer = TermAnalyzer.builder().caseFold()
        .transform(Dimension.ACCENT_FOLD, GermanUmlautCharSequenceNormalizer.getInstance()).build();
    assertEquals("gruesse", normalized(analyzer, "GR" + cp(0x00DC) + cp(0x00DF) + "E")); // GRUesseE
    assertEquals("ueber", normalized(analyzer, cp(0x00DC) + "ber"));                     // Ueber
  }

  @Test
  void genericAccentFoldStripsRomanceDiacritics() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().caseFold().accentFold().build();
    assertEquals("eleve", normalized(analyzer, cp(0x00C9) + "l" + cp(0x00E8) + "ve"));   // Eleve (fr)
    assertEquals("nino", normalized(analyzer, "Ni" + cp(0x00F1) + "o"));                  // Nino (es)
    assertEquals("cancion", normalized(analyzer, "Canci" + cp(0x00F3) + "n"));            // Cancion
  }

  @Test
  void cyrillicCaseFolds() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().caseFold().build();
    // MOSKVA -> moskva
    final String moscowUpper = cp(0x041C, 0x041E, 0x0421, 0x041A, 0x0412, 0x0410);
    final String moscowLower = cp(0x043C, 0x043E, 0x0441, 0x043A, 0x0432, 0x0430);
    assertEquals(moscowLower, normalized(analyzer, moscowUpper));
  }

  @Test
  void japaneseFullWidthLetterFoldsUnderNfkc() {
    // NFKC maps full-width compatibility letters to their canonical ASCII form; case fold lowers it.
    final TermAnalyzer analyzer = TermAnalyzer.builder().nfkc().caseFold().build();
    assertEquals("a", normalized(analyzer, cp(0xFF21))); // fullwidth A -> a
  }

  @Test
  void japaneseKanjiPassesThroughNfc() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().nfc().build();
    assertEquals(cp(0x6771), normalized(analyzer, cp(0x6771))); // kanji for "east" unchanged
  }
}
