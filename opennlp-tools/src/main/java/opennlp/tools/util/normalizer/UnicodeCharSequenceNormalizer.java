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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unicode normalizer based on https://github.com/shuyo/language-detection
 */
public class UnicodeCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final UnicodeCharSequenceNormalizer INSTANCE = new UnicodeCharSequenceNormalizer();

  public static UnicodeCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  private static final ResourceBundle RESOURCE_BUNDLE =
      ResourceBundle.getBundle("opennlp.tools.util.normalizer.unicode_normalizer");

  private static final String LATIN1_EXCLUDED = getMessage("NGram.LATIN1_EXCLUDE");

  public CharSequence normalize(CharSequence text) {
    StringBuilder ret = new StringBuilder();


    CharSequence modified = normalize_vi(text);

    char previous = 0;
    
    for (int i = 0; i < modified.length(); i++) {
      char current = normalize(modified.charAt(i));
      if (current != ' ' || previous != ' ') {
        ret.append(current);
      }
      previous = current;
    }


    return ret.toString();
  }

  public char normalize(char ch) {
    Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
    if (block == Character.UnicodeBlock.BASIC_LATIN) {
      if (ch < 'A' || (ch < 'a' && ch > 'Z') || ch > 'z') {
        ch = ' ';
      }
    } else if (block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT) {
      if (LATIN1_EXCLUDED.indexOf(ch) >= 0) {
        ch = ' ';
      }
    } else if (block == Character.UnicodeBlock.LATIN_EXTENDED_B) {
      // normalization for Romanian
      if (ch == '\u0219') {
        ch = '\u015f';  // Small S with comma below => with cedilla
      }
      if (ch == '\u021b') {
        ch = '\u0163';  // Small T with comma below => with cedilla
      }
    } else if (block == Character.UnicodeBlock.GENERAL_PUNCTUATION) {
      ch = ' ';
    } else if (block == Character.UnicodeBlock.ARABIC) {
      if (ch == '\u06cc') {
        ch = '\u064a';  // Farsi yeh => Arabic yeh
      }
    } else if (block == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL) {
      if (ch >= '\u1ea0') {
        ch = '\u1ec3';
      }
    } else if (block == Character.UnicodeBlock.HIRAGANA) {
      ch = '\u3042';
    } else if (block == Character.UnicodeBlock.KATAKANA) {
      ch = '\u30a2';
    } else if (block == Character.UnicodeBlock.BOPOMOFO ||
        block == Character.UnicodeBlock.BOPOMOFO_EXTENDED) {
      ch = '\u3105';
    } else if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
      if (CJK_MAP.containsKey(ch)) {
        ch = CJK_MAP.get(ch);
      }
    } else if (block == Character.UnicodeBlock.HANGUL_SYLLABLES) {
      ch = '\uac00';
    }
    return ch;
  }

  /**
   * Normalizer for Vietnamese (from Shuyo)
   *
   * @param text text to be normalized
   * @return normalized text
   */
  static CharSequence normalize_vi(CharSequence text) {
    Matcher m = ALPHABET_WITH_DMARK.matcher(text);
    StringBuffer buf = new StringBuffer();
    while (m.find()) {
      int alphabet = TO_NORMALIZE_VI_CHARS.indexOf(m.group(1));
      int dmark = DMARK_CLASS.indexOf(m.group(2)); // Diacritical Mark
      m.appendReplacement(buf, NORMALIZED_VI_CHARS[dmark].substring(alphabet, alphabet + 1));
    }
    if (buf.length() == 0) {
      return text;
    }
    m.appendTail(buf);
    return buf.toString();
  }


  static String getMessage(String key) {
    try {
      return RESOURCE_BUNDLE.getString(key);
    } catch (MissingResourceException e) {
      return '!' + key + '!';
    }
  }

  private static final String[] NORMALIZED_VI_CHARS = {
      getMessage("NORMALIZED_VI_CHARS_0300"),
      getMessage("NORMALIZED_VI_CHARS_0301"),
      getMessage("NORMALIZED_VI_CHARS_0303"),
      getMessage("NORMALIZED_VI_CHARS_0309"),
      getMessage("NORMALIZED_VI_CHARS_0323")};

  private static final String TO_NORMALIZE_VI_CHARS = getMessage("TO_NORMALIZE_VI_CHARS");
  private static final String DMARK_CLASS = getMessage("DMARK_CLASS");
  private static final Pattern ALPHABET_WITH_DMARK =
      Pattern.compile("([" + TO_NORMALIZE_VI_CHARS + "])([" + DMARK_CLASS + "])");

  /**
   * CJK Kanji Normalization Mapping
   */
  static final String[] CJK_CLASS = {
      getMessage("NGram.KANJI_1_0"),
      getMessage("NGram.KANJI_1_2"),
      getMessage("NGram.KANJI_1_4"),
      getMessage("NGram.KANJI_1_8"),
      getMessage("NGram.KANJI_1_11"),
      getMessage("NGram.KANJI_1_12"),
      getMessage("NGram.KANJI_1_13"),
      getMessage("NGram.KANJI_1_14"),
      getMessage("NGram.KANJI_1_16"),
      getMessage("NGram.KANJI_1_18"),
      getMessage("NGram.KANJI_1_22"),
      getMessage("NGram.KANJI_1_27"),
      getMessage("NGram.KANJI_1_29"),
      getMessage("NGram.KANJI_1_31"),
      getMessage("NGram.KANJI_1_35"),
      getMessage("NGram.KANJI_2_0"),
      getMessage("NGram.KANJI_2_1"),
      getMessage("NGram.KANJI_2_4"),
      getMessage("NGram.KANJI_2_9"),
      getMessage("NGram.KANJI_2_10"),
      getMessage("NGram.KANJI_2_11"),
      getMessage("NGram.KANJI_2_12"),
      getMessage("NGram.KANJI_2_13"),
      getMessage("NGram.KANJI_2_15"),
      getMessage("NGram.KANJI_2_16"),
      getMessage("NGram.KANJI_2_18"),
      getMessage("NGram.KANJI_2_21"),
      getMessage("NGram.KANJI_2_22"),
      getMessage("NGram.KANJI_2_23"),
      getMessage("NGram.KANJI_2_28"),
      getMessage("NGram.KANJI_2_29"),
      getMessage("NGram.KANJI_2_30"),
      getMessage("NGram.KANJI_2_31"),
      getMessage("NGram.KANJI_2_32"),
      getMessage("NGram.KANJI_2_35"),
      getMessage("NGram.KANJI_2_36"),
      getMessage("NGram.KANJI_2_37"),
      getMessage("NGram.KANJI_2_38"),
      getMessage("NGram.KANJI_3_1"),
      getMessage("NGram.KANJI_3_2"),
      getMessage("NGram.KANJI_3_3"),
      getMessage("NGram.KANJI_3_4"),
      getMessage("NGram.KANJI_3_5"),
      getMessage("NGram.KANJI_3_8"),
      getMessage("NGram.KANJI_3_9"),
      getMessage("NGram.KANJI_3_11"),
      getMessage("NGram.KANJI_3_12"),
      getMessage("NGram.KANJI_3_13"),
      getMessage("NGram.KANJI_3_15"),
      getMessage("NGram.KANJI_3_16"),
      getMessage("NGram.KANJI_3_18"),
      getMessage("NGram.KANJI_3_19"),
      getMessage("NGram.KANJI_3_22"),
      getMessage("NGram.KANJI_3_23"),
      getMessage("NGram.KANJI_3_27"),
      getMessage("NGram.KANJI_3_29"),
      getMessage("NGram.KANJI_3_30"),
      getMessage("NGram.KANJI_3_31"),
      getMessage("NGram.KANJI_3_32"),
      getMessage("NGram.KANJI_3_35"),
      getMessage("NGram.KANJI_3_36"),
      getMessage("NGram.KANJI_3_37"),
      getMessage("NGram.KANJI_3_38"),
      getMessage("NGram.KANJI_4_0"),
      getMessage("NGram.KANJI_4_9"),
      getMessage("NGram.KANJI_4_10"),
      getMessage("NGram.KANJI_4_16"),
      getMessage("NGram.KANJI_4_17"),
      getMessage("NGram.KANJI_4_18"),
      getMessage("NGram.KANJI_4_22"),
      getMessage("NGram.KANJI_4_24"),
      getMessage("NGram.KANJI_4_28"),
      getMessage("NGram.KANJI_4_34"),
      getMessage("NGram.KANJI_4_39"),
      getMessage("NGram.KANJI_5_10"),
      getMessage("NGram.KANJI_5_11"),
      getMessage("NGram.KANJI_5_12"),
      getMessage("NGram.KANJI_5_13"),
      getMessage("NGram.KANJI_5_14"),
      getMessage("NGram.KANJI_5_18"),
      getMessage("NGram.KANJI_5_26"),
      getMessage("NGram.KANJI_5_29"),
      getMessage("NGram.KANJI_5_34"),
      getMessage("NGram.KANJI_5_39"),
      getMessage("NGram.KANJI_6_0"),
      getMessage("NGram.KANJI_6_3"),
      getMessage("NGram.KANJI_6_9"),
      getMessage("NGram.KANJI_6_10"),
      getMessage("NGram.KANJI_6_11"),
      getMessage("NGram.KANJI_6_12"),
      getMessage("NGram.KANJI_6_16"),
      getMessage("NGram.KANJI_6_18"),
      getMessage("NGram.KANJI_6_20"),
      getMessage("NGram.KANJI_6_21"),
      getMessage("NGram.KANJI_6_22"),
      getMessage("NGram.KANJI_6_23"),
      getMessage("NGram.KANJI_6_25"),
      getMessage("NGram.KANJI_6_28"),
      getMessage("NGram.KANJI_6_29"),
      getMessage("NGram.KANJI_6_30"),
      getMessage("NGram.KANJI_6_32"),
      getMessage("NGram.KANJI_6_34"),
      getMessage("NGram.KANJI_6_35"),
      getMessage("NGram.KANJI_6_37"),
      getMessage("NGram.KANJI_6_39"),
      getMessage("NGram.KANJI_7_0"),
      getMessage("NGram.KANJI_7_3"),
      getMessage("NGram.KANJI_7_6"),
      getMessage("NGram.KANJI_7_7"),
      getMessage("NGram.KANJI_7_9"),
      getMessage("NGram.KANJI_7_11"),
      getMessage("NGram.KANJI_7_12"),
      getMessage("NGram.KANJI_7_13"),
      getMessage("NGram.KANJI_7_16"),
      getMessage("NGram.KANJI_7_18"),
      getMessage("NGram.KANJI_7_19"),
      getMessage("NGram.KANJI_7_20"),
      getMessage("NGram.KANJI_7_21"),
      getMessage("NGram.KANJI_7_23"),
      getMessage("NGram.KANJI_7_25"),
      getMessage("NGram.KANJI_7_28"),
      getMessage("NGram.KANJI_7_29"),
      getMessage("NGram.KANJI_7_32"),
      getMessage("NGram.KANJI_7_33"),
      getMessage("NGram.KANJI_7_35"),
      getMessage("NGram.KANJI_7_37"),
  };

  private static final Map<Character, Character> CJK_MAP;

  static {
    Map<Character, Character> _cjk_map = new HashMap();
    for (String cjk_list : CJK_CLASS) {
      char representative = cjk_list.charAt(0);
      for (int i = 0; i < cjk_list.length(); ++i) {
        _cjk_map.put(cjk_list.charAt(i), representative);
      }
    }
    CJK_MAP = Collections.unmodifiableMap(_cjk_map);
  }

}
