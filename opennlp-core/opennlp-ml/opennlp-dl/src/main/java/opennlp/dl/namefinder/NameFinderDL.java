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

package opennlp.dl.namefinder;

import java.io.File;
import java.io.IOException;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.dl.AbstractDL;
import opennlp.dl.InferenceOptions;
import opennlp.dl.Tokens;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.util.Span;

/**
 * An implementation of {@link TokenNameFinder} that uses ONNX models.
 *
 * <p>Tokenization performs BERT basic tokenization (text normalization)
 * before wordpiece, see {@link opennlp.tools.tokenize.BertTokenizer}. Input
 * text is <b>not</b> lower cased by default, because named entity recognition
 * models are commonly cased: capitalization is a strong signal for entity
 * boundaries. For uncased models, set
 * {@link InferenceOptions#setLowerCase(boolean)} to {@code true}.</p>
 *
 * <p>This class is thread-safe and may be shared across threads, provided the supplied
 * {@link SentenceDetector} is itself thread-safe (e.g. {@link opennlp.tools.sentdetect.SentenceDetectorME},
 * which is {@code @ThreadSafe}). Inference holds no per-call instance state, the relevant
 * {@link InferenceOptions} values are snapshotted into final fields at construction (so
 * mutating the passed options afterwards does not affect a shared instance), and the
 * underlying {@link OrtSession} supports concurrent execution. This thread-safety
 * guarantee applies until {@link #close()} is called; callers must not race
 * {@code close()} with inference methods.</p>
 *
 * @see TokenNameFinder
 * @see InferenceOptions
 */
@ThreadSafe
public class NameFinderDL extends AbstractDL implements TokenNameFinder {

  /** Example person labels; retained for reference. Decoding handles any B-/I- type. */
  public static final String I_PER = "I-PER";
  public static final String B_PER = "B-PER";
  public static final String SEPARATOR = "[SEP]";
  public static final String CLS_TOKEN = "[CLS]";

  /** Prefix used by BIO labels for the first token in an entity span. */
  public static final String PREFIX_BEGIN = "B-";

  /** Prefix used by BIO labels for continuation tokens in an entity span. */
  public static final String PREFIX_INSIDE = "I-";

  /** Tokens that attach directly to the preceding token when span text is reconstructed. */
  public static final String[] NO_SPACE_BEFORE_TOKENS =
      {".", ",", ":", ";", "!", "?", ")", "]", "}", "%", "'", "-", "/"};

  /** Tokens after which the following token attaches directly when span text is reconstructed. */
  public static final String[] NO_SPACE_AFTER_TOKENS =
      {"(", "[", "{", "$", "'", "-", "/"};

  /** NER models are commonly cased, so lower casing is off by default. */
  private static final boolean LOWER_CASE_DEFAULT = false;

  private static final String CHARS_TO_REPLACE = "##";
  private static final Logger logger = LoggerFactory.getLogger(NameFinderDL.class);

  private final SentenceDetector sentenceDetector;
  private final Map<Integer, String> ids2Labels;
  // Inference options are snapshotted into final fields at construction so a shared
  // instance never reads the caller's mutable InferenceOptions during inference.
  private final boolean includeAttentionMask;
  private final boolean includeTokenTypeIds;
  private final int documentSplitSize;
  private final int splitOverlapSize;

  /**
   * Instantiates a {@link TokenNameFinder name finder} using ONNX models.
   * 
   * @param model The ONNX model file.
   * @param vocabulary The model file's vocabulary file.
   * @param ids2Labels The mapping of ids to labels.
   * @param sentenceDetector The {@link SentenceDetector} to be used.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   */
  public NameFinderDL(File model, File vocabulary, Map<Integer, String> ids2Labels,
                      SentenceDetector sentenceDetector) throws IOException, OrtException {

    this(model, vocabulary, ids2Labels, new InferenceOptions(), sentenceDetector);

  }

