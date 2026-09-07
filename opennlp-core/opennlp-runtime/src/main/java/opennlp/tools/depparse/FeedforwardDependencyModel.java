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
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringUtil;

/**
 * The weights of the feedforward transition parser: embeddings for words, tags, and arc
 * labels, one hidden layer with cube activation, and a transition output layer, stored
 * in a plain versioned binary format with no serialization framework involved. The
 * architecture follows
 * <a href="https://aclanthology.org/D14-1082/">Chen and Manning (2014)</a>.
 *
 * <p>The network runs with Java arrays and requires no native runtime. The same class
 * scores configurations during training and decoding. Unknown words use a learned
 * fallback row; words are matched case-insensitively after
 * {@link #normalize(String) normalization}.</p>
 *
 * <p>Instances returned by {@link FeedforwardDependencyTrainer} are immutable and safe
 * to share between threads. {@link FeedforwardDependencyTrainer#refine refine} updates
 * an independent copy.</p>
 *
 * @see FeedforwardDependencyParser
 * @see FeedforwardDependencyTrainer
 * @since 3.0.0
 */
@ThreadSafe
public class FeedforwardDependencyModel {

  /** The format header written before every serialized model; it carries the format version. */
  private static final String MAGIC = "ONLP-FFDP-1";

  /** The vocabulary label used in load errors for the word map. */
  private static final String WORD_VOCABULARY = "word vocabulary";

  /** The vocabulary label used in load errors for the tag map. */
  private static final String TAG_VOCABULARY = "tag vocabulary";

  /** The vocabulary label used in load errors for the label map. */
  private static final String LABEL_VOCABULARY = "label vocabulary";

  /** Maximum combined entries across the word, tag, and label maps. */
  private static final int MAX_VOCABULARY_ENTRIES = 2_000_000;

  /** Maximum transitions accepted from a serialized model. */
  private static final int MAX_TRANSITIONS = 100_000;

  /** Maximum embedding width accepted from a serialized model. */
  static final int MAX_EMBEDDING_SIZE = 4_096;

  /** Maximum hidden-layer width accepted from a serialized model. */
  static final int MAX_HIDDEN_SIZE = 65_536;

  /** Maximum float values allocated while loading a serialized model. */
  static final long MAX_MODEL_FLOAT_VALUES = 100_000_000L;

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

