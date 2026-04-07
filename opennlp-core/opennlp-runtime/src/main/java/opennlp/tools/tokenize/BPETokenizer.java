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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import opennlp.tools.util.Span;

/**
 * A {@link Tokenizer} implementation that performs subword tokenization
 * using Byte Pair Encoding (BPE).
 * <p>
 * BPE iteratively merges the most frequent pair of adjacent symbols,
 * starting from a character-level representation of each word. This allows
 * the tokenizer to handle out-of-vocabulary words by decomposing them into
 * known subword units.
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * // Train a BPE model from a corpus
 * BPETokenizerTrainer trainer = new BPETokenizerTrainer();
 * BPEModel model = trainer.train(corpus, 10000, "en");
 *
 * // Save the model for later reuse
 * model.serialize(Path.of("bpe-en.bin"));
 *
 * // Load and tokenize
 * BPEModel loaded = new BPEModel(Path.of("bpe-en.bin"));
 * BPETokenizer tokenizer = new BPETokenizer(loaded);
 * String[] tokens = tokenizer.tokenize("unseen words are split into subwords");
 * }</pre>
 * <p>
 * The tokenizer first splits text on whitespace, then applies learned merge
 * operations to each word independently. Words are decomposed into characters
 * with an {@link #END_OF_WORD} marker on the final character, then merges are
 * applied in priority order (as learned during training) until no more merges
 * are applicable. The resulting subword units are returned as tokens.
 * <p>
 * For reference see:
 * <ul>
 *   <li>Sennrich, R., Haddow, B., &amp; Birch, A. (2016).
 *       Neural Machine Translation of Rare Words with Subword Units.
 *       <a href="https://arxiv.org/abs/1508.07909">
 *       https://arxiv.org/abs/1508.07909</a>
 *   </li>
 * </ul>
 *
 * @see BPEModel
 * @see BPETokenizerTrainer
 * @see WordpieceTokenizer
 */
public class BPETokenizer implements Tokenizer {

  /**
   * Suffix appended to the last symbol of each word during BPE encoding
   * to distinguish word-final characters from word-internal ones.
   * <p>
   * Users constructing {@link SymbolPair} merge rules manually must use this
   * constant to mark word-final symbols
   * (e.g., {@code new SymbolPair("a", "b" + END_OF_WORD)}).
   */
  public static final String END_OF_WORD = "</w>";

  /** Maps each merge pair to its priority rank. */
  private final LinkedHashMap<SymbolPair, Integer> mergeRanks;

