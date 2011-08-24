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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;

/**
 * Class for holding names for a single unit of text.
 */
public class NameSample {

  private final List<String> sentence;
  private final List<Span> names;
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
      throw new IllegalArgumentException("sentence must not be null!");
    }

    if (names == null) {
      names = new Span[0];
    }

    this.sentence = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(sentence)));
    this.names = Collections.unmodifiableList(new ArrayList<Span>(Arrays.asList(names)));
    
    if (additionalContext != null) {
      this.additionalContext = new String[additionalContext.length][];
      
      for (int i = 0; i < additionalContext.length; i++) {
        this.additionalContext[i] = new String[additionalContext[i].length];
        System.arraycopy(additionalContext[i], 0, this.additionalContext[i], 0, additionalContext[i].length);
      }
    }
    else {
      this.additionalContext = null;
    }
    isClearAdaptiveData = clearAdaptiveData;
    
    // TODO: Check that name spans are not overlapping, otherwise throw exception
  }

  public NameSample(String[] sentence, Span[] names, boolean clearAdaptiveData) {
    this(sentence, names, null, clearAdaptiveData);
  }
  
  public String[] getSentence() {
    return sentence.toArray(new String[sentence.size()]);
  }

  public Span[] getNames() {
    return names.toArray(new Span[names.size()]);
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
      return false;
    }
    
  }
  
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();

    // If adaptive data must be cleared insert an empty line
    // before the sample sentence line
    if (isClearAdaptiveDataSet())
      result.append("\n");
    
    for (int tokenIndex = 0; tokenIndex < sentence.size(); tokenIndex++) {
      // token

      for (int nameIndex = 0; nameIndex < names.size(); nameIndex++) {
        if (names.get(nameIndex).getStart() == tokenIndex) {
          // check if nameTypes is null, or if the nameType for this specific
          // entity is empty. If it is, we leave the nameType blank.
          if (names.get(nameIndex).getType() == null) {
            result.append(NameSampleDataStream.START_TAG).append(' ');
          }
          else {
            result.append(NameSampleDataStream.START_TAG_PREFIX).append(names.get(nameIndex).getType()).append("> ");
          }
        }

        if (names.get(nameIndex).getEnd() == tokenIndex) {
          result.append(NameSampleDataStream.END_TAG).append(' ');
        }
      }

      result.append(sentence.get(tokenIndex) + ' ');
    }

    if (sentence.size() > 1)
      result.setLength(result.length() - 1);
    
    for (int nameIndex = 0; nameIndex < names.size(); nameIndex++) {
      if (names.get(nameIndex).getEnd() == sentence.size()) {
        result.append(' ').append(NameSampleDataStream.END_TAG);
      }
    }

    return result.toString();
  }
  
  private static String errorTokenWithContext(String sentence[], int index) {
    
    StringBuilder errorString = new StringBuilder();
    
    // two token before
    if (index > 1)
      errorString.append(sentence[index -2]).append(" ");
    
    if (index > 0)
      errorString.append(sentence[index -1]).append(" ");
    
    // token itself
    errorString.append("###");
    errorString.append(sentence[index]);
    errorString.append("###").append(" ");
    
    // two token after
    if (index + 1 < sentence.length)
      errorString.append(sentence[index + 1]).append(" ");

    if (index + 2 < sentence.length)
      errorString.append(sentence[index + 2]);
    
    return errorString.toString();
  }
  
  private static final Pattern START_TAG_PATTERN = Pattern.compile("<START(:([^:>\\s]*))?>");
  
  public static NameSample parse(String taggedTokens, boolean isClearAdaptiveData)
    // TODO: Should throw another exception, and then convert it into an IOException in the stream
    throws IOException {
    String[] parts = WhitespaceTokenizer.INSTANCE.tokenize(taggedTokens);

    List<String> tokenList = new ArrayList<String>(parts.length);
    List<Span> nameList = new ArrayList<Span>();

    String nameType = null;
    int startIndex = -1;
    int wordIndex = 0;
    
    // we check if at least one name has the a type. If no one has, we will
    // leave the NameType property of NameSample null.
    boolean catchingName = false;
    
    for (int pi = 0; pi < parts.length; pi++) {
      Matcher startMatcher = START_TAG_PATTERN.matcher(parts[pi]);
      if (startMatcher.matches()) {
        if(catchingName) {
          throw new IOException("Found unexpected annotation" + 
              " while handling a name sequence: " + errorTokenWithContext(parts, pi));
        }
        catchingName = true;
        startIndex = wordIndex;
        nameType = startMatcher.group(2);
        if(nameType != null && nameType.length() == 0) {
          throw new IOException("Missing a name type: " + errorTokenWithContext(parts, pi));
        }
          
      }
      else if (parts[pi].equals(NameSampleDataStream.END_TAG)) {
        if(catchingName == false) {
          throw new IOException("Found unexpected annotation: " + errorTokenWithContext(parts, pi));
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
