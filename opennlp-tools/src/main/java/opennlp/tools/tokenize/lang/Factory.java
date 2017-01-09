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

  public static final String DEFAULT_ALPHANUMERIC = "^[A-Za-z0-9]+$";

  /**
   * Gets the alpha numeric pattern for the language. Please save the value
   * locally because this call is expensive.
   *
   * @param languageCode
   *          the language code. If null or unknow the default pattern will be
   *          returned.
   * @return the alpha numeric pattern for the language or the default pattern.
   */
  public Pattern getAlphanumeric(String languageCode) {
    if ("pt".equals(languageCode)) {
      return Pattern.compile("^[0-9a-záãâàéêíóõôúüçA-ZÁÃÂÀÉÊÍÓÕÔÚÜÇ]+$");
    }

    return Pattern.compile(DEFAULT_ALPHANUMERIC);
  }

  public TokenContextGenerator createTokenContextGenerator(String languageCode, Set<String> abbreviations) {
    return new DefaultTokenContextGenerator(abbreviations);
  }

}
