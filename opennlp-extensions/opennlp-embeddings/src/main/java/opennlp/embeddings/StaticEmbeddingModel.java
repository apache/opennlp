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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.IntPredicate;

import opennlp.subword.sentencepiece.SentencePieceTokenizer;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.tokenize.SubwordTokenizer;
import opennlp.tools.tokenize.WordpieceEncoder;
import opennlp.tools.tokenize.WordpieceTokenizer;

/**
 * A static (non-contextual) sentence embedding model: a per-token vector table plus subword
 * tokenization. Embedding a sentence is tokenize, gather each piece's row, optionally weight,
 * mean-pool, and optionally L2-normalize; there is no model forward pass.
 *
 * <p>It loads distilled tables in the Model2Vec release layout for both tokenizer families:
 * WordPiece models carry a {@code vocab.txt} whose line number is the matrix row, and
 * SentencePiece models carry a Unigram {@code tokenizer.json} whose {@code model.vocab} list
 * order is the row order, next to the trained SentencePiece {@code .model} file that performs
 * the segmentation. In both cases the {@code model.safetensors} holds one 2-D float matrix, with
 * an optional per-token {@code weights} tensor. Matrix rows are resolved by piece <i>string</i>,
 * never by tokenizer id, so the two files may order or offset their ids differently without
 * corrupting lookups; a piece the matrix does not carry fails loud at load time.</p>
 *
 * <p>Special pieces (the WordPiece {@code [CLS]}/{@code [SEP]}/{@code [UNK]} frame, a
 * SentencePiece model's control and unknown pieces) are never pooled; the sum is divided by the
 * count of pooled pieces, not the sum of weights. A text with no in-vocabulary pieces yields a
 * zero vector.</p>
 *
 * <p>Instances are immutable and safe for concurrent use after construction.</p>
 */
@ThreadSafe
public final class StaticEmbeddingModel implements TextEmbedder {

  /** How the tokenizer treats letter case, matching the base model's tokenizer configuration. */
  public enum Casing {

    /** Lower-case and strip accents, the uncased BGE/BERT convention. */
    UNCASED,

    /** Preserve case and accents. */
    CASED
  }

  /** Whether pooled vectors are length-normalized, matching the model's configuration. */
  public enum Normalization {

    /** L2-normalize each pooled vector. */
    L2,

    /** Leave pooled vectors unnormalized. */
    NONE
  }

  private static final float NORMALIZE_EPSILON = 1e-12f;
  private static final String WEIGHTS_TENSOR_NAME = "weights";
  private static final String VOCABULARY_FILE_NAME = "vocab.txt";
  private static final String SAFETENSORS_FILE_NAME = "model.safetensors";
  private static final String CONFIG_FILE_NAME = "config.json";
  private static final String TOKENIZER_CONFIG_FILE_NAME = "tokenizer_config.json";
  private static final String TOKENIZER_JSON_FILE_NAME = "tokenizer.json";
  // The file names SentencePiece models ship their trained .model under, by convention family.
  private static final List<String> SENTENCEPIECE_MODEL_FILE_NAMES =
      List.of("sentencepiece.bpe.model", "spiece.model", "tokenizer.model");
  private static final int[] NO_EXCLUDED_ROWS = new int[0];
  // Never meaningful as a "similar word" result.
  private static final Set<String> WORDPIECE_SPECIAL_TOKENS =
      Set.of(WordpieceTokenizer.BERT_CLS_TOKEN, WordpieceTokenizer.BERT_SEP_TOKEN,
          WordpieceTokenizer.BERT_UNK_TOKEN);
  private static final Set<String> SENTENCEPIECE_SPECIAL_TOKENS =
      Set.of("<s>", "</s>", "<pad>", "<unk>", "<mask>");

  private final float[] embeddings;
  private final float[] weights;
  private final int dimension;
  private final EmbeddingVocabulary vocabulary;
  private final SubwordTokenizer tokenizer;
  // Tokenizer-id-space test for pieces that are never pooled: the WordPiece frame and unknown
  // pieces, or a SentencePiece model's control and unknown pieces (whose piece string is the
  // unmatched surface text, not a vocabulary entry).
  private final IntPredicate skipPieceId;
  private final boolean normalize;
  // Per-row L2 norms and special-token mask, precomputed at load time so the neighbor scan
  // does no per-row square root or string hashing.
  private final double[] rowNorms;
  private final boolean[] specialRows;

