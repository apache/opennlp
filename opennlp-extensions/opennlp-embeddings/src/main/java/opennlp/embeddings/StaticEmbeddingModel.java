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
package opennlp.embeddings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import opennlp.tools.tokenize.BertTokenizer;
import opennlp.tools.tokenize.WordpieceTokenizer;

/**
 * A static (non-contextual) sentence embedding model: a per-token vector table plus WordPiece
 * tokenization, the pure-JVM word2vec/GloVe successor described in the design doc this module
 * implements. Distilled tables in this shape (Model2Vec and compatible releases) carry a modern
 * sentence-transformer's semantics in a flat lookup table, so embedding a sentence is tokenize,
 * gather, (optionally) weight, mean-pool, and (optionally) normalize: no model forward pass, no
 * GPU, no native runtime.
 *
 * <p>The pooling formula matches the reference Model2Vec implementations exactly (verified
 * against MinishLab's Rust {@code model2vec-rs}, not assumed): {@code [CLS]}/{@code [SEP]} are
 * never added to the pool (this class tokenizes for lookup, not for a transformer), unknown
 * tokens are dropped rather than contributing a meaningless vector, each remaining token's
 * vector is multiplied by its optional per-token weight, the sum is divided by the plain count
 * of pooled tokens (not the sum of weights), and if the model calls for normalization the
 * pooled vector is L2-normalized with an epsilon floor so a token-less input yields a zero
 * vector rather than a division by zero.</p>
 *
 * <p>Instances are immutable and safe for concurrent {@link #embed(String)} calls after
 * construction.</p>
 */
public final class StaticEmbeddingModel {

  private static final float NORMALIZE_EPSILON = 1e-12f;
  private static final String WEIGHTS_TENSOR_NAME = "weights";
  // Never meaningful as a "similar word" result.
  private static final Set<String> SPECIAL_TOKENS = Set.of(WordpieceTokenizer.BERT_CLS_TOKEN,
      WordpieceTokenizer.BERT_SEP_TOKEN, WordpieceTokenizer.BERT_UNK_TOKEN);

  private final float[] embeddings;
  private final float[] weights;
  private final int dimension;
  private final WordPieceVocabulary vocabulary;
  private final BertTokenizer tokenizer;
  private final boolean normalize;
  private final String unknownToken;

  private StaticEmbeddingModel(float[] embeddings, float[] weights, int dimension,
                                WordPieceVocabulary vocabulary, BertTokenizer tokenizer,
                                boolean normalize, String unknownToken) {
    this.embeddings = embeddings;
    this.weights = weights;
    this.dimension = dimension;
    this.vocabulary = vocabulary;
    this.tokenizer = tokenizer;
    this.normalize = normalize;
    this.unknownToken = unknownToken;
  }

  /**
   * Loads a static embedding model from a BERT-style {@code vocab.txt} and a safetensors weight
   * file, the file pair a Model2Vec-family distillation publishes. No model is bundled with this
   * module: the caller points at files they downloaded (see the module's design doc for the
   * license posture).
   *
   * @param vocabularyFile   The {@code vocab.txt} file: one token per line, line number is the
   *                         token's row id. Must not be {@code null} and must exist.
   * @param safetensorsFile  The {@code model.safetensors} file. Must not be {@code null} and
   *                         must exist, and must contain exactly one 2-D {@code F32} tensor
   *                         (the embedding matrix) whose row count matches the vocabulary size.
   *                         An optional 1-D {@code F32} tensor named {@code "weights"}, one
   *                         scalar per vocabulary row, is used as a per-token pooling weight
   *                         when present.
   * @param lowerCase        Whether the tokenizer should lower-case and strip accents, matching
   *                         the base model's tokenizer configuration ({@code true} for the
   *                         uncased BGE/BERT family this module targets).
   * @param normalize        Whether {@link #embed(String)} L2-normalizes its result, matching
   *                         the source model's {@code config.json} {@code normalize} field.
   * @return The loaded model.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, a file is missing
   *     or malformed, or the vocabulary size and the embedding matrix's row count disagree.
   */
  public static StaticEmbeddingModel load(Path vocabularyFile, Path safetensorsFile,
                                           boolean lowerCase, boolean normalize) {
    if (vocabularyFile == null) {
      throw new IllegalArgumentException("VocabularyFile must not be null");
    }
    if (safetensorsFile == null) {
      throw new IllegalArgumentException("SafetensorsFile must not be null");
    }
    final WordPieceVocabulary vocabulary = WordPieceVocabulary.read(vocabularyFile);
    final SafetensorsFile tensors = SafetensorsFile.read(safetensorsFile);

    final String matrixName = tensors.singleMatrixTensorName();
    final TensorInfo matrixInfo = tensors.tensorInfo(matrixName);
    if (matrixInfo.shape()[0] != vocabulary.size()) {
      throw new IllegalArgumentException("Vocabulary " + vocabularyFile + " has "
          + vocabulary.size() + " tokens but embedding matrix '" + matrixName + "' in "
          + safetensorsFile + " has " + matrixInfo.shape()[0] + " rows; these files do not "
          + "belong to the same model");
    }
    final int dimension = matrixInfo.shape()[1];
    final float[] embeddings = tensors.readFloat32(matrixName);

    float[] weights = null;
    if (tensors.tensorNames().contains(WEIGHTS_TENSOR_NAME)) {
      weights = tensors.readFloat32(WEIGHTS_TENSOR_NAME);
      if (weights.length != vocabulary.size()) {
        throw new IllegalArgumentException("Tensor '" + WEIGHTS_TENSOR_NAME + "' in "
            + safetensorsFile + " has " + weights.length + " elements but the vocabulary has "
            + vocabulary.size() + " tokens");
      }
    }

    final BertTokenizer tokenizer = new BertTokenizer(vocabulary.tokens(), lowerCase);
    return new StaticEmbeddingModel(embeddings, weights, dimension, vocabulary, tokenizer,
        normalize, WordpieceTokenizer.BERT_UNK_TOKEN);
  }

