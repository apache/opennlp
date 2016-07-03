/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentiment;

import java.io.IOException;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Class for converting Strings through Data Stream to SentimentSample using
 * tokenised text.
 */
public class SentimentSampleStream
    extends FilterObjectStream<String, SentimentSample> {

  /**
   * Initializes the sample stream.
   *
   * @param samples
   *          the sentiment samples to be used
   */
  public SentimentSampleStream(ObjectStream<String> samples) {
    super(samples);
  }

  /**
   * Reads the text
   *
   * @return a ready-to-be-trained SentimentSample object
   */
  @Override
  public SentimentSample read() throws IOException {
    String sentence = samples.read();

    if (sentence != null) {

      // Whitespace tokenize entire string
      String tokens[] = WhitespaceTokenizer.INSTANCE.tokenize(sentence);

      SentimentSample sample;

      if (tokens.length > 1) {
        String sentiment = tokens[0];
        String sentTokens[] = new String[tokens.length - 1];
        System.arraycopy(tokens, 1, sentTokens, 0, tokens.length - 1);

        sample = new SentimentSample(sentiment, sentTokens);
      } else {
        throw new IOException(
            "Empty lines, or lines with only a category string are not allowed!");
      }

      return sample;
    }

    return null;
  }

}
