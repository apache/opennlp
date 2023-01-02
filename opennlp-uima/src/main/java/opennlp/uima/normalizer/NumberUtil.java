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

package opennlp.uima.normalizer;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

/**
 * Provides methods to parse numbers which occur in natural language texts.
 */
public final class NumberUtil {

  /**
   * Checks if the language is supported.
   *
   * @param languageCode A ISO conform language code, e.g. "en", "pt"
   * @return true if the language is supported
   */
  public static boolean isLanguageSupported(String languageCode) {
    Locale locale = new Locale(languageCode);

    Locale[] possibleLocales = NumberFormat.getAvailableLocales();

    boolean isLocaleSupported = false;

    for (Locale possibleLocale : possibleLocales) {
      // search if locale is contained
      if (possibleLocale.equals(locale)) {
        isLocaleSupported = true;
        break;
      }
    }

    return isLocaleSupported;
  }

  /**
   * Removes a character from given string {@code s}.
   *
   * @param s The string to process.
   * @param remove The {@link Character} to remove from {@code s}.
   */
  private static String removeChar(String s, char remove) {

    StringBuilder result = new StringBuilder();

    int lastPosition = 0;
    int position;
    while ((position = s.indexOf(remove, lastPosition)) != -1) {
      result.append(s.substring(lastPosition, position));
      lastPosition = position + 1;
    }

    result.append(s.substring(lastPosition, s.length()));

    return result.toString();
  }

  /**
   * Parses a specified {@link String number} for a certain {@code languageCode}.
   *
   * @param number The suspected number to parse.
   * @param languageCode A ISO conform language code, e.g. "en", "pt"
   *                     
   * @return The parsed {@link Number}.
   * @throws ParseException Thrown if errors occurred during parsing.
   * @throws IllegalArgumentException Thrown if the {@code languageCode} is not supported.
   */
  public static Number parse(String number, String languageCode)
      throws ParseException {

    if (!isLanguageSupported(languageCode)) {
      throw new IllegalArgumentException("Language " + languageCode + " is not supported!");
    }

    Locale locale = new Locale(languageCode);

    NumberFormat numberFormat = NumberFormat.getInstance(locale);

    number = number.trim();
    number = removeChar(number, ' ');

    return numberFormat.parse(number);
  }
}