  /**
   * Assembles a model from its vocabularies and weights, copying the maps so later
   * changes by the caller do not reach the model.
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
   */
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
   * @throws IllegalArgumentException Thrown if {@code features} is {@code null}, does not
   *         have the required length, or contains an invalid embedding index.
   */
  double[] score(int[] features) {
    if (features == null) {
      throw new IllegalArgumentException("features must not be null");
    }
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
   * and only valid on a model whose weights stay fixed: training and refinement
   * work on uncached copies, and {@link #copy()} never carries a cache over.</p>
   */
  void enableScoringCache() {
    if (cache == null) {
      cache = new ContributionCache(FeedforwardContext.FEATURE_COUNT, embeddings.length);
    }
  }

  /**
   * The bounded lazy contribution cache behind {@link #enableScoringCache()}: one
   * slot per (template position, embedding row) pair, filled on first use. Filling is
   * idempotent, so concurrent readers may compute a contribution twice but never see
   * a partial one, and a shared budget bounds the total memory; pairs beyond the
   * budget use direct scoring.
   */
  private static final class ContributionCache {

    /**
     * The most (position, row) pairs the cache will hold. At a hidden size of 400 this
     * bounds the cache near 100 MB; typical models stay far below the cap because tag
     * and label inventories are small and word usage is Zipf-shaped.
     */
    private static final int MAX_PAIRS = 65536;

    /** The cached contribution vectors, indexed by template position and embedding row. */
    private final AtomicReferenceArray<float[]>[] byPosition;

    /** The number of pairs the cache may still add before the budget is spent. */
    private final AtomicInteger remaining = new AtomicInteger(MAX_PAIRS);

    /**
     * Creates an empty cache.
     *
     * @param positions The number of feature positions.
     * @param rows The number of embedding rows a position can hold.
     */
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
     * @param model The immutable model the contributions derive from.
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
   * @throws IllegalArgumentException Thrown if {@code symbols} is {@code null} or does
   *         not have the required length.
   */
  int[] featureIds(String[] symbols) {
    if (symbols == null) {
      throw new IllegalArgumentException("symbols must not be null");
    }
    if (symbols.length != FeedforwardContext.FEATURE_COUNT) {
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
   * <p>Case is mapped per code point like
   * {@link StringUtil#toLowerCase(CharSequence)}. Greek capital sigma also applies the
   * cased-letter and case-ignorable context defined by the Unicode
   * <a href="https://www.unicode.org/Public/UCD/latest/ucd/SpecialCasing.txt">SpecialCasing</a>
   * data within one token.</p>
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
    final String simple = StringUtil.toLowerCase(word);
    if (simple.equals(word)) {
      return word;
    }
    StringBuilder contextual = null;
    int sourceIndex = 0;
    int loweredIndex = 0;
    while (sourceIndex < word.length()) {
      final int cp = word.codePointAt(sourceIndex);
      final int width = Character.charCount(cp);
      final int loweredCp = simple.codePointAt(loweredIndex);
      final int loweredWidth = Character.charCount(loweredCp);
      if (cp == GREEK_CAPITAL_SIGMA && hasCasedLetterBefore(word, sourceIndex)
          && !hasCasedLetterAfter(word, sourceIndex + width)) {
        if (contextual == null) {
          contextual = new StringBuilder(simple.length());
          contextual.append(simple, 0, loweredIndex);
        }
        contextual.append(GREEK_SMALL_FINAL_SIGMA);
      } else if (contextual != null) {
        contextual.appendCodePoint(loweredCp);
      }
      sourceIndex += width;
      loweredIndex += loweredWidth;
    }
    return contextual == null ? simple : contextual.toString();
  }

  /**
   * Checks the final-sigma prefix context, skipping case-ignorable code points.
   *
   * @param word The word being normalized.
   * @param index The index of the capital sigma in {@code word}.
   * @return {@code true} if a cased letter precedes {@code index}.
   */
  private static boolean hasCasedLetterBefore(String word, int index) {
    int current = index;
    while (current > 0) {
      final int cp = word.codePointBefore(current);
      current -= Character.charCount(cp);
      if (!isCaseIgnorable(cp)) {
        return isCased(cp);
      }
    }
    return false;
  }

  /**
   * Checks the final-sigma suffix context, skipping case-ignorable code points.
   *
   * @param word The word being normalized.
   * @param index The index just after the capital sigma in {@code word}.
   * @return {@code true} if a cased letter follows at or after {@code index}.
   */
  private static boolean hasCasedLetterAfter(String word, int index) {
    int current = index;
    while (current < word.length()) {
      final int cp = word.codePointAt(current);
      if (!isCaseIgnorable(cp)) {
        return isCased(cp);
      }
      current += Character.charCount(cp);
    }
    return false;
  }

  /**
   * @param cp The code point to test.
   * @return {@code true} if {@code cp} is uppercase, lowercase, or titlecase.
   */
  private static boolean isCased(int cp) {
    return Character.isUpperCase(cp) || Character.isLowerCase(cp)
        || Character.isTitleCase(cp);
  }

  /**
   * @param cp The code point to test.
   * @return {@code true} if {@code cp} is skipped when looking for cased neighbors.
   */
  private static boolean isCaseIgnorable(int cp) {
    final boolean categoryMatches = switch (Character.getType(cp)) {
      case Character.NON_SPACING_MARK, Character.ENCLOSING_MARK,
          Character.FORMAT, Character.MODIFIER_LETTER, Character.MODIFIER_SYMBOL -> true;
      default -> false;
    };
    if (categoryMatches) {
      return true;
    }
    return switch (cp) {
      case '\'', '.', ':', '\u00B7', '\u0387', '\u055F', '\u05F4',
          '\u2018', '\u2019', '\u2024', '\u2027', '\uFE13', '\uFE52',
          '\uFE55', '\uFF07', '\uFF0E', '\uFF1A' -> true;
      default -> false;
    };
  }

  /**
   * @param symbol The symbol to test. May be {@code null}.
   * @return {@code true} if {@code symbol} is one of the reserved feature values.
   */
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
   * @throws IllegalStateException Thrown if {@code ids} has no {@link #UNKNOWN} row.
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
   * @throws IOException Thrown if reading fails.
   * @throws InvalidFormatException Thrown if the content is not a valid model.
   */
  public static FeedforwardDependencyModel load(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    final DataInputStream data = new DataInputStream(new BufferedInputStream(in));
    final String magic = data.readUTF();
    if (!MAGIC.equals(magic)) {
      throw new InvalidFormatException("not a feedforward dependency model: " + magic);
    }
    final Map<String, Integer> wordIds = readVocabulary(data, WORD_VOCABULARY);
    final Map<String, Integer> tagIds = readVocabulary(data, TAG_VOCABULARY);
    final Map<String, Integer> labelIds = readVocabulary(data, LABEL_VOCABULARY);
    final int embeddingRows = validateVocabularies(wordIds, tagIds, labelIds);
    final String[] transitions = new String[
        readCount(data, "transition count", MAX_TRANSITIONS, false)];
    final Set<String> transitionSet = new HashSet<>();
    boolean hasShift = false;
    boolean hasRightArc = false;
    for (int i = 0; i < transitions.length; i++) {
      transitions[i] = data.readUTF();
      if (!transitionSet.add(transitions[i])) {
        throw new InvalidFormatException("duplicate transition: " + transitions[i]);
      }
      try {
        final Transition transition = Transition.decode(transitions[i]);
        hasShift |= transition.type() == Transition.Type.SHIFT;
        hasRightArc |= transition.type() == Transition.Type.RIGHT_ARC;
      } catch (IllegalArgumentException e) {
        throw new InvalidFormatException("invalid transition: " + transitions[i], e);
      }
    }
    if (!hasShift) {
      throw new InvalidFormatException("transition inventory has no SHIFT action");
    }
    if (!hasRightArc) {
      throw new InvalidFormatException("transition inventory has no RIGHT_ARC action");
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
      throw new InvalidFormatException("trailing data after feedforward dependency model");
    }
    return new FeedforwardDependencyModel(wordIds, tagIds, labelIds, transitions,
        embeddingSize, embeddings, hiddenWeights, hiddenBias, outputWeights, outputBias);
  }

  /**
   * Loads a model from a file.
   *
   * @param path The file to read. Must not be {@code null}.
   * @return The loaded model. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws InvalidFormatException Thrown if the content is not a valid model.
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
   * Writes one vocabulary as its size followed by (symbol, id) pairs in ascending id
   * order, so that serializing the same model yields the same bytes on every JVM.
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
      data.writeUTF(entry.getKey());
      data.writeInt(entry.getValue());
    }
  }

  /**
   * Reads one vocabulary written by {@link #writeVocabulary}.
   *
   * @param data The input to read from.
   * @param label The vocabulary name used in error messages.
   * @return The symbol to id map. Never {@code null}.
   * @throws IOException Thrown if reading fails, the size is out of range, or a symbol
   *         repeats.
   */
  private static Map<String, Integer> readVocabulary(DataInputStream data, String label)
      throws IOException {
    final int size = readCount(data, label + " size", MAX_VOCABULARY_ENTRIES, true);
    final Map<String, Integer> ids = new HashMap<>(size * 2);
    for (int i = 0; i < size; i++) {
      final String symbol = data.readUTF();
      final int id = data.readInt();
      if (ids.put(symbol, id) != null) {
        throw new InvalidFormatException("duplicate symbol in " + label + ": " + symbol);
      }
    }
    return ids;
  }

  /**
   * Checks that all vocabulary ids form one consecutive embedding index range and that
   * each map has its required special symbols.
   *
   * @param wordIds The word vocabulary.
   * @param tagIds The tag vocabulary.
   * @param labelIds The label vocabulary.
   * @return The number of embedding rows the vocabularies address.
   * @throws IOException Thrown if the combined size exceeds the limit, a required symbol
   *         is missing, or the ids are not a consecutive range starting at zero.
   */
  private static int validateVocabularies(Map<String, Integer> wordIds,
      Map<String, Integer> tagIds, Map<String, Integer> labelIds) throws IOException {
    final long total = (long) wordIds.size() + tagIds.size() + labelIds.size();
    if (total > MAX_VOCABULARY_ENTRIES) {
      throw new InvalidFormatException("combined vocabulary size exceeds " + MAX_VOCABULARY_ENTRIES);
    }
    final boolean[] present = new boolean[(int) total];
    validateVocabulary(wordIds, present, WORD_VOCABULARY, UNKNOWN, ABSENT, ROOT_SYMBOL);
    validateVocabulary(tagIds, present, TAG_VOCABULARY, UNKNOWN, ABSENT, ROOT_SYMBOL);
    validateVocabulary(labelIds, present, LABEL_VOCABULARY, UNKNOWN, ABSENT);
    for (int i = 0; i < present.length; i++) {
      if (!present[i]) {
        throw new InvalidFormatException("missing embedding id: " + i);
      }
    }
    return present.length;
  }

  /**
   * Checks one vocabulary's required symbols and embedding ids.
   *
   * @param ids The vocabulary to check.
   * @param present The ids seen so far across all vocabularies; updated in place.
   * @param label The vocabulary name used in error messages.
   * @param requiredSymbols The symbols the vocabulary must contain.
   * @throws IOException Thrown if a required symbol is missing or an id is out of range
   *         or already taken.
   */
  private static void validateVocabulary(Map<String, Integer> ids, boolean[] present,
      String label, String... requiredSymbols) throws IOException {
    for (final String required : requiredSymbols) {
      if (!ids.containsKey(required)) {
        throw new InvalidFormatException(label + " has no " + required + " symbol");
      }
    }
    for (final Map.Entry<String, Integer> entry : ids.entrySet()) {
      final int id = entry.getValue();
      if (id < 0 || id >= present.length) {
        throw new InvalidFormatException(label + " id out of range for " + entry.getKey() + ": " + id);
      }
      if (present[id]) {
        throw new InvalidFormatException("duplicate embedding id: " + id);
      }
      present[id] = true;
    }
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
   * Reads a matrix written by {@link #writeMatrix}.
   *
   * @param data The input to read from.
   * @param expectedRows The required row count, or a negative value to accept any count
   *                     up to {@code maxRows}.
   * @param expectedColumns The required column count.
   * @param maxRows The largest row count accepted.
   * @param remainingFloats The one-element allocation budget, decremented in place.
   * @param label The matrix name used in error messages.
   * @return The matrix. Never {@code null}.
   * @throws IOException Thrown if reading fails, a dimension is out of range or does not
   *         match, the budget is exceeded, or a value is not finite.
   */
  private static float[][] readMatrix(DataInputStream data, int expectedRows,
      int expectedColumns, int maxRows, long[] remainingFloats, String label)
      throws IOException {
    final int rows = readCount(data, label + " rows", maxRows, false);
    final int columns = readCount(data, label + " columns", Integer.MAX_VALUE, false);
    if (expectedRows >= 0 && rows != expectedRows) {
      throw new InvalidFormatException(label + " row count is " + rows + ", expected " + expectedRows);
    }
    if (columns != expectedColumns) {
      throw new InvalidFormatException(label + " column count is " + columns
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
   * @param remainingFloats The one-element allocation budget, decremented in place.
   * @param label The vector name used in error messages.
   * @return The vector. Never {@code null}.
   * @throws IOException Thrown if reading fails, the length does not match, the budget
   *         is exceeded, or a value is not finite.
   */
  private static float[] readVector(DataInputStream data, int expectedLength,
      long[] remainingFloats, String label) throws IOException {
    final int length = readCount(data, label + " length", Integer.MAX_VALUE, false);
    if (length != expectedLength) {
      throw new InvalidFormatException(label + " length is " + length + ", expected " + expectedLength);
    }
    reserveFloats(remainingFloats, length, label);
    final float[] vector = new float[length];
    for (int i = 0; i < vector.length; i++) {
      vector[i] = readFiniteFloat(data, label);
    }
    return vector;
  }

  /**
   * Reads a bounded count from the model.
   *
   * @param data The input to read from.
   * @param label The count name used in error messages.
   * @param maximum The largest value accepted.
   * @param allowZero Whether zero is accepted.
   * @return The count.
   * @throws IOException Thrown if reading fails or the value is out of range.
   */
  private static int readCount(DataInputStream data, String label, int maximum,
      boolean allowZero) throws IOException {
    final int value = data.readInt();
    if (value < 0 || !allowZero && value == 0 || value > maximum) {
      throw new InvalidFormatException(label + " out of range: " + value);
    }
    return value;
  }

  /**
   * Reserves float entries before allocating a matrix or vector.
   *
   * @param remaining The one-element allocation budget, decremented in place.
   * @param count The number of values about to be allocated.
   * @param label The structure name used in error messages.
   * @throws IOException Thrown if {@code count} exceeds the remaining budget.
   */
  private static void reserveFloats(long[] remaining, long count, String label)
      throws IOException {
    if (count > remaining[0]) {
      throw new InvalidFormatException(label + " exceeds the model allocation limit");
    }
    remaining[0] -= count;
  }

  /**
   * Reads one model weight.
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

  /**
   * Creates an independent copy of this model: the weights and the transition
   * inventory array are deep-copied, and the vocabularies are shared because their
   * maps are immutable. Training passes update the copy, never a model a caller holds.
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
