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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.StringUtil;

/**
 * The weights of the feedforward transition parser: embeddings for words, tags, and arc
 * labels, one hidden layer with cube activation, and a transition output layer, stored
 * in a plain versioned binary format with no serialization framework involved. The
 * architecture follows
 * <a href="https://aclanthology.org/D14-1082/">Chen and Manning (2014)</a>.
 *
 * <p>This is the pure-Java neural tier: the network is executed with ordinary array
 * arithmetic, so parsing needs no native runtime, and the same class scores
 * configurations for training and decoding. Unknown words fall back to a learned
 * unknown symbol; words are matched case-insensitively after
 * {@link #normalize(String) normalization}.</p>
 *
 * <p>An instance is immutable and safe to share between threads once it has been handed
 * to a caller. {@link FeedforwardDependencyTrainer} fills the weights while building a
 * model and before that model escapes, and
 * {@link FeedforwardDependencyTrainer#refine refine} trains a copy rather than the model
 * it is given, so no model a caller holds ever changes underneath it.</p>
 *
 * @see FeedforwardDependencyParser
 * @see FeedforwardDependencyTrainer
 * @since 3.0.0
 */
@ThreadSafe
public class FeedforwardDependencyModel {

  private static final String MAGIC = "ONLP-FFDP-1";

  /** Maximum combined entries across the word, tag, and label maps. */
  private static final int MAX_VOCABULARY_ENTRIES = 2_000_000;

  /** Maximum transitions accepted from a serialized model. */
  private static final int MAX_TRANSITIONS = 100_000;

  /** Maximum embedding width accepted from a serialized model. */
  private static final int MAX_EMBEDDING_SIZE = 4_096;

  /** Maximum hidden-layer width accepted from a serialized model. */
  private static final int MAX_HIDDEN_SIZE = 65_536;

  /** Maximum float values allocated while loading a serialized model. */
  private static final long MAX_MODEL_FLOAT_VALUES = 100_000_000L;

  /** U+03A3, GREEK CAPITAL LETTER SIGMA, the one code point with a contextual lowering. */
  private static final int GREEK_CAPITAL_SIGMA = 0x03A3;

  /** U+03C2, GREEK SMALL LETTER FINAL SIGMA, the word-final lowering of the capital. */
  private static final char GREEK_SMALL_FINAL_SIGMA = '\u03C2';

  /** The vocabulary key of every word, tag, or label the model has no embedding row for. */
  static final String UNKNOWN = "*UNK*";

  /** The vocabulary key of a template position that does not exist in a configuration. */
  static final String ABSENT = "*NULL*";

  /** The vocabulary key of the artificial root node. */
  static final String ROOT_SYMBOL = "*ROOT*";

  /** The lazy scoring cache; {@code null} until {@link #enableScoringCache()}. */
  private volatile ContributionCache cache;

  private final Map<String, Integer> wordIds;
  private final Map<String, Integer> tagIds;
  private final Map<String, Integer> labelIds;
  private final String[] transitions;

  private final int embeddingSize;
  private final float[][] embeddings;
  private final float[][] hiddenWeights;
  private final float[] hiddenBias;
  private final float[][] outputWeights;
  private final float[] outputBias;

  FeedforwardDependencyModel(Map<String, Integer> wordIds, Map<String, Integer> tagIds,
      Map<String, Integer> labelIds, String[] transitions, int embeddingSize,
      float[][] embeddings, float[][] hiddenWeights, float[] hiddenBias,
      float[][] outputWeights, float[] outputBias) {
    this.wordIds = Map.copyOf(wordIds);
    this.tagIds = Map.copyOf(tagIds);
    this.labelIds = Map.copyOf(labelIds);
    this.transitions = transitions;
    this.embeddingSize = embeddingSize;
    this.embeddings = embeddings;
    this.hiddenWeights = hiddenWeights;
    this.hiddenBias = hiddenBias;
    this.outputWeights = outputWeights;
    this.outputBias = outputBias;
  }

