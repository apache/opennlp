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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.tools.tokenize.BertTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WordpieceTokenizer;
import opennlp.tools.util.Span;
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

  protected final OrtEnvironment env;
  protected final OrtSession session;
  protected final Tokenizer tokenizer;
  protected final Map<String, Integer> vocab;

  private final AtomicBoolean closed = new AtomicBoolean();

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
  protected record TextChunk(String text, int start, int end) {
  }

  private static final Pattern JSON_ENTRY_PATTERN =
      Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*(\\d+)");

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
   *     during tokenization), {@code false} for cased models.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if the {@code model} or {@code vocabulary} cannot be read.
   */
  protected AbstractDL(final File model, final File vocabulary,
                       final OrtSession.SessionOptions sessionOptions, final boolean lowerCase)
      throws IOException, OrtException {
    requireNonNullArg(model, "model");
    requireNonNullArg(vocabulary, "vocabulary");
    requireNonNullArg(sessionOptions, "sessionOptions");
    this.env = OrtEnvironment.getEnvironment();
    // try-with-resources closes the session options once the session has consumed them.
    try (sessionOptions) {
      final OrtSession createdSession = env.createSession(model.getPath(), sessionOptions);
      try {
        this.vocab = Map.copyOf(loadVocabFile(vocabulary));
        this.tokenizer = createBertTokenizer(vocab, lowerCase);
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
    this.tokenizer = createBertTokenizer(vocab, lowerCase);
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
    requireNonNullArg(inferenceOptions, "inferenceOptions");
    validateSplitOptions(inferenceOptions);
    final OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
    if (inferenceOptions.isGpu()) {
      sessionOptions.addCUDA(inferenceOptions.getGpuDeviceId());
    }
    return sessionOptions;
  }

  /**
   * Loads a vocabulary {@link File} from disk.
   * Supports both plain text files (one token per
   * line) and simple JSON vocabulary files mapping tokens to integer
   * IDs. JSON support is intentionally limited to the HuggingFace vocabulary
   * shape; it is not a general-purpose JSON parser.
   *
   * @param vocabFile The vocabulary file.
   * @return A map of vocabulary words to IDs.
   * @throws IOException Thrown if the vocabulary
   *     file cannot be opened or read.
   */
  public Map<String, Integer> loadVocab(
      final File vocabFile) throws IOException {

    return loadVocabFile(vocabFile);
  }

  static Map<String, Integer> loadVocabFile(
      final File vocabFile) throws IOException {

    final Path vocabPath =
        Path.of(vocabFile.getPath());
    final String content = Files.readString(
        vocabPath, StandardCharsets.UTF_8);
    final String trimmed = content.trim();

    // Detect JSON format by leading brace
    if (trimmed.startsWith("{")) {
      return loadJsonVocab(trimmed);
    }

    final Map<String, Integer> vocab =
        new HashMap<>();
    final AtomicInteger counter =
        new AtomicInteger(0);

    try (Stream<String> lines = Files.lines(
        vocabPath, StandardCharsets.UTF_8)) {
      lines.forEach(line ->
          vocab.put(line, counter.getAndIncrement())
      );
    }

    return vocab;
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
   * Creates a {@link BertTokenizer} that performs the full BERT tokenization
   * pipeline: basic tokenization (text normalization) followed by wordpiece.
   * The special tokens are selected based on the vocabulary: if it contains
   * RoBERTa-style tokens, those are used, otherwise the BERT defaults.
   *
   * @param vocab The vocabulary map.
   * @param lowerCase {@code true} for uncased models (lower casing and accent
   *     stripping), {@code false} for cased models.
   * @return A configured {@link BertTokenizer}.
   * @throws IllegalArgumentException Thrown if a RoBERTa-style vocabulary
   *     contains no supported unknown token.
   */
  protected BertTokenizer createTokenizer(
      final Map<String, Integer> vocab, final boolean lowerCase) {

    return createBertTokenizer(vocab, lowerCase);
  }

  static BertTokenizer createBertTokenizer(
      final Map<String, Integer> vocab, final boolean lowerCase) {
    if (vocab.containsKey(
            WordpieceTokenizer.ROBERTA_CLS_TOKEN)
        && vocab.containsKey(
            WordpieceTokenizer.ROBERTA_SEP_TOKEN)) {
      return new BertTokenizer(
          vocab.keySet(),
          lowerCase,
          WordpieceTokenizer.ROBERTA_CLS_TOKEN,
          WordpieceTokenizer.ROBERTA_SEP_TOKEN,
          resolveUnknownToken(vocab));
    }
    return new BertTokenizer(vocab.keySet(), lowerCase);
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
    requireNonNullArg(options, "options");
    return options.getLowerCase() != null ? options.getLowerCase() : componentDefault;
  }

  /**
   * Validates the document splitting options used by tokenizers that split long inputs.
   *
   * @param options The inference options to validate.
   * @throws IllegalArgumentException Thrown if the split settings cannot make progress.
   */
  protected static void validateSplitOptions(final InferenceOptions options) {
    requireNonNullArg(options, "options");
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
   * rather than the six ASCII characters Java's {@code \s} recognizes, and the same class is
   * reused by subclasses that need to match against whitespace in the source text.
   */
  protected static final CharClass WHITESPACE = CharClass.whitespace();

  /** Unicode dashes (excluding the mathematical minus signs), used for optional input folding. */
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
      result = compose(result, WHITESPACE.normalizeAligned(result.normalized()));
    }
    if (normalizeDashes) {
      result = compose(result, DASHES.normalizeAligned(result.normalized()));
    }
    return result;
  }

  // Threads a fold stage onto the running alignment: accumulated maps original -> current and next
  // maps current -> next.normalized(), so the composition maps original -> next.normalized().
  private static AlignedText compose(final AlignedText accumulated, final AlignedText next) {
    return new AlignedText(accumulated.original(), next.normalized(),
        accumulated.alignment().andThen(next.alignment()));
  }

  // An AlignedText whose alignment is the identity, for the case where no length-changing fold was
  // applied so the folded text has the same length and offsets as the original.
  private static AlignedText identityAligned(final String original, final String normalized) {
    final Alignment alignment =
        new Alignment.Builder().equal(normalized.length()).build(normalized.length());
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

  private static Map<String, Integer> loadJsonVocab(final String json) {

    final Map<String, Integer> vocab = new HashMap<>();
    final Matcher matcher = JSON_ENTRY_PATTERN.matcher(json);

    while (matcher.find()) {
      final String token = matcher.group(1)
          .transform(AbstractDL::unescapeJsonString);
      final int id = Integer.parseInt(matcher.group(2));
      vocab.put(token, id);
    }

    return vocab;
  }

  private static String unescapeJsonString(final String value) {
    final StringBuilder result = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      final char ch = value.charAt(i);
      if (ch != '\\') {
        result.append(ch);
        continue;
      }
      if (++i == value.length()) {
        throw new IllegalArgumentException("Invalid JSON string escape.");
      }
      final char escaped = value.charAt(i);
      switch (escaped) {
        case '"' -> result.append('"');
        case '\\' -> result.append('\\');
        case '/' -> result.append('/');
        case 'b' -> result.append('\b');
        case 'f' -> result.append('\f');
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 't' -> result.append('\t');
        case 'u' -> {
          if (i + 4 >= value.length()) {
            throw new IllegalArgumentException("Invalid JSON unicode escape.");
          }
          final String hex = value.substring(i + 1, i + 5);
          try {
            result.append((char) Integer.parseInt(hex, 16));
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid JSON unicode escape.", e);
          }
          i += 4;
        }
        default -> throw new IllegalArgumentException("Invalid JSON string escape.");
      }
    }
    return result.toString();
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

  // Null parameters report IllegalArgumentException rather than requireNonNull's
  // NullPointerException, matching the parameter contract of the engine and tokenizer layers.
  protected static void requireNonNullArg(Object value, String name) {
    if (value == null) {
      throw new IllegalArgumentException("The " + name + " must not be null.");
    }
  }
}
