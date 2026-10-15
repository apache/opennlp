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

package opennlp.tools.depparse;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.ArrayMath;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ParamChecks;
import opennlp.tools.util.ResourceLimits;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.model.ArtifactSerializer;

/**
 * The weights of the feedforward transition parser: embeddings for words, tags, and arc
 * labels, one hidden layer with cube activation, and a transition output layer, after
 * <a href="https://aclanthology.org/D14-1082/">Chen and Manning (2014)</a>.
 *
 * <p>The network holds every forward computation the parser and the trainer share:
 * {@link #addContribution} is the per-position hidden sum, {@link #outputScores} the
 * output layer, {@link #score} the complete inference pass, and
 * {@link #logSoftmaxInPlace} the normalization of beam decoding. The trainer writes
 * its updates into the live arrays the accessors return; the parser never modifies
 * them, so a network that is not being trained can be shared between threads.</p>
 *
 * <p>Each vocabulary occupies one consecutive block of embedding rows and starts with
 * its reserved symbols, so unknown symbols, absent positions, and the artificial root
 * always have a row. Words are matched after {@link #normalize(String) normalization}.</p>
 *
 * @see FeedforwardDependencyModel
 * @since 3.0.0
 */
final class FeedforwardNetwork {

  /** The vocabulary key of every word, tag, or label the network has no embedding row for. */
  static final String UNKNOWN = "*UNK*";

  /** The reserved word symbols, in the order of their embedding rows. */
  static final String[] RESERVED_WORD_SYMBOLS = {UNKNOWN,
      DependencyContextGenerator.NONE_VALUE, DependencyContextGenerator.ROOT_VALUE};

  /** The reserved tag symbols, in the order of their embedding rows. */
  static final String[] RESERVED_TAG_SYMBOLS = {UNKNOWN,
      DependencyContextGenerator.NONE_VALUE, DependencyContextGenerator.ROOT_VALUE};

  /** The reserved label symbols, in the order of their embedding rows. */
  static final String[] RESERVED_LABEL_SYMBOLS = {UNKNOWN,
      DependencyContextGenerator.NONE_VALUE};

  /** U+03A3, GREEK CAPITAL LETTER SIGMA, the one code point whose lowering depends on context. */
  private static final int GREEK_CAPITAL_SIGMA = 0x03A3;

  /** U+03C2, GREEK SMALL LETTER FINAL SIGMA, the lowering of the capital at the end of a word. */
  private static final int GREEK_SMALL_FINAL_SIGMA = 0x03C2;

  /** The embedding rows the reserved symbols of all three vocabularies take. */
  static final int RESERVED_ROWS = RESERVED_WORD_SYMBOLS.length
      + RESERVED_TAG_SYMBOLS.length + RESERVED_LABEL_SYMBOLS.length;

  /** The embedding row of the unknown symbol in each vocabulary block. */
  private static final int UNKNOWN_OFFSET = 0;

  /** The embedding row of the absent-position symbol in each vocabulary block. */
  private static final int NONE_OFFSET = 1;

  /** The embedding row of the root symbol in the word and tag blocks. */
  private static final int ROOT_OFFSET = 2;

  private final Map<String, Integer> wordIds;
  private final Map<String, Integer> tagIds;
  private final Map<String, Integer> labelIds;

  /** The first embedding row of each vocabulary, indexed like the vocabularies above. */
  private final int firstWordRow;
  private final int firstTagRow;
  private final int firstLabelRow;

  private final String[] transitions;
  private final Transition[] decodedTransitions;
  private final Map<Transition, Integer> transitionIds;

  private final int embeddingSize;
  private final float[][] embeddings;
  private final float[][] hiddenWeights;
  private final float[] hiddenBias;
  private final float[][] outputWeights;
  private final float[] outputBias;

