/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.tools.commons.Internal;
import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.tokenize.SubwordTokenizer;
import opennlp.tools.tokenize.WordpieceEncoder;
import opennlp.tools.tokenize.WordpieceTokenizer;
import opennlp.tools.util.ArgumentChecks;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.normalizer.AlignedText;
import opennlp.tools.util.normalizer.Alignment;
import opennlp.tools.util.normalizer.CharClass;

/**
 * Base class for OpenNLP deep-learning classes using ONNX Runtime.
 */
public abstract class AbstractDL implements AutoCloseable {

  public static final String INPUT_IDS = "input_ids";
  public static final String ATTENTION_MASK = "attention_mask";
  public static final String TOKEN_TYPE_IDS = "token_type_ids";

  private static final String TOKENIZER_MODEL_KEY = "model";
  private static final String TOKENIZER_TYPE_KEY = "type";
  private static final String TOKENIZER_VOCAB_KEY = "vocab";
  private static final String TOKENIZER_SUBWORD_PREFIX_KEY = "continuing_subword_prefix";
  private static final String TOKENIZER_ADDED_TOKENS_KEY = "added_tokens";
  private static final String TOKENIZER_ID_KEY = "id";
  private static final String TOKENIZER_CONTENT_KEY = "content";
  private static final String TOKENIZER_NORMALIZER_KEY = "normalizer";
  private static final String TOKENIZER_LOWERCASE_KEY = "lowercase";
  /** The only {@code model.type} of a {@code tokenizer.json} the WordPiece encoder can use. */
  private static final String WORDPIECE_MODEL_TYPE = "WordPiece";
  /** The continuing subword prefix the WordPiece encoder assumes. */
  private static final String WORDPIECE_SUBWORD_PREFIX = "##";
  /** The start of the message for a {@code tokenizer.json} the WordPiece encoder cannot use. */
  private static final String UNSUPPORTED_TOKENIZER = "Unsupported tokenizer.json: ";
  /** The start of the message for a JSON vocabulary that has neither accepted layout. */
  private static final String EXPECTED_LAYOUTS = "Expected one object mapping tokens to integer"
      + " ids, as in vocab.json, or a tokenizer.json of a WordPiece model: ";

  protected final OrtEnvironment env;
  protected final OrtSession session;
  protected final SubwordTokenizer tokenizer;
  protected final Map<String, Integer> vocab;

  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * A half-open range of token indices covered by one chunk.
   *
   * @param start The inclusive index of the first token of the chunk.
   * @param end   The exclusive index after the last token of the chunk.
   */
  @Internal(since = "3.0.0")
  protected record ChunkRange(int start, int end) {
  }

  /**
   * A rejoined chunk paired with its half-open character span in the text it was split from, so a
   * chunk's decoded entities can be located within the region the chunk actually covers.
   *
   * @param text  The chunk text, the chunk's whitespace tokens rejoined with single ASCII spaces.
   * @param start The inclusive character offset of the chunk in the source text.
   * @param end   The exclusive character offset of the chunk in the source text.
   */
  @Internal(since = "3.0.0")
  protected record TextChunk(String text, int start, int end) {
  }

  /**
   * Initializes the shared, immutable inference state: the ONNX environment and session,
   * the loaded vocabulary and the configured tokenizer. These fields are {@code final}
   * and assigned exactly once here, so a fully constructed instance is safely published
   * and can be shared across threads.
   *
   * @param model The ONNX model file.
   * @param vocabulary The vocabulary file matching the model.
   * @param sessionOptions The session options (e.g. CUDA execution provider); build with
   *     {@link #sessionOptions(InferenceOptions)} when honoring {@link InferenceOptions}.
   * @param lowerCase {@code true} for uncased models (lower casing and accent stripping
   *     during tokenization), {@code false} for cased models. A {@code tokenizer.json} that
   *     sets {@code normalizer.lowercase} must agree with it.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if the {@code model} or {@code vocabulary} cannot be read.
   * @throws InvalidFormatException Thrown if a JSON {@code vocabulary} is malformed, has an
   *     unsupported layout, or sets {@code normalizer.lowercase} to the other value than
   *     {@code lowerCase}.
   */
  protected AbstractDL(final File model, final File vocabulary,
                       final OrtSession.SessionOptions sessionOptions, final boolean lowerCase)
      throws IOException, OrtException {
    ArgumentChecks.requireNonNullArg(model, "model");
    ArgumentChecks.requireNonNullArg(vocabulary, "vocabulary");
    ArgumentChecks.requireNonNullArg(sessionOptions, "sessionOptions");
    this.env = OrtEnvironment.getEnvironment();
    // try-with-resources closes the session options once the session has consumed them.
    try (sessionOptions) {
      final OrtSession createdSession = env.createSession(model.getPath(), sessionOptions);
      try {
        final Vocabulary loaded = readVocabFile(vocabulary);
        requireLowerCase(vocabulary, loaded, lowerCase);
        this.vocab = Map.copyOf(loaded.ids());
        this.tokenizer = createWordpieceEncoder(vocab, lowerCase);
      } catch (IOException | RuntimeException e) {
        // Vocabulary/tokenizer init failed after the native session was created; close it
        // so a partially constructed instance never leaks the ONNX session.
        try {
          createdSession.close();
        } catch (OrtException suppressed) {
          e.addSuppressed(suppressed);
        }
        throw e;
      }
      this.session = createdSession;
    }
  }

