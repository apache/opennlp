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

package opennlp.tools.formats.masc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class MascSentenceSampleStream extends FilterObjectStream<MascDocument, SentenceSample> {

  private final int sentencesPerSample;
  private MascDocument buffer;

  public MascSentenceSampleStream(ObjectStream<MascDocument> samples, int sentencesPerSample)
      throws IOException {
    super(samples);
    this.sentencesPerSample = sentencesPerSample;
    buffer = samples.read();
  }

  /**
   * Reads a new {@link SentenceSample sample of sentences}.
   *
   * @return The {@link SentenceSample specified number of sentences}.
   *         If fewer left, then return whatever is left.
   *
   * @throws IOException Thrown if IO errors occurred during read operation.
   */
  @Override
  public SentenceSample read() throws IOException {

    try {
      StringBuilder documentText = new StringBuilder();
      List<Span> sentenceSpans = new ArrayList<>();

      for (int i = 0; i < sentencesPerSample; i++) {
        MascSentence sentence = buffer.read();
        if (sentence != null) {
          // Current document still has sentences
          int startIndex = documentText.length();
          documentText.append(sentence.getSentDetectText()).append(' ');
          sentenceSpans.add(new Span(startIndex, documentText.length() - 1));
        } else if ((buffer = samples.read()) != null) {
          documentText.append('\n');
          // Current document exhausted, but we can still move on to the next one
          i--; // This round does not count
        } else {
          // We exhausted all sentences in all documents
          break;
        }
      }

      if (documentText.length() > 0) {
        documentText.setLength(documentText.length() - 1);
        return new SentenceSample(documentText,
            sentenceSpans.toArray(new Span[0]));
      }

      return null;
    } catch (IOException e) {
      throw new IOException("You are reading an empty document stream. " +
          "Did you close it?");
    }
  }

  @Override
  public void close() throws IOException {
    samples.close();
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    samples.reset();
    buffer = samples.read();
  }
}
