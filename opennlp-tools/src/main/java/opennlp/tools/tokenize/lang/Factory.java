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

  // For reference: https://www.sttmedia.com/characterfrequency-dutch
  private static final Pattern DUTCH = Pattern.compile("^[A-Za-z0-9äöüëèéïĳÄÖÜËÉÈÏĲ]+$");
  private static final Pattern GERMAN = Pattern.compile("^[A-Za-z0-9äöüÄÖÜß]+$");

  /**
   * Gets the alphanumeric pattern for a language.
   *
   * @param languageCode The ISO_639-1 code. If {@code null}, or unknown, the
   *                     {@link #DEFAULT_ALPHANUMERIC} pattern will be returned.
   * @return The alphanumeric {@link Pattern} for the language, or the default pattern.
   */
  public Pattern getAlphanumeric(String languageCode) {
    // For reference: https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
    if ("pt".equals(languageCode) || "por".equals(languageCode)) {
      return PORTUGUESE;
    }
    if ("fr".equals(languageCode) || "fre".equals(languageCode) || "fra".equals(languageCode)) {
      return FRENCH;
    }
    if ("nl".equals(languageCode) || "nld".equals(languageCode) || "dut".equals(languageCode)) {
      return DUTCH;
    }
    if ("de".equals(languageCode) || "deu".equals(languageCode) || "ger".equals(languageCode)) {
      return GERMAN;
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
