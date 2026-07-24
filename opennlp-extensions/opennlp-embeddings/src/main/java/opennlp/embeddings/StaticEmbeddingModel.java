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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

import opennlp.subword.sentencepiece.SentencePieceTokenizer;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.tokenize.SubwordTokenizer;
import opennlp.tools.tokenize.WordpieceEncoder;
import opennlp.tools.tokenize.WordpieceTokenizer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.java.Experimental;

/**
 * A static (non-contextual) sentence embedding model: a per-token vector table plus subword
 * tokenization. Embedding a sentence is tokenize, gather each piece's row, optionally weight,
 * mean-pool, and optionally L2-normalize; there is no model forward pass.
 *
 * <p>It loads distilled tables in the
 * <a href="https://github.com/MinishLab/model2vec">Model2Vec</a> release layout for both
 * tokenizer families:
 * WordPiece models carry a {@code vocab.txt} whose line number is the matrix row, and
 * SentencePiece models carry a Unigram {@code tokenizer.json} whose {@code model.vocab} list
 * order is the row order, next to the trained SentencePiece {@code .model} file that performs
 * the segmentation. In both cases the {@code model.safetensors} holds one 2-D float matrix, with
 * an optional per-token {@code weights} tensor. Matrix rows are resolved by piece <i>string</i>,
 * never by tokenizer id, so the two files may order or offset their ids differently without
 * corrupting lookups; a piece the matrix does not carry fails loud at load time.</p>
 *
 * <p>Special pieces (the WordPiece {@code [CLS]}, {@code [SEP]}, and {@code [UNK]} tokens, a
 * SentencePiece model's control and unknown pieces) are never pooled; the sum is divided by the
 * count of pooled pieces, not the sum of weights. A text with no in-vocabulary pieces yields a
 * zero vector.</p>
 *
 * <p>Either layout may carry its matrix quantized in a {@code model.quantized} file (written by
 * the {@code QuantizeModel} tool), which holds the matrix and any per-token pooling weights
 * itself. A directory presents exactly one matrix file: the quantized file or the safetensors,
 * not both. A directory carrying both is rejected, because the quantizer writes the quantized
 * file next to the safetensors it read and so a directory holding both has not declared which
 * is authoritative; delete one to choose. Embedding and similarity over a quantized matrix
 * behave identically up to the quantization error of the chosen bit width; see
 * {@link QuantizedEmbeddingMatrix} for the storage and its cost.</p>
 *
 * <p>A model directory may additionally carry a {@code terms.txt}: whole words and multi-word
 * phrases distilled through the teacher as units, owning the matrix rows after the subword rows
 * (see {@link ModelDistiller}). Embedding then matches the text against these terms greedily
 * longest-first, pools a matched term's single row in place of its words' subword pieces, and
 * tokenizes only the text between matches; a model without the file embeds exactly as before.
 * Term matching is case-insensitive regardless of the subword tokenizer's casing.</p>
 *
 * <p>Instances are immutable and safe for concurrent use after construction.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
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
  // Shared with ModelQuantizer, which carries this tensor into the quantized file.
  static final String WEIGHTS_TENSOR_NAME = "weights";
  // The only pooling this model implements; the value the distiller writes into config.json.
  private static final String MEAN_POOLING = "mean";
  private static final int[] NO_EXCLUDED_ROWS = new int[0];
  // Excluded from neighbor results, including [PAD] and [MASK] that a distilled table keeps.
  private static final Set<String> WORDPIECE_SPECIAL_TOKENS =
      Set.of(WordpieceTokenizer.BERT_CLS_TOKEN, WordpieceTokenizer.BERT_SEP_TOKEN,
          WordpieceTokenizer.BERT_UNK_TOKEN, "[PAD]", "[MASK]");
  private static final Set<String> SENTENCEPIECE_SPECIAL_TOKENS =
      Set.of("<s>", "</s>", "<pad>", "<unk>", "<mask>");

  private final EmbeddingTable table;
  private final float[] weights;
  private final int dimension;
  private final EmbeddingVocabulary vocabulary;
  private final SubwordTokenizer tokenizer;
  // Tokenizer-id test for pieces that are never pooled (delimiter, control, unknown pieces).
  private final IntPredicate skipPieceId;
  private final boolean normalize;
  // Special-token mask, precomputed at load time for the neighbor scan.
  private final boolean[] specialRows;
  // The term rows after the subword rows; empty for a model without a term table.
  private final TermTable terms;

  /** Holds the loaded, validated state; callers reach this through the {@code load} factories. */
  private StaticEmbeddingModel(EmbeddingTable table, float[] weights,
                                EmbeddingVocabulary vocabulary, SubwordTokenizer tokenizer,
                                IntPredicate skipPieceId, boolean normalize,
                                boolean[] specialRows, TermTable terms) {
    this.table = table;
    this.weights = weights;
    this.dimension = table.dimension();
    this.vocabulary = vocabulary;
    this.tokenizer = tokenizer;
    this.skipPieceId = skipPieceId;
    this.normalize = normalize;
    this.specialRows = specialRows;
    this.terms = terms;
  }

  /** An embedding table and the optional per-token pooling weights that came with it. */
  private record TableAndWeights(EmbeddingTable table, float[] weights) {
  }

  /**
   * Reads a quantized table, holding its row count to the vocabulary's size plus the term
   * count.
   *
   * @param quantizedFile        The quantized matrix file.
   * @param vocabulary           The matrix row vocabulary.
   * @param termCount            The number of term rows after the vocabulary rows.
   * @param vocabularySourceName The vocabulary's source, for error messages.
   * @return The table and the pooling weights the file carries, if any.
   * @throws InvalidFormatException Thrown if the row count disagrees with the vocabulary.
   * @throws IOException Thrown if reading the file fails.
   */
  private static TableAndWeights readQuantizedTable(Path quantizedFile,
                                                    EmbeddingVocabulary vocabulary,
                                                    int termCount,
                                                    String vocabularySourceName)
      throws IOException {
    final QuantizedEmbeddingMatrix matrix = QuantizedEmbeddingMatrix.read(quantizedFile);
    final int expectedRows = vocabulary.size() + termCount;
    if (matrix.rowCount() != expectedRows) {
      throw new InvalidFormatException("Vocabulary " + vocabularySourceName + " has "
          + vocabulary.size() + " tokens"
          + (termCount > 0 ? " plus " + termCount + " terms" : "")
          + " but quantized matrix " + quantizedFile + " has "
          + matrix.rowCount() + " rows; these files do not belong to the same model");
    }
    return new TableAndWeights(new QuantizedTableAdapter(matrix), matrix.poolingWeights());
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
   * <p>In either layout, the matrix comes from a {@code model.quantized} file when the directory
   * has one and no {@code model.safetensors}; a directory holding both is rejected (see the
   * class comment). After quantizing, delete the safetensors to deploy the quantized matrix, or
   * delete the quantized file to fall back to the float matrix.</p>
   *
   * @param modelDirectory The model directory. Must not be {@code null} and must be a
   *                       directory.
   * @return The loaded model.
   * @throws IllegalArgumentException Thrown if {@code modelDirectory} is {@code null} or not a
   *     directory.
   * @throws InvalidFormatException Thrown if neither layout's files are present, a required
   *     file is missing, a configuration file is malformed or lacks its field, the accent
   *     handling is not representable, or the tokenizer and the embedding matrix disagree.
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
    final Path termsFile = modelDirectory.resolve(ModelFileNames.TERMS);
    final List<String> termLines = Files.isRegularFile(termsFile)
        ? Files.readAllLines(termsFile) : List.of();
    final Path vocabularyFile = modelDirectory.resolve(ModelFileNames.VOCABULARY);
    if (Files.isRegularFile(vocabularyFile)) {
      return loadWordpieceDirectory(modelDirectory, vocabularyFile, termLines,
          termsFile.toString());
    }
    final Path sentencePieceModelFile = ModelFileNames.firstRegularFile(modelDirectory,
        ModelFileNames.SENTENCEPIECE_MODELS);
    final Path tokenizerJsonFile = modelDirectory.resolve(ModelFileNames.TOKENIZER_JSON);
    if (sentencePieceModelFile != null && Files.isRegularFile(tokenizerJsonFile)) {
      final Normalization normalization =
          requiredNormalize(requiredFile(modelDirectory, ModelFileNames.CONFIG));
      final Path quantizedFile = quantizedMatrixFileOrNull(modelDirectory);
      if (quantizedFile != null) {
        return loadSentencePieceQuantized(sentencePieceModelFile, tokenizerJsonFile,
            quantizedFile, normalization, termLines, termsFile.toString());
      }
      return loadSentencePiece(sentencePieceModelFile, tokenizerJsonFile,
          requiredFile(modelDirectory, ModelFileNames.SAFETENSORS), normalization,
          termLines, termsFile.toString());
    }
    if (Files.isRegularFile(tokenizerJsonFile)) {
      throw new InvalidFormatException("Model directory " + modelDirectory + " has a "
          + ModelFileNames.TOKENIZER_JSON + " but no trained SentencePiece file ("
          + String.join(", ", ModelFileNames.SENTENCEPIECE_MODELS) + "); copy the .model file "
          + "from the model's base tokenizer next to it");
    }
    throw new InvalidFormatException("Model directory " + modelDirectory + " has neither a "
        + ModelFileNames.VOCABULARY + " (WordPiece layout) nor a "
        + ModelFileNames.TOKENIZER_JSON
        + " with a trained SentencePiece file (SentencePiece layout)");
  }

  /**
   * Loads the WordPiece directory layout, reading the tokenizer and pooling switches from the
   * model's own configuration files.
   *
   * @param modelDirectory  The model directory.
   * @param vocabularyFile  The directory's {@code vocab.txt}.
   * @param termLines       The directory's terms in row order; empty without a terms file.
   * @param termsSourceName The terms' source, for error messages.
   * @return The loaded model.
   * @throws IOException Thrown if reading a file fails.
   */
  private static StaticEmbeddingModel loadWordpieceDirectory(Path modelDirectory,
                                                             Path vocabularyFile,
                                                             List<String> termLines,
                                                             String termsSourceName)
      throws IOException {
    final Path tokenizerConfigFile =
        requiredFile(modelDirectory, ModelFileNames.TOKENIZER_CONFIG);
    final Normalization normalization =
        requiredNormalize(requiredFile(modelDirectory, ModelFileNames.CONFIG));
    final Boolean lowerCase =
        FlatJsonFields.topLevelBoolean(tokenizerConfigFile, "do_lower_case");
    if (lowerCase == null) {
      throw new InvalidFormatException(tokenizerConfigFile + " has no boolean "
          + "'do_lower_case' field; use load(vocabularyFile, safetensorsFile, casing, "
          + "normalization) and choose explicitly");
    }
    final Boolean stripAccents =
        FlatJsonFields.topLevelBoolean(tokenizerConfigFile, "strip_accents");
    if (stripAccents != null && !stripAccents.equals(lowerCase)) {
      throw new InvalidFormatException(tokenizerConfigFile + " sets strip_accents="
          + stripAccents + " against do_lower_case=" + lowerCase + "; the single lower-case "
          + "switch strips accents exactly when lower-casing, so this model must be loaded "
          + "with load(vocabularyFile, safetensorsFile, casing, normalization) after choosing "
          + "deliberately");
    }
    final Casing casing = lowerCase ? Casing.UNCASED : Casing.CASED;
    final Path quantizedFile = quantizedMatrixFileOrNull(modelDirectory);
    if (quantizedFile != null) {
      final EmbeddingVocabulary vocabulary = EmbeddingVocabulary.fromVocabTxt(vocabularyFile);
      final TermTable terms = TermTable.of(termLines, vocabulary.size(), termsSourceName);
      return createWordpiece(vocabulary,
          readQuantizedTable(quantizedFile, vocabulary, terms.size(),
              vocabularyFile.toString()),
          casing, normalization, vocabularyFile.toString(), terms);
    }
    return loadWordpiece(vocabularyFile,
        requiredFile(modelDirectory, ModelFileNames.SAFETENSORS),
        casing, normalization, termLines, termsSourceName);
  }

  /**
   * Resolves which matrix file a model directory presents, failing loud when the choice is
   * ambiguous. The quantizer writes {@code model.quantized} next to the {@code model.safetensors}
   * it read, so a directory holding both has not declared which is authoritative; deleting one
   * makes the deployment's choice explicit rather than letting the loader guess.
   *
   * @param modelDirectory The model directory.
   * @return the {@code model.quantized} file when it is the directory's only matrix file, or
   *     {@code null} when the directory presents only a {@code model.safetensors}.
   * @throws InvalidFormatException Thrown if the directory holds both matrix files.
   */
  private static Path quantizedMatrixFileOrNull(Path modelDirectory) throws InvalidFormatException {
    final Path quantizedFile = modelDirectory.resolve(ModelFileNames.QUANTIZED);
    if (!Files.isRegularFile(quantizedFile)) {
      return null;
    }
    if (Files.isRegularFile(modelDirectory.resolve(ModelFileNames.SAFETENSORS))) {
      throw new InvalidFormatException("Model directory " + modelDirectory + " has both "
          + ModelFileNames.QUANTIZED + " and " + ModelFileNames.SAFETENSORS + "; delete one so "
          + "the matrix source is unambiguous (keep " + ModelFileNames.QUANTIZED + " for a "
          + "quantized deployment, or " + ModelFileNames.SAFETENSORS + " for the float matrix)");
    }
    return quantizedFile;
  }

  /**
   * Reads the required {@code normalize} switch out of a model's {@code config.json}, rejecting
   * a configuration whose {@code pooling} field declares anything but the mean pooling this
   * model implements. Silently mean-pooling a table distilled for another pooling would produce
   * plausible but wrong vectors, so such a model fails loud here.
   *
   * @param configFile The {@code config.json} file.
   * @return The corresponding {@link Normalization}.
   * @throws InvalidFormatException Thrown if the {@code normalize} field is missing or not a
   *     boolean, or the {@code pooling} field declares a pooling other than {@code "mean"}.
   * @throws IOException Thrown if reading the file fails.
   */
  private static Normalization requiredNormalize(Path configFile) throws IOException {
    final String pooling = FlatJsonFields.topLevelString(configFile, "pooling");
    if (pooling != null && !MEAN_POOLING.equals(pooling)) {
      throw new InvalidFormatException(configFile + " declares pooling '" + pooling
          + "' but only '" + MEAN_POOLING + "' pooling is implemented; embedding this model "
          + "would silently pool differently than its distiller intended");
    }
    final Boolean normalize = FlatJsonFields.topLevelBoolean(configFile, "normalize");
    if (normalize == null) {
      throw new InvalidFormatException(configFile + " has no boolean 'normalize' field; "
          + "use the explicit load overloads and choose the normalization deliberately");
    }
    return normalize ? Normalization.L2 : Normalization.NONE;
  }

  /**
   * {@return the named file in the directory, requiring it to exist as a regular file}
   *
   * @param modelDirectory The model directory.
   * @param name           The required file name.
   * @throws InvalidFormatException Thrown if the file is absent.
   */
  private static Path requiredFile(Path modelDirectory, String name)
      throws InvalidFormatException {
    final Path file = modelDirectory.resolve(name);
    if (!Files.isRegularFile(file)) {
      throw new InvalidFormatException("Model directory " + modelDirectory + " has no "
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
   *                         contain the {@code [UNK]} token. The {@code [CLS]} and {@code [SEP]}
   *                         delimiter tokens are optional: a distilled table that dropped them
   *                         (as Model2Vec does) still loads, because they are never pooled.
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
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or a file is
   *     missing.
   * @throws InvalidFormatException Thrown if a file is malformed, the vocabulary lacks the
   *     {@code [UNK]} token, or the vocabulary size and the embedding matrix's row count
   *     disagree.
   * @throws IOException Thrown if reading a file fails.
   */
  public static StaticEmbeddingModel load(Path vocabularyFile, Path safetensorsFile,
                                           Casing casing, Normalization normalization)
      throws IOException {
    return loadWordpiece(vocabularyFile, safetensorsFile, casing, normalization, List.of(),
        ModelFileNames.TERMS);
  }

  /**
   * Loads the WordPiece layout with an optional term table.
   *
   * @param vocabularyFile  The {@code vocab.txt} file.
   * @param safetensorsFile The {@code model.safetensors} file.
   * @param casing          The tokenizer's casing.
   * @param normalization   The pooling normalization.
   * @param termLines       The terms in row order; empty without a term table.
   * @param termsSourceName The terms' source, for error messages.
   * @return The loaded model.
   * @throws IOException Thrown if reading a file fails.
   */
  private static StaticEmbeddingModel loadWordpiece(Path vocabularyFile, Path safetensorsFile,
                                                    Casing casing, Normalization normalization,
                                                    List<String> termLines,
                                                    String termsSourceName)
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
    final TermTable terms = TermTable.of(termLines, vocabulary.size(), termsSourceName);
    final Matrix matrix = readMatrix(vocabulary, terms.size(), safetensorsFile,
        vocabularyFile.toString());
    final TableAndWeights tableAndWeights = new TableAndWeights(
        new FloatEmbeddingTable(matrix.embeddings(), matrix.dimension(),
            vocabulary.size() + terms.size()),
        matrix.weights());
    return createWordpiece(vocabulary, tableAndWeights, casing, normalization,
        vocabularyFile.toString(), terms);
  }

  /**
   * Builds a WordPiece model over a loaded table, whatever its storage form.
   *
   * @param vocabulary           The matrix row vocabulary.
   * @param tableAndWeights      The table and its optional pooling weights.
   * @param casing               The tokenizer casing.
   * @param normalization        The pooling normalization.
   * @param vocabularySourceName The vocabulary's source, for error messages.
   * @param terms                The term rows after the subword rows; empty for none.
   * @return The loaded model.
   * @throws InvalidFormatException Thrown if the vocabulary has no unknown token.
   */
  private static StaticEmbeddingModel createWordpiece(EmbeddingVocabulary vocabulary,
                                                      TableAndWeights tableAndWeights,
                                                      Casing casing,
                                                      Normalization normalization,
                                                      String vocabularySourceName,
                                                      TermTable terms)
      throws InvalidFormatException {
    final int unknownId = vocabulary.id(WordpieceTokenizer.BERT_UNK_TOKEN);
    if (unknownId < 0) {
      throw new InvalidFormatException("Vocabulary " + vocabularySourceName + " has no "
          + WordpieceTokenizer.BERT_UNK_TOKEN + " token; a WordPiece embedding model needs an "
          + "unknown token as the fallback for out-of-vocabulary text");
    }
    final WordpieceEncoder tokenizer =
        wordpieceEncoder(vocabulary, casing == Casing.UNCASED, unknownId);
    // Pooling skips [CLS] and [SEP] by id; when absent they map to the unknown id, which is
    // skipped the same way. A negative id is the absent sentinel and matches no emitted piece.
    final int classificationId = vocabulary.id(WordpieceTokenizer.BERT_CLS_TOKEN);
    final int separatorId = vocabulary.id(WordpieceTokenizer.BERT_SEP_TOKEN);
    final IntPredicate skipPieceId =
        id -> id == unknownId || id == classificationId || id == separatorId;
    return new StaticEmbeddingModel(tableAndWeights.table(), tableAndWeights.weights(),
        vocabulary, tokenizer, skipPieceId, normalization == Normalization.L2,
        specialRows(vocabulary, WORDPIECE_SPECIAL_TOKENS, tableAndWeights.table().rowCount()),
        terms);
  }

  /**
   * Builds the WordPiece encoder, mapping {@code [CLS]} and {@code [SEP]} onto the unknown row
   * when the distilled vocabulary dropped them. A static embedding table mean-pools its content
   * pieces and never pools the delimiters, so distillers routinely remove
   * {@code [CLS]}/{@code [SEP]} from the table; the encoder still wraps every encoding in them
   * and needs an id for each, and pooling skips them regardless of their ids, so pointing the
   * absent delimiter tokens at the unknown row makes the model loadable without changing which
   * pieces are pooled.
   *
   * @param vocabulary The matrix row vocabulary; must contain the unknown token.
   * @param lowerCase  Whether the tokenizer lower-cases and strips accents.
   * @param unknownId  The unknown token's row, reused as the id of {@code [CLS]} or
   *                   {@code [SEP]} when that token is absent.
   * @return The encoder.
   */
  private static WordpieceEncoder wordpieceEncoder(EmbeddingVocabulary vocabulary,
                                                   boolean lowerCase, int unknownId) {
    if (vocabulary.id(WordpieceTokenizer.BERT_CLS_TOKEN) >= 0
        && vocabulary.id(WordpieceTokenizer.BERT_SEP_TOKEN) >= 0) {
      return new WordpieceEncoder(vocabulary.orderedTokens(), lowerCase);
    }
    final List<String> tokens = vocabulary.orderedTokens();
    final Map<String, Integer> ids = new HashMap<>(tokens.size() * 2);
    for (int id = 0; id < tokens.size(); id++) {
      ids.put(tokens.get(id), id);
    }
    ids.putIfAbsent(WordpieceTokenizer.BERT_CLS_TOKEN, unknownId);
    ids.putIfAbsent(WordpieceTokenizer.BERT_SEP_TOKEN, unknownId);
    return new WordpieceEncoder(ids, lowerCase, WordpieceTokenizer.BERT_CLS_TOKEN,
        WordpieceTokenizer.BERT_SEP_TOKEN, WordpieceTokenizer.BERT_UNK_TOKEN);
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
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or a file is
   *     missing.
   * @throws InvalidFormatException Thrown if a file is malformed, the vocabulary size and the
   *     embedding matrix's row count disagree, or the tokenizer emits pieces the vocabulary
   *     does not map.
   * @throws IOException Thrown if reading a file fails.
   */
  public static StaticEmbeddingModel loadSentencePiece(Path sentencePieceModelFile,
                                                        Path tokenizerJsonFile,
                                                        Path safetensorsFile,
                                                        Normalization normalization)
      throws IOException {
    return loadSentencePiece(sentencePieceModelFile, tokenizerJsonFile, safetensorsFile,
        normalization, List.of(), ModelFileNames.TERMS);
  }

  /**
   * Loads the SentencePiece layout with an optional term table.
   *
   * @param sentencePieceModelFile The trained SentencePiece {@code .model} file.
   * @param tokenizerJsonFile      The Unigram {@code tokenizer.json} file.
   * @param safetensorsFile        The {@code model.safetensors} file.
   * @param normalization          The pooling normalization.
   * @param termLines              The terms in row order; empty without a term table.
   * @param termsSourceName        The terms' source, for error messages.
   * @return The loaded model.
   * @throws IOException Thrown if reading a file fails.
   */
  private static StaticEmbeddingModel loadSentencePiece(Path sentencePieceModelFile,
                                                        Path tokenizerJsonFile,
                                                        Path safetensorsFile,
                                                        Normalization normalization,
                                                        List<String> termLines,
                                                        String termsSourceName)
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
    final TermTable terms = TermTable.of(termLines, vocabulary.size(), termsSourceName);
    final SentencePieceTokenizer tokenizer =
        SentencePieceTokenizer.load(sentencePieceModelFile);
    requireVocabularyCoverage(tokenizer, vocabulary, sentencePieceModelFile, tokenizerJsonFile);
    final Matrix matrix = readMatrix(vocabulary, terms.size(), safetensorsFile,
        tokenizerJsonFile.toString());
    final TableAndWeights tableAndWeights = new TableAndWeights(
        new FloatEmbeddingTable(matrix.embeddings(), matrix.dimension(),
            vocabulary.size() + terms.size()),
        matrix.weights());
    return createSentencePiece(tokenizer, vocabulary, tableAndWeights, normalization, terms);
  }

  /**
   * Loads the SentencePiece layout over a quantized matrix file.
   *
   * @param sentencePieceModelFile The trained SentencePiece {@code .model} file.
   * @param tokenizerJsonFile      The Unigram {@code tokenizer.json} naming the matrix rows.
   * @param quantizedFile          The quantized matrix file.
   * @param normalization          The pooling normalization.
   * @param termLines              The terms in row order; empty without a term table.
   * @param termsSourceName        The terms' source, for error messages.
   * @return The loaded model.
   * @throws IOException Thrown if reading a file fails.
   */
  private static StaticEmbeddingModel loadSentencePieceQuantized(Path sentencePieceModelFile,
                                                                 Path tokenizerJsonFile,
                                                                 Path quantizedFile,
                                                                 Normalization normalization,
                                                                 List<String> termLines,
                                                                 String termsSourceName)
      throws IOException {
    final EmbeddingVocabulary vocabulary =
        EmbeddingVocabulary.fromTokenizerJson(tokenizerJsonFile);
    final TermTable terms = TermTable.of(termLines, vocabulary.size(), termsSourceName);
    final SentencePieceTokenizer tokenizer =
        SentencePieceTokenizer.load(sentencePieceModelFile);
    requireVocabularyCoverage(tokenizer, vocabulary, sentencePieceModelFile, tokenizerJsonFile);
    return createSentencePiece(tokenizer, vocabulary,
        readQuantizedTable(quantizedFile, vocabulary, terms.size(),
            tokenizerJsonFile.toString()),
        normalization, terms);
  }

  /**
   * Builds a SentencePiece model over a loaded table, whatever its storage form.
   *
   * @param tokenizer       The loaded SentencePiece tokenizer.
   * @param vocabulary      The matrix row vocabulary.
   * @param tableAndWeights The table and its optional pooling weights.
   * @param normalization   The pooling normalization.
   * @param terms           The term rows after the subword rows; empty for none.
   * @return The loaded model.
   */
  private static StaticEmbeddingModel createSentencePiece(SentencePieceTokenizer tokenizer,
                                                          EmbeddingVocabulary vocabulary,
                                                          TableAndWeights tableAndWeights,
                                                          Normalization normalization,
                                                          TermTable terms) {
    final IntPredicate skipPieceId =
        id -> tokenizer.isUnknown(id) || tokenizer.isControl(id);
    return new StaticEmbeddingModel(tableAndWeights.table(), tableAndWeights.weights(),
        vocabulary, tokenizer, skipPieceId, normalization == Normalization.L2,
        specialRows(vocabulary, SENTENCEPIECE_SPECIAL_TOKENS,
            tableAndWeights.table().rowCount()),
        terms);
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
   * @throws InvalidFormatException Thrown if a poolable piece has no matrix row.
   */
  private static void requireVocabularyCoverage(SentencePieceTokenizer tokenizer,
                                                EmbeddingVocabulary vocabulary,
                                                Path sentencePieceModelFile,
                                                Path tokenizerJsonFile)
      throws InvalidFormatException {
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
      throw new InvalidFormatException(sentencePieceModelFile + " defines " + missing
          + " pieces that " + tokenizerJsonFile + " does not map to a matrix row (first: "
          + samples + "); these files do not belong to the same model");
    }
  }

  /** The embedding matrix and its optional per-token weights, as read from a safetensors file. */
  private record Matrix(float[] embeddings, float[] weights, int dimension) {
  }

  /**
   * Reads the embedding matrix and the optional {@code weights} tensor, holding both to the
   * model's row count: the vocabulary's size plus the term count.
   *
   * @param vocabulary           The matrix row vocabulary.
   * @param termCount            The number of term rows after the vocabulary rows.
   * @param safetensorsFile      The safetensors file to read.
   * @param vocabularySourceName The vocabulary's source, for error messages.
   * @return The matrix, its optional weights, and its dimension.
   * @throws InvalidFormatException Thrown if the matrix's row count or the weights tensor's
   *     length disagrees with the model's row count, or the matrix contains a non-finite value.
   * @throws IOException Thrown if reading the file fails.
   */
  private static Matrix readMatrix(EmbeddingVocabulary vocabulary, int termCount,
                                   Path safetensorsFile, String vocabularySourceName)
      throws IOException {
    final int expectedRows = vocabulary.size() + termCount;
    final SafetensorsFile tensors = SafetensorsFile.read(safetensorsFile);
    final String matrixName = tensors.singleMatrixTensorName();
    final TensorInfo matrixInfo = tensors.tensorInfo(matrixName);
    if (matrixInfo.shape()[0] != expectedRows) {
      throw new InvalidFormatException("Vocabulary " + vocabularySourceName + " has "
          + vocabulary.size() + " tokens"
          + (termCount > 0 ? " plus " + termCount + " terms" : "")
          + " but embedding matrix '" + matrixName + "' in "
          + safetensorsFile + " has " + matrixInfo.shape()[0] + " rows; these files do not "
          + "belong to the same model");
    }
    final int dimension = matrixInfo.shape()[1];
    final float[] embeddings = tensors.readFloats(matrixName);
    // Distillation replaces non-finite teacher values with zero before writing, so a NaN or
    // infinity here marks a corrupt or foreign file; unrejected, a single NaN row silently
    // defeats the norm guards and corrupts every similarity ranking it appears in.
    for (int i = 0; i < embeddings.length; i++) {
      if (!Float.isFinite(embeddings[i])) {
        throw new InvalidFormatException("Embedding matrix '" + matrixName + "' in "
            + safetensorsFile + " holds the non-finite value " + embeddings[i] + " in row "
            + (i / dimension) + "; the matrix is corrupt");
      }
    }

    float[] weights = null;
    if (tensors.tensorNames().contains(WEIGHTS_TENSOR_NAME)) {
      weights = tensors.readFloats(WEIGHTS_TENSOR_NAME);
      if (weights.length != expectedRows) {
        throw new InvalidFormatException("Tensor '" + WEIGHTS_TENSOR_NAME + "' in "
            + safetensorsFile + " has " + weights.length + " elements but the model has "
            + expectedRows + " rows");
      }
    }
    return new Matrix(embeddings, weights, dimension);
  }

  /**
   * {@return the mask of rows holding special tokens, excluded from neighbor results; term rows
   * are never special}
   *
   * @param vocabulary    The matrix row vocabulary.
   * @param specialTokens The special-token strings of the model's convention; tokens absent
   *                      from the vocabulary are simply not marked.
   * @param totalRows     The model's row count, the vocabulary's size plus the term count.
   */
  private static boolean[] specialRows(EmbeddingVocabulary vocabulary,
                                       Set<String> specialTokens, int totalRows) {
    final boolean[] specialRows = new boolean[totalRows];
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
    // Pooling accumulates in the table's working space (original space for the float table,
    // rotated space for the quantized one) and maps to original space once per text.
    final float[] sum = new float[table.pooledLength()];
    // The count travels through the IntConsumer as a one-element array.
    final int[] pooledCount = new int[1];
    forEachPooledRow(text, row -> {
      table.addRow(row, weights == null ? 1f : weights[row], sum);
      pooledCount[0]++;
    });
    final int denominator = Math.max(pooledCount[0], 1);
    for (int i = 0; i < sum.length; i++) {
      sum[i] /= denominator;
    }
    final float[] pooled = table.finishPooling(sum);
    if (normalize) {
      double sumOfSquares = 0;
      for (final float value : pooled) {
        sumOfSquares += (double) value * value;
      }
      final float norm = (float) Math.max(Math.sqrt(sumOfSquares), NORMALIZE_EPSILON);
      for (int d = 0; d < dimension; d++) {
        pooled[d] /= norm;
      }
    }
    return pooled;
  }

  /**
   * Feeds every matrix row a text pools to the action, in text order: matched terms' rows where
   * the term table matches, and subword piece rows everywhere else. Without a term table this
   * is exactly the piece walk over the whole text.
   *
   * @param text   The text to fold into rows.
   * @param action Receives each pooled row.
   */
  private void forEachPooledRow(String text, IntConsumer action) {
    if (terms.size() == 0) {
      forEachPieceRow(text, action);
      return;
    }
    int cursor = 0;
    for (final TermTable.Match match : terms.matches(text)) {
      if (match.start() > cursor) {
        forEachPieceRow(text.substring(cursor, match.start()), action);
      }
      action.accept(match.row());
      cursor = match.end();
    }
    if (cursor < text.length()) {
      forEachPieceRow(text.substring(cursor), action);
    }
  }

  /**
   * Feeds the matrix row of every poolable subword piece of a text to the action.
   *
   * @param text   The text to tokenize.
   * @param action Receives each piece's row.
   */
  private void forEachPieceRow(String text, IntConsumer action) {
    final List<SubwordPiece> pieces = tokenizer.encode(text);
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
      action.accept(row);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return dimension;
  }

  /** {@return the number of subword tokens in this model's vocabulary, without term rows} */
  public int vocabularySize() {
    return vocabulary.size();
  }

  /**
   * {@return the number of term rows appended after the subword vocabulary, {@code 0} for a
   * model without a term table}
   */
  public int termCount() {
    return terms.size();
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
   * most similar first. This is a brute-force scan over the whole table; a model with a term
   * table returns matching terms as neighbors like any token.
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
  private void requirePositive(int topK) {
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
  private int[] excludedRows(String... queryTerms) {
    final SortedSet<Integer> rows = new TreeSet<>();
    for (final String queryTerm : queryTerms) {
      forEachPooledRow(queryTerm, rows::add);
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
    // The query maps into the table's working space once; every row is scored there. Norms are
    // unchanged by the mapping, so the cosine denominator uses the original query norm.
    final float[] preparedQuery = table.prepareQuery(query);
    final int rowCount = table.rowCount();
    // The capacity sizes the candidate arrays; a topK beyond the vocabulary (the scan can never
    // yield more than every row) would otherwise allocate topK-sized arrays or overflow.
    final TopK best = new TopK(Math.min(topK, rowCount));
    int nextExcluded = 0;
    for (int row = 0; row < rowCount; row++) {
      if (nextExcluded < sortedExcludedRows.length && sortedExcludedRows[nextExcluded] == row) {
        nextExcluded++;
        continue;
      }
      if (specialRows[row]) {
        continue;
      }
      final double rowNorm = table.rowNorm(row);
      if (rowNorm < NORMALIZE_EPSILON) {
        // A zero row has no direction; scored 0 rather than NaN from a 0/0 division.
        best.offer(row, 0.0);
        continue;
      }
      best.offer(row, table.dot(row, preparedQuery) / (queryNorm * rowNorm));
    }
    final Neighbor[] ordered = new Neighbor[best.size()];
    for (int i = ordered.length - 1; i >= 0; i--) {
      ordered[i] = new Neighbor(rowToken(best.minRow()), best.minSimilarity());
      best.removeMin();
    }
    return List.of(ordered);
  }

  /**
   * {@return the string of a matrix row: the vocabulary token of a subword row, the term of a
   * term row}
   *
   * @param row The matrix row.
   */
  private String rowToken(int row) {
    return row < vocabulary.size() ? vocabulary.token(row) : terms.term(row);
  }

  /**
   * {@return the cosine similarity of two vectors, or {@code 0} when either has no direction}
   *
   * @param a The first vector.
   * @param b The second vector, of the same length as {@code a}.
   */
  private double cosineSimilarity(float[] a, float[] b) {
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
  private double norm(float[] vector) {
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
     * Creates an empty selection.
     *
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
