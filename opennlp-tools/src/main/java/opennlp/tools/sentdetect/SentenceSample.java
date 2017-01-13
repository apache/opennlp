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

package opennlp.tools.sentdetect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import opennlp.tools.tokenize.Detokenizer;
import opennlp.tools.util.Span;

/**
 * A {@link SentenceSample} contains a document with
 * begin indexes of the individual sentences.
 */
public class SentenceSample {

  private final String document;

  private final List<Span> sentences;

  /**
   * Initializes the current instance.
   *
   * @param document
   * @param sentences
   */
  public SentenceSample(CharSequence document, Span... sentences) {
    this.document = document.toString();
    this.sentences = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(sentences)));
  }

  public SentenceSample(Detokenizer detokenizer, String[][] sentences) {

    List<Span> spans = new ArrayList<>(sentences.length);

    StringBuilder documentBuilder = new StringBuilder();

    for (String sentenceTokens[] : sentences) {

      String sampleSentence = detokenizer.detokenize(sentenceTokens, null);

      int beginIndex = documentBuilder.length();
      documentBuilder.append(sampleSentence);

      spans.add(new Span(beginIndex, documentBuilder.length()));
    }

    document = documentBuilder.toString();
    this.sentences = Collections.unmodifiableList(spans);
  }

  /**
   * Retrieves the document.
   *
   * @return the document
   */
  public String getDocument() {
    return document;
  }

  /**
   * Retrieves the sentences.
   *
   * @return the begin indexes of the sentences in the document.

   */
  public Span[] getSentences() {
    return sentences.toArray(new Span[sentences.size()]);
  }

  // TODO: This one must output the tags!
  @Override
  public String toString() {
    StringBuilder documentBuilder = new StringBuilder();
    for (Span sentSpan : sentences) {
      documentBuilder.append(sentSpan.getCoveredText(document).toString()
          .replace("\r", "<CR>").replace("\n", "<LF>"));
      documentBuilder.append("\n");
    }
    return documentBuilder.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getDocument(), Arrays.hashCode(getSentences()));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof SentenceSample) {
      SentenceSample a = (SentenceSample) obj;

      return getDocument().equals(a.getDocument())
          && Arrays.equals(getSentences(), a.getSentences());
    }

    return false;
  }
}
