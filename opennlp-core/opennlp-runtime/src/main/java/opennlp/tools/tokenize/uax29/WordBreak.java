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
package opennlp.tools.tokenize.uax29;

/**
 * The Unicode {@code Word_Break} property values, used by the UAX #29 word boundary algorithm.
 *
 * <p>{@link #OTHER} is the default for code points that carry no {@code Word_Break} value in the
 * Unicode Character Database. The remaining constants correspond one-to-one to the values in
 * {@code WordBreakProperty.txt} (see
 * <a href="https://www.unicode.org/reports/tr29/">UAX #29</a>).</p>
 */
public enum WordBreak {

  /** No assigned {@code Word_Break} value (the default). */
  OTHER,
  /** Carriage return ({@code U+000D}). */
  CR,
  /** Line feed ({@code U+000A}). */
  LF,
  /** Other mandatory line breaks (vertical tab, form feed, NEL, line/paragraph separators). */
  NEWLINE,
  /** Combining marks and other characters that extend the preceding one. */
  EXTEND,
  /** Zero width joiner ({@code U+200D}). */
  ZWJ,
  /** Regional indicator symbols (used in pairs for flag emoji). */
  REGIONAL_INDICATOR,
  /** Format characters. */
  FORMAT,
  /** Katakana letters. */
  KATAKANA,
  /** Hebrew letters (distinguished so a single quote may join them). */
  HEBREW_LETTER,
  /** Alphabetic letters. */
  ALETTER,
  /** The apostrophe ({@code U+0027}). */
  SINGLE_QUOTE,
  /** The quotation mark ({@code U+0022}). */
  DOUBLE_QUOTE,
  /** Characters that join letters or numbers (for example the full stop). */
  MID_NUM_LET,
  /** Characters that join letters (for example the middle dot). */
  MID_LETTER,
  /** Characters that join numbers (for example the comma). */
  MID_NUM,
  /** Decimal digits. */
  NUMERIC,
  /** Characters that extend a number or letter sequence (for example the low line). */
  EXTEND_NUM_LET,
  /** Whitespace that segments words ({@code Word_Break=WSegSpace}). */
  WSEG_SPACE;

  /**
   * Maps a {@code Word_Break} value name, as written in {@code WordBreakProperty.txt}, to its
   * constant.
   *
   * @param name The property value name (for example {@code ALetter}).
   * @return The matching constant.
   * @throws IllegalArgumentException Thrown if the name is not a known {@code Word_Break} value.
   */
  static WordBreak fromPropertyName(String name) {
    return switch (name) {
      case "CR" -> CR;
      case "LF" -> LF;
      case "Newline" -> NEWLINE;
      case "Extend" -> EXTEND;
      case "ZWJ" -> ZWJ;
      case "Regional_Indicator" -> REGIONAL_INDICATOR;
      case "Format" -> FORMAT;
      case "Katakana" -> KATAKANA;
      case "Hebrew_Letter" -> HEBREW_LETTER;
      case "ALetter" -> ALETTER;
      case "Single_Quote" -> SINGLE_QUOTE;
      case "Double_Quote" -> DOUBLE_QUOTE;
      case "MidNumLet" -> MID_NUM_LET;
      case "MidLetter" -> MID_LETTER;
      case "MidNum" -> MID_NUM;
      case "Numeric" -> NUMERIC;
      case "ExtendNumLet" -> EXTEND_NUM_LET;
      case "WSegSpace" -> WSEG_SPACE;
      default -> throw new IllegalArgumentException("Unknown Word_Break value: " + name);
    };
  }
}
