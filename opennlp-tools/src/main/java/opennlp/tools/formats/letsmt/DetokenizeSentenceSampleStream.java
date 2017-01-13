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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.tokenize.Detokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class DetokenizeSentenceSampleStream
    extends FilterObjectStream<SentenceSample, SentenceSample> {

  private final Detokenizer detokenizer;

  public DetokenizeSentenceSampleStream(Detokenizer detokenizer, ObjectStream<SentenceSample> samples) {
    super(samples);
    this.detokenizer = Objects.requireNonNull(detokenizer);
  }

  @Override
  public SentenceSample read() throws IOException {

    SentenceSample sample = samples.read();

    if (sample != null) {
      List<String> sentenceTexts = new ArrayList<>();

      for (Span sentenceSpan : sample.getSentences()) {
        sentenceTexts.add(sample.getDocument().substring(sentenceSpan.getStart(), sentenceSpan.getEnd()));
      }

      StringBuilder documentText = new StringBuilder();
      List<Span> newSentenceSpans = new ArrayList<>();
      for (String sentenceText : sentenceTexts) {
        String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(sentenceText);

        int begin = documentText.length();

        documentText.append(detokenizer.detokenize(tokens, null));
        newSentenceSpans.add(new Span(begin, documentText.length()));
        documentText.append(' ');
      }

      return new SentenceSample(documentText, newSentenceSpans.toArray(new Span[newSentenceSpans.size()]));
    }

    return null;
  }
}
