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
 * One subword unit produced by a {@link SubwordTokenizer}, with its vocabulary representation
 * and source range.
 *
 * <p>The piece string is in the tokenizer's normalized form, so it is generally not a substring of
 * the input. {@code start} and {@code end} are UTF-16 offsets into the original text, so the
 * surface that produced this piece is {@code text.subSequence(start, end)}. Pieces without source
 * text, such as control symbols or the fill bytes of a byte-fallback expansion,
 * report an empty span with {@code start == end}.</p>
 *
 * @param piece The piece in the vocabulary's normalized form; must not be {@code null} or empty.
 * @param id    The non-negative vocabulary id of the piece.
 * @param start The inclusive start offset in the original text.
 * @param end   The exclusive end offset in the original text; not less than {@code start}.
 */
public record SubwordPiece(String piece, int id, int start, int end) {

  /**
   * Instantiates a {@link SubwordPiece}.
   *
   * @throws IllegalArgumentException Thrown if {@code piece} is {@code null} or empty, {@code id} is
   *     negative, or the span is negative or inverted.
   */
  public SubwordPiece {
    if (piece == null || piece.isEmpty()) {
      throw new IllegalArgumentException("piece must not be null or empty");
    }
    if (id < 0) {
      throw new IllegalArgumentException("id must not be negative");
    }
    if (start < 0) {
      throw new IllegalArgumentException("start must not be negative");
    }
    if (end < start) {
      throw new IllegalArgumentException("end must not be less than start");
    }
  }

  /** {@return the original-text span of this piece as a {@link Span}} */
  public Span span() {
    return new Span(start, end);
  }
}
