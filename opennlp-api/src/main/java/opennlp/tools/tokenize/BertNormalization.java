/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.tokenize;

/**
 * Character classifications and text transforms of the reference BERT
 * {@code BasicTokenizer}, shared by {@link WordpieceEncoder} and
 * {@link WordpieceTokenizer}.
 */
final class BertNormalization {

  private BertNormalization() {
  }

  /**
   * Surrounds every punctuation character with spaces, so each punctuation
   * character becomes its own token.
   */
  static String isolatePunctuation(String text) {
    final StringBuilder spaced = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (isPunctuation(codePoint)) {
        spaced.append(' ').appendCodePoint(codePoint).append(' ');
      } else {
        spaced.appendCodePoint(codePoint);
      }
    });
    return spaced.toString();
  }

  /**
   * A control character in the BERT sense: any {@code C*} category
   * (control, format, surrogate, private use, unassigned), except the
   * characters treated as whitespace by {@link #isWhitespace(int)}.
   */
  static boolean isControl(int codePoint) {
    if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
      return false;
    }
    return switch (Character.getType(codePoint)) {
      case Character.CONTROL, Character.FORMAT, Character.SURROGATE,
           Character.PRIVATE_USE, Character.UNASSIGNED -> true;
      default -> false;
    };
  }

  /**
   * A whitespace character in the BERT sense: space, tab, newline, carriage
   * return, or Unicode space separators ({@code Zs}).
   */
  static boolean isWhitespace(int codePoint) {
    if (codePoint == ' ' || codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
      return true;
    }
    return Character.getType(codePoint) == Character.SPACE_SEPARATOR;
  }

  /**
   * A punctuation character in the BERT sense: any non-alphanumeric ASCII
   * character that is not whitespace, or any Unicode punctuation category.
   */
  static boolean isPunctuation(int codePoint) {
    if ((codePoint >= 33 && codePoint <= 47) || (codePoint >= 58 && codePoint <= 64)
        || (codePoint >= 91 && codePoint <= 96) || (codePoint >= 123 && codePoint <= 126)) {
      return true;
    }
    return switch (Character.getType(codePoint)) {
      case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
           Character.START_PUNCTUATION, Character.END_PUNCTUATION,
           Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
           Character.OTHER_PUNCTUATION -> true;
      default -> false;
    };
  }

  /**
   * A CJK ideograph as defined by the reference BERT implementation: the CJK
   * Unified Ideographs blocks and their extensions. This intentionally does
   * not cover Japanese kana or Korean hangul, matching the reference.
   */
  static boolean isCjk(int codePoint) {
    return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
        || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
        || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
        || (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
        || (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
        || (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
        || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
        || (codePoint >= 0x2F800 && codePoint <= 0x2FA1F);
  }

}