  /**
   * Scores every transition for a configuration described by embedding row indices.
   *
   * @param features The embedding rows of the configuration, as produced by
   *                 {@link #featureIds(String[])}. Must not be {@code null}.
   * @return One unnormalized score per transition, indexed like
   *         {@link #transitions()}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code features} does not have the
   *         required length or contains an invalid embedding index.
   */
  public double[] score(int[] features) {
    if (features == null || features.length != FeedforwardContext.FEATURE_COUNT) {
      throw new IllegalArgumentException("features must contain "
          + FeedforwardContext.FEATURE_COUNT + " embedding indices");
    }
    for (int i = 0; i < features.length; i++) {
      if (features[i] < 0 || features[i] >= embeddings.length) {
        throw new IllegalArgumentException(
            "feature embedding out of range at index " + i + ": " + features[i]);
      }
    }
    final int hidden = hiddenBias.length;
    final double[] h = new double[hidden];
    for (int j = 0; j < hidden; j++) {
      h[j] = hiddenBias[j];
    }
    final ContributionCache cache = this.cache;
    for (int f = 0; f < features.length; f++) {
      final int row = features[f];
      final float[] contribution = cache == null ? null : cache.contribution(this, f, row);
      if (contribution != null) {
        for (int j = 0; j < hidden; j++) {
          h[j] += contribution[j];
        }
      } else {
        final float[] embedding = embeddings[row];
        final int offset = f * embeddingSize;
        for (int j = 0; j < hidden; j++) {
          final float[] weights = hiddenWeights[j];
          double sum = 0.0;
          for (int d = 0; d < embeddingSize; d++) {
            sum += weights[offset + d] * embedding[d];
          }
          h[j] += sum;
        }
      }
    }
    for (int j = 0; j < hidden; j++) {
      h[j] = h[j] * h[j] * h[j];
    }
    final double[] scores = new double[transitions.length];
    for (int o = 0; o < scores.length; o++) {
      final float[] row = outputWeights[o];
      double sum = outputBias[o];
      for (int j = 0; j < hidden; j++) {
        sum += row[j] * h[j];
      }
      scores[o] = sum;
    }
    return scores;
  }

  /**
   * Turns on the scoring cache: the hidden-layer contribution of a (template position,
   * embedding row) pair is a fixed vector while the weights do not change, so it is
   * computed once on first sight and afterwards added instead of being re-derived from
   * the embedding on every configuration.
   *
   * <p>Cached contributions are rounded to floats once, so scores may differ from the
   * uncached path in the last bits. The cache is bounded, safe for concurrent readers,
   * and only valid on a model whose weights no longer change: training and refinement
   * work on uncached copies, and {@link #copy()} never carries a cache over.</p>
   */
  void enableScoringCache() {
    if (cache == null) {
      cache = new ContributionCache(2 * FeedforwardContext.POSITIONS
          + FeedforwardContext.LABEL_POSITIONS, embeddings.length);
    }
  }

  /**
   * The bounded lazy contribution cache behind {@link #enableScoringCache()}: one
   * slot per (template position, embedding row) pair, filled on first use. Filling is
   * idempotent, so concurrent readers may compute a contribution twice but never see
   * a partial one, and a shared budget bounds the total memory; pairs beyond the
   * budget simply keep the direct path.
   */
  private static final class ContributionCache {

    /** The most (position, row) pairs the cache will hold. At a hidden size of 400
     * this bounds the cache near 100 MB; typical models stay far below the cap
     * because tag and label inventories are small and word usage is Zipf-shaped. */
    private static final int MAX_PAIRS = 65536;

    private final AtomicReferenceArray<float[]>[] byPosition;
    private final AtomicInteger remaining = new AtomicInteger(MAX_PAIRS);

    @SuppressWarnings("unchecked")
    private ContributionCache(int positions, int rows) {
      byPosition = new AtomicReferenceArray[positions];
      for (int f = 0; f < positions; f++) {
        byPosition[f] = new AtomicReferenceArray<>(rows);
      }
    }

