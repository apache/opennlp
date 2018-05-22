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

package opennlp.tools.util;

import java.util.Objects;

/**
 * Represents a language using ISO-639-3 codes.
 *
 */
public final class LanguageCode {

  public static final LanguageCode ARABIC = new LanguageCode("aao");
  public static final LanguageCode BENGALI = new LanguageCode("ben");
  public static final LanguageCode DUTCH = new LanguageCode("nld");
  public static final LanguageCode DEUTSCH = new LanguageCode("deu");
  public static final LanguageCode ENGLISH = new LanguageCode("eng");
  public static final LanguageCode FRENCH = new LanguageCode("fra");
  public static final LanguageCode GERMAN = new LanguageCode("deu");
  public static final LanguageCode HAUSA = new LanguageCode("hau");
  public static final LanguageCode ITALIAN = new LanguageCode("ita");
  public static final LanguageCode JAPANESE = new LanguageCode("jpn");
  public static final LanguageCode KOREAN = new LanguageCode("kor");
  public static final LanguageCode MALAY = new LanguageCode("mal");
  public static final LanguageCode PERSIAN = new LanguageCode("prp");
  public static final LanguageCode PORTUGUESE = new LanguageCode("por");
  public static final LanguageCode PUNJABI = new LanguageCode("pan");
  public static final LanguageCode RUSSIAN = new LanguageCode("rus");
  public static final LanguageCode SPANISH = new LanguageCode("spa");
  public static final LanguageCode SWAHILI = new LanguageCode("swa");

  private final String code;

  /**
   * Creates a new <code>code</code>. The code will be converted
   * to lowercase to conform with ISO 639-3 recommendation of
   * using lowercase codes.
   * @param code The language code. It must not be <code>null</code>
   * and three characters in length.
   */
  public LanguageCode(String code) {

    if (code == null || code.length() != 3) {
      throw new IllegalArgumentException("Code must be not null and 3 characters.");
    }
    this.code = code.toLowerCase();

  }

  /**
   * Gets the language code.
   */
  public String getCode() {
    return code;
  }

  @Override
  public String toString() {
    return code;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof LanguageCode) {
      LanguageCode a = (LanguageCode) obj;
      return getCode().equals(a.getCode());
    }

    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(code);
  }
  
}