  /**
   * Assembles a network from its vocabularies and weights, copying the maps so later
   * changes by the caller do not reach the network.
   *
   * @param wordIds The word vocabulary, mapping each normalized word to its row.
   * @param tagIds The tag vocabulary.
   * @param labelIds The dependency label vocabulary.
   * @param transitions The transition inventory, indexed by output row.
   * @param embeddingSize The embedding dimensionality.
   * @param embeddings The embedding rows for words, tags, and labels.
   * @param hiddenWeights The hidden layer weights.
   * @param hiddenBias The hidden layer bias.
   * @param outputWeights The output layer weights.
   * @param outputBias The output layer bias.
   * @throws IllegalArgumentException Thrown if a vocabulary lacks a reserved symbol, the
   *         vocabularies do not partition the embedding rows into consecutive blocks
   *         that start with the reserved symbols, a transition repeats or does not
   *         decode, the inventory lacks SHIFT or RIGHT_ARC, or a weight dimension does
   *         not match.
   */
  FeedforwardNetwork(Map<String, Integer> wordIds, Map<String, Integer> tagIds,
      Map<String, Integer> labelIds, String[] transitions, int embeddingSize,
      float[][] embeddings, float[][] hiddenWeights, float[] hiddenBias,
      float[][] outputWeights, float[] outputBias) {
    this.wordIds = Map.copyOf(wordIds);
    this.tagIds = Map.copyOf(tagIds);
    this.labelIds = Map.copyOf(labelIds);
    this.transitions = transitions.clone();
    this.embeddingSize = embeddingSize;
    this.embeddings = embeddings;
    this.hiddenWeights = hiddenWeights;
    this.hiddenBias = hiddenBias;
    this.outputWeights = outputWeights;
    this.outputBias = outputBias;

    final long rows = (long) wordIds.size() + tagIds.size() + labelIds.size();
    if (rows != embeddings.length) {
      throw new IllegalArgumentException("embedding matrix has " + embeddings.length
          + " rows for " + rows + " vocabulary entries");
    }
    final boolean[] taken = new boolean[embeddings.length];
    this.firstWordRow = validateVocabulary(this.wordIds, taken, "word vocabulary",
        RESERVED_WORD_SYMBOLS);
    this.firstTagRow = validateVocabulary(this.tagIds, taken, "tag vocabulary",
        RESERVED_TAG_SYMBOLS);
    this.firstLabelRow = validateVocabulary(this.labelIds, taken, "label vocabulary",
        RESERVED_LABEL_SYMBOLS);

    this.decodedTransitions = Transition.decodeInventory(this.transitions);
    this.transitionIds = new HashMap<>();
    for (int i = 0; i < decodedTransitions.length; i++) {
      transitionIds.put(decodedTransitions[i], i);
    }

    final int inputSize = FeedforwardContext.FEATURE_COUNT * embeddingSize;
    requireColumns(embeddings, embeddingSize, "embedding matrix");
    requireColumns(hiddenWeights, inputSize, "hidden matrix");
    if (hiddenBias.length != hiddenWeights.length) {
      throw new IllegalArgumentException("hidden bias length is " + hiddenBias.length
          + ", expected " + hiddenWeights.length);
    }
    if (outputWeights.length != this.transitions.length) {
      throw new IllegalArgumentException("output matrix row count is "
          + outputWeights.length + ", expected " + this.transitions.length);
    }
    requireColumns(outputWeights, hiddenWeights.length, "output matrix");
    if (outputBias.length != this.transitions.length) {
      throw new IllegalArgumentException("output bias length is " + outputBias.length
          + ", expected " + this.transitions.length);
    }
  }

