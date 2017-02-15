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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the @{@link UnicodeCharSequenceNormalizer} based on
 * https://github.com/shuyo/language-detection
 */
public class UnicodeCharSequenceNormalizerTest {

  public UnicodeCharSequenceNormalizer normalizer = UnicodeCharSequenceNormalizer.getInstance();

  @Test
  public void getMessage() throws Exception {
    Assert.assertEquals("\u4F7C\u6934", UnicodeCharSequenceNormalizer.getMessage("NGram.KANJI_1_0"));
    Assert.assertEquals("!blah!", UnicodeCharSequenceNormalizer.getMessage("blah"));
  }

  @Test
  public final void testNormalize() {
    Assert.assertEquals("a b c d á é í ó ú ã",
        normalizer.normalize("a b c d á é í ó ú ã"));

  }

  /**
   * Test method for {@link UnicodeCharSequenceNormalizer#normalize(char)} with Latin characters
   */
  @Test
  public final void testNormalizeWithLatin() {
    Assert.assertEquals(' ', normalizer.normalize('\u0000'));
    Assert.assertEquals(' ', normalizer.normalize('\u0020'));
    Assert.assertEquals(' ', normalizer.normalize('\u0030'));
    Assert.assertEquals(' ', normalizer.normalize('\u0040'));
    Assert.assertEquals('\u0041', normalizer.normalize('\u0041'));
    Assert.assertEquals('\u005a', normalizer.normalize('\u005a'));
    Assert.assertEquals(' ', normalizer.normalize('\u005b'));
    Assert.assertEquals(' ', normalizer.normalize('\u0060'));
    Assert.assertEquals('\u0061', normalizer.normalize('\u0061'));
    Assert.assertEquals('\u007a', normalizer.normalize('\u007a'));
    Assert.assertEquals(' ', normalizer.normalize('\u007b'));
    Assert.assertEquals(' ', normalizer.normalize('\u007f'));
    Assert.assertEquals('\u0080', normalizer.normalize('\u0080'));
    Assert.assertEquals(' ', normalizer.normalize('\u00a0'));
    Assert.assertEquals('\u00a1', normalizer.normalize('\u00a1'));
    // LATIN_EXTENDED_ADDITIONAL
    Assert.assertEquals('\u1ec3', normalizer.normalize('\u1EA0'));
    Assert.assertEquals('\u1ec3', normalizer.normalize('\u1EA1'));

    Assert.assertEquals(' ', normalizer.normalize('\u2012'));
    // Arabic
    Assert.assertEquals('\u064a', normalizer.normalize('\u06cc'));
    // Hiragana
    Assert.assertEquals('\u3042', normalizer.normalize('\u3041'));
    // Katakana
    Assert.assertEquals('\u30a2', normalizer.normalize('\u30A1'));
    // Bopomofo
    Assert.assertEquals('\u3105', normalizer.normalize('\u31A0'));
    // Bopomofo Ex
    Assert.assertEquals('\u3105', normalizer.normalize('\u3106'));
    //HANGUL_SYLLABLES
    Assert.assertEquals('\uac00', normalizer.normalize('\uAC01'));
  }

  /**
   * Test method for {@link UnicodeCharSequenceNormalizer#normalize(char)} with CJK Kanji characters
   */
  @Test
  public final void testNormalizeWithCJKKanji() {
    Assert.assertEquals('\u4E00', normalizer.normalize('\u4E00'));
    Assert.assertEquals('\u4E01', normalizer.normalize('\u4E01'));
    Assert.assertEquals('\u4E02', normalizer.normalize('\u4E02'));
    Assert.assertEquals('\u4E01', normalizer.normalize('\u4E03'));
    Assert.assertEquals('\u4E04', normalizer.normalize('\u4E04'));
    Assert.assertEquals('\u4E05', normalizer.normalize('\u4E05'));
    Assert.assertEquals('\u4E06', normalizer.normalize('\u4E06'));
    Assert.assertEquals('\u4E07', normalizer.normalize('\u4E07'));
    Assert.assertEquals('\u4E08', normalizer.normalize('\u4E08'));
    Assert.assertEquals('\u4E09', normalizer.normalize('\u4E09'));
    Assert.assertEquals('\u4E10', normalizer.normalize('\u4E10'));
    Assert.assertEquals('\u4E11', normalizer.normalize('\u4E11'));
    Assert.assertEquals('\u4E12', normalizer.normalize('\u4E12'));
    Assert.assertEquals('\u4E13', normalizer.normalize('\u4E13'));
    Assert.assertEquals('\u4E14', normalizer.normalize('\u4E14'));
    Assert.assertEquals('\u4E15', normalizer.normalize('\u4E15'));
    Assert.assertEquals('\u4E1e', normalizer.normalize('\u4E1e'));
    Assert.assertEquals('\u4E1f', normalizer.normalize('\u4E1f'));
    Assert.assertEquals('\u4E20', normalizer.normalize('\u4E20'));
    Assert.assertEquals('\u4E21', normalizer.normalize('\u4E21'));
    Assert.assertEquals('\u4E22', normalizer.normalize('\u4E22'));
    Assert.assertEquals('\u4E23', normalizer.normalize('\u4E23'));
    Assert.assertEquals('\u4E13', normalizer.normalize('\u4E24'));
    Assert.assertEquals('\u4E13', normalizer.normalize('\u4E25'));
    Assert.assertEquals('\u4E30', normalizer.normalize('\u4E30'));
  }


