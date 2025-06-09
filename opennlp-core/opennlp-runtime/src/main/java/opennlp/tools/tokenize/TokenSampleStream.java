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

package opennlp.tools.tokenize;

import java.io.IOException;
import java.util.Objects;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * This class is a {@link FilterObjectStream stream filter} which reads in string encoded
 * samples and creates {@link TokenSample samples} out of them.
 * The input string sample is tokenized if a whitespace or the special separator chars occur.
 * <p>
 * Sample:<br>
 * "token1 token2 token3&lt;SPLIT&gt;token4"<br>
 * The tokens {@code token1} and {@code token2} are separated by a whitespace,
 * {@code token3} and {@code token4} are separated by the special character sequence.
 * In this case, the default split sequence applies.
 * <p>
 * Note: The sequence must be unique in the input string and is not escaped.
 */
public class TokenSampleStream extends FilterObjectStream<String, TokenSample> {

  private final String separatorChars;

  /**
   * Initializes a {@link TokenSampleStream instance}.
   *
   * @param samples A plain text {@link ObjectStream line stream}.
   *                Must not be {@code null}.
   * @param separatorChars The characters to be considered separators.
   *                       See {@link TokenSample#DEFAULT_SEPARATOR_CHARS}.
   *                       Must not be {@code null}.
   */
  public TokenSampleStream(ObjectStream<String> samples, String separatorChars) {
    super(Objects.requireNonNull(samples, "sampleStrings must not be null"));
    this.separatorChars = Objects.requireNonNull(separatorChars,"separatorChars must not be null");
  }

  /**
   * Initializes a {@link TokenSampleStream instance}.
   *
   * @param sentences A plain text {@link ObjectStream line stream}.
   *                  Must not be {@code null}.
   */
  public TokenSampleStream(ObjectStream<String> sentences) {
    this(sentences, TokenSample.DEFAULT_SEPARATOR_CHARS);
  }

  @Override
  public TokenSample read() throws IOException {
    String sampleString = samples.read();

    if (sampleString != null) {
      return TokenSample.parse(sampleString, separatorChars);
    }
    else {
      return null;
    }
  }
}