  private StaticEmbeddingModel(float[] embeddings, float[] weights, int dimension,
                                EmbeddingVocabulary vocabulary, SubwordTokenizer tokenizer,
                                IntPredicate skipPieceId, boolean normalize, double[] rowNorms,
                                boolean[] specialRows) {
    this.embeddings = embeddings;
    this.weights = weights;
    this.dimension = dimension;
    this.vocabulary = vocabulary;
    this.tokenizer = tokenizer;
    this.skipPieceId = skipPieceId;
    this.normalize = normalize;
    this.rowNorms = rowNorms;
    this.specialRows = specialRows;
  }

  /**
   * Loads a static embedding model from a model directory, detecting the tokenizer family from
   * the files present and reading the pooling switch ({@code normalize}) from the model's
   * {@code config.json}.
   *
   * <p>A directory with a {@code vocab.txt} is a WordPiece model; its casing is read from
   * {@code do_lower_case} in {@code tokenizer_config.json}. A {@code strip_accents} that
   * explicitly disagrees with {@code do_lower_case} cannot be represented by the single
   * lower-case switch of {@link #load(Path, Path, Casing, Normalization)} and is rejected rather
   * than silently mis-tokenized; when absent or {@code null} it follows the BERT convention of
   * stripping accents exactly when lower-casing. When both layouts are present, the
   * {@code vocab.txt} wins.</p>
   *
   * <p>A directory with a trained SentencePiece file ({@code sentencepiece.bpe.model},
   * {@code spiece.model}, or {@code tokenizer.model}) next to a Unigram {@code tokenizer.json}
   * is a SentencePiece model; the {@code .model} file carries its own text normalizer, so there
   * is no casing switch to read.</p>
   *
   * @param modelDirectory The model directory. Must not be {@code null} and must be a
   *                       directory.
   * @return The loaded model.
   * @throws IllegalArgumentException Thrown if {@code modelDirectory} is {@code null} or not a
   *     directory, neither layout's files are present, a configuration file is malformed or
   *     lacks its field, the accent handling is not representable, or the tokenizer and the
   *     embedding matrix disagree.
   * @throws IOException Thrown if reading a file fails.
   */
  public static StaticEmbeddingModel load(Path modelDirectory) throws IOException {
    if (modelDirectory == null) {
      throw new IllegalArgumentException("ModelDirectory must not be null");
    }
    if (!Files.isDirectory(modelDirectory)) {
      throw new IllegalArgumentException(
          "Model directory does not exist or is not a directory: " + modelDirectory);
    }
    final Path vocabularyFile = modelDirectory.resolve(VOCABULARY_FILE_NAME);
    if (Files.isRegularFile(vocabularyFile)) {
      return loadWordpieceDirectory(modelDirectory, vocabularyFile);
    }
    final Path sentencePieceModelFile = firstRegularFile(modelDirectory,
        SENTENCEPIECE_MODEL_FILE_NAMES);
    final Path tokenizerJsonFile = modelDirectory.resolve(TOKENIZER_JSON_FILE_NAME);
    if (sentencePieceModelFile != null && Files.isRegularFile(tokenizerJsonFile)) {
      return loadSentencePiece(sentencePieceModelFile, tokenizerJsonFile,
          requiredFile(modelDirectory, SAFETENSORS_FILE_NAME),
          requiredNormalize(requiredFile(modelDirectory, CONFIG_FILE_NAME)));
    }
    if (Files.isRegularFile(tokenizerJsonFile)) {
      throw new IllegalArgumentException("Model directory " + modelDirectory + " has a "
          + TOKENIZER_JSON_FILE_NAME + " but no trained SentencePiece file ("
          + String.join(", ", SENTENCEPIECE_MODEL_FILE_NAMES) + "); copy the .model file "
          + "from the model's base tokenizer next to it");
    }
    throw new IllegalArgumentException("Model directory " + modelDirectory + " has neither a "
        + VOCABULARY_FILE_NAME + " (WordPiece layout) nor a " + TOKENIZER_JSON_FILE_NAME
        + " with a trained SentencePiece file (SentencePiece layout)");
  }

