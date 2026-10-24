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

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import opennlp.tools.commons.SuppressForbidden;
import opennlp.tools.tokenize.DefaultTokenContextGenerator;
import opennlp.tools.tokenize.TokenContextGenerator;
import opennlp.tools.tokenize.TokenizerCharacterPolicy;
import opennlp.tools.util.normalizer.CodePointSet;

/**
 * Provides the language dependent tokenizer resources: the {@link TokenizerCharacterPolicy}
 * that decides which tokens the alphanumeric optimization accepts without further splitting,
 * and the {@link TokenContextGenerator}.
 */
public class Factory {

  /**
   * The policy for languages without a dedicated one: ASCII letters and digits, no marks.
   */
  public static final TokenizerCharacterPolicy DEFAULT_CHARACTER_POLICY =
      TokenizerCharacterPolicy.ascii();

  private static final String ASCII_EXPRESSION = "^[A-Za-z0-9]+$";

  /**
   * The alphanumeric pattern of earlier releases for languages without a dedicated one.
   *
   * @deprecated Use {@link #DEFAULT_CHARACTER_POLICY}, which accepts the same characters. This
   *     constant will be removed in 4.0.
   */
  @Deprecated(since = "3.0.0", forRemoval = true)
  @SuppressForbidden("alphanumeric pattern of earlier releases, kept for the deprecated API")
  public static final Pattern DEFAULT_ALPHANUMERIC = compile(ASCII_EXPRESSION);

  private static final CodePointSet NO_MARKS = CodePointSet.of();

  private static final TokenizerCharacterPolicy PORTUGUESE = latinPolicy("áãâàéêíóõôúüçÁÃÂÀÉÊÍÓÕÔÚÜÇ");
  private static final TokenizerCharacterPolicy FRENCH = latinPolicy("àâäèéêëîïôœùûüÿçÀÂÄÈÉÊËÎÏÔŒÙÛÜŸÇ");

  // From: https://www.sttmedia.com/characterfrequency-dutch
  private static final TokenizerCharacterPolicy DUTCH = latinPolicy("äöüëèéïĳÄÖÜËÉÈÏĲ");

  // Note: The extra é and É are included to cover German "Lehnwörter" such as "Café"
  private static final TokenizerCharacterPolicy GERMAN = latinPolicy("äéöüÄÉÖÜß");

  // From: https://en.wikipedia.org/wiki/Polish_alphabet
  //       https://pl.wikipedia.org/wiki/Alfabet_polski
  private static final TokenizerCharacterPolicy POLISH = latinPolicy("żźćńółęąśŻŹĆĄŚĘŁÓŃ");

  // From: https://it.wikipedia.org/wiki/Alfabeto_italiano
  private static final TokenizerCharacterPolicy ITALIAN = latinPolicy("àèéìîíòóùüÀÈÉÌÎÍÒÓÙÜ");

  // From: https://en.wikiversity.org/wiki/Alphabet/Spanish_alphabet &
  //       https://en.wikipedia.org/wiki/Spanish_orthography#Alphabet_in_Spanish &
  //       https://www.fundeu.es/consulta/tilde-en-la-y-y-griega-o-ye-24786/
  private static final TokenizerCharacterPolicy SPANISH = latinPolicy("áéíóúüýñÁÉÍÓÚÝÑ");

  // From: https://en.wikipedia.org/wiki/Catalan_orthography#Spelling_patterns
  private static final TokenizerCharacterPolicy CATALAN = latinPolicy("àèéíïòóúüçÀÈÉÍÏÒÓÚÜÇ");