  /**
   * Directly assigns the shared inference state. This seam exists for unit tests that need to
   * construct a component without loading an ONNX model (e.g. passing a {@code null}
   * {@link OrtSession} to exercise inference-failure handling). The fields remain {@code final}
   * and are assigned exactly once, so safe publication is preserved.
   *
   * @param env The ONNX environment, or {@code null} in tests.
   * @param session The ONNX session, or {@code null} in tests that do not run inference.
   * @param vocab The vocabulary used by the tokenizer.
   * @param lowerCase {@code true} for uncased models, {@code false} for cased models.
   */
  protected AbstractDL(final OrtEnvironment env, final OrtSession session,
                       final Map<String, Integer> vocab, final boolean lowerCase) {
    this.env = env;
    this.session = session;
    this.vocab = vocab;
    this.tokenizer = createWordpieceEncoder(vocab, lowerCase);
  }

  /**
   * Builds ONNX session options from the given {@link InferenceOptions}, enabling the CUDA
   * execution provider on the configured device when GPU inference is requested.
   *
   * @param inferenceOptions The inference options to read the GPU configuration from.
   * @return The configured session options.
   *
   * @throws OrtException Thrown if the CUDA execution provider cannot be added.
   */
  protected static OrtSession.SessionOptions sessionOptions(final InferenceOptions inferenceOptions)
      throws OrtException {
    ArgumentChecks.requireNonNullArg(inferenceOptions, "inferenceOptions");
    validateSplitOptions(inferenceOptions);
    final OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
    if (inferenceOptions.isGpu()) {
      sessionOptions.addCUDA(inferenceOptions.getGpuDeviceId());
    }
    return sessionOptions;
  }

  /**
   * Loads a vocabulary {@link File} from disk. A file with an opening brace as its first
   * non-whitespace character is read as JSON, in one of two layouts: one object that maps each
   * token to a non-negative integer ID, as in {@code vocab.json}, or a Hugging Face
   * {@code tokenizer.json} of a WordPiece model, whose {@code model.vocab} object supplies the
   * tokens and whose {@code added_tokens} absent from {@code model.vocab} are added with their
   * ids. Any other file is read as plain text with one token per line, the line number being
   * the ID, as in {@code vocab.txt}. An empty line in a plain text file is skipped but still
   * counts toward the IDs of the lines after it; a line that holds only whitespace is a token.
   * A byte order mark at the start of the file is not content in either format.
   *
   * @param vocabFile The vocabulary file.
   * @return A map of vocabulary words to IDs.
   * @throws IOException Thrown if the vocabulary file cannot be opened or read.
   * @throws InvalidFormatException Thrown if a JSON vocabulary is malformed, contains a value
   *     that is not a non-negative integer, or is a {@code tokenizer.json} of another model type
   *     than WordPiece or with an added token that conflicts with the vocabulary. The message
   *     names the file and the offset, the token, or the unsupported member.
   */
  public Map<String, Integer> loadVocab(
      final File vocabFile) throws IOException {

    return loadVocabFile(vocabFile);
  }

  /**
   * Loads a vocabulary file as {@link #loadVocab(File)} does.
   *
   * @param vocabFile The vocabulary file.
   * @return A map of vocabulary words to IDs.
   * @throws IOException Thrown if the vocabulary file cannot be opened or read.
   * @throws InvalidFormatException Thrown if a JSON vocabulary is malformed, has a layout that
   *     is not supported, or contains a value that is not a non-negative integer.
   */
  static Map<String, Integer> loadVocabFile(
      final File vocabFile) throws IOException {

    return readVocabFile(vocabFile).ids();
  }

  /**
   * A vocabulary read from a file or JSON text.
   *
   * @param ids The map of tokens to IDs.
   * @param lowercase The {@code normalizer.lowercase} setting of a {@code tokenizer.json}, or
   *     {@code null} if the vocabulary does not carry that setting.
   */
  record Vocabulary(Map<String, Integer> ids, Boolean lowercase) {
  }

  /**
   * Reads a vocabulary file as {@link #loadVocab(File)} does, keeping the settings of a
   * {@code tokenizer.json} that a component must agree with.
   *
   * @param vocabFile The vocabulary file.
   * @return The vocabulary.
   * @throws IOException Thrown if the vocabulary file cannot be opened or read.
   * @throws InvalidFormatException Thrown if a JSON vocabulary is malformed, has a layout that
   *     is not supported, or contains a value that is not a non-negative integer.
   */
  static Vocabulary readVocabFile(final File vocabFile) throws IOException {
    final String read = Files.readString(Path.of(vocabFile.getPath()), StandardCharsets.UTF_8);
    final String content = StringUtil.stripByteOrderMark(read);
    final String trimmed = content.trim();

    // Detect JSON format by leading brace
    if (trimmed.startsWith("{")) {
      try {
        return readJsonVocab(trimmed);
      } catch (IllegalArgumentException e) {
        throw new InvalidFormatException(
            "Vocabulary file " + vocabFile.getName() + ": " + e.getMessage(), e);
      }
    }

    final Map<String, Integer> vocab =
        new HashMap<>();
    final AtomicInteger counter =
        new AtomicInteger(0);

    content.lines().forEach(line -> {
      final int id = counter.getAndIncrement();
      if (!line.isEmpty()) {
        vocab.put(line, id);
      }
    });

    return new Vocabulary(vocab, null);
  }

