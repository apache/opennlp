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

/**
 * One subword unit produced by a {@link SubwordTokenizer}, including the model representation
 * and source range.
 *
 * <p>The piece string is in the tokenizer's normalized form and need not equal the input.
 * {@code start} and {@code end} are UTF-16 offsets into the original text, so the
 * surface associated with this piece is {@code text.subSequence(start, end)}. A span can include
 * adjacent source characters when normalization reorders characters. Pieces without source text,
 * such as control symbols, report an empty span with {@code start == end}.</p>
 *
 * <p>The piece, id and span triple follows the representation established by SentencePiece
 * (Kudo and Richardson, 2018), whose {@code SentencePieceText.SentencePiece} message carries
 * {@code piece}, {@code id} and a {@code [begin, end)} range into the input, and gives control
 * symbols an empty surface with {@code begin == end}. The {@code surface} field of that message
 * has no counterpart here because it is {@code text.subSequence(start, end)}. The offsets are
 * UTF-16 indices rather than the UTF-8 byte offsets used there.</p>
 *
 * @param piece The piece in the vocabulary's normalized form; must not be {@code null} or empty.
 * @param id    The non-negative vocabulary id of the piece.
 * @param start The inclusive start offset in the original text.
 * @param end   The exclusive end offset in the original text; at least {@code start}.
 *
 * @see SubwordTokenizer
 * @see <a href="https://aclanthology.org/D18-2012/">Taku Kudo, John Richardson (2018):
 *     SentencePiece: A simple and language independent subword tokenizer and detokenizer for
 *     Neural Text Processing. EMNLP 2018 (system demonstrations), pages 66-71</a>
 * @since 3.0.0
 */
public record SubwordPiece(String piece, int id, int start, int end) {

  /**
   * Instantiates a {@link SubwordPiece}.
   *
   * @throws IllegalArgumentException Thrown if {@code piece} is {@code null} or empty,
   *     {@code id} is negative, or the span is negative or inverted.
   */
  public SubwordPiece {
    if (piece == null) {
      throw new IllegalArgumentException("piece must not be null");
    }
    if (piece.isEmpty()) {
      throw new IllegalArgumentException("piece must not be empty");
    }
    if (id < 0) {
      throw new IllegalArgumentException("id must not be negative");
    }
    if (start < 0) {
      throw new IllegalArgumentException("start must not be negative");
    }
    if (end < start) {
      throw new IllegalArgumentException("end must be at least start");
    }
  }

}
