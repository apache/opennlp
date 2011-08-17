/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import opennlp.tools.tokenize.WhitespaceTokenizer;

/**
 * Class which holds a classified document and its category.
 */
public class DocumentSample {

  private final String category;
  private final List<String> text;

  public DocumentSample(String category, String text) {
    this(category, WhitespaceTokenizer.INSTANCE.tokenize(text));
  }

  public DocumentSample(String category, String text[]) {
    if (category == null || text == null) {
      throw new IllegalArgumentException();
    }

    this.category = category;
    this.text = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(text)));
  }

  public String getCategory() {
    return category;
  }

  public String[] getText() {
    return text.toArray(new String[text.size()]);
  }
  
  @Override
  public String toString() {
    
    StringBuilder sampleString = new StringBuilder();
    
    sampleString.append(category).append('\t');
        
    for (int i = 0; i < text.size(); i++) {
      sampleString.append(text.get(i)).append(' ');
    }
    
    if (sampleString.length() > 0) {
      // remove last space
      sampleString.setLength(sampleString.length() - 1);
    }
    
    return sampleString.toString();
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof DocumentSample) {
      DocumentSample a = (DocumentSample) obj;

      return getCategory().equals(a.getCategory())
          && Arrays.equals(getText(), a.getText());
    } else {
      return false;
    }
  }
}