  /**
   * Checks that the lower casing a component is configured with agrees with the
   * {@code normalizer.lowercase} setting of its vocabulary file, so that the component encodes
   * text as the model's own tokenizer does.
   *
   * @param vocabFile The vocabulary file, named in the message.
   * @param vocabulary The vocabulary read from it.
   * @param lowerCase {@code true} if the component lower cases text, {@code false} otherwise.
   * @throws InvalidFormatException Thrown if the file sets {@code normalizer.lowercase} to the
   *     other value.
   */
  static void requireLowerCase(final File vocabFile, final Vocabulary vocabulary,
      final boolean lowerCase) throws InvalidFormatException {
    final Boolean lowercase = vocabulary.lowercase();
    if (lowercase != null && lowercase != lowerCase) {
      throw new InvalidFormatException("Vocabulary file " + vocabFile.getName() + " sets "
          + TOKENIZER_NORMALIZER_KEY + "." + TOKENIZER_LOWERCASE_KEY + " to " + lowercase
          + ", but the component is configured with lowerCase " + lowerCase);
    }
  }

  /**
   * Creates a {@link WordpieceTokenizer} that uses the
   * appropriate special tokens based on the vocabulary.
   * If the vocabulary contains RoBERTa-style tokens,
   * those are used. Otherwise, the BERT defaults are
   * used.
   *
   * @param vocab The vocabulary map.
   * @return A configured {@link WordpieceTokenizer}.
   */
  protected WordpieceTokenizer createTokenizer(
      final Map<String, Integer> vocab) {

    return createWordpieceTokenizer(vocab);
  }

  static WordpieceTokenizer createWordpieceTokenizer(
      final Map<String, Integer> vocab) {
    if (vocab.containsKey(
            WordpieceTokenizer.ROBERTA_CLS_TOKEN)
        && vocab.containsKey(
            WordpieceTokenizer.ROBERTA_SEP_TOKEN)) {
      return new WordpieceTokenizer(
          vocab.keySet(),
          WordpieceTokenizer.ROBERTA_CLS_TOKEN,
          WordpieceTokenizer.ROBERTA_SEP_TOKEN,
          resolveUnknownToken(vocab));
    }
    return new WordpieceTokenizer(vocab.keySet());
  }

  /**
   * Builds the BERT encoder, selecting RoBERTa special tokens when present and BERT defaults
   * otherwise.
   *
   * @param vocab     The vocabulary map.
   * @param lowerCase {@code true} for uncased models, {@code false} for cased models.
   * @return A configured {@link WordpieceEncoder}.
   * @throws IllegalArgumentException Thrown if {@code vocab} is {@code null} or the selected
   *     special tokens are not all present in it.
   */
  static WordpieceEncoder createWordpieceEncoder(
      final Map<String, Integer> vocab, final boolean lowerCase) {
    ArgumentChecks.requireNonNullArg(vocab, "vocab");
    if (vocab.containsKey(
            WordpieceTokenizer.ROBERTA_CLS_TOKEN)
        && vocab.containsKey(
            WordpieceTokenizer.ROBERTA_SEP_TOKEN)) {
      return new WordpieceEncoder(
          vocab,
          lowerCase,
          WordpieceTokenizer.ROBERTA_CLS_TOKEN,
          WordpieceTokenizer.ROBERTA_SEP_TOKEN,
          resolveUnknownToken(vocab));
    }
    return new WordpieceEncoder(vocab, lowerCase,
        WordpieceTokenizer.BERT_CLS_TOKEN,
        WordpieceTokenizer.BERT_SEP_TOKEN,
        WordpieceTokenizer.BERT_UNK_TOKEN);
  }

