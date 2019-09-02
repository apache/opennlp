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

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class MascTokenSampleStream extends FilterObjectStream<MascDocument, TokenSample> {

  MascDocument buffer;

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

  public TokenSample read() throws IOException {

    /* Read the documents one sentence at a time
    If the document is over, move to the next one
    If both document stream and sentence stream are over, return null
     */
    try {
      boolean sentenceFound = true;
      String sentenceString;
      List<Span> tokensSpans;
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
        tokensSpans = sentence.getTokensSpans();

        if (sentenceString.length() == 0) {
          System.err.println("[WARNING] Zero sentence found: " +
              "there is a sentence without any tokens.");
          System.err.println(sentenceString);
          System.err.println(tokensSpans.toString());
          sentenceFound = false;
        }

        for (int i = 0; i < tokensSpans.size(); i++) {
          Span t = tokensSpans.get(i);
          if (t.getEnd() - t.getStart() == 0) {
            System.err.println("[WARNING] Zero token found: " +
                "there is a token without any quarks.");
            System.err.println(sentenceString);
            System.err.println(tokensSpans.toString());
            sentenceFound = false;
          }
        }


      } while (!sentenceFound);

      Span[] tokensSpansArray = new Span[tokensSpans.size()];
      tokensSpans.toArray(tokensSpansArray);

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