  /**
   * Loads the WordPiece directory layout, reading the tokenizer and pooling switches from the
   * model's own configuration files.
   *
   * @param modelDirectory The model directory.
   * @param vocabularyFile The directory's {@code vocab.txt}.
   * @return The loaded model.
   * @throws IOException Thrown if reading a file fails.
   */
  private static StaticEmbeddingModel loadWordpieceDirectory(Path modelDirectory,
                                                             Path vocabularyFile)
      throws IOException {
    final Path safetensorsFile = requiredFile(modelDirectory, SAFETENSORS_FILE_NAME);
    final Path tokenizerConfigFile = requiredFile(modelDirectory, TOKENIZER_CONFIG_FILE_NAME);
    final Normalization normalization =
        requiredNormalize(requiredFile(modelDirectory, CONFIG_FILE_NAME));
    final Boolean lowerCase =
        FlatJsonFields.topLevelBoolean(tokenizerConfigFile, "do_lower_case");
    if (lowerCase == null) {
      throw new IllegalArgumentException(tokenizerConfigFile + " has no boolean "
          + "'do_lower_case' field; use load(vocabularyFile, safetensorsFile, casing, "
          + "normalization) and choose explicitly");
    }
    final Boolean stripAccents =
        FlatJsonFields.topLevelBoolean(tokenizerConfigFile, "strip_accents");
    if (stripAccents != null && !stripAccents.equals(lowerCase)) {
      throw new IllegalArgumentException(tokenizerConfigFile + " sets strip_accents="
          + stripAccents + " against do_lower_case=" + lowerCase + "; the single lower-case "
          + "switch strips accents exactly when lower-casing, so this model must be loaded "
          + "with load(vocabularyFile, safetensorsFile, casing, normalization) after choosing "
          + "deliberately");
    }
    return load(vocabularyFile, safetensorsFile,
        lowerCase ? Casing.UNCASED : Casing.CASED, normalization);
  }

  /**
   * Reads the required {@code normalize} switch out of a model's {@code config.json}.
   *
   * @param configFile The {@code config.json} file.
   * @return The corresponding {@link Normalization}.
   * @throws IllegalArgumentException Thrown if the field is missing or not a boolean.
   * @throws IOException Thrown if reading the file fails.
   */
  private static Normalization requiredNormalize(Path configFile) throws IOException {
    final Boolean normalize = FlatJsonFields.topLevelBoolean(configFile, "normalize");
    if (normalize == null) {
      throw new IllegalArgumentException(configFile + " has no boolean 'normalize' field; "
          + "use the explicit load overloads and choose the normalization deliberately");
    }
    return normalize ? Normalization.L2 : Normalization.NONE;
  }

  /**
   * {@return the first of the given file names that exists as a regular file in the directory,
   * or {@code null} when none does}
   *
   * @param directory The directory to look in.
   * @param names     The file names to try, in order.
   */
  private static Path firstRegularFile(Path directory, List<String> names) {
    for (final String name : names) {
      final Path file = directory.resolve(name);
      if (Files.isRegularFile(file)) {
        return file;
      }
    }
    return null;
  }