  /**
   * The alphanumeric expressions of releases before 3.0, keyed by the policy that accepts the same
   * characters.
   */
  private static final Map<TokenizerCharacterPolicy, String> LEGACY_EXPRESSIONS = Map.of(
      DEFAULT_CHARACTER_POLICY, ASCII_EXPRESSION,
      PORTUGUESE, "^[0-9a-záãâàéêíóõôúüçA-ZÁÃÂÀÉÊÍÓÕÔÚÜÇ]+$",
      FRENCH, "^[a-zA-Z0-9àâäèéêëîïôœùûüÿçÀÂÄÈÉÊËÎÏÔŒÙÛÜŸÇ]+$",
      DUTCH, "^[A-Za-z0-9äöüëèéïĳÄÖÜËÉÈÏĲ]+$",
      GERMAN, "^[A-Za-z0-9äéöüÄÉÖÜß]+$",
      POLISH, "^[A-Za-z0-9żźćńółęąśŻŹĆĄŚĘŁÓŃ]+$",
      ITALIAN, "^[0-9a-zàèéìîíòóùüA-ZÀÈÉÌÎÍÒÓÙÜ]+$",
      SPANISH, "^[0-9a-záéíóúüýñA-ZÁÉÍÓÚÝÑ]+$",
      CATALAN, "^[0-9a-zàèéíïòóúüçA-ZÀÈÉÍÏÒÓÚÜÇ]+$");

  /**
   * Gets the character policy for a language.
   *
   * @param languageCode An ISO 639-1 code, or an ISO 639-2 code in its terminology or
   *                     bibliographic form ({@code deu} or {@code ger}). If {@code null}, or unknown, the
   *                     {@link #DEFAULT_CHARACTER_POLICY} will be returned.
   * @return The {@link TokenizerCharacterPolicy} for the language, or the default policy.
   */
  public TokenizerCharacterPolicy getCharacterPolicy(String languageCode) {
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

    return DEFAULT_CHARACTER_POLICY;
  }

  /**
   * Gets the alphanumeric pattern that releases before 3.0 used for a language.
   *
   * @param languageCode An ISO 639-1 code, or an ISO 639-2 code in its terminology or
   *                     bibliographic form. If {@code null}, or unknown, the
   *                     {@link #DEFAULT_ALPHANUMERIC} pattern will be returned.
   * @return The {@link Pattern} that accepts the same characters as
   *     {@link #getCharacterPolicy(String)} for the language.
   * @deprecated Use {@link #getCharacterPolicy(String)}. This method will be removed in 4.0.
   */
  @Deprecated(since = "3.0.0", forRemoval = true)
  @SuppressForbidden("alphanumeric pattern of earlier releases, kept for the deprecated API")
  public Pattern getAlphanumeric(String languageCode) {
    return compile(LEGACY_EXPRESSIONS.get(getCharacterPolicy(languageCode)));
  }

  /**
   * {@return the policy of the language whose alphanumeric expression of an earlier release is
   * {@code expression}, or {@code null} if there is none}
   *
   * @param expression An alphanumeric expression of an earlier release.
   */
  public static TokenizerCharacterPolicy policyOfLegacyExpression(String expression) {
    for (Map.Entry<TokenizerCharacterPolicy, String> entry : LEGACY_EXPRESSIONS.entrySet()) {
      if (entry.getValue().equals(expression)) {
        return entry.getKey();
      }
    }
    return null;
  }

  /**
   * {@return the alphanumeric expression of an earlier release that accepts the same characters
   * as {@code policy}, or {@code null} if there is none}
   *
   * @param policy A character policy, or {@code null}.
   */
  public static String legacyExpressionOf(TokenizerCharacterPolicy policy) {
    return policy == null ? null : LEGACY_EXPRESSIONS.get(policy);
  }

  /**
   * {@return the compiled alphanumeric expression of an earlier release}
   *
   * @param expression The expression.
   */
  @SuppressForbidden("alphanumeric pattern of earlier releases, kept for the deprecated API")
  private static Pattern compile(String expression) {
    return Pattern.compile(expression);
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

  /**
   * Builds a policy of the ASCII letters plus {@code extraLetters}, the ASCII digits, and no marks.
   *
   * @param extraLetters The letters of a Latin script language beyond ASCII.
   * @return The policy.
   */
  private static TokenizerCharacterPolicy latinPolicy(String extraLetters) {
    CodePointSet letters = TokenizerCharacterPolicy.ascii().getLetters();
    for (int offset = 0; offset < extraLetters.length();) {
      final int codePoint = extraLetters.codePointAt(offset);
      letters = letters.union(CodePointSet.of(codePoint));
      offset += Character.charCount(codePoint);
    }
    return TokenizerCharacterPolicy.of(letters, TokenizerCharacterPolicy.ascii().getDigits(),
        NO_MARKS);
  }
}
