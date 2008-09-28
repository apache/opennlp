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

package opennlp.tools.namefind;

import opennlp.tools.util.Span;

/** 
 * Class for holding names for a single unit of text.
 */
public class NameSample {

  private final String[] sentence;
  private final Span[] names;
  private final String[] nameTypes;
  private final String[][] additionalContext;
  private final boolean isClearAdaptiveData;

  /**
   * Initializes the current instance. 
   * 
   * @param sentence training sentence
   * @param names 
   * @param additionalContext
   * @param clearAdaptiveData if true the adaptive data of the feature generators is cleared
   */
  public NameSample(String[] sentence, Span[] names, String[] types,
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
    this.nameTypes = types;
  }

  /**
   * Initializes the current instance.
   * 
   * @param sentence
   * @param names
   * @param clearAdaptiveData
   */
  public NameSample(String sentence[], Span[] names, boolean clearAdaptiveData) {
    this(sentence, names, null, null, clearAdaptiveData);
  }
  
  public NameSample(String sentence[], Span[] names, String[] nameTypes, boolean clearAdaptiveData) {
    this(sentence, names, nameTypes, null, clearAdaptiveData);
  }

  public String[] getSentence() {
    return sentence;
  }

  public Span[] getNames() {
    return names;
  }
  
  public String[] getNameTypes() {
    return nameTypes;
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
          if (nameTypes == null) {
            result.append(NameSampleDataStream.START_TAG).append(' ');
          }
          else {
            result.append("<").append(names[nameIndex]).append("> ");
          }
        }

        if (names[nameIndex].getEnd() == tokenIndex) {
          if (nameTypes == null) {
            result.append(NameSampleDataStream.END_TAG).append(' ');
          }
          else {
            result.append("</").append(names[nameIndex]).append("> ");
          }
        }
      }

      result.append(sentence[tokenIndex] + ' ');
    }

    for (int nameIndex = 0; nameIndex < names.length; nameIndex++) {
      if (names[nameIndex].getEnd() == sentence.length) {
        if (nameTypes == null) {
          result.append(NameSampleDataStream.END_TAG + ' ');
        }
        else {
          result.append("</").append(names[nameIndex]).append("> ");
        }
      }
    }

    result.setLength(result.length() - 1);

    return result.toString();
  }
}