  private static Path requiredFile(Path modelDirectory, String name) {
    final Path file = modelDirectory.resolve(name);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("Model directory " + modelDirectory + " has no "
          + name + "; for a different layout, use the explicit load overloads");
    }
    return file;
  }

  /**
   * Loads a WordPiece static embedding model from a BERT-style {@code vocab.txt} and a
   * safetensors weight file. No model is bundled with this module; the caller supplies the
   * files.
   *
   * @param vocabularyFile   The {@code vocab.txt} file: one token per line, line number is the
   *                         token's row id. Must not be {@code null}, must exist, and must
   *                         contain the {@code [CLS]}, {@code [SEP]}, and {@code [UNK]} tokens.
   * @param safetensorsFile  The {@code model.safetensors} file. Must not be {@code null} and
   *                         must exist, and must contain exactly one 2-D float tensor
   *                         (the embedding matrix) whose row count matches the vocabulary size.
   *                         An optional 1-D {@code F32} tensor named {@code "weights"}, one
   *                         scalar per vocabulary row, is used as a per-token pooling weight
   *                         when present.
   * @param casing           Whether the tokenizer lower-cases and strips accents
   *                         ({@link Casing#UNCASED}) or preserves case ({@link Casing#CASED}).
   * @param normalization    Whether {@link #embed(String)} L2-normalizes its result
   *                         ({@link Normalization#L2}) or not ({@link Normalization#NONE}).
   * @return The loaded model.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, a file is missing
   *     or malformed, or the vocabulary size and the embedding matrix's row count disagree.
   * @throws IOException Thrown if reading a file fails.
   */
  public static StaticEmbeddingModel load(Path vocabularyFile, Path safetensorsFile,
                                           Casing casing, Normalization normalization)
      throws IOException {
    if (vocabularyFile == null) {
      throw new IllegalArgumentException("VocabularyFile must not be null");
    }
    if (safetensorsFile == null) {
      throw new IllegalArgumentException("SafetensorsFile must not be null");
    }
    if (casing == null) {
      throw new IllegalArgumentException("Casing must not be null");
    }
    if (normalization == null) {
      throw new IllegalArgumentException("Normalization must not be null");
    }
    final EmbeddingVocabulary vocabulary = EmbeddingVocabulary.fromVocabTxt(vocabularyFile);
    final Matrix matrix = readMatrix(vocabulary, safetensorsFile, vocabularyFile.toString());
    final WordpieceEncoder tokenizer =
        new WordpieceEncoder(vocabulary.orderedTokens(), casing == Casing.UNCASED);
    // The encoder validated the frame tokens' presence, so these rows exist.
    final int classificationId = vocabulary.id(WordpieceTokenizer.BERT_CLS_TOKEN);
    final int separatorId = vocabulary.id(WordpieceTokenizer.BERT_SEP_TOKEN);
    final int unknownId = vocabulary.id(WordpieceTokenizer.BERT_UNK_TOKEN);
    final IntPredicate skipPieceId =
        id -> id == classificationId || id == separatorId || id == unknownId;
    return new StaticEmbeddingModel(matrix.embeddings(), matrix.weights(), matrix.dimension(),
        vocabulary, tokenizer, skipPieceId, normalization == Normalization.L2,
        rowNorms(matrix.embeddings(), matrix.dimension(), vocabulary.size()),
        specialRows(vocabulary, WORDPIECE_SPECIAL_TOKENS));
  }

  /**
   * Loads a SentencePiece static embedding model from a trained SentencePiece {@code .model}
   * file, the Unigram {@code tokenizer.json} naming the matrix rows, and a safetensors weight
   * file. No model is bundled with this module; the caller supplies the files.
   *
   * <p>The {@code .model} file carries the model's own text normalizer and segmentation state,
   * so there is no casing switch. The two vocabulary files may order or offset their ids
   * differently: matrix rows are resolved by piece string, and every piece the tokenizer can
   * emit (except its control and unknown pieces, which are never pooled) must be present in the
   * {@code tokenizer.json} vocabulary, verified once at load time.</p>
   *
   * @param sentencePieceModelFile The trained SentencePiece {@code .model} file. Must not be
   *                               {@code null} and must exist.
   * @param tokenizerJsonFile      The Unigram {@code tokenizer.json} file; its
   *                               {@code model.vocab} list order is the matrix row order, with
   *                               {@code added_tokens} overlaid. Must not be {@code null} and
   *                               must exist.
   * @param safetensorsFile        The {@code model.safetensors} file. Must not be {@code null}
   *                               and must exist, and must contain exactly one 2-D float tensor
   *                               (the embedding matrix) whose row count matches the vocabulary
   *                               size. An optional 1-D {@code F32} tensor named
   *                               {@code "weights"}, one scalar per vocabulary row, is used as
   *                               a per-token pooling weight when present.
   * @param normalization          Whether {@link #embed(String)} L2-normalizes its result
   *                               ({@link Normalization#L2}) or not ({@link Normalization#NONE}).
   * @return The loaded model.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, a file is missing
   *     or malformed, the vocabulary size and the embedding matrix's row count disagree, or the
   *     tokenizer emits pieces the vocabulary does not map.
   * @throws IOException Thrown if reading a file fails.
   */
  public static StaticEmbeddingModel loadSentencePiece(Path sentencePieceModelFile,
                                                        Path tokenizerJsonFile,
                                                        Path safetensorsFile,
                                                        Normalization normalization)
      throws IOException {
    if (sentencePieceModelFile == null) {
      throw new IllegalArgumentException("SentencePieceModelFile must not be null");
    }
    if (tokenizerJsonFile == null) {
      throw new IllegalArgumentException("TokenizerJsonFile must not be null");
    }
    if (safetensorsFile == null) {
      throw new IllegalArgumentException("SafetensorsFile must not be null");
    }
    if (normalization == null) {
      throw new IllegalArgumentException("Normalization must not be null");
    }
    final EmbeddingVocabulary vocabulary =
        EmbeddingVocabulary.fromTokenizerJson(tokenizerJsonFile);
    final SentencePieceTokenizer tokenizer =
        SentencePieceTokenizer.load(sentencePieceModelFile);
    requireVocabularyCoverage(tokenizer, vocabulary, sentencePieceModelFile, tokenizerJsonFile);
    final Matrix matrix = readMatrix(vocabulary, safetensorsFile, tokenizerJsonFile.toString());
    final IntPredicate skipPieceId =
        id -> tokenizer.isUnknown(id) || tokenizer.isControl(id);
    return new StaticEmbeddingModel(matrix.embeddings(), matrix.weights(), matrix.dimension(),
        vocabulary, tokenizer, skipPieceId, normalization == Normalization.L2,
        rowNorms(matrix.embeddings(), matrix.dimension(), vocabulary.size()),
        specialRows(vocabulary, SENTENCEPIECE_SPECIAL_TOKENS));
  }

  /**
   * Verifies once at load time that every piece the tokenizer can emit maps to a matrix row, so
   * embedding never meets an unmapped piece. Control and unknown pieces are exempt: they are
   * never pooled, and a distillation legitimately drops them from the matrix.
   *
   * @param tokenizer              The loaded SentencePiece tokenizer.
   * @param vocabulary             The matrix row vocabulary.
   * @param sentencePieceModelFile The tokenizer's source file, for error messages.
   * @param tokenizerJsonFile      The vocabulary's source file, for error messages.
   * @throws IllegalArgumentException Thrown if a poolable piece has no matrix row.
   */
  private static void requireVocabularyCoverage(SentencePieceTokenizer tokenizer,
                                                EmbeddingVocabulary vocabulary,
                                                Path sentencePieceModelFile,
                                                Path tokenizerJsonFile) {
    int missing = 0;
    final StringBuilder samples = new StringBuilder();
    for (int id = 0; id < tokenizer.vocabularySize(); id++) {
      if (tokenizer.isUnknown(id) || tokenizer.isControl(id)) {
        continue;
      }
      if (vocabulary.id(tokenizer.idToPiece(id)) < 0) {
        if (missing < 5) {
          if (missing > 0) {
            samples.append(", ");
          }
          samples.append('\'').append(tokenizer.idToPiece(id)).append('\'');
        }
        missing++;
      }
    }
    if (missing > 0) {
      throw new IllegalArgumentException(sentencePieceModelFile + " defines " + missing
          + " pieces that " + tokenizerJsonFile + " does not map to a matrix row (first: "
          + samples + "); these files do not belong to the same model");
    }
  }

  /** The embedding matrix and its optional per-token weights, as read from a safetensors file. */
  private record Matrix(float[] embeddings, float[] weights, int dimension) {
  }

  /**
   * Reads the embedding matrix and the optional {@code weights} tensor, holding both to the
   * vocabulary's size.
   *
   * @param vocabulary           The matrix row vocabulary.
   * @param safetensorsFile      The safetensors file to read.
   * @param vocabularySourceName The vocabulary's source, for error messages.
   * @return The matrix, its optional weights, and its dimension.
   * @throws IllegalArgumentException Thrown if the matrix's row count or the weights tensor's
   *     length disagrees with the vocabulary size.
   * @throws IOException Thrown if reading the file fails.
   */
  private static Matrix readMatrix(EmbeddingVocabulary vocabulary, Path safetensorsFile,
                                   String vocabularySourceName) throws IOException {
    final SafetensorsFile tensors = SafetensorsFile.read(safetensorsFile);
    final String matrixName = tensors.singleMatrixTensorName();
    final TensorInfo matrixInfo = tensors.tensorInfo(matrixName);
    if (matrixInfo.shape()[0] != vocabulary.size()) {
      throw new IllegalArgumentException("Vocabulary " + vocabularySourceName + " has "
          + vocabulary.size() + " tokens but embedding matrix '" + matrixName + "' in "
          + safetensorsFile + " has " + matrixInfo.shape()[0] + " rows; these files do not "
          + "belong to the same model");
    }
    final int dimension = matrixInfo.shape()[1];
    final float[] embeddings = tensors.readFloats(matrixName);

    float[] weights = null;
    if (tensors.tensorNames().contains(WEIGHTS_TENSOR_NAME)) {
      weights = tensors.readFloats(WEIGHTS_TENSOR_NAME);
      if (weights.length != vocabulary.size()) {
        throw new IllegalArgumentException("Tensor '" + WEIGHTS_TENSOR_NAME + "' in "
            + safetensorsFile + " has " + weights.length + " elements but the vocabulary has "
            + vocabulary.size() + " tokens");
      }
    }
    return new Matrix(embeddings, weights, dimension);
  }

  /**
   * {@return the L2 norm of every matrix row, precomputed for the neighbor scan}
   *
   * @param embeddings The flat row-major matrix.
   * @param dimension  The row width.
   * @param rowCount   The number of rows.
   */
  private static double[] rowNorms(float[] embeddings, int dimension, int rowCount) {
    final double[] rowNorms = new double[rowCount];
    for (int row = 0; row < rowCount; row++) {
      final int base = row * dimension;
      double sumOfSquares = 0;
      for (int d = 0; d < dimension; d++) {
        final float value = embeddings[base + d];
        sumOfSquares += (double) value * value;
      }
      rowNorms[row] = Math.sqrt(sumOfSquares);
    }
    return rowNorms;
  }

  /**
   * {@return the mask of rows holding special tokens, excluded from neighbor results}
   *
   * @param vocabulary    The matrix row vocabulary.
   * @param specialTokens The special-token strings of the model's convention; tokens absent
   *                      from the vocabulary are simply not marked.
   */
  private static boolean[] specialRows(EmbeddingVocabulary vocabulary,
                                       Set<String> specialTokens) {
    final boolean[] specialRows = new boolean[vocabulary.size()];
    for (final String special : specialTokens) {
      final int row = vocabulary.id(special);
      if (row >= 0) {
        specialRows[row] = true;
      }
    }
    return specialRows;
  }

  /**
   * {@inheritDoc}
   *
   * <p>A text with no in-vocabulary tokens yields a zero vector.</p>
   *
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  @Override
  public float[] embed(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    return embed(text instanceof String s ? s : text.toString());
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
    final List<SubwordPiece> pieces = tokenizer.encode(text);
    final float[] sum = new float[dimension];
    int pooledCount = 0;
    for (int i = 0; i < pieces.size(); i++) {
      final SubwordPiece piece = pieces.get(i);
      if (skipPieceId.test(piece.id())) {
        continue;
      }
      final int row = vocabulary.id(piece.piece());
      if (row < 0) {
        throw new IllegalStateException("Tokenizer produced piece '" + piece.piece()
            + "' that has no matrix row; load-time validation admits no such piece, so this "
            + "indicates a construction bug, not an input problem");
      }
      final int base = row * dimension;
      if (weights == null) {
        for (int d = 0; d < dimension; d++) {
          sum[d] += embeddings[base + d];
        }
      } else {
        final float weight = weights[row];
        for (int d = 0; d < dimension; d++) {
          sum[d] += embeddings[base + d] * weight;
        }
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

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return dimension;
  }

  /** {@return the number of tokens in this model's vocabulary} */
  public int vocabularySize() {
    return vocabulary.size();
  }

  /**
   * Cosine similarity between two pieces of text's pooled embeddings.
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
   * most similar first. This is a brute-force scan over the whole vocabulary.
   *
   * @param text The query text. Must not be {@code null}.
   * @param topK The maximum number of results. Must be at least 1.
   * @return Up to {@code topK} neighbors, most similar first, excluding the model's special
   *     tokens; empty when {@code text} has no in-vocabulary tokens.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null} or {@code topK} is
   *     less than 1.
   */
  public List<Neighbor> mostSimilar(String text, int topK) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    requirePositive(topK);
    return nearestNeighbors(embed(text), topK, NO_EXCLUDED_ROWS);
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
   * @return Up to {@code topK} neighbors, most similar first, excluding the model's special
   *     tokens and every vocabulary token the three terms themselves tokenize to. The exclusion
   *     folds the terms exactly the way {@link #embed(String)} folds text, so on an uncased
   *     model a capitalized input excludes its lower-cased vocabulary row, and a multiword term
   *     excludes each of its word pieces.
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
    return nearestNeighbors(target, topK, excludedRows(a, b, c));
  }

  /**
   * Requires {@code topK} to be at least 1.
   *
   * @param topK The requested result count.
   * @throws IllegalArgumentException Thrown if {@code topK} is less than 1.
   */
  private static void requirePositive(int topK) {
    if (topK < 1) {
      throw new IllegalArgumentException("TopK must be at least 1, got " + topK);
    }
  }

  /**
   * {@return the vocabulary rows the given terms tokenize to, ascending and duplicate-free}
   * Folding the terms through the model's own tokenizer keeps the exclusion case- and
   * accent-insensitive on models that normalize.
   *
   * @param terms The terms to fold and exclude.
   */
  private int[] excludedRows(String... terms) {
    final SortedSet<Integer> rows = new TreeSet<>();
    for (final String term : terms) {
      for (final SubwordPiece piece : tokenizer.encode(term)) {
        if (skipPieceId.test(piece.id())) {
          continue;
        }
        final int row = vocabulary.id(piece.piece());
        if (row >= 0) {
          rows.add(row);
        }
      }
    }
    final int[] sorted = new int[rows.size()];
    int i = 0;
    for (final int row : rows) {
      sorted[i++] = row;
    }
    return sorted;
  }

  /**
   * Scans the whole vocabulary for the rows nearest {@code query}, most similar first.
   *
   * @param query The query vector.
   * @param topK The maximum number of neighbors to return.
   * @param sortedExcludedRows Row ids to skip, in ascending order; the scan advances a single
   *     pointer through them as it visits rows in order.
   * @return Up to {@code topK} neighbors, most similar first; empty when {@code query} has no
   *     direction.
   */
  private List<Neighbor> nearestNeighbors(float[] query, int topK, int[] sortedExcludedRows) {
    final double queryNorm = norm(query);
    if (queryNorm < NORMALIZE_EPSILON) {
      return List.of();
    }
    final TopK best = new TopK(topK);
    int nextExcluded = 0;
    final int rowCount = rowNorms.length;
    for (int row = 0; row < rowCount; row++) {
      if (nextExcluded < sortedExcludedRows.length && sortedExcludedRows[nextExcluded] == row) {
        nextExcluded++;
        continue;
      }
      if (specialRows[row]) {
        continue;
      }
      final double rowNorm = rowNorms[row];
      if (rowNorm < NORMALIZE_EPSILON) {
        // A zero row has no direction; scored 0 rather than NaN from a 0/0 division.
        best.offer(row, 0.0);
        continue;
      }
      final int base = row * dimension;
      // Four accumulators so the JIT can vectorize the dot product without reordering FP adds.
      double dot0 = 0;
      double dot1 = 0;
      double dot2 = 0;
      double dot3 = 0;
      int d = 0;
      for (final int limit = dimension - 3; d < limit; d += 4) {
        dot0 += query[d] * embeddings[base + d];
        dot1 += query[d + 1] * embeddings[base + d + 1];
        dot2 += query[d + 2] * embeddings[base + d + 2];
        dot3 += query[d + 3] * embeddings[base + d + 3];
      }
      double dot = dot0 + dot1 + dot2 + dot3;
      for (; d < dimension; d++) {
        dot += query[d] * embeddings[base + d];
      }
      best.offer(row, dot / (queryNorm * rowNorm));
    }
    final Neighbor[] ordered = new Neighbor[best.size()];
    for (int i = ordered.length - 1; i >= 0; i--) {
      ordered[i] = new Neighbor(vocabulary.token(best.minRow()), best.minSimilarity());
      best.removeMin();
    }
    return List.of(ordered);
  }

  /**
   * {@return the cosine similarity of two vectors, or {@code 0} when either has no direction}
   *
   * @param a The first vector.
   * @param b The second vector, of the same length as {@code a}.
   */
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

  /**
   * {@return the L2 norm of a vector}
   *
   * @param vector The vector to measure.
   */
  private static double norm(float[] vector) {
    double sumOfSquares = 0;
    for (final float value : vector) {
      sumOfSquares += (double) value * value;
    }
    return Math.sqrt(sumOfSquares);
  }

  /**
   * A bounded selection of the {@code k} highest-similarity rows, kept as a min-heap over
   * primitive parallel arrays: the root is always the weakest kept candidate, so a full scan
   * decides most rows with one comparison against it and allocates nothing per row.
   */
  private static final class TopK {

    private final double[] similarities;
    private final int[] rows;
    private int size;

    /**
     * @param capacity The maximum number of rows to keep.
     */
    TopK(int capacity) {
      this.similarities = new double[capacity];
      this.rows = new int[capacity];
    }

    /**
     * Offers a candidate row, keeping it only if it ranks among the top {@code capacity}.
     *
     * @param row The candidate row id.
     * @param similarity The row's similarity to the query.
     */
    void offer(int row, double similarity) {
      if (size < similarities.length) {
        int i = size++;
        similarities[i] = similarity;
        rows[i] = row;
        while (i > 0) {
          final int parent = (i - 1) >>> 1;
          if (similarities[parent] <= similarities[i]) {
            break;
          }
          swap(parent, i);
          i = parent;
        }
      } else if (similarity > similarities[0]) {
        similarities[0] = similarity;
        rows[0] = row;
        siftDown();
      }
    }

    /** {@return the number of rows currently held} */
    int size() {
      return size;
    }

    /** {@return the row id of the weakest held candidate, the heap root} */
    int minRow() {
      return rows[0];
    }

    /** {@return the similarity of the weakest held candidate, the heap root} */
    double minSimilarity() {
      return similarities[0];
    }

    /** Removes the weakest held candidate, the heap root. */
    void removeMin() {
      size--;
      similarities[0] = similarities[size];
      rows[0] = rows[size];
      siftDown();
    }

    /** Restores the min-heap invariant from the root downward. */
    private void siftDown() {
      int i = 0;
      while (true) {
        final int left = 2 * i + 1;
        final int right = left + 1;
        int smallest = i;
        if (left < size && similarities[left] < similarities[smallest]) {
          smallest = left;
        }
        if (right < size && similarities[right] < similarities[smallest]) {
          smallest = right;
        }
        if (smallest == i) {
          return;
        }
        swap(i, smallest);
        i = smallest;
      }
    }

    /**
     * Swaps two heap entries in both parallel arrays.
     *
     * @param i The first index.
     * @param j The second index.
     */
    private void swap(int i, int j) {
      final double similarity = similarities[i];
      similarities[i] = similarities[j];
      similarities[j] = similarity;
      final int row = rows[i];
      rows[i] = rows[j];
      rows[j] = row;
    }
  }
}
