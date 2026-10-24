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

package opennlp.tools.tokenize;

import java.util.Map;

/**
 * The alphanumeric regular expressions that OpenNLP wrote into tokenizer model manifests up
 * to 2.x, keyed by the language code of the policy that replaces each one. Tests use them as
 * the oracle for the policies and as legacy manifest input.
 */
public final class LegacyAlphaNumericPatterns {

  public static final String DEFAULT = "^[A-Za-z0-9]+$";
  public static final String PORTUGUESE = "^[0-9a-záãâàéêíóõôúüçA-ZÁÃÂÀÉÊÍÓÕÔÚÜÇ]+$";
  public static final String FRENCH = "^[a-zA-Z0-9àâäèéêëîïôœùûüÿçÀÂÄÈÉÊËÎÏÔŒÙÛÜŸÇ]+$";
  public static final String DUTCH = "^[A-Za-z0-9äöüëèéïĳÄÖÜËÉÈÏĲ]+$";
  public static final String GERMAN = "^[A-Za-z0-9äéöüÄÉÖÜß]+$";
  public static final String POLISH = "^[A-Za-z0-9żźćńółęąśŻŹĆĄŚĘŁÓŃ]+$";
  public static final String ITALIAN = "^[0-9a-zàèéìîíòóùüA-ZÀÈÉÌÎÍÒÓÙÜ]+$";
  public static final String SPANISH = "^[0-9a-záéíóúüýñA-ZÁÉÍÓÚÝÑ]+$";
  public static final String CATALAN = "^[0-9a-zàèéíïòóúüçA-ZÀÈÉÍÏÒÓÚÜÇ]+$";

  /**
   * Each language code with the regular expression its policy replaces.
   */
  public static final Map<String, String> BY_LANGUAGE = Map.of(
      "en", DEFAULT,
      "pt", PORTUGUESE,
      "fr", FRENCH,
      "nl", DUTCH,
      "de", GERMAN,
      "pl", POLISH,
      "it", ITALIAN,
      "es", SPANISH,
      "ca", CATALAN);

  private LegacyAlphaNumericPatterns() {
  }
}
