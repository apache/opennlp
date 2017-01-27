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

package opennlp.tools.formats.letsmt;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

class LetsmtSentenceStream implements ObjectStream<SentenceSample>  {

  private final LetsmtDocument source;

  private Iterator<LetsmtDocument.LetsmtSentence> sentenceIt;

  LetsmtSentenceStream(LetsmtDocument source) {
    this.source = source;
    reset();
  }

  @Override
  public SentenceSample read() throws IOException {

    StringBuilder sentencesString = new StringBuilder();
    List<Span> sentenceSpans = new LinkedList<>();

    for (int i = 0; sentenceIt.hasNext() && i < 25 ; i++) {
      LetsmtDocument.LetsmtSentence sentence = sentenceIt.next();

      int begin = sentencesString.length();

      if (sentence.getTokens() != null) {
        sentencesString.append(String.join(" ", sentence.getTokens()));
      } else if (sentence.getNonTokenizedText() != null) {
        sentencesString.append(sentence.getNonTokenizedText());
      }

      sentenceSpans.add(new Span(begin, sentencesString.length()));
      sentencesString.append(' ');
    }

    // end of stream is reached, indicate that with null return value
    if (sentenceSpans.size() == 0) {
      return null;
    }

    return new SentenceSample(sentencesString.toString(),
        sentenceSpans.toArray(new Span[sentenceSpans.size()]));
  }

  @Override
  public void reset() {
    sentenceIt = source.getSentences().iterator();
  }
}
