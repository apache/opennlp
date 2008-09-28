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

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ParseException;

public class POSSample {
  
  private String sentence[];
  
  private String tags[];
  
  public POSSample(String sentence[], String tags[]) {
    
    if (sentence.length != tags.length)
        throw new IllegalArgumentException(
        "There must be exactly one tag for each token!");
    
    this.sentence = sentence;
    this.tags = tags;
  }
  
  public String[] getSentence() {
    return sentence;
  }
  
  public String[] getTags() {
    return tags;
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
  
  public static POSSample parse(String sentenceString) throws ParseException {
    
    String tokenTags[] = WhitespaceTokenizer.INSTANCE.tokenize(sentenceString);
    
    String sentence[] = new String[tokenTags.length];
    String tags[] = new String[tokenTags.length];
    
    for (int i = 0; i < tokenTags.length; i++) {
      int split = tokenTags[i].lastIndexOf("_");
      
      if (split == -1) {
        throw new ParseException("Cannot find \"_\" inside token!");
      }
      
      sentence[i] = tokenTags[i].substring(0, split);
      tags[i] = tokenTags[i].substring(split+1);
    }
    
    return new POSSample(sentence, tags);
  }
}