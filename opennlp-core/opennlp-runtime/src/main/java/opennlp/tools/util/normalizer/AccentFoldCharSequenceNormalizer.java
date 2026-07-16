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

import java.text.Normalizer;
import java.util.Set;

/**
 * A {@link CharSequenceNormalizer} that folds diacritics for matching, the
 * multilingual-safe counterpart to a Latin-only ASCII folding filter.
 *
 * <p>Folding decomposes the text (NFD) and drops nonspacing combining marks, but only for base
 * characters whose script is in {@code foldScripts} (Latin, Greek, and Cyrillic by default). Marks
 * on other scripts are left untouched, because there they are essential orthography rather than
 * decoration: stripping an Indic vowel sign or a virama, an Arabic harakat, a Hebrew point, or a
 * Thai vowel changes the word. This script gating is the key correctness rule; never strip all
 * nonspacing marks globally.</p>
 *
 * <p>Many "accented" Latin letters are atomic and do not decompose ({@code o} with stroke, the
 * {@code ae}/{@code oe} ligatures, eszett, thorn, and so on). When {@code foldStrokeLetters} is
 * enabled (the default) these are mapped to an ASCII approximation. Folding is a recall
 * optimization, not a linguistically correct transform, so it is intended for a matching
 * token rather than for display or language-specific analysis.</p>
 *
 * <p>Scanning is a single cursor pass over the decomposed text; no regular expression is used, and
 * no global {@code \p{Mn}} strip is performed.</p>
 */
public class AccentFoldCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = -7502792584669454623L;

  private static final Set<Character.UnicodeScript> DEFAULT_SCRIPTS = Set.of(
      Character.UnicodeScript.LATIN,
      Character.UnicodeScript.GREEK,
      Character.UnicodeScript.CYRILLIC);

  private static final AccentFoldCharSequenceNormalizer INSTANCE =
      new AccentFoldCharSequenceNormalizer(DEFAULT_SCRIPTS, true);

  private final Set<Character.UnicodeScript> foldScripts;
  private final boolean foldStrokeLetters;

  /**
   * Creates an accent-folding normalizer.
   *
   * @param foldScripts The scripts whose base characters' diacritics are folded; marks on every
   *     other script are preserved.
   * @param foldStrokeLetters Whether atomic Latin letters such as the stroke letters and ligatures
   *     are mapped to an ASCII approximation.
   */
  public AccentFoldCharSequenceNormalizer(Set<Character.UnicodeScript> foldScripts,
                                          boolean foldStrokeLetters) {
    this.foldScripts = Set.copyOf(foldScripts);
    this.foldStrokeLetters = foldStrokeLetters;
  }

  /** {@return the shared instance with the safe defaults: Latin, Greek, and Cyrillic plus the
   *     stroke-letter map} */
  public static AccentFoldCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    final StringBuilder out = new StringBuilder(decomposed.length());

    Character.UnicodeScript baseScript = null;
    int i = 0;
    final int length = decomposed.length();
    while (i < length) {
      final int codePoint = decomposed.codePointAt(i);
      if (Character.getType(codePoint) == Character.NON_SPACING_MARK) {
        // Drop the mark only when its base character belongs to a folded script.
        if (baseScript == null || !foldScripts.contains(baseScript)) {
          out.appendCodePoint(codePoint);
        }
      } else {
        final String mapped = foldStrokeLetters ? strokeLetter(codePoint) : null;
        if (mapped != null) {
          out.append(mapped);
          baseScript = Character.UnicodeScript.LATIN;
        } else {
          out.appendCodePoint(codePoint);
          baseScript = Character.UnicodeScript.of(codePoint);
        }
      }
      i += Character.charCount(codePoint);
    }

    return Normalizer.normalize(out, Normalizer.Form.NFC);
  }

  // Atomic Latin letters that NFD does not decompose, mapped to an ASCII approximation.
  private static String strokeLetter(int codePoint) {
    return switch (codePoint) {
      case 0x00F8 -> "o";   // o with stroke
      case 0x00D8 -> "O";   // O with stroke
      case 0x00E6 -> "ae";  // ae ligature
      case 0x00C6 -> "AE";  // AE ligature
      case 0x0153 -> "oe";  // oe ligature
      case 0x0152 -> "OE";  // OE ligature
      case 0x00DF -> "ss";  // eszett
      case 0x1E9E -> "SS";  // capital eszett
      case 0x00FE -> "th";  // thorn
      case 0x00DE -> "TH";  // capital thorn
      case 0x00F0 -> "d";   // eth
      case 0x00D0 -> "D";   // capital eth
      case 0x0111 -> "d";   // d with stroke
      case 0x0110 -> "D";   // D with stroke
      case 0x0142 -> "l";   // l with stroke
      case 0x0141 -> "L";   // L with stroke
      case 0x0127 -> "h";   // h with stroke
      case 0x0126 -> "H";   // H with stroke
      case 0x0131 -> "i";   // dotless i
      default -> null;
    };
  }
}
