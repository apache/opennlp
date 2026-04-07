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

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.BPETokenizer.SymbolPair;
import opennlp.tools.util.Span;

/**
 * Tests for the {@link BPETokenizer} class.
 * <p>
 * Verifies that BPE tokenization correctly splits text into subword tokens
 * based on learned merge operations, and that span positions map back to
 * the original text.
 *
 * @see BPETokenizer
 */
public class BPETokenizerTest {

  private static BPEModel createModel(List<SymbolPair> merges) {
    final BPETokenizerFactory factory = new BPETokenizerFactory("en");
    return new BPEModel(merges, new HashMap<>(), factory);
  }

  /**
   * Tests that a fully merged word produces a single token.
   */
  @Test
  void testBasicBPETokenization() {
    final List<SymbolPair> merges = List.of(
        new SymbolPair("l", "o"),
        new SymbolPair("lo", "w" + BPETokenizer.END_OF_WORD),
        new SymbolPair("e", "r" + BPETokenizer.END_OF_WORD)
    );

    final BPETokenizer tokenizer = new BPETokenizer(createModel(merges));
    final String[] tokens = tokenizer.tokenize("low");

    Assertions.assertArrayEquals(new String[]{"low"}, tokens);
  }

  /**
   * Tests that a word not fully covered by merges is split into subword tokens.
   */
  @Test
  void testSubwordSplitting() {
    final List<SymbolPair> merges = List.of(
        new SymbolPair("l", "o"),
        new SymbolPair("lo", "w" + BPETokenizer.END_OF_WORD)
    );

    final BPETokenizer tokenizer = new BPETokenizer(createModel(merges));
    final String[] tokens = tokenizer.tokenize("lower");

    // "lower" cannot fully merge since "w" is not word-final here
    Assertions.assertTrue(tokens.length > 1);
    Assertions.assertEquals("lower", String.join("", tokens));
  }

  /**
   * Tests tokenization of multiple whitespace-separated words.
   */
  @Test
  void testMultipleWords() {
    final List<SymbolPair> merges = List.of(
        new SymbolPair("l", "o"),
        new SymbolPair("lo", "w" + BPETokenizer.END_OF_WORD)
    );

    final BPETokenizer tokenizer = new BPETokenizer(createModel(merges));
    final String[] tokens = tokenizer.tokenize("low low");

    Assertions.assertEquals(2, tokens.length);
    Assertions.assertEquals("low", tokens[0]);
    Assertions.assertEquals("low", tokens[1]);
  }

  /**
   * Tests that empty and null input produce empty arrays.
   */
  @Test
  void testEmptyInput() {
    final BPETokenizer tokenizer = new BPETokenizer(createModel(List.of()));

    Assertions.assertArrayEquals(new String[0], tokenizer.tokenize(""));
    Assertions.assertArrayEquals(new String[0], tokenizer.tokenize(null));
    Assertions.assertArrayEquals(new Span[0], tokenizer.tokenizePos(""));
    Assertions.assertArrayEquals(new Span[0], tokenizer.tokenizePos(null));
  }

  /**
   * Tests that with no merges, each character becomes a separate token.
   */
  @Test
  void testNoMergesProducesCharacterTokens() {
    final BPETokenizer tokenizer = new BPETokenizer(createModel(List.of()));
    final String[] tokens = tokenizer.tokenize("hi");

    Assertions.assertArrayEquals(new String[]{"h", "i"}, tokens);
  }

  /**
   * Tests single-character word tokenization.
   */
  @Test
  void testSingleCharacterWord() {
    final BPETokenizer tokenizer = new BPETokenizer(createModel(List.of()));
    final String[] tokens = tokenizer.tokenize("a");

    Assertions.assertArrayEquals(new String[]{"a"}, tokens);
  }

  /**
   * Tests that {@link BPETokenizer#tokenizePos(String)} returns correct spans
   * that map back to the original text.
   */
  @Test
  void testTokenizePos() {
    final List<SymbolPair> merges = List.of(
        new SymbolPair("l", "o"),
        new SymbolPair("lo", "w" + BPETokenizer.END_OF_WORD)
    );

    final BPETokenizer tokenizer = new BPETokenizer(createModel(merges));
    final String text = "low hi";
    final Span[] spans = tokenizer.tokenizePos(text);

    // "low" -> 1 token, "hi" -> 2 tokens (no merges for h, i)
    Assertions.assertEquals(3, spans.length);
    Assertions.assertEquals(0, spans[0].getStart());
    Assertions.assertEquals(3, spans[0].getEnd());
    Assertions.assertEquals("low", spans[0].getCoveredText(text));
    // "h"
    Assertions.assertEquals(4, spans[1].getStart());
    Assertions.assertEquals(5, spans[1].getEnd());
    Assertions.assertEquals("h", spans[1].getCoveredText(text));
    // "i"
    Assertions.assertEquals(5, spans[2].getStart());
    Assertions.assertEquals(6, spans[2].getEnd());
    Assertions.assertEquals("i", spans[2].getCoveredText(text));
  }

  /**
   * Tests that span offsets are correct for subword-split words.
   */
  @Test
  void testTokenizePosWithSubwords() {
    final BPETokenizer tokenizer = new BPETokenizer(createModel(List.of()));
    final String text = "ab cd";
    final Span[] spans = tokenizer.tokenizePos(text);

    // "ab" -> a, b; "cd" -> c, d
    Assertions.assertEquals(4, spans.length);
    Assertions.assertEquals("a", spans[0].getCoveredText(text));
    Assertions.assertEquals("b", spans[1].getCoveredText(text));
    Assertions.assertEquals("c", spans[2].getCoveredText(text));
    Assertions.assertEquals("d", spans[3].getCoveredText(text));
  }

  /**
   * Tests that concatenating all tokens reconstructs the original word.
   */
  @Test
  void testTokenConcatenationEqualsOriginal() {
    final List<SymbolPair> merges = List.of(
        new SymbolPair("l", "o"),
        new SymbolPair("lo", "w" + BPETokenizer.END_OF_WORD)
    );

    final BPETokenizer tokenizer = new BPETokenizer(createModel(merges));
    final String[] tokens = tokenizer.tokenize("lower");

    Assertions.assertEquals("lower", String.join("", tokens));
  }

  /**
   * Tests that a null model throws IllegalArgumentException.
   */
  @Test
  void testNullModelThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BPETokenizer(null));
  }

  @Test
  void testSymbolPairNullLeftThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new SymbolPair(null, "b"));
  }

  @Test
  void testSymbolPairNullRightThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new SymbolPair("a", null));
  }

  @Test
  void testSymbolPairEquality() {
    final SymbolPair a = new SymbolPair("lo", "w");
    final SymbolPair b = new SymbolPair("lo", "w");
    final SymbolPair c = new SymbolPair("l", "ow");

    Assertions.assertEquals(a, b);
    Assertions.assertEquals(a.hashCode(), b.hashCode());
    Assertions.assertNotEquals(a, c);
  }

  @Test
  void testSymbolPairToString() {
    final SymbolPair pair = new SymbolPair("lo", "w");
    Assertions.assertEquals("lo w", pair.toString());
  }
}