  /**
   * Encodes text as the inputs consumed by a BERT model: the piece strings, their vocabulary
   * ids, an attention mask of ones, and single-segment token types.
   *
   * @param text The text to encode; must not be {@code null}.
   * @return The encoded pieces, ids, attention mask, and token types.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  protected final Tokens encodeTokens(CharSequence text) {
    final List<SubwordPiece> pieces = tokenizer.encode(text);
    final String[] tokens = new String[pieces.size()];
    final long[] ids = new long[pieces.size()];
    final long[] mask = new long[pieces.size()];
    final long[] types = new long[pieces.size()];
    for (int i = 0; i < pieces.size(); i++) {
      final SubwordPiece piece = pieces.get(i);
      tokens[i] = piece.piece();
      ids[i] = piece.id();
    }
    Arrays.fill(mask, 1);
    return new Tokens(tokens, ids, mask, types);
  }

  /**
   * Resolves the unknown token of a RoBERTa-style vocabulary. The RoBERTa
   * token {@link WordpieceTokenizer#ROBERTA_UNK_TOKEN} is preferred; vocabularies
   * mixing conventions may instead contain {@link WordpieceTokenizer#BERT_UNK_TOKEN}.
   * An unknown token that is absent from the vocabulary must never be selected, as
   * the tokenizer would emit tokens that later fail the token-to-id mapping.
   *
   * @param vocab The vocabulary map.
   * @return The unknown token present in the vocabulary.
   * @throws IllegalArgumentException Thrown if the vocabulary contains neither
   *     supported unknown token.
   */
  private static String resolveUnknownToken(final Map<String, Integer> vocab) {
    if (vocab.containsKey(WordpieceTokenizer.ROBERTA_UNK_TOKEN)) {
      return WordpieceTokenizer.ROBERTA_UNK_TOKEN;
    }
    if (vocab.containsKey(WordpieceTokenizer.BERT_UNK_TOKEN)) {
      return WordpieceTokenizer.BERT_UNK_TOKEN;
    }
    throw new IllegalArgumentException(
        "The vocabulary contains neither '" + WordpieceTokenizer.ROBERTA_UNK_TOKEN
            + "' nor '" + WordpieceTokenizer.BERT_UNK_TOKEN + "' as an unknown token.");
  }

  /**
   * Resolves the effective lower casing behavior from the
   * given {@link InferenceOptions}.
   *
   * @param options The {@link InferenceOptions} to consult.
   * @param componentDefault The default to apply if the option is not set.
   * @return The effective lower casing behavior.
   */
  protected static boolean resolveLowerCase(
      final InferenceOptions options, final boolean componentDefault) {
    ArgumentChecks.requireNonNullArg(options, "options");
    return options.getLowerCase() != null ? options.getLowerCase() : componentDefault;
  }

  /**
   * Validates the document splitting options used by tokenizers that split long inputs.
   *
   * @param options The inference options to validate.
   * @throws IllegalArgumentException Thrown if the split settings cannot make progress.
   */
  protected static void validateSplitOptions(final InferenceOptions options) {
    ArgumentChecks.requireNonNullArg(options, "options");
    validateSplitOptions(options.getDocumentSplitSize(), options.getSplitOverlapSize());
  }

  /**
   * Validates the document splitting values used by tokenizers that split long inputs.
   *
   * @param documentSplitSize The number of tokens per split.
   * @param splitOverlapSize The number of tokens to overlap between adjacent splits.
   * @throws IllegalArgumentException Thrown if the split settings cannot make progress.
   */
  protected static void validateSplitOptions(final int documentSplitSize, final int splitOverlapSize) {
    if (documentSplitSize <= 0) {
      throw new IllegalArgumentException("The documentSplitSize must be greater than zero.");
    }
    if (splitOverlapSize < 0) {
      throw new IllegalArgumentException("The splitOverlapSize must not be negative.");
    }
    if (splitOverlapSize >= documentSplitSize) {
      throw new IllegalArgumentException(
          "The splitOverlapSize must be smaller than documentSplitSize.");
    }
  }

  /**
   * Unicode-aware whitespace. Input is tokenized on the full Unicode {@code White_Space} set
   * rather than the six ASCII characters Java's {@code \s} recognizes.
   */
  @Internal(since = "3.0.0")
  protected static final CharClass WHITESPACE = CharClass.whitespace();

  /** Unicode dashes (excluding the mathematical minus signs), used for optional input folding. */
  @Internal(since = "3.0.0")
  protected static final CharClass DASHES = CharClass.dashes();

  /**
   * Optionally folds Unicode whitespace and/or dashes in the input to their ASCII forms before
   * inference, returning just the folded text. This is suitable for callers that do not map model
   * output back to character offsets, such as whole-document classification. When the result must
   * be mapped back to the original text (for example to report entity spans), use
   * {@link #normalizeInputAligned(String, boolean, boolean)} instead, which also returns an
   * {@link Alignment} that stays correct even when a fold changes the string length.
   *
   * @param text The input text.
   * @param normalizeWhitespace Whether to fold whitespace to ASCII spaces.
   * @param normalizeDashes Whether to fold dashes to the ASCII hyphen.
   * @return The optionally normalized text.
   */
  @Internal(since = "3.0.0")
  protected static String normalizeInput(final String text, final boolean normalizeWhitespace,
                                         final boolean normalizeDashes) {
    String result = text;
    if (normalizeWhitespace) {
      result = WHITESPACE.normalize(result).toString();
    }
    if (normalizeDashes) {
      result = DASHES.normalize(result).toString();
    }
    return result;
  }

