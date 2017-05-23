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

package opennlp.tools.formats.conllu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class ConlluSentenceSampleStream extends FilterObjectStream<ConlluSentence, SentenceSample> {

  private final int sentencesPerSample;

  public ConlluSentenceSampleStream(ObjectStream<ConlluSentence> samples, int sentencesPerSample) {
    super(samples);
    this.sentencesPerSample = sentencesPerSample;
  }

  @Override
  public SentenceSample read() throws IOException {
    StringBuilder documentText = new StringBuilder();

    List<Span> sentenceSpans = new ArrayList<>();

    ConlluSentence sentence;
    for (int i = 0; i <  sentencesPerSample && (sentence = samples.read()) != null; i++) {

      int startIndex = documentText.length();
      documentText.append(sentence.getTextComment()).append(' ');
      sentenceSpans.add(new Span(startIndex, documentText.length() - 1));
    }

    if (documentText.length() > 0) {
      documentText.setLength(documentText.length() - 1);
      return new SentenceSample(documentText, sentenceSpans.toArray(new Span[sentenceSpans.size()]));
    }

    return null;
  }
}
