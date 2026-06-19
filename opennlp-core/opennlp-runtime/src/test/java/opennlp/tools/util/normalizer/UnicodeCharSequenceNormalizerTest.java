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
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the {@link CharClass}-backed and Unicode-normalization {@link CharSequenceNormalizer}
 * implementations, and their composition through {@link AggregateCharSequenceNormalizer}.
 */
public class UnicodeCharSequenceNormalizerTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void testWhitespaceCollapsesUnicodeRunsAndTrims() {
    final String input = "  a" + cp(0x00A0) + cp(0x00A0) + "b" + cp(0x3000) + "  ";
    assertEquals("a b",
        WhitespaceCharSequenceNormalizer.getInstance().normalize(input).toString());
  }

  @Test
  void testDashFoldsUnicodeDashesButNotMathMinus() {
    assertEquals("a-b",
        DashCharSequenceNormalizer.getInstance().normalize("a" + cp(0x2014) + "b").toString());
    final String math = "5" + cp(0x2212) + "3";
    assertEquals(math, DashCharSequenceNormalizer.getInstance().normalize(math).toString());
  }

  @Test
  void testNfcComposesDecomposedSequences() {
    // "e" + combining acute accent -> the precomposed letter U+00E9.
    assertEquals(cp(0x00E9),
        NfcCharSequenceNormalizer.getInstance().normalize("e" + cp(0x0301)).toString());
  }

  @Test
  void testNfkcFoldsCompatibilityForms() {
    assertEquals("A",
        NfkcCharSequenceNormalizer.getInstance().normalize(cp(0xFF21)).toString());
    assertEquals("fi",
        NfkcCharSequenceNormalizer.getInstance().normalize(cp(0xFB01)).toString());
  }

  @Test
  void testCaseFoldLowercasesIndependentOfLocale() {
    assertEquals("abc", CaseFoldCharSequenceNormalizer.getInstance().normalize("ABC").toString());
    // Accents are preserved; only case changes (CAFE-acute -> cafe-acute).
    assertEquals("caf" + cp(0x00E9),
        CaseFoldCharSequenceNormalizer.getInstance().normalize("CAF" + cp(0x00C9)).toString());
  }

  @Test
  void testInstancesAreSharedSingletons() {
    assertSame(WhitespaceCharSequenceNormalizer.getInstance(),
        WhitespaceCharSequenceNormalizer.getInstance());
    assertSame(DashCharSequenceNormalizer.getInstance(),
        DashCharSequenceNormalizer.getInstance());
    assertSame(NfcCharSequenceNormalizer.getInstance(),
        NfcCharSequenceNormalizer.getInstance());
    assertSame(NfkcCharSequenceNormalizer.getInstance(),
        NfkcCharSequenceNormalizer.getInstance());
    assertSame(CaseFoldCharSequenceNormalizer.getInstance(),
        CaseFoldCharSequenceNormalizer.getInstance());
  }

  @Test
  void testComposeIntoAUnifiedPipeline() {
    // NFC, then Unicode whitespace, then dash folding, applied in order through the aggregate.
    final CharSequenceNormalizer pipeline = new AggregateCharSequenceNormalizer(
        NfcCharSequenceNormalizer.getInstance(),
        WhitespaceCharSequenceNormalizer.getInstance(),
        DashCharSequenceNormalizer.getInstance());

    final String input = cp(0x00A0) + "a" + cp(0x2014) + "b" + cp(0x00A0);
    assertEquals("a-b", pipeline.normalize(input).toString());
  }
}
