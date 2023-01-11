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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class MascTokenSampleStream extends FilterObjectStream<MascDocument, TokenSample> {

  private static final Logger logger = LoggerFactory.getLogger(MascTokenSampleStream.class);
  private MascDocument buffer;

  /**
   * Initializes a {@link MascTokenSampleStream}.
   *
   * @param samples The {@link ObjectStream<MascDocument>} samples to process.
   * @throws IOException Thrown if non of the {@link MascDocument documents} had Penn tokenization.
   */
  public MascTokenSampleStream(ObjectStream<MascDocument> samples) throws IOException {
    super(samples);
    try {
      do {
        buffer = samples.read();
      } while (!buffer.hasPennTags()); // For now, we only use Penn tokenization
    } catch (Exception e) {
      throw new IOException("None of the documents has Penn tokenization" +
          e.getMessage());
    }
  }

  @Override
  public TokenSample read() throws IOException {

    /*
     * Read the documents one sentence at a time
     * If the document is over, move to the next one
     * If both document stream and sentence stream are over, return null
     */
    try {
      boolean sentenceFound = true;
      String sentenceString;
      List<Span> tokenSpans;
      MascSentence sentence;
      do {
        sentence = buffer.read();
        while (sentence == null) {
          buffer = samples.read();
          if (buffer == null) {
            return null;
          }
          if (buffer.hasPennTags()) {
            sentence = buffer.read();
          }
        }

        sentenceString = sentence.getTokenText();
        tokenSpans = sentence.getTokensSpans();

        if (sentenceString.length() == 0) {
          logger.warn("Zero sentence found. There is a sentence " +
                  "without any tokens. sentence: {}, spans: {}", sentenceString, tokenSpans);
          sentenceFound = false;
        }

        for (int i = 0; i < tokenSpans.size(); i++) {
          Span t = tokenSpans.get(i);
          if (t.getEnd() - t.getStart() == 0) {
            logger.warn("Zero token found. There is a token without any quarks." +
                    " sentence: {}, spans: {}", sentenceString, tokenSpans);
            sentenceFound = false;
          }
        }


      } while (!sentenceFound);

      Span[] tokensSpansArray = new Span[tokenSpans.size()];
      tokenSpans.toArray(tokensSpansArray);

      return new TokenSample(sentenceString, tokensSpansArray);

    } catch (IOException e) {
      throw new IOException("Could not get a sample of tokens from the data.");
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
