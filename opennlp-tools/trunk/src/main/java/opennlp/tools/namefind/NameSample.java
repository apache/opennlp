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

package opennlp.tools.namefind;

import opennlp.tools.util.Span;

/**
 * Class for holding names for a single unit of text.
 */
public class NameSample {

  private final String[] sentence;
  private final Span[] names;
  private final String[][] additionalContext;
  private final boolean isClearAdaptiveData;

  /**
   * Initializes the current instance.
   *
   * @param sentence training sentence
   * @param names
   * @param additionalContext
   * @param clearAdaptiveData if true the adaptive data of the 
   *     feature generators is cleared
   */
  public NameSample(String[] sentence, Span[] names,
      String[][] additionalContext, boolean clearAdaptiveData) {

    if (sentence == null) {
      throw new IllegalArgumentException();
    }

    if (names == null) {
      names = new Span[0];
    }

    this.sentence = sentence;
    this.names = names;
    this.additionalContext = additionalContext;
    isClearAdaptiveData = clearAdaptiveData;
  }

  public NameSample(String[] sentence, Span[] names, boolean clearAdaptiveData) {
    this(sentence, names, null, clearAdaptiveData);
  }
  
  public String[] getSentence() {
    return sentence;
  }

  public Span[] getNames() {
    return names;
  }

  public String[][] getAdditionalContext() {
    return additionalContext;
  }

  public boolean isClearAdaptiveDataSet() {
    return isClearAdaptiveData;
  }

  public String toString() {
    StringBuilder result = new StringBuilder();

    for (int tokenIndex = 0; tokenIndex < sentence.length; tokenIndex++) {
      // token

      for (int nameIndex = 0; nameIndex < names.length; nameIndex++) {
        if (names[nameIndex].getStart() == tokenIndex) {
          // check if nameTypes is null, or if the nameType for this specific
          // entity is empty. If it is, we leave the nameType blank.
          if (names[nameIndex].getType() == null) {
            result.append(NameSampleDataStream.START_TAG).append(' ');
          }
          else {
            result.append(NameSampleDataStream.START_TAG_PREFIX).append(names[nameIndex].getType()).append("> ");
          }
        }

        if (names[nameIndex].getEnd() == tokenIndex) {
          result.append(NameSampleDataStream.END_TAG).append(' ');
        }
      }

      result.append(sentence[tokenIndex] + ' ');
    }

    result.setLength(result.length() - 1);
    
    for (int nameIndex = 0; nameIndex < names.length; nameIndex++) {
      if (names[nameIndex].getEnd() == sentence.length) {
        result.append(' ').append(NameSampleDataStream.END_TAG);
      }
    }

    return result.toString();
  }
}