  /**
   * Instantiates a {@link TokenNameFinder name finder} using ONNX models.
   *
   * @param model The ONNX model file.
   * @param vocabulary The model file's vocabulary file.
   * @param ids2Labels The mapping of ids to labels.
   * @param inferenceOptions {@link InferenceOptions} to control the inference.
   * @param sentenceDetector The {@link SentenceDetector} to be used.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   */
  public NameFinderDL(File model, File vocabulary, Map<Integer, String> ids2Labels,
                      InferenceOptions inferenceOptions,
                      SentenceDetector sentenceDetector) throws IOException, OrtException {

    super(model, vocabulary,
        sessionOptions(validateConstructorArguments(
            inferenceOptions, ids2Labels, sentenceDetector)),
        resolveLowerCase(inferenceOptions, LOWER_CASE_DEFAULT));

    this.ids2Labels = Map.copyOf(ids2Labels);
    this.includeAttentionMask = inferenceOptions.isIncludeAttentionMask();
    this.includeTokenTypeIds = inferenceOptions.isIncludeTokenTypeIds();
    this.documentSplitSize = inferenceOptions.getDocumentSplitSize();
    this.splitOverlapSize = inferenceOptions.getSplitOverlapSize();
    this.sentenceDetector = sentenceDetector;

  }

  private static InferenceOptions validateConstructorArguments(
      final InferenceOptions inferenceOptions, final Map<Integer, String> ids2Labels,
      final SentenceDetector sentenceDetector) {
    Objects.requireNonNull(ids2Labels, "ids2Labels");
    Objects.requireNonNull(sentenceDetector, "sentenceDetector");
    return inferenceOptions;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method joins the provided tokens with spaces, sentence-splits the joined text,
   * runs each sentence through the ONNX token-classification model, decodes BIO labels into
   * {@link Span spans}, and resolves those spans back to character offsets in the joined text.</p>
   *
   * @throws IllegalStateException Thrown if inference fails, if the model output shape is not
   *     the expected {@code float[batch][token][label]} form, or if the model output contains
   *     no usable label score for a token.
   */
  @Override
  public Span[] find(String[] input) {

    final List<Span> spans = new ArrayList<>();

    // Join the tokens here because they will be tokenized using Wordpiece during inference.
    final String text = String.join(" ", input);

    // sentPosDetect (not sentDetect) so each sentence's offset in the full text is known.
    final Span[] sentenceSpans = sentenceDetector.sentPosDetect(text);

    for (final Span sentenceSpan : sentenceSpans) {

      // Floor the character cursor at this sentence's start, then thread it forward across the
      // sentence's chunks so a repeated surface form is located at its next occurrence. Flooring
      // per sentence keeps an entity from being matched against an identical surface form in an
      // earlier sentence -- even one that produced no spans, which would otherwise leave the
      // cursor behind and mis-locate the match.
      int searchStart = sentenceSpan.getStart();

      // The WordPiece tokenized text. This changes the spacing in the text.
      final List<Tokens> wordpieceTokens = tokenize(sentenceSpan.getCoveredText(text).toString());

      for (final Tokens tokens : wordpieceTokens) {
        final List<Span> decoded =
            decodeSpans(text, tokens.tokens(), infer(tokens), ids2Labels, searchStart,
                sentenceSpan.getEnd());
        spans.addAll(decoded);
        if (!decoded.isEmpty()) {
          searchStart = decoded.get(decoded.size() - 1).getEnd();
        }
      }

    }

    return spans.toArray(new Span[0]);

  }

  /**
   * Runs the model on one token window and returns the per-token label score rows. A failure
   * executing the model (an {@link OrtException} or any runtime fault) is surfaced as an
   * {@link IllegalStateException} (cause preserved); an unexpected output shape is its own loud
   * failure. This mirrors the fail-loud contract of the sibling {@code DocumentCategorizerDL}.
   *
   * @param tokens The tokens for one chunk to run inference on.
   * @return The {@code [token][label]} score matrix for the chunk.
   */
  private float[][] infer(final Tokens tokens) {

    final Map<String, OnnxTensor> inputs = new HashMap<>();
    final Object output;
    try {
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.ids()),
          new long[] {1, tokens.ids().length}));

