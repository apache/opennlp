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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStreamException;
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

  @Override
  public boolean equals(Object obj) {
    
    if (this == obj) {
      return true;
    }
    else if (obj instanceof NameSample) {
      NameSample a = (NameSample) obj;
      
      return Arrays.equals(getSentence(), a.getSentence()) &&
          Arrays.equals(getNames(), a.getNames()) &&
          Arrays.equals(getAdditionalContext(), a.getAdditionalContext()) &&
          isClearAdaptiveDataSet() == a.isClearAdaptiveDataSet();
    }
    else {
      return true;
    }
    
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

    if (sentence.length > 1)
      result.setLength(result.length() - 1);
    
    for (int nameIndex = 0; nameIndex < names.length; nameIndex++) {
      if (names[nameIndex].getEnd() == sentence.length) {
        result.append(' ').append(NameSampleDataStream.END_TAG);
      }
    }

    return result.toString();
  }
  
  public static NameSample parse(String taggedTokens, boolean isClearAdaptiveData) 
    throws ObjectStreamException {
    String[] parts = WhitespaceTokenizer.INSTANCE.tokenize(taggedTokens);

    List<String> tokenList = new ArrayList<String>(parts.length);
    List<Span> nameList = new ArrayList<Span>();

    String nameType = null;
    int startIndex = -1;
    int wordIndex = 0;
    
    // we check if at least one name has the a type. If no one has, we will
    // leave the NameType property of NameSample null.
    boolean catchingName = false;
    
    Pattern startTagPattern = Pattern.compile("<START(:(\\w*))?>");
    
    for (int pi = 0; pi < parts.length; pi++) {
      Matcher startMatcher = startTagPattern.matcher(parts[pi]);
      if (startMatcher.matches()) {
        if(catchingName) {
          throw new ObjectStreamException("Found unexpected annotation " + parts[pi] + " while handling a name sequence.");
        }
        catchingName = true;
        startIndex = wordIndex;
        nameType = startMatcher.group(2);
        if(nameType != null && nameType.length() == 0) {
          throw new ObjectStreamException("Missing a name type: " + parts[pi]);
        }
          
      }
      else if (parts[pi].equals(NameSampleDataStream.END_TAG)) {
        if(catchingName == false) {
          throw new ObjectStreamException("Found unexpected annotation " + parts[pi] + ".");
        }
        catchingName = false;
        // create name
        nameList.add(new Span(startIndex, wordIndex, nameType));
        
      }
      else {
        tokenList.add(parts[pi]);
        wordIndex++;
      }
    }
    String[] sentence = tokenList.toArray(new String[tokenList.size()]);
    Span[] names = nameList.toArray(new Span[nameList.size()]);
    
    return new NameSample(sentence, names, isClearAdaptiveData);
  }
}