  /**
   * Test method for {@link UnicodeCharSequenceNormalizer#normalize(char)} for Romanian characters
   */
  @Test
  public final void testNormalizeForRomanian() {
    Assert.assertEquals('\u015f', normalizer.normalize('\u015f'));
    Assert.assertEquals('\u0163', normalizer.normalize('\u0163'));
    Assert.assertEquals('\u015f', normalizer.normalize('\u0219'));
    Assert.assertEquals('\u0163', normalizer.normalize('\u021b'));
  }

  /**
   * Test method for {@link UnicodeCharSequenceNormalizer#normalize_vi(CharSequence)}
   */
  @Test
  public final void testNormalizeVietnamese() {
    Assert.assertEquals("", normalizer.normalize_vi(""));
    Assert.assertEquals("ABC", normalizer.normalize_vi("ABC"));
    Assert.assertEquals("012", normalizer.normalize_vi("012"));
    Assert.assertEquals("\u00c0", normalizer.normalize_vi("\u00c0"));

    Assert.assertEquals("\u00C0", normalizer.normalize_vi("\u0041\u0300"));
    Assert.assertEquals("\u00C8", normalizer.normalize_vi("\u0045\u0300"));
    Assert.assertEquals("\u00CC", normalizer.normalize_vi("\u0049\u0300"));
    Assert.assertEquals("\u00D2", normalizer.normalize_vi("\u004F\u0300"));
    Assert.assertEquals("\u00D9", normalizer.normalize_vi("\u0055\u0300"));
    Assert.assertEquals("\u1EF2", normalizer.normalize_vi("\u0059\u0300"));
    Assert.assertEquals("\u00E0", normalizer.normalize_vi("\u0061\u0300"));
    Assert.assertEquals("\u00E8", normalizer.normalize_vi("\u0065\u0300"));
    Assert.assertEquals("\u00EC", normalizer.normalize_vi("\u0069\u0300"));
    Assert.assertEquals("\u00F2", normalizer.normalize_vi("\u006F\u0300"));
    Assert.assertEquals("\u00F9", normalizer.normalize_vi("\u0075\u0300"));
    Assert.assertEquals("\u1EF3", normalizer.normalize_vi("\u0079\u0300"));
    Assert.assertEquals("\u1EA6", normalizer.normalize_vi("\u00C2\u0300"));
    Assert.assertEquals("\u1EC0", normalizer.normalize_vi("\u00CA\u0300"));
    Assert.assertEquals("\u1ED2", normalizer.normalize_vi("\u00D4\u0300"));
    Assert.assertEquals("\u1EA7", normalizer.normalize_vi("\u00E2\u0300"));
    Assert.assertEquals("\u1EC1", normalizer.normalize_vi("\u00EA\u0300"));
    Assert.assertEquals("\u1ED3", normalizer.normalize_vi("\u00F4\u0300"));
    Assert.assertEquals("\u1EB0", normalizer.normalize_vi("\u0102\u0300"));
    Assert.assertEquals("\u1EB1", normalizer.normalize_vi("\u0103\u0300"));
    Assert.assertEquals("\u1EDC", normalizer.normalize_vi("\u01A0\u0300"));
    Assert.assertEquals("\u1EDD", normalizer.normalize_vi("\u01A1\u0300"));
    Assert.assertEquals("\u1EEA", normalizer.normalize_vi("\u01AF\u0300"));
    Assert.assertEquals("\u1EEB", normalizer.normalize_vi("\u01B0\u0300"));

    Assert.assertEquals("\u00C1", normalizer.normalize_vi("\u0041\u0301"));
    Assert.assertEquals("\u00C9", normalizer.normalize_vi("\u0045\u0301"));
    Assert.assertEquals("\u00CD", normalizer.normalize_vi("\u0049\u0301"));
    Assert.assertEquals("\u00D3", normalizer.normalize_vi("\u004F\u0301"));
    Assert.assertEquals("\u00DA", normalizer.normalize_vi("\u0055\u0301"));
    Assert.assertEquals("\u00DD", normalizer.normalize_vi("\u0059\u0301"));
    Assert.assertEquals("\u00E1", normalizer.normalize_vi("\u0061\u0301"));
    Assert.assertEquals("\u00E9", normalizer.normalize_vi("\u0065\u0301"));
    Assert.assertEquals("\u00ED", normalizer.normalize_vi("\u0069\u0301"));
    Assert.assertEquals("\u00F3", normalizer.normalize_vi("\u006F\u0301"));
    Assert.assertEquals("\u00FA", normalizer.normalize_vi("\u0075\u0301"));
    Assert.assertEquals("\u00FD", normalizer.normalize_vi("\u0079\u0301"));
    Assert.assertEquals("\u1EA4", normalizer.normalize_vi("\u00C2\u0301"));
    Assert.assertEquals("\u1EBE", normalizer.normalize_vi("\u00CA\u0301"));
    Assert.assertEquals("\u1ED0", normalizer.normalize_vi("\u00D4\u0301"));
    Assert.assertEquals("\u1EA5", normalizer.normalize_vi("\u00E2\u0301"));
    Assert.assertEquals("\u1EBF", normalizer.normalize_vi("\u00EA\u0301"));
    Assert.assertEquals("\u1ED1", normalizer.normalize_vi("\u00F4\u0301"));
    Assert.assertEquals("\u1EAE", normalizer.normalize_vi("\u0102\u0301"));
    Assert.assertEquals("\u1EAF", normalizer.normalize_vi("\u0103\u0301"));
    Assert.assertEquals("\u1EDA", normalizer.normalize_vi("\u01A0\u0301"));
    Assert.assertEquals("\u1EDB", normalizer.normalize_vi("\u01A1\u0301"));
    Assert.assertEquals("\u1EE8", normalizer.normalize_vi("\u01AF\u0301"));
    Assert.assertEquals("\u1EE9", normalizer.normalize_vi("\u01B0\u0301"));

    Assert.assertEquals("\u00C3", normalizer.normalize_vi("\u0041\u0303"));
    Assert.assertEquals("\u1EBC", normalizer.normalize_vi("\u0045\u0303"));
    Assert.assertEquals("\u0128", normalizer.normalize_vi("\u0049\u0303"));
    Assert.assertEquals("\u00D5", normalizer.normalize_vi("\u004F\u0303"));
    Assert.assertEquals("\u0168", normalizer.normalize_vi("\u0055\u0303"));
    Assert.assertEquals("\u1EF8", normalizer.normalize_vi("\u0059\u0303"));
    Assert.assertEquals("\u00E3", normalizer.normalize_vi("\u0061\u0303"));
    Assert.assertEquals("\u1EBD", normalizer.normalize_vi("\u0065\u0303"));
    Assert.assertEquals("\u0129", normalizer.normalize_vi("\u0069\u0303"));
    Assert.assertEquals("\u00F5", normalizer.normalize_vi("\u006F\u0303"));
    Assert.assertEquals("\u0169", normalizer.normalize_vi("\u0075\u0303"));
    Assert.assertEquals("\u1EF9", normalizer.normalize_vi("\u0079\u0303"));
    Assert.assertEquals("\u1EAA", normalizer.normalize_vi("\u00C2\u0303"));
    Assert.assertEquals("\u1EC4", normalizer.normalize_vi("\u00CA\u0303"));
    Assert.assertEquals("\u1ED6", normalizer.normalize_vi("\u00D4\u0303"));
    Assert.assertEquals("\u1EAB", normalizer.normalize_vi("\u00E2\u0303"));
    Assert.assertEquals("\u1EC5", normalizer.normalize_vi("\u00EA\u0303"));
    Assert.assertEquals("\u1ED7", normalizer.normalize_vi("\u00F4\u0303"));
    Assert.assertEquals("\u1EB4", normalizer.normalize_vi("\u0102\u0303"));
    Assert.assertEquals("\u1EB5", normalizer.normalize_vi("\u0103\u0303"));
    Assert.assertEquals("\u1EE0", normalizer.normalize_vi("\u01A0\u0303"));
    Assert.assertEquals("\u1EE1", normalizer.normalize_vi("\u01A1\u0303"));
    Assert.assertEquals("\u1EEE", normalizer.normalize_vi("\u01AF\u0303"));
    Assert.assertEquals("\u1EEF", normalizer.normalize_vi("\u01B0\u0303"));

    Assert.assertEquals("\u1EA2", normalizer.normalize_vi("\u0041\u0309"));
    Assert.assertEquals("\u1EBA", normalizer.normalize_vi("\u0045\u0309"));
    Assert.assertEquals("\u1EC8", normalizer.normalize_vi("\u0049\u0309"));
    Assert.assertEquals("\u1ECE", normalizer.normalize_vi("\u004F\u0309"));
    Assert.assertEquals("\u1EE6", normalizer.normalize_vi("\u0055\u0309"));
    Assert.assertEquals("\u1EF6", normalizer.normalize_vi("\u0059\u0309"));
    Assert.assertEquals("\u1EA3", normalizer.normalize_vi("\u0061\u0309"));
    Assert.assertEquals("\u1EBB", normalizer.normalize_vi("\u0065\u0309"));
    Assert.assertEquals("\u1EC9", normalizer.normalize_vi("\u0069\u0309"));
    Assert.assertEquals("\u1ECF", normalizer.normalize_vi("\u006F\u0309"));
    Assert.assertEquals("\u1EE7", normalizer.normalize_vi("\u0075\u0309"));
    Assert.assertEquals("\u1EF7", normalizer.normalize_vi("\u0079\u0309"));
    Assert.assertEquals("\u1EA8", normalizer.normalize_vi("\u00C2\u0309"));
    Assert.assertEquals("\u1EC2", normalizer.normalize_vi("\u00CA\u0309"));
    Assert.assertEquals("\u1ED4", normalizer.normalize_vi("\u00D4\u0309"));
    Assert.assertEquals("\u1EA9", normalizer.normalize_vi("\u00E2\u0309"));
    Assert.assertEquals("\u1EC3", normalizer.normalize_vi("\u00EA\u0309"));
    Assert.assertEquals("\u1ED5", normalizer.normalize_vi("\u00F4\u0309"));
    Assert.assertEquals("\u1EB2", normalizer.normalize_vi("\u0102\u0309"));
    Assert.assertEquals("\u1EB3", normalizer.normalize_vi("\u0103\u0309"));
    Assert.assertEquals("\u1EDE", normalizer.normalize_vi("\u01A0\u0309"));
    Assert.assertEquals("\u1EDF", normalizer.normalize_vi("\u01A1\u0309"));
    Assert.assertEquals("\u1EEC", normalizer.normalize_vi("\u01AF\u0309"));
    Assert.assertEquals("\u1EED", normalizer.normalize_vi("\u01B0\u0309"));

    Assert.assertEquals("\u1EA0", normalizer.normalize_vi("\u0041\u0323"));
    Assert.assertEquals("\u1EB8", normalizer.normalize_vi("\u0045\u0323"));
    Assert.assertEquals("\u1ECA", normalizer.normalize_vi("\u0049\u0323"));
    Assert.assertEquals("\u1ECC", normalizer.normalize_vi("\u004F\u0323"));
    Assert.assertEquals("\u1EE4", normalizer.normalize_vi("\u0055\u0323"));
    Assert.assertEquals("\u1EF4", normalizer.normalize_vi("\u0059\u0323"));
    Assert.assertEquals("\u1EA1", normalizer.normalize_vi("\u0061\u0323"));
    Assert.assertEquals("\u1EB9", normalizer.normalize_vi("\u0065\u0323"));
    Assert.assertEquals("\u1ECB", normalizer.normalize_vi("\u0069\u0323"));
    Assert.assertEquals("\u1ECD", normalizer.normalize_vi("\u006F\u0323"));
    Assert.assertEquals("\u1EE5", normalizer.normalize_vi("\u0075\u0323"));
    Assert.assertEquals("\u1EF5", normalizer.normalize_vi("\u0079\u0323"));
    Assert.assertEquals("\u1EAC", normalizer.normalize_vi("\u00C2\u0323"));
    Assert.assertEquals("\u1EC6", normalizer.normalize_vi("\u00CA\u0323"));
    Assert.assertEquals("\u1ED8", normalizer.normalize_vi("\u00D4\u0323"));
    Assert.assertEquals("\u1EAD", normalizer.normalize_vi("\u00E2\u0323"));
    Assert.assertEquals("\u1EC7", normalizer.normalize_vi("\u00EA\u0323"));
    Assert.assertEquals("\u1ED9", normalizer.normalize_vi("\u00F4\u0323"));
    Assert.assertEquals("\u1EB6", normalizer.normalize_vi("\u0102\u0323"));
    Assert.assertEquals("\u1EB7", normalizer.normalize_vi("\u0103\u0323"));
    Assert.assertEquals("\u1EE2", normalizer.normalize_vi("\u01A0\u0323"));
    Assert.assertEquals("\u1EE3", normalizer.normalize_vi("\u01A1\u0323"));
    Assert.assertEquals("\u1EF0", normalizer.normalize_vi("\u01AF\u0323"));
    Assert.assertEquals("\u1EF1", normalizer.normalize_vi("\u01B0\u0323"));

  }
}
