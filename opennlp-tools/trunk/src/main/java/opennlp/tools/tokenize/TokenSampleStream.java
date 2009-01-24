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


package opennlp.tools.tokenize;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;

/**
 * The {@link TokenSampleStream} reads token samples form a dictionary.
 * Each dictionary entry is one training sample. If the dictionary entry
 * contains multiple tokens it is assumed that they occur without
 * a whitespace in naturual text and should be splitted.
 *
 * @see Dictionary
 */
public class TokenSampleStream implements Iterator<TokenSample> {

  private Iterator<StringList> samples;

  public TokenSampleStream(Dictionary trainingDictionary) {
    samples = trainingDictionary.iterator();
  }

  public boolean hasNext() {
    return samples.hasNext();
  }

  public TokenSample next() {

    StringList sample = samples.next();

    List<Span> tokenSpans = new ArrayList<Span>();
    StringBuffer text = new StringBuffer();

    for (String token : sample) {
      int begin = text.length();
      text.append(token);

      tokenSpans.add(new Span(begin, text.length()));
    }

    return new TokenSample(text.toString(), tokenSpans.toArray(
        new Span[tokenSpans.size()]));
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }
}
