/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WordpieceEncoder;
import opennlp.tools.util.Span;

/**
 * Adapts a {@link WordpieceEncoder} to the {@link Tokenizer} plumbing of the inference
 * classes: {@link #tokenize(String)} returns the encoder's piece strings.
 */
final class EncoderTokenizer implements Tokenizer {

  private final WordpieceEncoder encoder;

  /**
   * Instantiates the adapter.
   *
   * @param encoder The encoder whose pieces this tokenizer returns.
   */
  EncoderTokenizer(final WordpieceEncoder encoder) {
    this.encoder = encoder;
  }

  /** {@inheritDoc} */
  @Override
  public String[] tokenize(final String text) {
    return encoder.encodeToPieces(text);
  }

  /**
   * Not supported under the {@link Tokenizer} contract, whose spans are expected to contain
   * their token's surface form; wordpiece pieces are not substrings of the input. Use
   * {@link WordpieceEncoder#encode(CharSequence)} for pieces with original-text spans.
   *
   * @throws UnsupportedOperationException Always.
   */
  @Override
  public Span[] tokenizePos(final String text) {
    throw new UnsupportedOperationException(
        "Wordpiece tokens cannot be mapped to character spans of the original text");
  }
}