  /**
   * Checks that one vocabulary starts with its reserved symbols and occupies one
   * consecutive block of embedding rows that no other vocabulary uses.
   *
   * @param ids The vocabulary to check.
   * @param taken The rows claimed so far across all vocabularies; updated in place.
   * @param label The vocabulary name used in error messages.
   * @param reserved The reserved symbols, in the order of their rows.
   * @return The first row of the block.
   * @throws IllegalArgumentException Thrown if the block is not consecutive, a row is
   *         already taken, or a reserved symbol is missing or misplaced.
   */
  private static int validateVocabulary(Map<String, Integer> ids, boolean[] taken,
      String label, String[] reserved) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (final Map.Entry<String, Integer> entry : ids.entrySet()) {
      final int id = entry.getValue();
      if (id < 0 || id >= taken.length) {
        throw new IllegalArgumentException(label + " id out of range for "
            + entry.getKey() + ": " + id);
      }
      if (taken[id]) {
        throw new IllegalArgumentException("duplicate embedding id: " + id);
      }
      taken[id] = true;
      min = Math.min(min, id);
      max = Math.max(max, id);
    }
    if (ids.size() < reserved.length || max - min + 1 != ids.size()) {
      throw new IllegalArgumentException(label + " must occupy one consecutive block of "
          + "embedding rows that starts with its reserved symbols");
    }
    for (int i = 0; i < reserved.length; i++) {
      final Integer id = ids.get(reserved[i]);
      if (id == null || id != min + i) {
        throw new IllegalArgumentException(label + " must have " + reserved[i]
            + " at row " + (min + i));
      }
    }
    return min;
  }

  /**
   * @param matrix The matrix to check.
   * @param columns The required width of every row.
   * @param label The matrix name used in error messages.
   * @throws IllegalArgumentException Thrown if a row has another width.
   */
  private static void requireColumns(float[][] matrix, int columns, String label) {
    for (final float[] row : matrix) {
      if (row.length != columns) {
        throw new IllegalArgumentException(label + " column count is " + row.length
            + ", expected " + columns);
      }
    }
  }

  /**
   * Scores every transition for a configuration described by embedding rows.
   *
   * @param features The embedding rows of the configuration, as produced by
   *                 {@link FeedforwardContext#featureIds}. Must not be {@code null}.
   * @param cache The contribution cache to read, or {@code null} to compute every
   *              contribution.
   * @param hidden Scratch space for the hidden layer, {@link #hiddenSize()} entries;
   *               overwritten.
   * @param scores Receives one unnormalized score per transition, indexed like
   *               {@link #transitions()}; overwritten.
   * @return {@code scores}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code features} is {@code null}, does not
   *         have the required length, or contains an invalid embedding row.
   * @throws IllegalStateException Thrown if the network produces a non-finite score.
   */
  double[] score(int[] features, ContributionCache cache, double[] hidden, double[] scores) {
    ParamChecks.requireNonNullArg(features, "features");
    if (features.length != FeedforwardContext.FEATURE_COUNT) {
      throw new IllegalArgumentException("features must contain "
          + FeedforwardContext.FEATURE_COUNT + " embedding indices");
    }
    for (int i = 0; i < features.length; i++) {
      if (features[i] < 0 || features[i] >= embeddings.length) {
        throw new IllegalArgumentException(
            "feature embedding out of range at index " + i + ": " + features[i]);
      }
    }
    preActivations(features, cache, hidden);
    for (int j = 0; j < hidden.length; j++) {
      hidden[j] = hidden[j] * hidden[j] * hidden[j];
    }
    outputScores(hidden, scores);
    for (final double score : scores) {
      if (!Double.isFinite(score)) {
        throw new IllegalStateException("the model produced a non-finite transition score");
      }
    }
    return scores;
  }

  /**
   * Computes the hidden pre-activations of a configuration: the bias plus the
   * contribution of every feature position.
   *
   * @param features The embedding rows of the configuration, one per position.
   * @param cache The contribution cache to read, or {@code null} to compute every
   *              contribution.
   * @param pre Receives the pre-activations; overwritten.
   */
  void preActivations(int[] features, ContributionCache cache, double[] pre) {
    for (int j = 0; j < pre.length; j++) {
      pre[j] = hiddenBias[j];
    }
    for (int f = 0; f < features.length; f++) {
      final double[] cached = cache == null ? null : cache.contribution(f, features[f]);
      if (cached != null) {
        for (int j = 0; j < pre.length; j++) {
          pre[j] += cached[j];
        }
      } else {
        addContribution(f, features[f], pre);
      }
    }
  }

  /**
   * Adds the hidden-layer contribution of one embedding row at one feature position.
   *
   * @param position The feature position.
   * @param row The embedding row at that position.
   * @param out The hidden vector to add to.
   */
  void addContribution(int position, int row, double[] out) {
    final float[] embedding = embeddings[row];
    final int offset = position * embeddingSize;
    for (int j = 0; j < out.length; j++) {
      final float[] weights = hiddenWeights[j];
      double sum = 0.0;
      for (int d = 0; d < embeddingSize; d++) {
        sum += (double) weights[offset + d] * embedding[d];
      }
      out[j] += sum;
    }
  }

  /**
   * Applies the output layer to hidden activations.
   *
   * @param hidden The hidden activations after the cube.
   * @param scores Receives one raw score per transition; overwritten.
   */
  void outputScores(double[] hidden, double[] scores) {
    for (int o = 0; o < scores.length; o++) {
      final float[] row = outputWeights[o];
      double sum = outputBias[o];
      for (int j = 0; j < hidden.length; j++) {
        sum += row[j] * hidden[j];
      }
      scores[o] = sum;
    }
  }

  /**
   * Turns finite raw scores into log-probabilities in place. The maximum is removed
   * before the sum of exponentials so a large common offset does not lose the
   * normalizer to rounding.
   *
   * @param scores The raw scores, overwritten with their log-softmax. Must not be
   *               empty.
   */
  static void logSoftmaxInPlace(double[] scores) {
    final double max = ArrayMath.max(scores);
    for (int i = 0; i < scores.length; i++) {
      scores[i] -= max;
    }
    final double logSum = ArrayMath.logSumOfExps(scores);
    for (int i = 0; i < scores.length; i++) {
      scores[i] -= logSum;
    }
  }

  /**
   * Resolves the tokens of a sentence to their word rows once, so that extracting the
   * features of every configuration only indexes.
   *
   * @param tokens The sentence tokens. Must not contain {@code null}.
   * @return The word row per token. Never {@code null}.
   */
  int[] wordRows(String[] tokens) {
    final int[] rows = new int[tokens.length];
    for (int i = 0; i < tokens.length; i++) {
      rows[i] = lookup(wordIds, normalize(tokens[i]), firstWordRow);
    }
    return rows;
  }

  /**
   * Resolves the tags of a sentence to their tag rows once.
   *
   * @param tags The part-of-speech tags. Must not contain {@code null}.
   * @return The tag row per tag. Never {@code null}.
   */
  int[] tagRows(String[] tags) {
    final int[] rows = new int[tags.length];
    for (int i = 0; i < tags.length; i++) {
      rows[i] = lookup(tagIds, tags[i], firstTagRow);
    }
    return rows;
  }

  /**
   * @param relation The arc label of an attached dependent, or {@code null} for an
   *                 absent position.
   * @return The label row, falling back to the unknown row.
   */
  int labelRow(String relation) {
    return lookup(labelIds, relation, firstLabelRow);
  }

  /**
   * @return The word row standing for the artificial root.
   */
  int rootWordRow() {
    return firstWordRow + ROOT_OFFSET;
  }

  /**
   * @return The word row standing for an absent position.
   */
  int noneWordRow() {
    return firstWordRow + NONE_OFFSET;
  }

  /**
   * @return The tag row standing for the artificial root.
   */
  int rootTagRow() {
    return firstTagRow + ROOT_OFFSET;
  }

  /**
   * @return The tag row standing for an absent position.
   */
  int noneTagRow() {
    return firstTagRow + NONE_OFFSET;
  }

  /**
   * Resolves a symbol to its embedding row: absences map to the reserved absent
   * symbol, symbols without a row of their own to the reserved unknown symbol.
   *
   * @param ids The vocabulary to resolve against.
   * @param symbol The symbol to resolve, or {@code null} for an absent position.
   * @param firstRow The first row of the vocabulary block.
   * @return The embedding row of the symbol or of its fallback.
   */
  private static int lookup(Map<String, Integer> ids, String symbol, int firstRow) {
    if (symbol == null) {
      return firstRow + NONE_OFFSET;
    }
    final Integer id = ids.get(symbol);
    return id == null ? firstRow + UNKNOWN_OFFSET : id;
  }

  /**
   * Lowercases a word symbol with {@link StringUtil#toLowerCase(CharSequence)}, which
   * maps per code point, and applies the one contextual rule of the Unicode
   * <a href="https://www.unicode.org/Public/UCD/latest/ucd/SpecialCasing.txt">SpecialCasing</a>
   * data: a Greek capital sigma at the end of a word, after a cased letter and before no
   * further cased letter, becomes the final sigma U+03C2. Reserved symbols and absences
   * pass through.
   *
   * @param word The word to normalize. May be {@code null}.
   * @return The vocabulary key of {@code word}, or {@code null} if {@code word} is
   *         {@code null}.
   */
  static String normalize(String word) {
    if (word == null || isReservedSymbol(word)) {
      return word;
    }
    final String lowered = StringUtil.toLowerCase(word);
    return word.indexOf(GREEK_CAPITAL_SIGMA) < 0 ? lowered : applyFinalSigma(word, lowered);
  }

  /**
   * Replaces each word-final sigma of a lowered word with the final form. The lowering
   * maps one code point to one code point, so both arrays index the same positions.
   *
   * @param word The word before lowering.
   * @param lowered The word lowered per code point.
   * @return {@code lowered} with the Final_Sigma context applied. Never {@code null}.
   */
  private static String applyFinalSigma(String word, String lowered) {
    final int[] source = word.codePoints().toArray();
    final int[] target = lowered.codePoints().toArray();
    for (int i = 0; i < source.length; i++) {
      if (source[i] == GREEK_CAPITAL_SIGMA && isFinalSigma(source, i)) {
        target[i] = GREEK_SMALL_FINAL_SIGMA;
      }
    }
    return new String(target, 0, target.length);
  }

  /**
   * The Final_Sigma condition of the Unicode SpecialCasing data: the code point is
   * preceded by a cased letter and then zero or more case-ignorable code points, and
   * is not followed by zero or more case-ignorable code points and then a cased letter.
   *
   * @param cp The code points of the sequence.
   * @param index The position of the capital sigma.
   * @return {@code true} if the sigma at {@code index} is word-final.
   */
  private static boolean isFinalSigma(int[] cp, int index) {
    int before = index - 1;
    while (before >= 0 && isCaseIgnorable(cp[before])) {
      before--;
    }
    if (before < 0 || !isCased(cp[before])) {
      return false;
    }
    int after = index + 1;
    while (after < cp.length && isCaseIgnorable(cp[after])) {
      after++;
    }
    return after == cp.length || !isCased(cp[after]);
  }

  /**
   * @param cp The code point to test.
   * @return {@code true} if the code point has the Unicode Cased property: an upper,
   *         lower, or title case letter, including the Other_Uppercase and
   *         Other_Lowercase code points.
   */
  private static boolean isCased(int cp) {
    return Character.isUpperCase(cp) || Character.isLowerCase(cp) || Character.isTitleCase(cp);
  }

  /**
   * @param cp The code point to test.
   * @return {@code true} if the code point has the Unicode Case_Ignorable property: the
   *         general categories Mn, Me, Cf, Lm, and Sk, and the
   *         <a href="https://www.unicode.org/Public/UCD/latest/ucd/auxiliary/WordBreakProperty.txt">Word_Break</a>
   *         values MidLetter, MidNumLet, and Single_Quote.
   */
  private static boolean isCaseIgnorable(int cp) {
    switch (Character.getType(cp)) {
      case Character.NON_SPACING_MARK:
      case Character.ENCLOSING_MARK:
      case Character.FORMAT:
      case Character.MODIFIER_LETTER:
      case Character.MODIFIER_SYMBOL:
        return true;
      default:
        break;
    }
    switch (cp) {
      // Single_Quote
      case 0x0027:
      // MidLetter
      case 0x003A:
      case 0x00B7:
      case 0x0387:
      case 0x055F:
      case 0x05F4:
      case 0x2027:
      case 0xFE13:
      case 0xFE55:
      case 0xFF1A:
      // MidNumLet
      case 0x002E:
      case 0x2018:
      case 0x2019:
      case 0x2024:
      case 0xFE52:
      case 0xFF07:
      case 0xFF0E:
        return true;
      default:
        return false;
    }
  }
  /**
   * @param symbol The symbol to test. May be {@code null}.
   * @return {@code true} if {@code symbol} is one of the reserved feature values.
   */
  static boolean isReservedSymbol(String symbol) {
    // the word symbols include the tag and label symbols
    for (final String reserved : RESERVED_WORD_SYMBOLS) {
      if (reserved.equals(symbol)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks that a symbol fits the length the {@link Serializer} reads back.
   *
   * @param symbol The symbol to check.
   * @param label The vocabulary name used in error messages.
   * @throws IllegalArgumentException Thrown if the UTF-8 form of {@code symbol} is
   *         longer than {@link ResourceLimits#MAX_ENTRIES} bytes.
   */
  static void requireStorable(String symbol, String label) {
    // a UTF-16 unit encodes to at most three UTF-8 bytes, so short symbols need no encoding
    if ((long) symbol.length() * 3 <= ResourceLimits.MAX_ENTRIES) {
      return;
    }
    final long bytes = symbol.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > ResourceLimits.MAX_ENTRIES) {
      throw new IllegalArgumentException(label + " symbol length " + bytes
          + " exceeds the limit of " + ResourceLimits.MAX_ENTRIES + " UTF-8 bytes");
    }
  }

  /**
   * @return The transition outcome strings by output index. Never {@code null}.
   */
  String[] transitions() {
    return transitions.clone();
  }

  /**
   * @return The decoded transitions by output index. Never {@code null}.
   */
  Transition[] decodedTransitions() {
    return decodedTransitions.clone();
  }

  /**
   * @param transition The transition to look up.
   * @return Its output index, or {@code -1} if the inventory lacks it.
   */
  int transitionIndex(Transition transition) {
    final Integer id = transitionIds.get(transition);
    return id == null ? -1 : id;
  }

  /**
   * Creates an independent copy of this network: the weights are deep-copied and the
   * vocabularies are shared because their maps are immutable.
   *
   * @return A copy sharing no mutable state with this network. Never {@code null}.
   */
  FeedforwardNetwork copy() {
    return new FeedforwardNetwork(wordIds, tagIds, labelIds, transitions,
        embeddingSize, copyOf(embeddings), copyOf(hiddenWeights), hiddenBias.clone(),
        copyOf(outputWeights), outputBias.clone());
  }

  /**
   * Deep-copies a matrix, row by row.
   *
   * @param matrix The matrix to copy.
   * @return A copy sharing no rows with {@code matrix}. Never {@code null}.
   */
  private static float[][] copyOf(float[][] matrix) {
    final float[][] copy = new float[matrix.length][];
    for (int r = 0; r < matrix.length; r++) {
      copy[r] = matrix[r].clone();
    }
    return copy;
  }

  /**
   * @param vocabularyRows The combined word, tag, and label row count.
   * @param transitionCount The output layer width.
   * @param embeddingSize The embedding dimensionality.
   * @param hiddenSize The hidden layer width.
   * @return The number of float values a network of that shape stores.
   */
  static long floatValues(long vocabularyRows, long transitionCount, long embeddingSize,
      long hiddenSize) {
    final long inputSize = FeedforwardContext.FEATURE_COUNT * embeddingSize;
    return vocabularyRows * embeddingSize + hiddenSize * inputSize + hiddenSize
        + transitionCount * hiddenSize + transitionCount;
  }

  /**
   * @return The immutable map from a normalized word to its embedding row. Never {@code null}.
   */
  Map<String, Integer> wordIds() {
    return wordIds;
  }

  /**
   * @return The immutable map from a tag to its embedding row. Never {@code null}.
   */
  Map<String, Integer> tagIds() {
    return tagIds;
  }

  /**
   * @return The immutable map from an arc label to its embedding row. Never {@code null}.
   */
  Map<String, Integer> labelIds() {
    return labelIds;
  }

  /**
   * @return The first embedding row of the word vocabulary block.
   */
  int firstWordRow() {
    return firstWordRow;
  }

  /**
   * @return The first embedding row of the tag vocabulary block.
   */
  int firstTagRow() {
    return firstTagRow;
  }

  /**
   * @return The first embedding row of the label vocabulary block.
   */
  int firstLabelRow() {
    return firstLabelRow;
  }

  /**
   * @return The width of one embedding row.
   */
  int embeddingSize() {
    return embeddingSize;
  }

  /**
   * @return The hidden layer width.
   */
  int hiddenSize() {
    return hiddenBias.length;
  }

  /**
   * @return The live embedding matrix, one row per vocabulary entry, not a copy: the
   *         trainer writes its updates into it. Never {@code null}.
   */
  float[][] embeddings() {
    return embeddings;
  }

  /**
   * @return The live hidden layer weights, not a copy. Never {@code null}.
   */
  float[][] hiddenWeights() {
    return hiddenWeights;
  }

  /**
   * @return The live hidden layer bias, not a copy. Never {@code null}.
   */
  float[] hiddenBias() {
    return hiddenBias;
  }

  /**
   * @return The live output layer weights, one row per transition, not a copy. Never
   *         {@code null}.
   */
  float[][] outputWeights() {
    return outputWeights;
  }

  /**
   * @return The live output layer bias, one entry per transition, not a copy. Never
   *         {@code null}.
   */
  float[] outputBias() {
    return outputBias;
  }

  /**
   * Reads and writes a network as a {@link FeedforwardDependencyModel} artifact: a
   * four-byte magic, an {@code int} format version, the three vocabularies, the
   * transition inventory, the embedding size, and the weight matrices and vectors.
   * Symbols are stored as an {@code int} byte length followed by UTF-8 bytes; a symbol
   * may have up to {@link ResourceLimits#MAX_ENTRIES} bytes.
   *
   * <p>Counts read from the stream are bounded by {@link ResourceLimits#MAX_ENTRIES},
   * and the float values of all matrices and vectors together by
   * {@link ResourceLimits#MAX_MATRIX_CELLS}; matrix rows are allocated one at a time
   * while reading.</p>
   */
  static final class Serializer implements ArtifactSerializer<FeedforwardNetwork> {

    /** The bytes every serialized network starts with. */
    static final byte[] MAGIC = {'F', 'F', 'D', 'P'};

    /** The format version this serializer reads and writes. */
    static final int FORMAT_VERSION = 1;

    /**
     * {@inheritDoc}
     *
     * @throws InvalidFormatException Thrown if the content is not a network in a
     *         supported version, a count or symbol length is out of range, the
     *         embedding size or hidden layer width is zero, the structure is
     *         inconsistent, a value is not finite, or the stream ends early. Malformed
     *         UTF-8 in a symbol is replaced, not rejected.
     */
    @Override
    public FeedforwardNetwork create(InputStream in) throws IOException {
      ParamChecks.requireNonNullArg(in, "in");
      final DataInputStream data = new DataInputStream(in);
      try {
        return read(data);
      } catch (EOFException e) {
        throw new InvalidFormatException("feedforward network ends early", e);
      } catch (IllegalArgumentException e) {
        throw new InvalidFormatException(e.getMessage(), e);
      }
    }

    /**
     * Reads the network body after any wrapping has been set up.
     *
     * @param data The input to read from.
     * @return The network. Never {@code null}.
     * @throws IOException Thrown if reading fails or the content is invalid.
     */
    private static FeedforwardNetwork read(DataInputStream data) throws IOException {
      final byte[] magic = new byte[MAGIC.length];
      data.readFully(magic);
      for (int i = 0; i < magic.length; i++) {
        if (magic[i] != MAGIC[i]) {
          throw new InvalidFormatException("not a feedforward dependency network");
        }
      }
      final int version = data.readInt();
      if (version != FORMAT_VERSION) {
        throw new InvalidFormatException("unsupported feedforward network version: " + version);
      }
      final long[] remainingEntries = {ResourceLimits.MAX_ENTRIES};
      final Map<String, Integer> wordIds = readVocabulary(data, "word vocabulary",
          remainingEntries);
      final Map<String, Integer> tagIds = readVocabulary(data, "tag vocabulary",
          remainingEntries);
      final Map<String, Integer> labelIds = readVocabulary(data, "label vocabulary",
          remainingEntries);
      final long rows = (long) wordIds.size() + tagIds.size() + labelIds.size();
      final int transitionCount = readCount(data, "transition count");
      final List<String> transitions = new ArrayList<>();
      for (int i = 0; i < transitionCount; i++) {
        transitions.add(readSymbol(data, "transition"));
      }
      final int embeddingSize = readCount(data, "embedding size");
      if (embeddingSize == 0) {
        throw new InvalidFormatException("embedding size must be positive");
      }
      final long[] remainingCells = {ResourceLimits.MAX_MATRIX_CELLS};
      final float[][] embeddings = readMatrix(data, rows, embeddingSize,
          remainingCells, "embedding matrix");
      final long inputSize = (long) FeedforwardContext.FEATURE_COUNT * embeddingSize;
      final float[][] hiddenWeights = readMatrix(data, -1, inputSize, remainingCells,
          "hidden matrix");
      final float[] hiddenBias = readVector(data, hiddenWeights.length, remainingCells,
          "hidden bias");
      final float[][] outputWeights = readMatrix(data, transitions.size(),
          hiddenWeights.length, remainingCells, "output matrix");
      final float[] outputBias = readVector(data, transitions.size(), remainingCells,
          "output bias");
      if (data.read() != -1) {
        throw new InvalidFormatException("trailing data after feedforward network");
      }
      return new FeedforwardNetwork(wordIds, tagIds, labelIds,
          transitions.toArray(String[]::new), embeddingSize, embeddings, hiddenWeights,
          hiddenBias, outputWeights, outputBias);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException Thrown if {@code artifact} or {@code out} is
     *         {@code null}.
     */
    @Override
    public void serialize(FeedforwardNetwork artifact, OutputStream out) throws IOException {
      ParamChecks.requireNonNullArg(artifact, "artifact");
      ParamChecks.requireNonNullArg(out, "out");
      final DataOutputStream data = new DataOutputStream(out);
      data.write(MAGIC);
      data.writeInt(FORMAT_VERSION);
      writeVocabulary(data, artifact.wordIds);
      writeVocabulary(data, artifact.tagIds);
      writeVocabulary(data, artifact.labelIds);
      data.writeInt(artifact.transitions.length);
      for (final String transition : artifact.transitions) {
        writeSymbol(data, transition);
      }
      data.writeInt(artifact.embeddingSize);
      writeMatrix(data, artifact.embeddings);
      writeMatrix(data, artifact.hiddenWeights);
      writeVector(data, artifact.hiddenBias);
      writeMatrix(data, artifact.outputWeights);
      writeVector(data, artifact.outputBias);
      data.flush();
    }

    /**
     * Writes one vocabulary as its size followed by (symbol, id) pairs in ascending id
     * order, so that serializing the same network yields the same bytes on every JVM.
     *
     * @param data The output to write to.
     * @param ids The vocabulary to write.
     * @throws IOException Thrown if writing fails.
     */
    private static void writeVocabulary(DataOutputStream data, Map<String, Integer> ids)
        throws IOException {
      data.writeInt(ids.size());
      final List<Map.Entry<String, Integer>> entries = new ArrayList<>(ids.entrySet());
      entries.sort(Map.Entry.comparingByValue());
      for (final Map.Entry<String, Integer> entry : entries) {
        writeSymbol(data, entry.getKey());
        data.writeInt(entry.getValue());
      }
    }

    /**
     * Reads one vocabulary written by {@link #writeVocabulary}. The map grows while
     * reading instead of being sized from the header.
     *
     * @param data The input to read from.
     * @param label The vocabulary name used in error messages.
     * @param remainingEntries The one-element budget of vocabulary entries across all
     *                         vocabularies, decremented in place.
     * @return The symbol to id map. Never {@code null}.
     * @throws IOException Thrown if reading fails, the size exceeds the budget, or a
     *         symbol repeats.
     */
    private static Map<String, Integer> readVocabulary(DataInputStream data, String label,
        long[] remainingEntries) throws IOException {
      final int size = readCount(data, label + " size");
      if (size > remainingEntries[0]) {
        throw new InvalidFormatException("combined vocabulary size exceeds "
            + ResourceLimits.MAX_ENTRIES);
      }
      remainingEntries[0] -= size;
      final Map<String, Integer> ids = new HashMap<>();
      for (int i = 0; i < size; i++) {
        final String symbol = readSymbol(data, label + " symbol");
        final int id = data.readInt();
        if (ids.put(symbol, id) != null) {
          throw new InvalidFormatException("duplicate symbol in " + label + ": " + symbol);
        }
      }
      return ids;
    }

    /**
     * Writes a symbol as its UTF-8 byte length followed by the bytes.
     *
     * @param data The output to write to.
     * @param symbol The symbol to write.
     * @throws IOException Thrown if writing fails.
     */
    private static void writeSymbol(DataOutputStream data, String symbol) throws IOException {
      final byte[] bytes = symbol.getBytes(StandardCharsets.UTF_8);
      data.writeInt(bytes.length);
      data.write(bytes);
    }

    /**
     * Reads a symbol written by {@link #writeSymbol}.
     *
     * @param data The input to read from.
     * @param label The symbol name used in error messages.
     * @return The symbol. Never {@code null}.
     * @throws IOException Thrown if reading fails or the length is out of range.
     */
    private static String readSymbol(DataInputStream data, String label) throws IOException {
      final int length = data.readInt();
      ResourceLimits.requireWithinMaxEntries(length, label + " length",
          InvalidFormatException::new);
      final byte[] bytes = new byte[length];
      data.readFully(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Writes a rectangular matrix as its dimensions followed by its values in row order.
     *
     * @param data The output to write to.
     * @param matrix The matrix to write.
     * @throws IOException Thrown if writing fails.
     */
    private static void writeMatrix(DataOutputStream data, float[][] matrix)
        throws IOException {
      data.writeInt(matrix.length);
      data.writeInt(matrix.length == 0 ? 0 : matrix[0].length);
      for (final float[] row : matrix) {
        for (final float value : row) {
          data.writeFloat(value);
        }
      }
    }

    /**
     * Reads a matrix written by {@link #writeMatrix}, allocating one row at a time.
     *
     * @param data The input to read from.
     * @param expectedRows The required row count, or a negative value to accept any
     *                     positive count within the limits.
     * @param expectedColumns The required column count.
     * @param remainingCells The one-element float budget, decremented in place.
     * @param label The matrix name used in error messages.
     * @return The matrix. Never {@code null}.
     * @throws IOException Thrown if reading fails, a dimension is out of range or does
     *         not match, the budget is exceeded, or a value is not finite.
     */
    private static float[][] readMatrix(DataInputStream data, long expectedRows,
        long expectedColumns, long[] remainingCells, String label) throws IOException {
      final int rows = readCount(data, label + " rows");
      final int columns = readCount(data, label + " columns");
      if (expectedRows < 0 && rows == 0) {
        throw new InvalidFormatException(label + " rows must be positive");
      }
      if (expectedRows >= 0 && rows != expectedRows) {
        throw new InvalidFormatException(label + " row count is " + rows + ", expected "
            + expectedRows);
      }
      if (columns != expectedColumns) {
        throw new InvalidFormatException(label + " column count is " + columns
            + ", expected " + expectedColumns);
      }
      reserveCells(remainingCells, (long) rows * columns, label);
      final float[][] matrix = new float[rows][];
      for (int r = 0; r < rows; r++) {
        final float[] row = new float[columns];
        for (int c = 0; c < columns; c++) {
          row[c] = readFiniteFloat(data, label);
        }
        matrix[r] = row;
      }
      return matrix;
    }

    /**
     * Writes a vector as its length followed by its values.
     *
     * @param data The output to write to.
     * @param vector The vector to write.
     * @throws IOException Thrown if writing fails.
     */
    private static void writeVector(DataOutputStream data, float[] vector) throws IOException {
      data.writeInt(vector.length);
      for (final float value : vector) {
        data.writeFloat(value);
      }
    }

    /**
     * Reads a vector written by {@link #writeVector}.
     *
     * @param data The input to read from.
     * @param expectedLength The required length.
     * @param remainingCells The one-element float budget, decremented in place.
     * @param label The vector name used in error messages.
     * @return The vector. Never {@code null}.
     * @throws IOException Thrown if reading fails, the length does not match, the budget
     *         is exceeded, or a value is not finite.
     */
    private static float[] readVector(DataInputStream data, long expectedLength,
        long[] remainingCells, String label) throws IOException {
      final int length = readCount(data, label + " length");
      if (length != expectedLength) {
        throw new InvalidFormatException(label + " length is " + length + ", expected "
            + expectedLength);
      }
      reserveCells(remainingCells, length, label);
      final float[] vector = new float[length];
      for (int i = 0; i < vector.length; i++) {
        vector[i] = readFiniteFloat(data, label);
      }
      return vector;
    }

    /**
     * Reads a count and checks it against {@link ResourceLimits#MAX_ENTRIES}.
     *
     * @param data The input to read from.
     * @param label The count name used in error messages.
     * @return The count, never negative.
     * @throws IOException Thrown if reading fails or the value is out of range.
     */
    private static int readCount(DataInputStream data, String label) throws IOException {
      final int value = data.readInt();
      ResourceLimits.requireWithinMaxEntries(value, label, InvalidFormatException::new);
      return value;
    }

    /**
     * Reserves float cells before allocating a matrix or vector.
     *
     * @param remaining The one-element budget, decremented in place.
     * @param count The number of values about to be allocated.
     * @param label The structure name used in error messages.
     * @throws IOException Thrown if {@code count} exceeds the remaining budget.
     */
    private static void reserveCells(long[] remaining, long count, String label)
        throws IOException {
      if (count > remaining[0]) {
        throw new InvalidFormatException(label + " exceeds the model cell limit of "
            + ResourceLimits.MAX_MATRIX_CELLS);
      }
      remaining[0] -= count;
    }

    /**
     * Reads one weight.
     *
     * @param data The input to read from.
     * @param label The structure name used in error messages.
     * @return The weight.
     * @throws IOException Thrown if reading fails or the value is NaN or infinite.
     */
    private static float readFiniteFloat(DataInputStream data, String label)
        throws IOException {
      final float value = data.readFloat();
      if (!Float.isFinite(value)) {
        throw new InvalidFormatException(label + " contains a non-finite value");
      }
      return value;
    }
  }
}