  /**
   * Like {@link #normalizeInput(String, boolean, boolean)} but also produces an {@link Alignment}
   * from the folded text back to {@code text}, so model output positions map to original character
   * offsets even when a fold changes the string length (a supplementary dash shrinking, or, for
   * folds that may be added later, an expansion such as an ellipsis to three dots).
   *
   * @param text The input text.
   * @param normalizeWhitespace Whether to fold whitespace to ASCII spaces.
   * @param normalizeDashes Whether to fold dashes to the ASCII hyphen.
   * @return The optionally normalized text paired with its alignment back to {@code text}.
   */
  @Internal(since = "3.0.0")
  protected static AlignedText normalizeInputAligned(final String text,
      final boolean normalizeWhitespace, final boolean normalizeDashes) {
    // Compose each enabled fold's alignment with the running alignment so the returned mapping is
    // correct no matter whether a stage changes length. Whitespace folding here is a one-for-one
    // replacement and so is length-preserving today; only dash folding moves offsets (a
    // supplementary-plane dash shrinks from two chars to one). Composing through andThen rather
    // than relying on the whitespace stage staying length-preserving keeps findInOriginal() correct
    // if that ever changes.
    AlignedText result = identityAligned(text, text);
    if (normalizeWhitespace) {
      result = result.andThen(WHITESPACE.normalizeAligned(result.normalized()));
    }
    if (normalizeDashes) {
      result = result.andThen(DASHES.normalizeAligned(result.normalized()));
    }
    return result;
  }

  // An AlignedText whose alignment is the identity, for the case where no length-changing fold was
  // applied so the folded text has the same length and offsets as the original.
  private static AlignedText identityAligned(final String original, final String normalized) {
    final Alignment alignment = new Alignment.Builder(normalized.length())
        .equal(normalized.length()).build(normalized.length());
    return new AlignedText(original, normalized, alignment);
  }

  /**
   * Splits {@code text} on Unicode whitespace and groups the resulting tokens into overlapping
   * chunks, each rejoined with single ASCII spaces, ready for WordPiece tokenization. The split
   * uses the Unicode {@code White_Space} set, so spacing such as a no-break space or the
   * ideographic space is recognized, and it yields no empty tokens from leading, trailing, or
   * repeated whitespace.
   *
   * @param text The input text.
   * @param documentSplitSize The maximum number of whitespace tokens per chunk.
   * @param splitOverlapSize The number of tokens shared between consecutive chunks.
   * @return The chunk strings, in order.
   */
  @Internal(since = "3.0.0")
  protected static List<String> whitespaceChunks(final String text, final int documentSplitSize,
                                                 final int splitOverlapSize) {
    final List<TextChunk> chunks = whitespaceChunkSpans(text, documentSplitSize, splitOverlapSize);
    final List<String> groups = new ArrayList<>(chunks.size());
    for (final TextChunk chunk : chunks) {
      groups.add(chunk.text());
    }
    return groups;
  }

  /**
   * Like {@link #whitespaceChunks(String, int, int)} but also carries each chunk's character span
   * in {@code text}, so a chunk can be decoded bounded to the region it covers and overlapping
   * chunks yield overlapping candidate spans rather than silently dropping a boundary entity.
   *
   * @param text The input text.
   * @param documentSplitSize The maximum number of whitespace tokens per chunk.
   * @param splitOverlapSize The number of tokens shared between consecutive chunks.
   * @return The chunks, in order, each with its character span in {@code text}.
   */
  @Internal(since = "3.0.0")
  protected static List<TextChunk> whitespaceChunkSpans(final String text,
      final int documentSplitSize, final int splitOverlapSize) {
    final List<Span> tokenSpans = WHITESPACE.splitSpans(text);
    final List<TextChunk> chunks = new ArrayList<>();
    for (final ChunkRange range : chunkRanges(tokenSpans.size(), documentSplitSize,
        splitOverlapSize)) {
      final StringBuilder rejoined = new StringBuilder();
      for (int i = range.start(); i < range.end(); i++) {
        if (i > range.start()) {
          rejoined.append(' ');
        }
        rejoined.append(text, tokenSpans.get(i).getStart(), tokenSpans.get(i).getEnd());
      }
      final int start = tokenSpans.get(range.start()).getStart();
      final int end = tokenSpans.get(range.end() - 1).getEnd();
      chunks.add(new TextChunk(rejoined.toString(), start, end));
    }
    return chunks;
  }

  /**
   * Splits a token sequence into overlapping chunk ranges.
   *
   * @param tokenCount The number of tokens to split.
   * @param documentSplitSize The number of tokens per split.
   * @param splitOverlapSize The number of tokens to overlap between adjacent splits.
   * @return The chunk ranges to process.
   * @throws IllegalArgumentException Thrown if the token count is negative or the split settings
   *     cannot make progress.
   */
  @Internal(since = "3.0.0")
  protected static List<ChunkRange> chunkRanges(final int tokenCount, final int documentSplitSize,
                                                final int splitOverlapSize) {
    if (tokenCount < 0) {
      throw new IllegalArgumentException("The tokenCount must not be negative.");
    }
    validateSplitOptions(documentSplitSize, splitOverlapSize);

    final List<ChunkRange> ranges = new ArrayList<>();
    int start = 0;
    while (start < tokenCount) {
      final int end = Math.min(start + documentSplitSize, tokenCount);
      ranges.add(new ChunkRange(start, end));
      start = end == tokenCount ? end : end - splitOverlapSize;
    }
    return List.copyOf(ranges);
  }

