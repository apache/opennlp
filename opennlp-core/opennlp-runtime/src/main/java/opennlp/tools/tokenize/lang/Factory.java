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

package opennlp.tools.tokenize.lang;

import java.util.Set;
import java.util.regex.Pattern;

import opennlp.tools.tokenize.DefaultTokenContextGenerator;
import opennlp.tools.tokenize.TokenContextGenerator;

public class Factory {

  public static final Pattern DEFAULT_ALPHANUMERIC = Pattern.compile("^[A-Za-z0-9]+$");

  private static final Pattern PORTUGUESE = Pattern.compile("^[0-9a-záãâàéêíóõôúüçA-ZÁÃÂÀÉÊÍÓÕÔÚÜÇ]+$");
  private static final Pattern FRENCH = Pattern.compile("^[a-zA-Z0-9àâäèéêëîïôœùûüÿçÀÂÄÈÉÊËÎÏÔŒÙÛÜŸÇ]+$");

  // From: https://www.sttmedia.com/characterfrequency-dutch
  private static final Pattern DUTCH = Pattern.compile("^[A-Za-z0-9äöüëèéïĳÄÖÜËÉÈÏĲ]+$");

  // Note: The extra é and É are included to cover German "Lehnwörter" such as "Café"
  private static final Pattern GERMAN = Pattern.compile("^[A-Za-z0-9äéöüÄÉÖÜß]+$");

  // From: https://en.wikipedia.org/wiki/Polish_alphabet
  //       https://pl.wikipedia.org/wiki/Alfabet_polski
  private static final Pattern POLISH = Pattern.compile("^[A-Za-z0-9żźćńółęąśŻŹĆĄŚĘŁÓŃ]+$");

  // From: https://it.wikipedia.org/wiki/Alfabeto_italiano
  private static final Pattern ITALIAN = Pattern.compile("^[0-9a-zàèéìîíòóùüA-ZÀÈÉÌÎÍÒÓÙÜ]+$");

  // From: https://en.wikiversity.org/wiki/Alphabet/Spanish_alphabet &
  //       https://en.wikipedia.org/wiki/Spanish_orthography#Alphabet_in_Spanish &
  //       https://www.fundeu.es/consulta/tilde-en-la-y-y-griega-o-ye-24786/
  private static final Pattern SPANISH = Pattern.compile("^[0-9a-záéíóúüýñA-ZÁÉÍÓÚÝÑ]+$");

  // From: https://en.wikipedia.org/wiki/Catalan_orthography#Spelling_patterns
  private static final Pattern CATALAN = Pattern.compile("^[0-9a-zàèéíïòóúüçA-ZÀÈÉÍÏÒÓÚÜÇ]+$");
  
  /**
   * Gets the alphanumeric pattern for a language.
   *
   * @param languageCode The ISO_639-1 code. If {@code null}, or unknown, the
   *                     {@link #DEFAULT_ALPHANUMERIC} pattern will be returned.
   * @return The alphanumeric {@link Pattern} for the language, or the default pattern.
   */
  public Pattern getAlphanumeric(String languageCode) {
    // For reference: https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
    if ("es".equals(languageCode) || "spa".equals(languageCode)) {
      return SPANISH;
    }
    if ("it".equals(languageCode) || "ita".equals(languageCode)) {
      return ITALIAN;
    }
    if ("pt".equals(languageCode) || "por".equals(languageCode)) {
      return PORTUGUESE;
    }
    if ("ca".equals(languageCode) || "cat".equals(languageCode)) {
      return CATALAN;
    }
    if ("pl".equals(languageCode) || "pol".equals(languageCode)) {
      return POLISH;
    }
    if ("de".equals(languageCode) || "deu".equals(languageCode) || "ger".equals(languageCode)) {
      return GERMAN;
    }
    if ("fr".equals(languageCode) || "fre".equals(languageCode) || "fra".equals(languageCode)) {
      return FRENCH;
    }
    if ("nl".equals(languageCode) || "nld".equals(languageCode) || "dut".equals(languageCode)) {
      return DUTCH;
    }

    return DEFAULT_ALPHANUMERIC;
  }

  /**
   * Initializes a customized {@link TokenContextGenerator} via a set of {@code abbreviations}.
   * 
   * @param languageCode The ISO_639-1 code to be used.
   * @param abbreviations The abbreviations to be used for new instance.
   */
  public TokenContextGenerator createTokenContextGenerator(String languageCode, Set<String> abbreviations) {
    return new DefaultTokenContextGenerator(abbreviations);
  }

}
