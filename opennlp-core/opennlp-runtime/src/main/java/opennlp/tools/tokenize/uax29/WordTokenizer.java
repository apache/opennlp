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
package opennlp.tools.tokenize.uax29;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

/**
 * A word tokenizer built on the Unicode Text Segmentation algorithm (UAX #29). It finds segments
 * with {@link WordSegmenter}, keeps the ones that are words (letters, digits, ideographs, kana,
 * Hangul, Southeast-Asian script, or emoji), drops whitespace and punctuation, and classifies each
 * kept token with a {@link WordType}. Emoji here means any {@code Extended_Pictographic} code point,
 * so symbol-like characters such as the copyright, trademark, and double-exclamation signs are kept
 * (typed {@link WordType#EMOJI}) rather than dropped as punctuation.
 *
 * <p>A token longer than {@code maxTokenLength} is emitted as consecutive pieces, never splitting a
 * surrogate pair. The tokenizer reports offset {@link Span}s, so the original text and its character
 * offsets are preserved for downstream normalization.</p>
 *
 * <p>It implements {@link Tokenizer}: {@link #tokenize(String)} returns the token strings and
 * {@link #tokenizePos(String)} their offsets. {@link #tokenizeTyped(CharSequence)} additionally
 * carries each token's {@link WordType}, and {@link #tokenize(CharSequence, TokenHandler)} streams
 * tokens with no per-token allocation. Instances are immutable and thread-safe.</p>
 */
// Implements Tokenizer directly rather than extending AbstractTokenizer: this tokenizer produces
// its spans from the UAX #29 segmenter in one pass and shares none of AbstractTokenizer's
// per-character probability/merge machinery, so subclassing it would only add unused state.
public final class WordTokenizer implements Tokenizer {

  /** Receives each word token as a character range and its type, with no allocation. */
  @FunctionalInterface
  public interface TokenHandler {
    /**
     * Accepts one word token.
     *
     * @param start The inclusive start character offset.
     * @param end   The exclusive end character offset.
     * @param type  The token category.
     */
    void token(int start, int end, WordType type);
  }

  /** The default maximum token length. */
  public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

  private final int maxTokenLength;

  /**
   * Creates a tokenizer with the {@linkplain #DEFAULT_MAX_TOKEN_LENGTH default} maximum token
   * length.
   */
  public WordTokenizer() {
    this(DEFAULT_MAX_TOKEN_LENGTH);
  }

  /**
   * Creates a tokenizer with the given maximum token length.
   *
   * @param maxTokenLength The maximum number of characters in a token; longer tokens are chopped
   *                       into consecutive pieces. Must be at least {@code 1}.
   * @throws IllegalArgumentException if {@code maxTokenLength} is less than {@code 1}.
   */
  public WordTokenizer(int maxTokenLength) {
    if (maxTokenLength < 1) {
      throw new IllegalArgumentException("maxTokenLength must be at least 1, got " + maxTokenLength);
    }
    this.maxTokenLength = maxTokenLength;
  }

  /**
   * Streams the word tokens of {@code text} to {@code handler} in order, allocating nothing.
   *
   * @param text    The text to tokenize.
   * @param handler The receiver of the tokens.
   */
  public void tokenize(CharSequence text, TokenHandler handler) {
    WordSegmenter.forEachSegment(text, (start, end) -> {
      final WordType type = WordType.of(text, start, end);
      if (type != null) {
        emit(text, start, end, type, handler);
      }
    });
  }

  /**
   * Returns the word tokens of {@code s} as strings, in order.
   *
   * @param s The text to tokenize.
   * @return The token strings.
   */
  @Override
  public String[] tokenize(String s) {
    final List<String> tokens = new ArrayList<>();
    tokenize(s, (start, end, type) -> tokens.add(s.substring(start, end)));
    return tokens.toArray(new String[0]);
  }

  /**
   * Returns the offset spans of the word tokens of {@code s}, in order.
   *
   * @param s The text to tokenize.
   * @return The token spans.
   */
  @Override
  public Span[] tokenizePos(String s) {
    final List<Span> spans = tokenizeSpans(s);
    return spans.toArray(new Span[0]);
  }

  /**
   * Returns the offset spans of the word tokens in {@code text}, in order.
   *
   * @param text The text to tokenize.
   * @return The word-token spans.
   */
  public List<Span> tokenizeSpans(CharSequence text) {
    final List<Span> spans = new ArrayList<>();
    tokenize(text, (start, end, type) -> spans.add(new Span(start, end)));
    return spans;
  }

  /**
   * Returns the word tokens in {@code text}, each carrying its {@link WordType}, in order.
   *
   * @param text The text to tokenize.
   * @return The typed word tokens.
   */
  public List<WordToken> tokenizeTyped(CharSequence text) {
    final List<WordToken> tokens = new ArrayList<>();
    tokenize(text, (start, end, type) -> tokens.add(new WordToken(new Span(start, end), type)));
    return tokens;
  }

  // Emits [start, end) as one or more tokens no longer than maxTokenLength, never splitting a
  // surrogate pair. The whole word is classified once and every piece carries that type.
  private void emit(CharSequence text, int start, int end, WordType type, TokenHandler handler) {
    int from = start;
    while (end - from > maxTokenLength) {
      int cut = from + maxTokenLength;
      if (Character.isHighSurrogate(text.charAt(cut - 1))) {
        cut--; // keep the surrogate pair together
      }
      if (cut <= from) {
        // maxTokenLength is shorter than the leading code point; emit it whole rather than stall.
        cut = from + Character.charCount(Character.codePointAt(text, from));
      }
      handler.token(from, cut, type);
      from = cut;
    }
    if (from < end) {
      handler.token(from, end, type);
    }
  }
}