  /**
   * Reads a JSON vocabulary in one of two layouts: a {@code vocab.json} object that maps each
   * token to its ID, or a {@code tokenizer.json} of a WordPiece model, recognized by a
   * {@code model} object with a string {@code type}, whose {@code model.vocab} object supplies
   * the tokens and whose {@code added_tokens} entries absent from {@code model.vocab} are added
   * with their ids. Keys are decoded from their escapes, and a later entry for the same token
   * overwrites an earlier one.
   *
   * @param json The JSON text of the vocabulary.
   * @return A map of vocabulary tokens to IDs.
   * @throws IllegalArgumentException Thrown if the text is not a single well-formed JSON object,
   *     if a value of the vocabulary object is not a non-negative integer that fits into an
   *     {@code int}, if a {@code tokenizer.json} is not that of a WordPiece model with the
   *     {@code ##} continuing subword prefix and a {@code model.vocab} object, or if an added
   *     token is malformed or conflicts with another token or id. The message names the offset,
   *     the token, or the unsupported member.
   */
  static Map<String, Integer> loadJsonVocab(final String json) {
    return readJsonVocab(json).ids();
  }

  /**
   * Reads a JSON vocabulary as {@link #loadJsonVocab(String)} does, keeping the
   * {@code normalizer.lowercase} setting of a {@code tokenizer.json}.
   *
   * @param json The JSON text of the vocabulary.
   * @return The vocabulary.
   * @throws IllegalArgumentException Thrown as by {@link #loadJsonVocab(String)}.
   */
  static Vocabulary readJsonVocab(final String json) {
    final List<JsonScan.Member> document = JsonScan.document(json);
    final JsonScan.Member model = JsonScan.member(document, TOKENIZER_MODEL_KEY);
    if (model != null && JsonScan.isObject(json, model)) {
      final List<JsonScan.Member> modelMembers = JsonScan.members(json, model.valueStart());
      final JsonScan.Member type = JsonScan.member(modelMembers, TOKENIZER_TYPE_KEY);
      if (type != null && JsonScan.isString(json, type)) {
        final Map<String, Integer> vocab =
            tokenizerVocab(json, modelMembers, JsonScan.stringValue(json, type));
        addTokens(json, document, vocab);
        return new Vocabulary(vocab, lowercaseSetting(json, document));
      }
    }
    final Map<String, Integer> vocab = new HashMap<>();
    for (JsonScan.Member member : document) {
      try {
        vocab.put(member.key(), JsonScan.nonNegativeIntValue(json, member));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(EXPECTED_LAYOUTS + e.getMessage(), e);
      }
    }
    return new Vocabulary(vocab, null);
  }

