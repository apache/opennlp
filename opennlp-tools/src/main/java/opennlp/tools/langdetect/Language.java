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

package opennlp.tools.langdetect;

import java.io.Serializable;
import java.util.Objects;

/**
 * Class for holding the document language and its confidence
 */
public class Language implements Serializable {
  private final String lang;
  private final double confidence;

  public Language(String lang) {
    this(lang, 0);
  }

  public Language(String lang, double confidence) {
    Objects.requireNonNull(lang, "lang must not be null");
    this.lang = lang;
    this.confidence = confidence;
  }

  public String getLang() {
    return lang;
  }

  public double getConfidence() {
    return confidence;
  }

  @Override
  public String toString() {
    return getLang() + " (" + this.confidence + ")";
  }

  @Override
  public int hashCode() {
    return Objects.hash(getLang(), getConfidence());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof Language) {
      Language a = (Language) obj;

      return getLang().equals(a.getLang());
    }

    return false;
  }
}