    /**
     * Returns the cached hidden-layer contribution of one pair, computing and
     * publishing it on first sight while the budget lasts.
     *
     * @param model The frozen model the contributions derive from.
     * @param position The template position.
     * @param row The embedding row at that position.
     * @return The contribution vector, or {@code null} when the budget is spent and
     *         the pair is not cached.
     */
    private float[] contribution(FeedforwardDependencyModel model, int position, int row) {
      final AtomicReferenceArray<float[]> slots = byPosition[position];
      float[] contribution = slots.get(row);
      if (contribution != null) {
        return contribution;
      }
      if (remaining.get() <= 0) {
        return null;
      }
      final int hidden = model.hiddenBias.length;
      final float[] embedding = model.embeddings[row];
      final int offset = position * model.embeddingSize;
      contribution = new float[hidden];
      for (int j = 0; j < hidden; j++) {
        final float[] weights = model.hiddenWeights[j];
        double sum = 0.0;
        for (int d = 0; d < model.embeddingSize; d++) {
          sum += weights[offset + d] * embedding[d];
        }
        contribution[j] = (float) sum;
      }
      if (slots.compareAndSet(row, null, contribution)) {
        remaining.decrementAndGet();
      } else {
        contribution = slots.get(row);
      }
      return contribution;
    }
  }

  /**
   * Maps the symbolic features of {@link FeedforwardContext} onto embedding rows.
   *
   * @param symbols The symbolic features. Must not be {@code null}.
   * @return The embedding row per feature. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code symbols} does not have the
   *         required length.
   */
  public int[] featureIds(String[] symbols) {
    if (symbols == null || symbols.length != FeedforwardContext.FEATURE_COUNT) {
      throw new IllegalArgumentException("symbols must contain "
          + FeedforwardContext.FEATURE_COUNT + " features");
    }
    final int[] ids = new int[symbols.length];
    for (int i = 0; i < FeedforwardContext.POSITIONS; i++) {
      ids[i] = lookup(wordIds, normalize(symbols[i]));
    }
    for (int i = FeedforwardContext.POSITIONS; i < 2 * FeedforwardContext.POSITIONS; i++) {
      ids[i] = lookup(tagIds, symbols[i]);
    }
    for (int i = 2 * FeedforwardContext.POSITIONS; i < symbols.length; i++) {
      ids[i] = lookup(labelIds, symbols[i]);
    }
    return ids;
  }

  /**
   * @return The transition outcome strings by output index. Never {@code null}.
   */
  public String[] transitions() {
    return transitions.clone();
  }

  /**
   * Lowercases a word symbol; special symbols and absences pass through.
   *
   * <p>Case is mapped per code point through UnicodeData, the same mapping as
   * {@link StringUtil#toLowerCase(CharSequence)}, with one contextual rule on top: a
   * Greek capital sigma preceded by a letter and not followed by one lowercases to
   * the final form U+03C2, the Final_Sigma condition of the Unicode
   * <a href="https://www.unicode.org/Public/UCD/latest/ucd/SpecialCasing.txt">SpecialCasing</a>
   * file restricted to a single token. Natural lowercase Greek text, and with it
   * every vocabulary key derived from a treebank, spells a word-final sigma that
   * way, so without the rule an uppercase Greek word would normalize to a spelling
   * the vocabulary never contains. A word that is already lowercase, the common
   * case at parse time, is returned unchanged without allocating.</p>
   *
   * @param word The word to normalize. May be {@code null}.
   * @return The vocabulary key of {@code word}, or {@code null} if {@code word} is
   *         {@code null}.
   */
  static String normalize(String word) {
    if (word == null) {
      return null;
    }
    if (isSpecialSymbol(word)) {
      return word;
    }
    int i = 0;
    while (i < word.length()) {
      final int cp = word.codePointAt(i);
      if (Character.toLowerCase(cp) != cp) {
        break;
      }
      i += Character.charCount(cp);
    }
    if (i == word.length()) {
      return word;
    }
    final StringBuilder lowered = new StringBuilder(word.length());
    lowered.append(word, 0, i);
    while (i < word.length()) {
      final int cp = word.codePointAt(i);
      final int width = Character.charCount(cp);
      if (cp == GREEK_CAPITAL_SIGMA && i > 0
          && Character.isLetter(word.codePointBefore(i))
          && (i + width >= word.length()
              || !Character.isLetter(word.codePointAt(i + width)))) {
        lowered.append(GREEK_SMALL_FINAL_SIGMA);
      } else {
        lowered.appendCodePoint(Character.toLowerCase(cp));
      }
      i += width;
    }
    return lowered.toString();
  }

