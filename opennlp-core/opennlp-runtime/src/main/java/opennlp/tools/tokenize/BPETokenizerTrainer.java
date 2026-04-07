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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.Trainer;
import opennlp.tools.tokenize.BPETokenizer.SymbolPair;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingConfiguration;

/**
 * Learns BPE merge operations from a training corpus and
 * produces a {@link BPEModel}.
 * <p>
 * Implements the BPE learning algorithm from
 * Sennrich et al. (2016):
 * <ol>
 *   <li>Build a vocabulary of character-level symbol
 *       sequences from the corpus, where each word is split
 *       into individual characters with an end-of-word
 *       marker.</li>
 *   <li>Count all adjacent symbol pairs across the
 *       vocabulary, weighted by word frequency.</li>
 *   <li>Merge the most frequent pair into a single new
 *       symbol.</li>
 *   <li>Repeat until the desired number of merges
 *       ({@code numMerges}) is reached.</li>
 * </ol>
 * <p>
 * The number of merges controls the granularity of the
 * resulting vocabulary: fewer merges produce finer-grained
 * (more character-level) tokens, while more merges produce
 * coarser (more word-level) tokens. A typical value ranges
 * from a few thousand to tens of thousands, depending on
 * the corpus size and language.
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * List<String> corpus = List.of(
 *     "the cat sat on the mat",
 *     "the dog sat on the log"
 * );
 *
 * BPETokenizerTrainer trainer = new BPETokenizerTrainer();
 * BPEModel model = trainer.train(corpus, 10000, "en");
 *
 * // Persist the model
 * model.serialize(Path.of("bpe-en.bin"));
 *
 * // Use it for tokenization
 * BPETokenizer tokenizer = new BPETokenizer(model);
 * String[] tokens = tokenizer.tokenize("the cat");
 * }</pre>
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
 * @see BPETokenizer
 * @see BPEModel
 */
public final class BPETokenizerTrainer implements Trainer<Parameters> {

  private Parameters trainingParameters;
  private Map<String, String> reportMap;
  private TrainingConfiguration trainingConfiguration;

  /**
   * Creates a new {@link BPETokenizerTrainer}.
   */
  public BPETokenizerTrainer() {
  }

  /** {@inheritDoc} */
  @Override
  public void init(final Parameters trainParams,
                   final Map<String, String> reportMap) {
    this.trainingParameters = trainParams;
    this.reportMap = reportMap;
  }

  /** {@inheritDoc} */
  @Override
  public void init(final Parameters trainParams,
                   final Map<String, String> reportMap,
                   final TrainingConfiguration config) {
    init(trainParams, reportMap);
    this.trainingConfiguration = config;
  }

  /**
   * Learns BPE merge operations from a training corpus
   * and returns a {@link BPEModel}.
   *
   * @param corpus       An iterable of text strings
   *                     (e.g., sentences or documents).
   *                     Must not be {@code null}.
   * @param numMerges    The number of merge operations
   *                     to learn. Must be positive.
   * @param languageCode The ISO language code
   *                     (e.g., "en", "de").
   *                     Must not be {@code null}.
   * @return A trained {@link BPEModel} containing the
   *         learned merge operations.
   * @throws IllegalArgumentException if {@code numMerges}
   *         is not positive, or if {@code corpus} or
   *         {@code languageCode} is {@code null}.
   */
  public BPEModel train(final Iterable<String> corpus,
                         final int numMerges,
                         final String languageCode) {
    if (corpus == null) {
      throw new IllegalArgumentException(
          "corpus must not be null");
    }
    if (languageCode == null) {
      throw new IllegalArgumentException(
          "languageCode must not be null");
    }
    if (numMerges <= 0) {
      throw new IllegalArgumentException(
          "numMerges must be positive, got: " + numMerges);
    }

    final List<SymbolPair> merges = learnMerges(corpus, numMerges);
    final BPETokenizerFactory factory =
        new BPETokenizerFactory(languageCode);

    return new BPEModel(merges, new HashMap<>(), factory);
  }