  /**
   * Embeds a piece of text.
   *
   * @param text The text to embed. Must not be {@code null}.
   * @return The pooled embedding vector, of length {@link #dimension()}. A text with no
   *     in-vocabulary tokens yields a zero vector.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public float[] embed(String text) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    // The tokenizer always wraps its output in [CLS] ... [SEP]; neither belongs in the pool
    // (this is table lookup, not transformer input), so the first and last tokens are skipped.
    final String[] tokens = tokenizer.tokenize(text);
    final float[] sum = new float[dimension];
    int pooledCount = 0;
    for (int i = 1; i < tokens.length - 1; i++) {
      final String token = tokens[i];
      if (unknownToken.equals(token)) {
        continue;
      }
      final OptionalInt id = vocabulary.id(token);
      if (id.isEmpty()) {
        throw new IllegalStateException("Tokenizer produced token '" + token
            + "' that is not in its own vocabulary; this indicates a tokenizer/vocabulary "
            + "construction bug, not an input problem");
      }
      final int row = id.getAsInt();
      final float weight = weights == null ? 1f : weights[row];
      final int base = row * dimension;
      for (int d = 0; d < dimension; d++) {
        sum[d] += embeddings[base + d] * weight;
      }
      pooledCount++;
    }
    final int denominator = Math.max(pooledCount, 1);
    for (int d = 0; d < dimension; d++) {
      sum[d] /= denominator;
    }
    if (normalize) {
      double sumOfSquares = 0;
      for (final float value : sum) {
        sumOfSquares += (double) value * value;
      }
      final float norm = (float) Math.max(Math.sqrt(sumOfSquares), NORMALIZE_EPSILON);
      for (int d = 0; d < dimension; d++) {
        sum[d] /= norm;
      }
    }
    return sum;
  }

  /** {@return the dimension of every vector this model produces} */
  public int dimension() {
    return dimension;
  }

  /** {@return the number of tokens in this model's vocabulary} */
  public int vocabularySize() {
    return vocabulary.size();
  }

  /**
   * Cosine similarity between two pieces of text's pooled embeddings, the classic word2vec-era
   * convenience this module exists to modernize.
   *
   * @param text1 The first text. Must not be {@code null}.
   * @param text2 The second text. Must not be {@code null}.
   * @return The cosine similarity, in {@code [-1, 1]}; {@code 0} when either text has no
   *     in-vocabulary tokens (an undefined direction, not an error).
   * @throws IllegalArgumentException Thrown if {@code text1} or {@code text2} is {@code null}.
   */
  public double similarity(String text1, String text2) {
    if (text1 == null) {
      throw new IllegalArgumentException("Text1 must not be null");
    }
    if (text2 == null) {
      throw new IllegalArgumentException("Text2 must not be null");
    }
    return cosineSimilarity(embed(text1), embed(text2));
  }

