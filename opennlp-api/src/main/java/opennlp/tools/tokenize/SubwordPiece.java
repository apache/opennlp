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

import opennlp.tools.util.Span;

/**
 * One subword unit produced by a {@link SubwordTokenizer}, carrying both the vocabulary view
 * (the piece string and its id) and the exact place in the caller's text it came from.
 *
 * <p>The piece string is in the tokenizer's internal, normalized form (for example, a leading
 * word-boundary marker instead of a space), so it is generally not a substring of the input.
 * {@code start} and {@code end} are UTF-16 offsets into the original input text, so the surface
 * that produced this piece is {@code text.subSequence(start, end)}. Pieces that carry no surface
 * of their own (control symbols, or the fill bytes of a byte-fallback expansion) report an empty
 * span, {@code start == end}.</p>
 *
 * @param piece The piece in the vocabulary's normalized form; never null or empty.
 * @param id    The vocabulary id of the piece.
 * @param start The inclusive start offset in the original text.
 * @param end   The exclusive end offset in the original text; not less than {@code start}.
 */
public record SubwordPiece(String piece, int id, int start, int end) {

  /**
   * Instantiates a {@link SubwordPiece}.
   *
   * @throws IllegalArgumentException Thrown if {@code piece} is null or empty, or the span is
   *     negative or inverted.
   */
  public SubwordPiece {
    if (piece == null || piece.isEmpty()) {
      throw new IllegalArgumentException("The piece must not be null or empty.");
    }
    if (start < 0 || end < start) {
      throw new IllegalArgumentException(
          "The span [" + start + ", " + end + ") must not be negative or inverted.");
    }
  }

  /** {@return the original-text span of this piece as a {@link Span}} */
  public Span span() {
    return new Span(start, end);
  }
}