  /**
   * Learns BPE merge operations from the given corpus.
   * <p>
   * The algorithm proceeds as follows:
   * <ol>
   *   <li>Builds a word frequency map from the corpus using whitespace tokenization.</li>
   *   <li>Converts each word into a character-level symbol sequence with an
   *       end-of-word marker on the final character.</li>
   *   <li>Iteratively counts all adjacent symbol pairs (weighted by word frequency),
   *       selects the most frequent pair, records it as a merge operation, and applies
   *       the merge to all vocabulary entries.</li>
   *   <li>Stops after {@code numMerges} iterations or when no further pairs exist.</li>
   * </ol>
   *
   * @param corpus    The training corpus, where each element is a text string.
   * @param numMerges The maximum number of merge operations to learn.
   * @return An ordered list of learned {@link SymbolPair} merge operations.
   */
  private List<SymbolPair> learnMerges(
      final Iterable<String> corpus,
      final int numMerges) {
    // Step 1: Build word frequency map from corpus
    final Map<String, Integer> wordFreqs = new HashMap<>();
    for (final String line : corpus) {
      final String[] words = WhitespaceTokenizer.INSTANCE.tokenize(line);
      for (final String word : words) {
        wordFreqs.merge(word, 1, Integer::sum);
      }
    }

    // Step 2: Convert to symbol sequences with frequencies
    final Map<List<String>, Integer> vocab = new HashMap<>();
    for (final Map.Entry<String, Integer> entry : wordFreqs.entrySet()) {
      final List<String> symbols = splitToSymbols(entry.getKey());
      vocab.put(symbols, entry.getValue());
    }

    // Step 3: Iteratively learn merges
    final List<SymbolPair> merges = new ArrayList<>();

    for (int step = 0; step < numMerges; step++) {
      // Count all adjacent pairs
      final Map<SymbolPair, Integer> pairCounts = new HashMap<>();
      for (final Map.Entry<List<String>, Integer> entry : vocab.entrySet()) {
        final List<String> symbols = entry.getKey();
        final int freq = entry.getValue();
        for (int i = 0; i < symbols.size() - 1; i++) {
          final SymbolPair pair = new SymbolPair(
              symbols.get(i), symbols.get(i + 1));
          pairCounts.merge(pair, freq, Integer::sum);
        }
      }

      if (pairCounts.isEmpty()) {
        break;
      }

      // Find most frequent pair
      SymbolPair bestPair = null;
      int bestCount = 0;
      for (final Map.Entry<SymbolPair, Integer> entry : pairCounts.entrySet()) {
        if (entry.getValue() > bestCount) {
          bestCount = entry.getValue();
          bestPair = entry.getKey();
        }
      }

      if (bestPair == null || bestCount < 1) {
        break;
      }

      merges.add(bestPair);

      // Apply merge to vocabulary
      final Map<List<String>, Integer> newVocab = new HashMap<>();
      for (final Map.Entry<List<String>, Integer> entry : vocab.entrySet()) {
        final List<String> merged = applyMerge(entry.getKey(), bestPair);
        newVocab.merge(merged, entry.getValue(), Integer::sum);
      }
      vocab.clear();
      vocab.putAll(newVocab);
    }

    return merges;
  }

  private List<String> splitToSymbols(final String word) {
    final List<String> symbols = new ArrayList<>(word.length());
    for (int i = 0; i < word.length(); i++) {
      if (i == word.length() - 1) {
        symbols.add(word.charAt(i) + BPETokenizer.END_OF_WORD);
      } else {
        symbols.add(String.valueOf(word.charAt(i)));
      }
    }
    return symbols;
  }

  /**
   * Applies a single merge operation to a symbol sequence.
   * Scans the list for adjacent symbols matching the given pair and replaces
   * each occurrence with a single concatenated symbol.
   *
   * @param symbols The current symbol sequence for a word.
   * @param pair    The {@link SymbolPair} to merge.
   * @return A new list with all occurrences of the pair merged.
   */
  private List<String> applyMerge(
      final List<String> symbols,
      final SymbolPair pair) {
    final List<String> result = new ArrayList<>();
    int i = 0;
    while (i < symbols.size()) {
      if (i < symbols.size() - 1
          && symbols.get(i).equals(pair.left())
          && symbols.get(i + 1).equals(pair.right())) {
        result.add(pair.left() + pair.right());
        i += 2;
      } else {
        result.add(symbols.get(i));
        i++;
      }
    }
    return result;
  }
}