  /**
   * Initializes a {@link BPETokenizer} from a trained
   * {@link BPEModel}.
   *
   * @param model The trained BPE model. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code model} is {@code null}.
   */
  public BPETokenizer(final BPEModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    final List<SymbolPair> merges = model.getMerges();
    this.mergeRanks = new LinkedHashMap<>();
    for (int i = 0; i < merges.size(); i++) {
      mergeRanks.put(merges.get(i), i);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Splits the input text on whitespace, then applies BPE merge operations
   * to each word to produce subword tokens. Words not fully covered by
   * learned merges are decomposed into individual characters.
   */
  @Override
  public String[] tokenize(final String text) {
    if (text == null || text.isEmpty()) {
      return new String[0];
    }

    final String[] words = WhitespaceTokenizer.INSTANCE.tokenize(text);
    final List<String> allTokens = new ArrayList<>();

    for (final String word : words) {
      allTokens.addAll(encodeToBPE(word));
    }

    return allTokens.toArray(new String[0]);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Returns {@link Span} offsets into the original text for each subword token.
   * Each span maps back to the exact character range in the input string.
   */
  @Override
  public Span[] tokenizePos(final String text) {
    if (text == null || text.isEmpty()) {
      return new Span[0];
    }

    final Span[] wordSpans = WhitespaceTokenizer.INSTANCE.tokenizePos(text);
    final List<Span> allSpans = new ArrayList<>();

    for (final Span wordSpan : wordSpans) {
      final String word = wordSpan.getCoveredText(text).toString();
      final List<String> symbols = splitToSymbols(word);
      final List<String> merged = applyMerges(symbols);

      int offset = wordSpan.getStart();
      for (final String token : merged) {
        String clean = token.endsWith(END_OF_WORD)
            ? token.substring(0, token.length() - END_OF_WORD.length())
            : token;
        int len = clean.length();
        allSpans.add(new Span(offset, offset + len));
        offset += len;
      }
    }

    return allSpans.toArray(new Span[0]);
  }

  /**
   * Splits a word into its initial character-level BPE symbol sequence.
   * Each character becomes its own symbol, with {@link #END_OF_WORD} appended
   * to the final character.
   *
   * @param word The word to split. Must not be {@code null} or empty.
   * @return A mutable list of character symbols.
   */
  private List<String> splitToSymbols(final String word) {
    final List<String> symbols = new ArrayList<>(word.length());
    for (int i = 0; i < word.length(); i++) {
      if (i == word.length() - 1) {
        symbols.add(word.charAt(i) + END_OF_WORD);
      } else {
        symbols.add(String.valueOf(word.charAt(i)));
      }
    }
    return symbols;
  }

  /**
   * Encodes a single word into BPE subword tokens by splitting it into
   * character-level symbols, applying learned merge operations, and stripping
   * the {@link #END_OF_WORD} markers from the resulting tokens.
   *
   * @param word The word to encode. Must not be {@code null}.
   * @return A list of subword token strings whose concatenation equals the original word.
   */
  private List<String> encodeToBPE(final String word) {
    if (word.isEmpty()) {
      return List.of();
    }

    final List<String> symbols = splitToSymbols(word);
    final List<String> merged = applyMerges(symbols);

    // Strip end-of-word markers and collect final tokens
    final List<String> result = new ArrayList<>();
    for (final String token : merged) {
      if (token.endsWith(END_OF_WORD)) {
        result.add(token.substring(0, token.length() - END_OF_WORD.length()));
      } else {
        result.add(token);
      }
    }

    return result;
  }

  /**
   * Iteratively applies learned BPE merge operations to a list of symbols.
   * In each iteration, the highest-priority (lowest-rank) adjacent pair is merged
   * into a single symbol. The process continues until no more applicable merges
   * remain or the symbol list is reduced to a single element.
   *
   * @param symbols The mutable list of symbols to merge. Must not be {@code null}.
   * @return The list of symbols after all applicable merges have been applied.
   */
  private List<String> applyMerges(final List<String> symbols) {
    if (symbols.size() <= 1) {
      return symbols;
    }

    List<String> current = new ArrayList<>(symbols);

    while (current.size() > 1) {
      int bestRank = Integer.MAX_VALUE;
      SymbolPair bestPair = null;

      for (int i = 0; i < current.size() - 1; i++) {
        final SymbolPair pair = new SymbolPair(
            current.get(i), current.get(i + 1));
        final Integer rank = mergeRanks.get(pair);
        if (rank != null && rank < bestRank) {
          bestRank = rank;
          bestPair = pair;
        }
      }

      if (bestPair == null) {
        break;
      }

      final List<String> next = new ArrayList<>();
      int i = 0;
      while (i < current.size()) {
        if (i < current.size() - 1
            && current.get(i).equals(bestPair.left())
            && current.get(i + 1).equals(bestPair.right())) {
          next.add(bestPair.left() + bestPair.right());
          i += 2;
        } else {
          next.add(current.get(i));
          i++;
        }
      }
      current = next;
    }

    return current;
  }

  /**
   * Represents a pair of adjacent symbols in BPE.
   *
   * @param left  The left symbol.
   * @param right The right symbol.
   */
  public record SymbolPair(String left, String right) {

    /**
     * Creates a new {@link SymbolPair}.
     *
     * @param left  The left symbol. Must not be {@code null}.
     * @param right The right symbol. Must not be {@code null}.
     * @throws IllegalArgumentException if {@code left} or {@code right} is {@code null}.
     */
    public SymbolPair {
      if (left == null) {
        throw new IllegalArgumentException("left must not be null");
      }
      if (right == null) {
        throw new IllegalArgumentException("right must not be null");
      }
    }

    @Override
    public String toString() {
      return left + " " + right;
    }
  }
}
