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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.maxent.DataStream;
import opennlp.tools.util.Span;

/**
 * The {@link NameSampleDataStream} class converts tagged {@link String}s
 * provided by a {@link DataStream} to {@link NameSample} objects.
 * It uses text that is is one-sentence per line and tokenized
 * with names identified by <code>&lt;START&gt;</code> and <code>&lt;END&gt;</code> tags. 
 */
public class NameSampleDataStream implements Iterator<NameSample> {

  public static final String START_TAG = "<START>";

  public static final String END_TAG = "<END>";
  
  private final DataStream in;

  public NameSampleDataStream(DataStream in) {
    this.in = in;
  }

  /* (non-Javadoc)
   * @see opennlp.tools.namefind.NameSampleStream#hasNext()
   */
  public boolean hasNext() {
    return in.hasNext();
  }

  /* (non-Javadoc)
   * @see opennlp.tools.namefind.NameSampleStream#nextNameSample()
   */
  public NameSample next() {
    String token = (String) in.nextToken();
    // clear adaptive data for every empty line
    return createNameSample(token);
  }
  
  private NameSample createNameSample(String taggedTokens) {
    String[] parts = taggedTokens.split(" ");

    List<String> tokenList = new ArrayList<String>(parts.length);
    List<Span> nameList = new ArrayList<Span>();

    int startIndex = -1;
    int wordIndex = 0;
    for (int pi = 0; pi < parts.length; pi++) {
      if (parts[pi].equals(START_TAG)) {
        startIndex = wordIndex;
      } 
      else if (parts[pi].equals(END_TAG)) {
        // create name
        nameList.add(new Span(startIndex, wordIndex));
      } 
      else {
        tokenList.add(parts[pi]);
        wordIndex++;
      }
    }
    String[] sentence = (String[]) tokenList.toArray(new String[tokenList.size()]);
    Span[] names = (Span[]) nameList.toArray(new Span[nameList.size()]);
    return new NameSample(sentence,names,sentence.length==0);
  }
  
  public void remove() {
    throw new UnsupportedOperationException();
  }
}