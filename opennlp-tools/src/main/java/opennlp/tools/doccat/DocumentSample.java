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

package opennlp.tools.doccat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.tokenize.WhitespaceTokenizer;

/**
 * Class which holds a classified document and its category.
 */
public class DocumentSample {

  private final String category;
  private final List<String> text;
  private final Map<String, Object> extraInformation;

  public DocumentSample(String category, String text) {
    this(category, WhitespaceTokenizer.INSTANCE.tokenize(text));
  }

  public DocumentSample(String category, String text[]) {
    this(category, text, null);
  }

  public DocumentSample(String category, String text[], Map<String, Object> extraInformation) {
    Objects.requireNonNull(text, "text must not be null");

    this.category = Objects.requireNonNull(category, "category must not be null");
    this.text = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(text)));

    if (extraInformation == null) {
      this.extraInformation = Collections.emptyMap();
    } else {
      this.extraInformation = extraInformation;
    }
  }

  public String getCategory() {
    return category;
  }

  public String[] getText() {
    return text.toArray(new String[text.size()]);
  }

  public Map<String, Object> getExtraInformation() {
    return extraInformation;
  }

  @Override
  public String toString() {

    StringBuilder sampleString = new StringBuilder();

    sampleString.append(category).append('\t');

    for (String s : text) {
      sampleString.append(s).append(' ');
    }

    if (sampleString.length() > 0) {
      // remove last space
      sampleString.setLength(sampleString.length() - 1);
    }

    return sampleString.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getCategory(), Arrays.hashCode(getText()));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof DocumentSample) {
      DocumentSample a = (DocumentSample) obj;

      return getCategory().equals(a.getCategory())
          && Arrays.equals(getText(), a.getText());
    }

    return false;
  }
}