  /** Returns whether a symbol is reserved for the model's internal feature values. */
  static boolean isSpecialSymbol(String symbol) {
    return UNKNOWN.equals(symbol) || ABSENT.equals(symbol) || ROOT_SYMBOL.equals(symbol);
  }

  /**
   * Resolves a symbol to its embedding row: absences map to {@link #ABSENT}, symbols
   * without a row of their own fall back to {@link #UNKNOWN}.
   *
   * @param ids The vocabulary to resolve against.
   * @param symbol The symbol to resolve, or {@code null} for an absent position.
   * @return The embedding row of the symbol or of its fallback.
   */
  private static int lookup(Map<String, Integer> ids, String symbol) {
    Integer id = ids.get(symbol == null ? ABSENT : symbol);
    if (id == null) {
      id = ids.get(UNKNOWN);
    }
    if (id == null) {
      throw new IllegalStateException("vocabulary has no " + UNKNOWN + " row to fall back on");
    }
    return id;
  }

  /**
   * Writes the model in the versioned binary format.
   *
   * @param out The stream to write to. Must not be {@code null}. Not closed.
   * @throws IOException Thrown if writing fails.
   */
  public void serialize(OutputStream out) throws IOException {
    if (out == null) {
      throw new IllegalArgumentException("out must not be null");
    }
    final DataOutputStream data = new DataOutputStream(new BufferedOutputStream(out));
    data.writeUTF(MAGIC);
    writeVocabulary(data, wordIds);
    writeVocabulary(data, tagIds);
    writeVocabulary(data, labelIds);
    data.writeInt(transitions.length);
    for (final String transition : transitions) {
      data.writeUTF(transition);
    }
    data.writeInt(embeddingSize);
    writeMatrix(data, embeddings);
    writeMatrix(data, hiddenWeights);
    writeVector(data, hiddenBias);
    writeMatrix(data, outputWeights);
    writeVector(data, outputBias);
    data.flush();
  }

  /**
   * Loads a model from the versioned binary format.
   *
   * @param in The stream to read from. Must not be {@code null}. Not closed.
   * @return The loaded model. Never {@code null}.
   * @throws IOException Thrown if reading fails or the content is malformed.
   */
  public static FeedforwardDependencyModel load(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    final DataInputStream data = new DataInputStream(new BufferedInputStream(in));
    final String magic = data.readUTF();
    if (!MAGIC.equals(magic)) {
      throw new IOException("not a feedforward dependency model: " + magic);
    }
    final Map<String, Integer> wordIds = readVocabulary(data, "word vocabulary");
    final Map<String, Integer> tagIds = readVocabulary(data, "tag vocabulary");
    final Map<String, Integer> labelIds = readVocabulary(data, "label vocabulary");
    final int embeddingRows = validateVocabularies(wordIds, tagIds, labelIds);
    final String[] transitions = new String[
        readCount(data, "transition count", MAX_TRANSITIONS, false)];
    final Set<String> transitionSet = new HashSet<>();
    boolean hasShift = false;
    boolean hasRightArc = false;
    for (int i = 0; i < transitions.length; i++) {
      transitions[i] = data.readUTF();
      if (!transitionSet.add(transitions[i])) {
        throw new IOException("duplicate transition: " + transitions[i]);
      }
      try {
        final Transition transition = Transition.decode(transitions[i]);
        hasShift |= transition.type() == Transition.Type.SHIFT;
        hasRightArc |= transition.type() == Transition.Type.RIGHT_ARC;
      } catch (IllegalArgumentException e) {
        throw new IOException("invalid transition: " + transitions[i], e);
      }
    }
    if (!hasShift) {
      throw new IOException("transition inventory has no SHIFT action");
    }
    if (!hasRightArc) {
      throw new IOException("transition inventory has no RIGHT_ARC action");
    }
    final int embeddingSize = readCount(data, "embedding size", MAX_EMBEDDING_SIZE, false);
    final long[] remainingFloats = {MAX_MODEL_FLOAT_VALUES};
    final float[][] embeddings = readMatrix(data, embeddingRows, embeddingSize,
        MAX_VOCABULARY_ENTRIES, remainingFloats, "embedding matrix");
    final int inputSize = FeedforwardContext.FEATURE_COUNT * embeddingSize;
    final float[][] hiddenWeights = readMatrix(data, -1, inputSize,
        MAX_HIDDEN_SIZE, remainingFloats, "hidden matrix");
    final float[] hiddenBias = readVector(data, hiddenWeights.length,
        remainingFloats, "hidden bias");
    final float[][] outputWeights = readMatrix(data, transitions.length,
        hiddenWeights.length, MAX_TRANSITIONS, remainingFloats, "output matrix");
    final float[] outputBias = readVector(data, transitions.length,
        remainingFloats, "output bias");
    if (data.read() != -1) {
      throw new IOException("trailing data after feedforward dependency model");
    }
    return new FeedforwardDependencyModel(wordIds, tagIds, labelIds, transitions,
        embeddingSize, embeddings, hiddenWeights, hiddenBias, outputWeights, outputBias);
  }