  /**
   * Reads the vocabulary of the {@code model} object of a {@code tokenizer.json}.
   *
   * @param json The JSON text.
   * @param model The members of the {@code model} object.
   * @param type The value of {@code model.type}.
   * @return A map of the tokens of {@code model.vocab} to their IDs.
   * @throws IllegalArgumentException Thrown if {@code type} is not {@code WordPiece}, if
   *     {@code model.continuing_subword_prefix} is present and not {@code ##}, if
   *     {@code model.vocab} is missing or not an object, or if a value of it is not a
   *     non-negative integer that fits into an {@code int}.
   */
  private static Map<String, Integer> tokenizerVocab(final String json,
      final List<JsonScan.Member> model, final String type) {
    if (!WORDPIECE_MODEL_TYPE.equals(type)) {
      throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + "only " + WORDPIECE_MODEL_TYPE
          + " models are supported, found " + TOKENIZER_MODEL_KEY + "." + TOKENIZER_TYPE_KEY
          + " \"" + type + "\"");
    }
    final JsonScan.Member prefix = JsonScan.member(model, TOKENIZER_SUBWORD_PREFIX_KEY);
    if (prefix != null) {
      final String found = JsonScan.isString(json, prefix) ? JsonScan.stringValue(json, prefix)
          : json.substring(prefix.valueStart(), prefix.valueEnd());
      if (!WORDPIECE_SUBWORD_PREFIX.equals(found)) {
        throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + TOKENIZER_MODEL_KEY + "."
            + TOKENIZER_SUBWORD_PREFIX_KEY + " must be \"" + WORDPIECE_SUBWORD_PREFIX + "\", found "
            + found);
      }
    }
    final JsonScan.Member vocab = JsonScan.member(model, TOKENIZER_VOCAB_KEY);
    if (vocab == null) {
      throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + TOKENIZER_MODEL_KEY + "."
          + TOKENIZER_VOCAB_KEY + " is missing");
    }
    if (!JsonScan.isObject(json, vocab)) {
      throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + TOKENIZER_MODEL_KEY + "."
          + TOKENIZER_VOCAB_KEY + " must be an object that maps tokens to ids");
    }
    final Map<String, Integer> ids = new HashMap<>();
    for (JsonScan.Member member : JsonScan.members(json, vocab.valueStart())) {
      try {
        ids.put(member.key(), JsonScan.nonNegativeIntValue(json, member));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(TOKENIZER_MODEL_KEY + "." + TOKENIZER_VOCAB_KEY + ": "
            + e.getMessage(), e);
      }
    }
    return ids;
  }

  /**
   * Reads the {@code normalizer.lowercase} setting of a {@code tokenizer.json}.
   *
   * @param json The JSON text.
   * @param document The members of the top-level object.
   * @return The setting, or {@code null} if there is no {@code normalizer} object or it has no
   *     {@code lowercase} member.
   * @throws IllegalArgumentException Thrown if the setting is neither {@code true} nor
   *     {@code false}.
   */
  private static Boolean lowercaseSetting(final String json,
      final List<JsonScan.Member> document) {
    final JsonScan.Member normalizer = JsonScan.member(document, TOKENIZER_NORMALIZER_KEY);
    if (normalizer == null || !JsonScan.isObject(json, normalizer)) {
      return null;
    }
    final JsonScan.Member lowercase = JsonScan.member(
        JsonScan.members(json, normalizer.valueStart()), TOKENIZER_LOWERCASE_KEY);
    if (lowercase == null) {
      return null;
    }
    try {
      return JsonScan.booleanValue(json, lowercase);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + TOKENIZER_NORMALIZER_KEY + ": "
          + e.getMessage(), e);
    }
  }

  /**
   * Adds the entries of the {@code added_tokens} list of a {@code tokenizer.json} to a
   * vocabulary. An entry whose token the vocabulary already maps to the same id is left as it
   * is.
   *
   * @param json The JSON text.
   * @param document The members of the top-level object.
   * @param vocab The vocabulary read from {@code model.vocab}, extended in place.
   * @throws IllegalArgumentException Thrown if {@code added_tokens} is present and not a list,
   *     if an entry is not an object with a string {@code content} and a non-negative integer
   *     {@code id}, or if an entry conflicts with the vocabulary: its token has another id
   *     there, or its id belongs to another token.
   */
  private static void addTokens(final String json, final List<JsonScan.Member> document,
      final Map<String, Integer> vocab) {
    final JsonScan.Member addedTokens = JsonScan.member(document, TOKENIZER_ADDED_TOKENS_KEY);
    if (addedTokens == null) {
      return;
    }
    if (!JsonScan.isArray(json, addedTokens)) {
      throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + TOKENIZER_ADDED_TOKENS_KEY
          + " must be a list");
    }
    Map<Integer, String> tokensById = null;
    for (JsonScan.Member entry : JsonScan.elements(json, addedTokens.valueStart())) {
      final String where = TOKENIZER_ADDED_TOKENS_KEY + "[" + entry.key() + "]";
      if (!JsonScan.isObject(json, entry)) {
        throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + where + " must be an object");
      }
      final List<JsonScan.Member> fields = JsonScan.members(json, entry.valueStart());
      final JsonScan.Member content = JsonScan.member(fields, TOKENIZER_CONTENT_KEY);
      final JsonScan.Member id = JsonScan.member(fields, TOKENIZER_ID_KEY);
      if (content == null || id == null) {
        throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + where + " must have \""
            + TOKENIZER_CONTENT_KEY + "\" and \"" + TOKENIZER_ID_KEY + "\"");
      }
      final String token;
      final int tokenId;
      try {
        token = JsonScan.stringValue(json, content);
        tokenId = JsonScan.nonNegativeIntValue(json, id);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + where + ": " + e.getMessage(), e);
      }
      final Integer known = vocab.get(token);
      if (known != null) {
        if (known != tokenId) {
          throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + "added token \"" + token
              + "\" has id " + tokenId + " but the vocabulary maps it to " + known);
        }
        continue;
      }
      if (tokensById == null) {
        tokensById = new HashMap<>();
        for (Map.Entry<String, Integer> e : vocab.entrySet()) {
          tokensById.put(e.getValue(), e.getKey());
        }
      }
      final String holder = tokensById.putIfAbsent(tokenId, token);
      if (holder != null) {
        throw new IllegalArgumentException(UNSUPPORTED_TOKENIZER + "added token \"" + token
            + "\" has id " + tokenId + " but the vocabulary maps \"" + holder + "\" to it");
      }
      vocab.put(token, tokenId);
    }
  }

  /**
   * Closes the ONNX {@link OrtSession} owned by this instance.
   *
   * <p>The {@link OrtEnvironment} is deliberately <b>not</b> closed:
   * {@link OrtEnvironment#getEnvironment()} returns a process-wide singleton shared by
   * every deep-learning component, so closing it here would tear down the environment
   * other live components still depend on.</p>
   *
   * <p>This method is idempotent: calling {@code close()} more than once, or calling it on
   * a never-used but successfully constructed instance, is a no-op after the first successful
   * close attempt. The underlying {@link OrtSession#close()} is only invoked once.</p>
   *
   * @throws OrtException Thrown if the close attempt fails in the native layer.
   */
  @Override
  public void close() throws OrtException {
    if (closed.compareAndSet(false, true) && session != null) {
      session.close();
    }
  }

  /**
   * Converts model scores into a probability distribution with a softmax that subtracts the
   * largest score before exponentiating, so large finite scores do not overflow.
   *
   * <p>Non-finite scores follow one rule: if any score is {@code +Infinity}, those entries share
   * the probability mass equally and all others get {@code 0}. Otherwise {@code NaN} scores are
   * left out of the normalization and get {@code NaN}, and {@code -Infinity} scores get
   * {@code 0}. If no score is finite or {@code +Infinity}, every entry gets
   * {@code 1 / scores.length}.</p>
   *
   * @param scores The raw model scores. Must not be {@code null}.
   * @return The probabilities, in the order of {@code scores}.
   * @throws IllegalArgumentException Thrown if {@code scores} is {@code null}.
   */
  @Internal(since = "3.0.0")
  protected static double[] softmaxProbabilities(final float[] scores) {
    ArgumentChecks.requireNonNullArg(scores, "scores");

    final ScoreSummary summary = summarize(scores);
    final double[] probabilities = new double[scores.length];
    if (summary.isDegenerate()) {
      for (int i = 0; i < scores.length; i++) {
        probabilities[i] = degenerateProbability(scores[i], scores.length, summary);
      }
      return probabilities;
    }
    // Keep each exp(score - max) in the output and divide in place, so exp runs once per score.
    double denominator = 0d;
    for (int i = 0; i < scores.length; i++) {
      final double scaled = Math.exp(scores[i] - summary.max());
      probabilities[i] = scaled;
      if (!Float.isNaN(scores[i])) {
        denominator += scaled;
      }
    }
    for (int i = 0; i < scores.length; i++) {
      probabilities[i] /= denominator;
    }
    return probabilities;
  }

  /**
   * Computes the probability of one entry of
   * {@link #softmaxProbabilities(float[])} without allocating the full distribution.
   *
   * @param scores The raw model scores. Must not be {@code null}.
   * @param index The index of the score whose probability is returned. Must be a valid index
   *     into {@code scores}.
   * @return The probability at {@code index}, following the non-finite score rule of
   *     {@link #softmaxProbabilities(float[])}.
   * @throws IllegalArgumentException Thrown if {@code scores} is {@code null} or {@code index}
   *     is out of range.
   */
  @Internal(since = "3.0.0")
  protected static double softmaxProbability(final float[] scores, final int index) {
    ArgumentChecks.requireNonNullArg(scores, "scores");
    if (index < 0 || index >= scores.length) {
      throw new IllegalArgumentException("The index " + index
          + " is out of range for " + scores.length + " scores.");
    }

    final ScoreSummary summary = summarize(scores);
    if (summary.isDegenerate()) {
      return degenerateProbability(scores[index], scores.length, summary);
    }
    double denominator = 0d;
    for (final float score : scores) {
      if (!Float.isNaN(score)) {
        denominator += Math.exp(score - summary.max());
      }
    }
    return Math.exp(scores[index] - summary.max()) / denominator;
  }

  /**
   * The two facts about a score array that decide the softmax rule: how many scores are
   * {@code +Infinity}, and the largest score that is neither {@code NaN} nor {@code +Infinity}.
   *
   * @param positiveInfinityCount The number of {@code +Infinity} scores.
   * @param max The largest other score, or {@code -Infinity} if there is none.
   */
  private record ScoreSummary(int positiveInfinityCount, double max) {

    /**
     * @return {@code true} if the distribution is decided without exponentials, because a
     *     score is {@code +Infinity} or no score is finite.
     */
    boolean isDegenerate() {
      return positiveInfinityCount > 0 || max == Double.NEGATIVE_INFINITY;
    }
  }

  /**
   * Gathers the {@link ScoreSummary} of {@code scores} in one pass.
   *
   * @param scores The raw model scores.
   * @return The summary.
   */
  private static ScoreSummary summarize(final float[] scores) {
    int positiveInfinityCount = 0;
    double max = Double.NEGATIVE_INFINITY;
    for (final float score : scores) {
      if (score == Float.POSITIVE_INFINITY) {
        positiveInfinityCount++;
      } else if (!Float.isNaN(score) && score > max) {
        max = score;
      }
    }
    return new ScoreSummary(positiveInfinityCount, max);
  }

  /**
   * Applies the softmax rule of {@link #softmaxProbabilities(float[])} to one score of a
   * degenerate distribution, see {@link ScoreSummary#isDegenerate()}.
   *
   * @param score The score to normalize.
   * @param length The number of scores.
   * @param summary The summary of all scores.
   * @return The probability of {@code score}.
   */
  private static double degenerateProbability(final float score, final int length,
                                              final ScoreSummary summary) {
    if (summary.positiveInfinityCount() > 0) {
      return score == Float.POSITIVE_INFINITY ? 1d / summary.positiveInfinityCount() : 0d;
    }
    return 1d / length;
  }

  /**
   * Validates that a parameter is not {@code null}.
   *
   * @param value The parameter value to validate.
   * @param name The parameter name used in the exception message.
   * @throws IllegalArgumentException Thrown if {@code value} is {@code null}.
   * @deprecated Use {@link ArgumentChecks#requireNonNullArg(Object, String)} instead.
   */
  @Deprecated(since = "3.0.0", forRemoval = true)
  protected static void requireNonNullArg(Object value, String name) {
    ArgumentChecks.requireNonNullArg(value, name);
  }
}