      if (includeAttentionMask) {
        inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env,
            LongBuffer.wrap(tokens.mask()), new long[] {1, tokens.mask().length}));
      }

      if (includeTokenTypeIds) {
        inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env,
            LongBuffer.wrap(tokens.types()), new long[] {1, tokens.types().length}));
      }

      // getValue() copies the tensor into Java arrays, so the result can be closed safely.
      try (OrtSession.Result result = session.run(inputs)) {
        output = result.get(0).getValue();
      }
    } catch (OrtException ex) {
      throw new IllegalStateException(
          "Unable to perform name finder inference: " + ex.getMessage(), ex);
    } catch (RuntimeException ex) {
      throw new IllegalStateException(
          "Unexpected runtime failure during name finder inference: " + ex.getMessage(), ex);
    } finally {
      inputs.values().forEach(OnnxTensor::close);
    }

    // The model returns one score row per token, batched: float[batch][token][label]. Any other
    // shape (or an empty batch) is a model-contract violation, surfaced on its own rather than as
    // "inference failed".
    if (output instanceof float[][][] v) {
      if (v.length == 0) {
        throw new IllegalStateException("Model output batch must contain at least one entry.");
      }
      return v[0];
    }
    throw new IllegalStateException("Unexpected model output type: "
        + (output == null ? "null" : output.getClass().getName()));
  }

  @Override
  public void clearAdaptiveData() {
    // No use in this implementation.
  }

  /**
   * Decodes {@link Span spans} beginning the character search at the start of {@code text}. Equivalent to
   * {@link #decodeSpans(String, String[], float[][], Map, int)} with {@code searchStart == 0}.
   *
   * @param text The original text passed to the model.
   * @param tokens The WordPiece tokens produced for the text.
   * @param tokenLabelScores The per-token label scores returned by the model.
   * @param id2Labels The mapping from model output indexes to BIO labels.
   * @return The decoded {@link Span spans}.
   */
  static List<Span> decodeSpans(String text, String[] tokens, float[][] tokenLabelScores,
                                Map<Integer, String> id2Labels) {
    return decodeSpans(text, tokens, tokenLabelScores, id2Labels, 0);
  }

  /**
   * Converts model token classifications into character {@link Span spans} in the original input text.
   *
   * <p>The ONNX model returns one score vector for each WordPiece token. This method applies
   * BIO decoding, reconstructs WordPiece fragments, and then resolves the reconstructed text
   * against the original sentence so that {@link Span#getCoveredText(CharSequence)} works with
   * the caller's input.</p>
   *
   * @param text The original text passed to the model.
   * @param tokens The WordPiece tokens produced for the text.
   * @param tokenLabelScores The per-token label scores returned by the model.
   * @param id2Labels The mapping from model output indexes to BIO labels.
   * @param searchStart The character offset in {@code text} to begin locating spans from. Threading
   *     a monotonic cursor across the chunks and sentences of a single {@link #find(String[])} call
   *     keeps a repeated entity surface form from being emitted twice at the same first occurrence.
   * @return The decoded {@link Span spans}.
   */
  static List<Span> decodeSpans(String text, String[] tokens, float[][] tokenLabelScores,
                                Map<Integer, String> id2Labels, int searchStart) {
    return decodeSpans(text, tokens, tokenLabelScores, id2Labels, searchStart, text.length());
  }

  /**
   * Converts model token classifications into character {@link Span spans} within a bounded
   * region of the original input text.
   *
   * @param text The original text passed to the model.
   * @param tokens The WordPiece tokens produced for the text.
   * @param tokenLabelScores The per-token label scores returned by the model.
   * @param id2Labels The mapping from model output indexes to BIO labels.
   * @param searchStart The first character offset in {@code text} to search.
   * @param searchEnd The exclusive upper bound for locating reconstructed spans. During
   *     {@link #find(String[])}, this is the current sentence end so an entity from one sentence
   *     cannot be resolved to an identical surface form in a later sentence.
   * @return The decoded {@link Span spans}.
   */
  static List<Span> decodeSpans(String text, String[] tokens, float[][] tokenLabelScores,
                                Map<Integer, String> id2Labels, int searchStart, int searchEnd) {

    if (tokens.length != tokenLabelScores.length) {
      throw new IllegalArgumentException("The number of tokens (" + tokens.length
          + ") must match the number of model output rows (" + tokenLabelScores.length + ").");
    }

    final List<Span> spans = new ArrayList<>();

    int characterStart = searchStart;

    for (int x = 0; x < tokenLabelScores.length; x++) {
      final LabelPrediction prediction = predictLabel(tokenLabelScores[x], id2Labels);
      if (!isBeginLabel(prediction.label())) {
        continue;
      }

      final String entityType = prediction.label().substring(PREFIX_BEGIN.length());
      final EntityPrediction entity = findEntityEnd(tokenLabelScores, x, id2Labels,
          entityType, prediction.probability());
      final String spanText = buildSpanText(tokens, x, entity.endIndex());

      if (spanText.isBlank()) {
        x = entity.endIndex();
        continue;
      }

      final SpanMatch match = findByRegex(text, spanText, characterStart, searchEnd);
      if (match.start() != -1) {
        spans.add(new Span(match.start(), match.end(), entityType, entity.probability()));
        characterStart = match.end();
      } else {
        logger.debug("Unable to locate decoded {} span '{}' in source text region [{}, {}).",
            entityType, spanText, characterStart, searchEnd);
      }

      x = entity.endIndex();
    }

    return spans;

  }

  /**
   * Finds the final token index and confidence for one BIO entity that starts at {@code startIndex}.
   *
   * <p>The span continues while subsequent predictions are {@code I-<same type>}. The returned
   * probability is the minimum token probability across the entity, so a multi-token span reflects
   * its weakest continuation.</p>
   *
   * @param tokenLabelScores The per-token label scores returned by the model.
   * @param startIndex The token index where the entity begins.
   * @param id2Labels The mapping from model output indexes to BIO labels.
   * @param entityType The entity type without its BIO prefix, for example {@code PER}.
   * @param startProbability The normalized probability of the begin label.
   * @return The last token index and probability for the entity.
   */
  private static EntityPrediction findEntityEnd(float[][] tokenLabelScores, int startIndex,
                                                Map<Integer, String> id2Labels,
                                                String entityType,
                                                double startProbability) {

    final String insideLabel = PREFIX_INSIDE + entityType;
    int endIndex = startIndex;
    double probability = startProbability;

    for (int x = startIndex + 1; x < tokenLabelScores.length; x++) {
      final LabelPrediction prediction = predictLabel(tokenLabelScores[x], id2Labels);
      if (!insideLabel.equals(prediction.label())) {
        break;
      }
      endIndex = x;
      probability = Math.min(probability, prediction.probability());
    }

    return new EntityPrediction(endIndex, probability);

  }

  /**
   * Returns whether a label is a well-formed BIO begin label.
   *
   * @param label The label to inspect.
   * @return {@code true} for {@code B-<TYPE>} labels with a non-empty type.
   */
  private static boolean isBeginLabel(String label) {
    return label.startsWith(PREFIX_BEGIN) && label.length() > PREFIX_BEGIN.length();
  }

  /**
   * Picks the predicted BIO label for one token.
   *
   * <p>If the model's argmax index is absent from {@code id2Labels}, the token is treated as
   * outside ({@code O}). This preserves the previous graceful behavior for partial label maps:
   * one unmapped output row does not discard the whole {@link #find(String[])} result.</p>
   *
   * @param scores The model scores for one token.
   * @param id2Labels The mapping from model output indexes to BIO labels.
   * @return The predicted label and its normalized probability.
   */
  private static LabelPrediction predictLabel(float[] scores, Map<Integer, String> id2Labels) {

    final int labelIndex = maxIndex(scores);
    final String label = id2Labels.get(labelIndex);
    if (label == null) {
      return new LabelPrediction("O", 0d);
    }

    return new LabelPrediction(label, labelProbability(scores, labelIndex));

  }

  /**
   * Normalizes model scores into a probability for one label index using a numerically stable
   * softmax.
   *
   * @param scores The raw model scores for one token.
   * @param labelIndex The label index whose probability should be returned.
   * @return The normalized probability in {@code [0, 1]}.
   */
  static double labelProbability(float[] scores, int labelIndex) {

    int positiveInfinityCount = 0;
    double max = Float.NEGATIVE_INFINITY;

    for (float score : scores) {
      if (score == Float.POSITIVE_INFINITY) {
        positiveInfinityCount++;
      } else if (!Float.isNaN(score) && score > max) {
        max = score;
      }
    }

    if (positiveInfinityCount > 0) {
      // From decodeSpans, labelIndex is always the argmax, so when any +Inf is present the chosen
      // score is +Inf and this returns 1/(number of +Inf). The 0d arm covers a direct caller
      // asking for a non-+Inf label's probability while a +Inf label exists (exercised by tests).
      return scores[labelIndex] == Float.POSITIVE_INFINITY ? 1d / positiveInfinityCount : 0d;
    }

    if (max == Float.NEGATIVE_INFINITY) {
      return 1d / scores.length;
    }

    double denominator = 0;
    for (float score : scores) {
      if (!Float.isNaN(score)) {
        denominator += Math.exp(score - max);
      }
    }

    return Math.exp(scores[labelIndex] - max) / denominator;

  }

  /**
   * Reconstructs source-like text from a span of WordPiece tokens.
   *
   * <p>Special BERT tokens are skipped, {@code ##} continuations are merged into the preceding
   * surface form, and simple punctuation spacing is normalized so the result can be located in
   * the caller's original text.</p>
   *
   * @param tokens The WordPiece token sequence.
   * @param startIndex The first token index to include.
   * @param endIndex The last token index to include.
   * @return The reconstructed span text.
   */
  static String buildSpanText(String[] tokens, int startIndex, int endIndex) {

    final StringBuilder span = new StringBuilder();
    String previousToken = null;

    for (int x = startIndex; x <= endIndex && x < tokens.length; x++) {
      final String token = tokens[x];
      if (CLS_TOKEN.equals(token) || SEPARATOR.equals(token)) {
        continue;
      }

      final boolean subword = token.startsWith(CHARS_TO_REPLACE);
      final String surface = subword ? token.substring(CHARS_TO_REPLACE.length()) : token;
      if (surface.isEmpty()) {
        continue;
      }

      if (span.length() > 0 && !subword && shouldInsertSpace(previousToken, surface)) {
        span.append(' ');
      }
      span.append(surface);
      previousToken = surface;
    }

    return span.toString();

  }

  private static boolean shouldInsertSpace(String previousToken, String token) {
    return previousToken != null && !hasNoSpaceBefore(token) && !hasNoSpaceAfter(previousToken);
  }

  private static boolean hasNoSpaceBefore(String token) {
    return containsToken(NO_SPACE_BEFORE_TOKENS, token);
  }

  private static boolean hasNoSpaceAfter(String token) {
    return containsToken(NO_SPACE_AFTER_TOKENS, token);
  }

  private static boolean containsToken(String[] tokens, String token) {
    for (String candidate : tokens) {
      if (candidate.equals(token)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the index of the largest non-NaN score.
   *
   * @param arr The score array to inspect.
   * @return The index of the maximum non-NaN value.
   * @throws IllegalStateException Thrown if the model output contains no non-NaN score.
   */
  private static int maxIndex(float[] arr) {

    double max = Float.NEGATIVE_INFINITY;
    int index = -1;

    for (int x = 0; x < arr.length; x++) {
      if (!Float.isNaN(arr[x]) && (index == -1 || arr[x] > max)) {
        index = x;
        max = arr[x];
      }
    }

    if (index == -1) {
      throw new IllegalStateException(
          "Model output scores must contain at least one non-NaN value.");
    }

    return index;

  }

  /**
   * Locates reconstructed span text in a bounded region of the original input text.
   *
   * @param text The original text.
   * @param span The reconstructed span text.
   * @param searchStart The first character offset to search from.
   * @param searchEnd The exclusive upper bound of the region to search.
   * @return The matched character offsets, or {@code (-1, -1)} when the reconstructed text
   *     cannot be found in the requested region.
   */
  private static SpanMatch findByRegex(String text, String span, int searchStart, int searchEnd) {

    // Reconstructed span text normalizes whitespace, so match flexibly: a space in the span may
    // map to any run of whitespace OR none in the source (e.g. punctuation/'&' inside "U.S.A",
    // "AT&T" that wordpiece tokenization split apart). Use \s* rather than \s+ so such entities
    // are still located instead of being silently dropped.
    final String regex = Pattern.quote(span).replace(" ", "\\E\\s*\\Q");

    final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    final Matcher matcher = pattern.matcher(text);
    final int regionStart = Math.min(Math.max(searchStart, 0), text.length());
    final int regionEnd = Math.min(Math.max(searchEnd, regionStart), text.length());
    matcher.region(regionStart, regionEnd);

    if (matcher.find()) {
      return new SpanMatch(matcher.start(), matcher.end());
    }

    return new SpanMatch(-1, -1);

  }

  private record LabelPrediction(String label, double probability) {
  }

  private record EntityPrediction(int endIndex, double probability) {
  }

  /**
   * Character offsets for a matched span. {@code (-1, -1)} means the reconstructed entity text
   * could not be located in the searched source-text region.
   */
  private record SpanMatch(int start, int end) {
  }

  private List<Tokens> tokenize(final String text) {

    final List<Tokens> t = new LinkedList<>();

    // Segment long input text into overlapping chunks configured by InferenceOptions before
    // feeding each chunk into BERT.
    // https://medium.com/analytics-vidhya/text-classification-with-bert-using-transformers-for-long-text-inputs-f54833994dfd
    final String[] whitespaceTokenized = text.split("\\s+");

    for (ChunkRange chunkRange : chunkRanges(
        whitespaceTokenized.length, documentSplitSize, splitOverlapSize)) {

      // The group is that subsection of string.
      final String group = String.join(" ",
          Arrays.copyOfRange(whitespaceTokenized, chunkRange.start(), chunkRange.end()));

      // Now we can tokenize the group and continue.
      final String[] tokens = tokenizer.tokenize(group);

      final long[] ids = tokenIds(tokens, vocab);

      final long[] mask = new long[ids.length];
      Arrays.fill(mask, 1);

      final long[] types = new long[ids.length];
      Arrays.fill(types, 0);

      t.add(new Tokens(tokens, ids, mask, types));

    }

    return t;

  }

  /**
   * Maps tokens to their vocabulary ids.
   *
   * @param tokens The tokens to map.
   * @param vocab The vocabulary map.
   * @return The token ids.
   *
   * @throws IllegalArgumentException Thrown if a token is not present in the
   *     vocabulary.
   */
  static long[] tokenIds(final String[] tokens, final Map<String, Integer> vocab) {

    final long[] ids = new long[tokens.length];

    for (int x = 0; x < tokens.length; x++) {
      final Integer id = vocab.get(tokens[x]);
      if (id == null) {
        throw new IllegalArgumentException("Token '" + tokens[x]
            + "' is not present in the vocabulary; the vocabulary file does not match the model.");
      }
      ids[x] = id;
    }

    return ids;

  }

}