  /**
   * Finds the vocabulary tokens whose vectors are nearest a piece of text's pooled embedding,
   * most similar first. A brute-force scan over the whole vocabulary; fine for the vocabulary
   * sizes this module targets (tens of thousands of rows), not an approximate-nearest-neighbor
   * index (a documented follow-up, not v1 scope).
   *
   * @param text The query text. Must not be {@code null}.
   * @param topK The maximum number of results. Must be at least 1.
   * @return Up to {@code topK} neighbors, most similar first, excluding the special tokens
   *     ({@code [CLS]}, {@code [SEP]}, {@code [UNK]}); empty when {@code text} has no
   *     in-vocabulary tokens.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null} or {@code topK} is
   *     less than 1.
   */
  public List<Neighbor> mostSimilar(String text, int topK) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    requirePositive(topK);
    return nearestNeighbors(embed(text), topK, Set.of());
  }

  /**
   * The classic word2vec analogy: {@code b} is to {@code a} as the results are to {@code c}
   * (computed as {@code embed(b) - embed(a) + embed(c)}), for example {@code analogy("man",
   * "king", "woman", 1)} for "man is to king as woman is to ?".
   *
   * @param a    The first term. Must not be {@code null}.
   * @param b    The second term. Must not be {@code null}.
   * @param c    The third term. Must not be {@code null}.
   * @param topK The maximum number of results. Must be at least 1.
   * @return Up to {@code topK} neighbors, most similar first, excluding the special tokens and
   *     any vocabulary token that exactly matches {@code a}, {@code b}, or {@code c}.
   * @throws IllegalArgumentException Thrown if {@code a}, {@code b}, or {@code c} is
   *     {@code null}, or {@code topK} is less than 1.
   */
  public List<Neighbor> analogy(String a, String b, String c, int topK) {
    if (a == null) {
      throw new IllegalArgumentException("A must not be null");
    }
    if (b == null) {
      throw new IllegalArgumentException("B must not be null");
    }
    if (c == null) {
      throw new IllegalArgumentException("C must not be null");
    }
    requirePositive(topK);
    final float[] va = embed(a);
    final float[] vb = embed(b);
    final float[] vc = embed(c);
    final float[] target = new float[dimension];
    for (int d = 0; d < dimension; d++) {
      target[d] = vb[d] - va[d] + vc[d];
    }
    return nearestNeighbors(target, topK, Set.of(a, b, c));
  }

  private static void requirePositive(int topK) {
    if (topK < 1) {
      throw new IllegalArgumentException("TopK must be at least 1, got " + topK);
    }
  }

  private List<Neighbor> nearestNeighbors(float[] query, int topK, Set<String> exclude) {
    final double queryNorm = norm(query);
    if (queryNorm < NORMALIZE_EPSILON) {
      return List.of();
    }
    final List<Neighbor> candidates = new ArrayList<>(vocabulary.size());
    for (int row = 0; row < vocabulary.size(); row++) {
      final String token = vocabulary.token(row);
      if (SPECIAL_TOKENS.contains(token) || exclude.contains(token)) {
        continue;
      }
      final int base = row * dimension;
      double dot = 0;
      double rowNormSquared = 0;
      for (int d = 0; d < dimension; d++) {
        final float value = embeddings[base + d];
        dot += query[d] * value;
        rowNormSquared += (double) value * value;
      }
      final double rowNorm = Math.sqrt(rowNormSquared);
      final double similarity = rowNorm < NORMALIZE_EPSILON ? 0.0 : dot / (queryNorm * rowNorm);
      candidates.add(new Neighbor(token, similarity));
    }
    candidates.sort(Comparator.comparingDouble(Neighbor::similarity).reversed());
    return topK >= candidates.size() ? List.copyOf(candidates)
        : List.copyOf(candidates.subList(0, topK));
  }

  private static double cosineSimilarity(float[] a, float[] b) {
    double dot = 0;
    double normASquared = 0;
    double normBSquared = 0;
    for (int d = 0; d < a.length; d++) {
      dot += (double) a[d] * b[d];
      normASquared += (double) a[d] * a[d];
      normBSquared += (double) b[d] * b[d];
    }
    final double denominator = Math.sqrt(normASquared) * Math.sqrt(normBSquared);
    return denominator < NORMALIZE_EPSILON ? 0.0 : dot / denominator;
  }

  private static double norm(float[] vector) {
    double sumOfSquares = 0;
    for (final float value : vector) {
      sumOfSquares += (double) value * value;
    }
    return Math.sqrt(sumOfSquares);
  }
}
