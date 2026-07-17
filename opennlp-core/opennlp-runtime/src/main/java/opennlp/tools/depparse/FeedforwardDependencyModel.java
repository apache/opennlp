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
import java.util.List;
import java.util.Map;

import opennlp.tools.util.StringUtil;

/**
 * The weights of the feedforward transition parser: embeddings for words, tags, and arc
 * labels, one hidden layer with cube activation, and a transition output layer, stored
 * in a plain versioned binary format with no serialization framework involved.
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
public class FeedforwardDependencyModel {

  private static final String MAGIC = "ONLP-FFDP-1";

  /** U+03A3, GREEK CAPITAL LETTER SIGMA, the one code point with a contextual lowering. */
  private static final int GREEK_CAPITAL_SIGMA = 0x03A3;

  /** U+03C2, GREEK SMALL LETTER FINAL SIGMA, the word-final lowering of the capital. */
  private static final char GREEK_SMALL_FINAL_SIGMA = '\u03C2';

  static final String UNKNOWN = "*UNK*";
  static final String ABSENT = "*NULL*";

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
   */
  public double[] score(int[] features) {
    final int hidden = hiddenBias.length;
    final double[] h = new double[hidden];
    for (int j = 0; j < hidden; j++) {
      final float[] row = hiddenWeights[j];
      double sum = hiddenBias[j];
      for (int f = 0; f < features.length; f++) {
        final float[] embedding = embeddings[features[f]];
        final int offset = f * embeddingSize;
        for (int d = 0; d < embeddingSize; d++) {
          sum += row[offset + d] * embedding[d];
        }
      }
      h[j] = sum * sum * sum;
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
   * Maps the symbolic features of {@link FeedforwardContext} onto embedding rows.
   *
   * @param symbols The symbolic features. Must not be {@code null}.
   * @return The embedding row per feature. Never {@code null}.
   */
  public int[] featureIds(String[] symbols) {
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
   * the final form U+03C2, the Final_Sigma condition of the Unicode SpecialCasing
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
    if (word.startsWith("*")) {
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

  private static int lookup(Map<String, Integer> ids, String symbol) {
    Integer id = ids.get(symbol == null ? ABSENT : symbol);
    if (id == null) {
      id = ids.get(UNKNOWN);
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
   * @throws IOException Thrown if reading fails or the content is not this format.
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
    final Map<String, Integer> wordIds = readVocabulary(data);
    final Map<String, Integer> tagIds = readVocabulary(data);
    final Map<String, Integer> labelIds = readVocabulary(data);
    final String[] transitions = new String[data.readInt()];
    for (int i = 0; i < transitions.length; i++) {
      transitions[i] = data.readUTF();
    }
    final int embeddingSize = data.readInt();
    return new FeedforwardDependencyModel(wordIds, tagIds, labelIds, transitions,
        embeddingSize, readMatrix(data), readMatrix(data), readVector(data),
        readMatrix(data), readVector(data));
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

  private static Map<String, Integer> readVocabulary(DataInputStream data)
      throws IOException {
    final int size = data.readInt();
    final Map<String, Integer> ids = new HashMap<>(size * 2);
    for (int i = 0; i < size; i++) {
      final String symbol = data.readUTF();
      ids.put(symbol, data.readInt());
    }
    return ids;
  }

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

  private static float[][] readMatrix(DataInputStream data) throws IOException {
    final int rows = data.readInt();
    final int columns = data.readInt();
    final float[][] matrix = new float[rows][columns];
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < columns; c++) {
        matrix[r][c] = data.readFloat();
      }
    }
    return matrix;
  }

  private static void writeVector(DataOutputStream data, float[] vector) throws IOException {
    data.writeInt(vector.length);
    for (final float value : vector) {
      data.writeFloat(value);
    }
  }

  private static float[] readVector(DataInputStream data) throws IOException {
    final float[] vector = new float[data.readInt()];
    for (int i = 0; i < vector.length; i++) {
      vector[i] = data.readFloat();
    }
    return vector;
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

  private static float[][] copyOf(float[][] matrix) {
    final float[][] copy = new float[matrix.length][];
    for (int r = 0; r < matrix.length; r++) {
      copy[r] = matrix[r].clone();
    }
    return copy;
  }

  Map<String, Integer> wordIds() {
    return wordIds;
  }

  Map<String, Integer> tagIds() {
    return tagIds;
  }

  Map<String, Integer> labelIds() {
    return labelIds;
  }

  int embeddingSize() {
    return embeddingSize;
  }

  float[][] embeddings() {
    return embeddings;
  }

  float[][] hiddenWeights() {
    return hiddenWeights;
  }

  float[] hiddenBias() {
    return hiddenBias;
  }

  float[][] outputWeights() {
    return outputWeights;
  }

  float[] outputBias() {
    return outputBias;
  }
}
