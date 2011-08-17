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


package opennlp.tools.postag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;

/**
 * Represents an pos-tagged sentence.
 */
public class POSSample {

  private List<String> sentence;

  private List<String> tags;

  public POSSample(String sentence[], String tags[]) {
    this.sentence = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(sentence)));
    this.tags = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(tags)));
    
    checkArguments();
  }
  
  public POSSample(List<String> sentence, List<String> tags) {
    this.sentence = Collections.unmodifiableList(new ArrayList<String>(sentence));
    this.tags = Collections.unmodifiableList(new ArrayList<String>(tags));
    
    checkArguments();
  }
  
  private void checkArguments() {
    if (sentence.size() != tags.size())
      throw new IllegalArgumentException(
      "There must be exactly one tag for each token!");
    
      if (sentence.contains(null) || tags.contains(null))
        throw new IllegalArgumentException("null elements are not allowed!");
  }
  
  public String[] getSentence() {
    return sentence.toArray(new String[sentence.size()]);
  }

  public String[] getTags() {
    return tags.toArray(new String[tags.size()]);
  }

  @Override
  public String toString() {

    StringBuilder result = new StringBuilder();

    for (int i = 0; i < getSentence().length; i++) {
      result.append(getSentence()[i]);
      result.append('_');
      result.append(getTags()[i]);
      result.append(' ');
    }

    if (result.length() > 0) {
      // get rid of last space
      result.setLength(result.length() - 1);
    }

    return result.toString();
  }

  public static POSSample parse(String sentenceString) throws InvalidFormatException {

    String tokenTags[] = WhitespaceTokenizer.INSTANCE.tokenize(sentenceString);

    String sentence[] = new String[tokenTags.length];
    String tags[] = new String[tokenTags.length];

    for (int i = 0; i < tokenTags.length; i++) {
      int split = tokenTags[i].lastIndexOf("_");

      if (split == -1) {
        throw new InvalidFormatException("Cannot find \"_\" inside token!");
      }

      sentence[i] = tokenTags[i].substring(0, split);
      tags[i] = tokenTags[i].substring(split+1);
    }

    return new POSSample(sentence, tags);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof POSSample) {
      POSSample a = (POSSample) obj;

      return Arrays.equals(getSentence(), a.getSentence())
          && Arrays.equals(getTags(), a.getTags());
    } else {
      return false;
    }
  }
}