  /**
   * Loads a model from a file.
   *
   * @param path The file to read. Must not be {@code null}.
   * @return The loaded model. Never {@code null}.
   * @throws IOException Thrown if reading fails or the content is not this format.
   */
  public static FeedforwardDependencyModel load(Path path) throws IOException {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    try (InputStream in = Files.newInputStream(path)) {
      return load(in);
    }
  }

  /**
   * Writes one vocabulary as its size followed by (symbol, id) pairs.
   */
  private static void writeVocabulary(DataOutputStream data, Map<String, Integer> ids)
      throws IOException {
    data.writeInt(ids.size());
    // Entries are written in ascending id order: the iteration order of the immutable
    // maps is salted per JVM launch, and serializing the same model must produce the
    // same bytes on every run.
    final List<Map.Entry<String, Integer>> entries = new ArrayList<>(ids.entrySet());
    entries.sort(Map.Entry.comparingByValue());
    for (final Map.Entry<String, Integer> entry : entries) {
      data.writeUTF(entry.getKey());
      data.writeInt(entry.getValue());
    }
  }

  /**
   * Reads one vocabulary written by {@link #writeVocabulary}.
   */
  private static Map<String, Integer> readVocabulary(DataInputStream data, String label)
      throws IOException {
    final int size = readCount(data, label + " size", MAX_VOCABULARY_ENTRIES, true);
    final Map<String, Integer> ids = new HashMap<>(size * 2);
    for (int i = 0; i < size; i++) {
      final String symbol = data.readUTF();
      final int id = data.readInt();
      if (ids.put(symbol, id) != null) {
        throw new IOException("duplicate symbol in " + label + ": " + symbol);
      }
    }
    return ids;
  }

  /**
   * Checks that all vocabulary ids form one consecutive embedding index range and that
   * each map has its required special symbols.
   */
  private static int validateVocabularies(Map<String, Integer> wordIds,
      Map<String, Integer> tagIds, Map<String, Integer> labelIds) throws IOException {
    final long total = (long) wordIds.size() + tagIds.size() + labelIds.size();
    if (total > MAX_VOCABULARY_ENTRIES) {
      throw new IOException("combined vocabulary size exceeds " + MAX_VOCABULARY_ENTRIES);
    }
    final boolean[] present = new boolean[(int) total];
    validateVocabulary(wordIds, present, "word vocabulary", UNKNOWN, ABSENT, ROOT_SYMBOL);
    validateVocabulary(tagIds, present, "tag vocabulary", UNKNOWN, ABSENT, ROOT_SYMBOL);
    validateVocabulary(labelIds, present, "label vocabulary", UNKNOWN, ABSENT);
    for (int i = 0; i < present.length; i++) {
      if (!present[i]) {
        throw new IOException("missing embedding id: " + i);
      }
    }
    return present.length;
  }

  /** Checks one vocabulary's required symbols and embedding ids. */
  private static void validateVocabulary(Map<String, Integer> ids, boolean[] present,
      String label, String... requiredSymbols) throws IOException {
    for (final String required : requiredSymbols) {
      if (!ids.containsKey(required)) {
        throw new IOException(label + " has no " + required + " symbol");
      }
    }
    for (final Map.Entry<String, Integer> entry : ids.entrySet()) {
      final int id = entry.getValue();
      if (id < 0 || id >= present.length) {
        throw new IOException(label + " id out of range for " + entry.getKey() + ": " + id);
      }
      if (present[id]) {
        throw new IOException("duplicate embedding id: " + id);
      }
      present[id] = true;
    }
  }

