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
 * Class which holds a classified document and its @{@link Language}.
 */
public class LanguageSample implements Serializable {

  private final Language language;
  private final CharSequence context;

  public LanguageSample(Language language, CharSequence context) {
    this.language = Objects.requireNonNull(language, "language must not be null");
    this.context = Objects.requireNonNull(context, "context must not be null");
  }

  public Language getLanguage() {
    return language;
  }

  public CharSequence getContext() {
    return context;
  }

  @Override
  public String toString() {
    return language.getLang() + '\t' +  context;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getContext(), getLanguage());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof LanguageSample) {
      LanguageSample a = (LanguageSample) obj;

      return getLanguage().equals(a.getLanguage())
          && getContext().equals(a.getContext());
    }

    return false;
  }
}