  /**
   * Writes a rectangular matrix as its dimensions followed by its values in row order.
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
   * Reads a matrix written by {@link #writeMatrix}.
   */
  private static float[][] readMatrix(DataInputStream data, int expectedRows,
      int expectedColumns, int maxRows, long[] remainingFloats, String label)
      throws IOException {
    final int rows = readCount(data, label + " rows", maxRows, false);
    final int columns = readCount(data, label + " columns", Integer.MAX_VALUE, false);
    if (expectedRows >= 0 && rows != expectedRows) {
      throw new IOException(label + " row count is " + rows + ", expected " + expectedRows);
    }
    if (columns != expectedColumns) {
      throw new IOException(label + " column count is " + columns
          + ", expected " + expectedColumns);
    }
    reserveFloats(remainingFloats, (long) rows * columns, label);
    final float[][] matrix = new float[rows][columns];
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < columns; c++) {
        matrix[r][c] = readFiniteFloat(data, label);
      }
    }
    return matrix;
  }

  /**
   * Writes a vector as its length followed by its values.
   */
  private static void writeVector(DataOutputStream data, float[] vector) throws IOException {
    data.writeInt(vector.length);
    for (final float value : vector) {
      data.writeFloat(value);
    }
  }

  /**
   * Reads a vector written by {@link #writeVector}.
   */
  private static float[] readVector(DataInputStream data, int expectedLength,
      long[] remainingFloats, String label) throws IOException {
    final int length = readCount(data, label + " length", Integer.MAX_VALUE, false);
    if (length != expectedLength) {
      throw new IOException(label + " length is " + length + ", expected " + expectedLength);
    }
    reserveFloats(remainingFloats, length, label);
    final float[] vector = new float[length];
    for (int i = 0; i < vector.length; i++) {
      vector[i] = readFiniteFloat(data, label);
    }
    return vector;
  }

  /** Reads a nonnegative bounded count from the model. */
  private static int readCount(DataInputStream data, String label, int maximum,
      boolean allowZero) throws IOException {
    final int value = data.readInt();
    if (value < 0 || !allowZero && value == 0 || value > maximum) {
      throw new IOException(label + " out of range: " + value);
    }
    return value;
  }

  /** Reserves float entries before allocating a matrix or vector. */
  private static void reserveFloats(long[] remaining, long count, String label)
      throws IOException {
    if (count > remaining[0]) {
      throw new IOException(label + " exceeds the model allocation limit");
    }
    remaining[0] -= count;
  }

  /** Reads one finite model weight. */
  private static float readFiniteFloat(DataInputStream data, String label)
      throws IOException {
    final float value = data.readFloat();
    if (!Float.isFinite(value)) {
      throw new IOException(label + " contains a non-finite value");
    }
    return value;
  }

  /**
   * Creates an independent copy of this model: the weights and the transition
   * inventory array are deep-copied, and the vocabularies are shared because their
   * maps are immutable.
   *
   * <p>This lets a training pass update the copy without ever writing to a model a
   * caller already holds, which is what keeps the immutability this class documents
   * true.</p>
   *
   * @return A copy of this model sharing no mutable state with it. Never {@code null}.
   */
  FeedforwardDependencyModel copy() {
    return new FeedforwardDependencyModel(wordIds, tagIds, labelIds, transitions.clone(),
        embeddingSize, copyOf(embeddings), copyOf(hiddenWeights), hiddenBias.clone(),
        copyOf(outputWeights), outputBias.clone());
  }

  /**
   * Deep-copies a matrix, row by row.
   */
  private static float[][] copyOf(float[][] matrix) {
    final float[][] copy = new float[matrix.length][];
    for (int r = 0; r < matrix.length; r++) {
      copy[r] = matrix[r].clone();
    }
    return copy;
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
   * @return The width of one embedding row.
   */
  int embeddingSize() {
    return embeddingSize;
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
}
