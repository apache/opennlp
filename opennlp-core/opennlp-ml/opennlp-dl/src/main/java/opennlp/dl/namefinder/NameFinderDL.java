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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.dl.AbstractDL;
import opennlp.dl.InferenceOptions;
import opennlp.dl.Tokens;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.namefind.OffsetMappingNameFinder;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.WordpieceTokenizer;
import opennlp.tools.util.Span;
import opennlp.tools.util.normalizer.AlignedText;
import opennlp.tools.util.normalizer.Alignment;

/**
 * An implementation of {@link opennlp.tools.namefind.TokenNameFinder} that uses ONNX models.
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
 * @see opennlp.tools.namefind.TokenNameFinder
 * @see InferenceOptions
 */
@ThreadSafe
public class NameFinderDL extends AbstractDL implements OffsetMappingNameFinder {

  public static final String SEPARATOR = "[SEP]";
  public static final String CLS_TOKEN = "[CLS]";

  // Tokenizer-added markers (BERT and RoBERTa) that must never appear in a reconstructed span.
  private static final Set<String> SPECIAL_TOKENS = Set.of(
      CLS_TOKEN, SEPARATOR,
      WordpieceTokenizer.ROBERTA_CLS_TOKEN, WordpieceTokenizer.ROBERTA_SEP_TOKEN);

  /** Prefix used by BIO labels for the first token in an entity span. */
  public static final String PREFIX_BEGIN = "B-";

  /** Prefix used by BIO labels for continuation tokens in an entity span. */
  public static final String PREFIX_INSIDE = "I-";

  /** Tokens that attach directly to the preceding token when span text is reconstructed. */
  public static final Set<String> NO_SPACE_BEFORE_TOKENS =
      Set.of(".", ",", ":", ";", "!", "?", ")", "]", "}", "%", "'", "-", "/");

  /** Tokens after which the following token attaches directly when span text is reconstructed. */
  public static final Set<String> NO_SPACE_AFTER_TOKENS =
      Set.of("(", "[", "{", "$", "'", "-", "/");

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
  private final boolean normalizeWhitespace;
  private final boolean normalizeDashes;

  /**
   * Instantiates a {@link opennlp.tools.namefind.TokenNameFinder name finder} using ONNX models.
   * 
   * @param model The ONNX model file.
   * @param vocabulary The model file's vocabulary file.
   * @param ids2Labels The mapping of model output indices to BIO labels. This must be exhaustive
   *     over the model's output indices; a token whose predicted index is unmapped raises an
   *     {@link IllegalStateException} during {@link #find(String[])}.
   * @param sentenceDetector The {@link SentenceDetector} to be used.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   * @throws IllegalArgumentException Thrown if {@code inferenceOptions}, {@code ids2Labels}, or
   *     the sentence detector is {@code null}.
   */
  public NameFinderDL(File model, File vocabulary, Map<Integer, String> ids2Labels,
                      SentenceDetector sentenceDetector) throws IOException, OrtException {

    this(model, vocabulary, ids2Labels, new InferenceOptions(), sentenceDetector);

  }

  /**
   * Instantiates a {@link opennlp.tools.namefind.TokenNameFinder name finder} using ONNX models.
   *
   * @param model The ONNX model file.
   * @param vocabulary The model file's vocabulary file.
   * @param ids2Labels The mapping of model output indices to BIO labels. This must be exhaustive
   *     over the model's output indices; a token whose predicted index is unmapped raises an
   *     {@link IllegalStateException} during {@link #find(String[])}.
   * @param inferenceOptions {@link InferenceOptions} to control the inference.
   * @param sentenceDetector The {@link SentenceDetector} to be used.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   * @throws IllegalArgumentException Thrown if {@code inferenceOptions}, {@code ids2Labels}, or
   *     the sentence detector is {@code null}.
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
    this.normalizeWhitespace = inferenceOptions.isNormalizeWhitespace();
    this.normalizeDashes = inferenceOptions.isNormalizeDashes();
    this.sentenceDetector = sentenceDetector;

  }

  private static InferenceOptions validateConstructorArguments(
      final InferenceOptions inferenceOptions, final Map<Integer, String> ids2Labels,
      final SentenceDetector sentenceDetector) {
    requireNonNullArg(inferenceOptions, "inferenceOptions");
    requireNonNullArg(ids2Labels, "ids2Labels");
    requireNonNullArg(sentenceDetector, "sentenceDetector");
    return inferenceOptions;
  }


  /**
   * {@inheritDoc}
   *
   * <p>Joins the provided tokens with spaces, sentence-splits the joined text, runs each sentence
   * through the ONNX token-classification model, decodes BIO labels into {@link Span spans}, and
   * resolves those spans to character offsets in the joined text <em>after</em> any optional input
   * normalization.</p>
   *
   * <p>Note: this returns correct original offsets in every case except one. Whitespace folding is
   * length-preserving, so it never moves offsets. Only dash folding can change the input length, and
   * only for a non-BMP dash; so when {@code normalizeDashes} is enabled and the input contains a
   * supplementary-plane dash, the returned spans are offsets into the normalized text rather than
   * the original. For an exact original mapping in that case, use {@link #findInOriginal(String[])}.</p>
   *
   * @throws IllegalStateException Thrown if inference fails, if the model output shape is not
   *     the expected {@code float[batch][token][label]} form, if the model output contains
   *     no usable label score for a token, or if the model's predicted index for a token is not
   *     present in the configured label map.
   * @throws IllegalArgumentException Thrown if {@code input} is {@code null} or contains a
   *     {@code null} token, or if a token produced for the input is not present in the
   *     vocabulary, which indicates the vocabulary file does not match the model.
   */
  @Override
  public Span[] find(String[] input) {
    return locate(input).spans().toArray(new Span[0]);
  }

  /**
   * Finds names and returns their {@link Span spans} in coordinates of the original joined input
   * ({@code String.join(" ", input)}), regardless of any whitespace or dash normalization applied
   * before inference. Spans are mapped back through the normalization {@link Alignment}, so a fold
   * that changes the input length (a supplementary dash shrinking, or an expansion) does not shift
   * the reported offsets. This implements {@link OffsetMappingNameFinder}, so an interface-typed
   * caller can reach the offset-correct path with
   * {@code finder instanceof OffsetMappingNameFinder}.
   *
   * @param input The tokens to search.
   * @return The detected spans, in original-input character coordinates.
   * @throws IllegalStateException Thrown under the same conditions as {@link #find(String[])}.
   * @throws IllegalArgumentException Thrown under the same conditions as {@link #find(String[])}.
   */
  @Override
  public Span[] findInOriginal(String[] input) {
    final DecodedSpans decoded = locate(input);
    final Alignment alignment = decoded.aligned().alignment();
    final List<Span> mapped = new ArrayList<>(decoded.spans().size());
    for (final Span span : decoded.spans()) {
      final Span original = alignment.toOriginalSpan(span.getStart(), span.getEnd());
      mapped.add(new Span(original.getStart(), original.getEnd(), span.getType(), span.getProb()));
    }
    return mapped.toArray(new Span[0]);
  }

  // Shared core: normalize the joined input (capturing the alignment back to the original), then
  // decode each overlapping chunk bounded to its own character region and resolve overlaps. Bounding
  // per chunk lets a boundary entity that two consecutive chunks both cover surface as overlapping
  // candidates, which mergeOverlappingSpans collapses to the longer (more complete) span instead of
  // silently keeping whichever a single forward cursor reached first.
  private DecodedSpans locate(String[] input) {

    requireNonNullArg(input, "input");
    for (int i = 0; i < input.length; i++) {
      if (input[i] == null) {
        throw new IllegalArgumentException(
            "The input must not contain null tokens; the token at index " + i + " was null.");
      }
    }

    // Join the tokens here because they will be tokenized using Wordpiece during inference.
    final AlignedText normalized =
        normalizeInputAligned(String.join(" ", input), normalizeWhitespace, normalizeDashes);
    final String text = normalized.normalizedString();

    // sentPosDetect (not sentDetect) so each sentence's offset in the full text is known.
    final Span[] sentenceSpans = sentenceDetector.sentPosDetect(text);

    final List<Span> candidates = new ArrayList<>();
    for (final Span sentenceSpan : sentenceSpans) {

      final int sentenceStart = sentenceSpan.getStart();
      final String sentence = sentenceSpan.getCoveredText(text).toString();

      // The WordPiece tokenized text, in overlapping chunks. This changes the spacing in the text.
      for (final ChunkTokens chunk : tokenize(sentence)) {
        // Decode within the chunk's own character region in the full text. Keeping each chunk's
        // entities inside the region it was built from locates a repeated surface form in the right
        // chunk rather than mis-matching it to an earlier occurrence, while still letting two
        // overlapping chunks both emit a boundary entity for mergeOverlappingSpans to reconcile.
        final int regionStart = sentenceStart + chunk.start();
        final int regionEnd = sentenceStart + chunk.end();
        candidates.addAll(decodeSpans(text, chunk.tokens().tokens(), infer(chunk.tokens()),
            ids2Labels, regionStart, regionEnd));
      }

    }

    return new DecodedSpans(mergeOverlappingSpans(candidates), normalized);
  }

  private record DecodedSpans(List<Span> spans, AlignedText aligned) {
  }

  // A chunk's WordPiece tokens paired with the chunk's half-open character span in the full text.
  private record ChunkTokens(Tokens tokens, int start, int end) {
  }

  // Ordering for overlap resolution: longest span first, then higher probability. The dominant
  // detection is kept and any later span overlapping it is dropped.
  private static final Comparator<Span> BY_LENGTH_THEN_PROBABILITY =
      Comparator.comparingInt(Span::length).reversed()
          .thenComparing(Comparator.comparingDouble(Span::getProb).reversed());

  /**
   * Resolves spans that overlap in character coordinates, as happens when an entity falls in the
   * shared region of two consecutive overlapping chunks and is decoded by both. The longer span is
   * kept (the more complete decode) and ties break toward the higher probability; any span that
   * overlaps an already kept one is dropped. Adjacent but disjoint spans are never merged, so
   * neighbouring distinct entities and repeated surface forms at different offsets are preserved.
   * The returned list is in document order. The choice is length-dominant rather than type-aware:
   * when two overlapping spans carry different entity types, the longer still wins regardless of
   * type, which is the intended heuristic for the rare cross-type overlap at a chunk boundary.
   *
   * @param spans The decoded candidate spans, in the order they were produced.
   * @return The overlap-free spans, ordered by start offset.
   */
  static List<Span> mergeOverlappingSpans(final List<Span> spans) {
    if (spans.size() < 2) {
      // Return a fresh list so the caller always owns the result, matching the >= 2 path below
      // (which returns a new list); the input is never handed back aliased.
      return new ArrayList<>(spans);
    }
    final List<Span> byDominance = new ArrayList<>(spans);
    byDominance.sort(BY_LENGTH_THEN_PROBABILITY);
    // Kept spans never overlap each other, so they form a start-sorted partition. A candidate can
    // only intersect the kept span that starts at or just before it (floor) or the next kept span
    // that starts within it (ceiling); checking those two is O(log n) instead of scanning every kept
    // span, making the whole longest-wins pass O(n log n) rather than O(n^2). Keyed by start, the map
    // also yields the result already in document order.
    final TreeMap<Integer, Span> kept = new TreeMap<>();
    for (final Span candidate : byDominance) {
      final Map.Entry<Integer, Span> before = kept.floorEntry(candidate.getStart());
      if (before != null && before.getValue().getEnd() > candidate.getStart()) {
        continue;
      }
      final Map.Entry<Integer, Span> after = kept.ceilingEntry(candidate.getStart());
      if (after != null && after.getKey() < candidate.getEnd()) {
        continue;
      }
      kept.put(candidate.getStart(), candidate);
    }
    return new ArrayList<>(kept.values());
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

      final SpanMatch match = findInSource(text, spanText, characterStart, searchEnd);
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
   * @param scores The model scores for one token.
   * @param id2Labels The mapping from model output indexes to BIO labels.
   * @return The predicted label and its normalized probability.
   * @throws IllegalStateException Thrown if the model's argmax index is absent from
   *     {@code id2Labels}, which means the label map is not exhaustive over the model's output
   *     indices and the model/label-map pair is misconfigured.
   */
  private static LabelPrediction predictLabel(float[] scores, Map<Integer, String> id2Labels) {

    final int labelIndex = maxIndex(scores);
    final String label = id2Labels.get(labelIndex);
    if (label == null) {
      throw new IllegalStateException("Model output index " + labelIndex
          + " has no configured label; ids2Labels must map every model output index.");
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
   * <p>Special BERT and RoBERTa tokens are skipped, {@code ##} continuations are merged into the
   * preceding surface form, and simple punctuation spacing is normalized so the result can be
   * located in the caller's original text.</p>
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
      if (SPECIAL_TOKENS.contains(token)) {
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
    return NO_SPACE_BEFORE_TOKENS.contains(token);
  }

  private static boolean hasNoSpaceAfter(String token) {
    return NO_SPACE_AFTER_TOKENS.contains(token);
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
   * <p>Matching is a single forward cursor scan, not a regular expression. Each space in the
   * reconstructed span matches a run of zero or more Unicode whitespace characters in the source
   * (so an entity whose WordPiece pieces were rejoined with spaces, such as {@code "AT & T"} for
   * {@code "AT&T"}, is still located), and every other code point matches case-insensitively.
   * Using a cursor avoids {@link java.util.regex.Pattern}/{@link java.util.regex.Matcher}
   * allocation and the ReDoS surface of regular expressions, and recognizes Unicode whitespace
   * that Java's {@code \s} does not.</p>
   *
   * @param text The original text.
   * @param span The reconstructed span text, with sub-tokens separated by single ASCII spaces.
   * @param searchStart The first character offset to search from.
   * @param searchEnd The exclusive upper bound of the region to search.
   * @return The matched character offsets, or {@code (-1, -1)} when the reconstructed text
   *     cannot be found in the requested region.
   */
  private static SpanMatch findInSource(String text, String span, int searchStart, int searchEnd) {

    final int regionStart = Math.min(Math.max(searchStart, 0), text.length());
    final int regionEnd = Math.min(Math.max(searchEnd, regionStart), text.length());

    int start = regionStart;
    while (start < regionEnd) {
      final int end = matchAt(text, span, start, regionEnd);
      if (end != -1) {
        return new SpanMatch(start, end);
      }
      start += Character.charCount(text.codePointAt(start));
    }

    return new SpanMatch(-1, -1);

  }

  /**
   * Attempts to match {@code span} against {@code text} beginning at {@code start} and bounded by
   * {@code regionEnd}. A space in {@code span} consumes a run of zero or more Unicode whitespace
   * code points in the source; every other code point must match case-insensitively.
   *
   * @return The exclusive end offset of the match in {@code text}, or {@code -1} if no match
   *     begins at {@code start}.
   */
  private static int matchAt(String text, String span, int start, int regionEnd) {

    int t = start;
    int s = 0;

    while (s < span.length()) {
      final int spanCp = span.codePointAt(s);
      if (spanCp == ' ') {
        while (t < regionEnd && WHITESPACE.contains(text.codePointAt(t))) {
          t += Character.charCount(text.codePointAt(t));
        }
        s += 1;
      } else {
        if (t >= regionEnd) {
          return -1;
        }
        final int textCp = text.codePointAt(t);
        if (!equalsIgnoreCase(spanCp, textCp)) {
          return -1;
        }
        t += Character.charCount(textCp);
        s += Character.charCount(spanCp);
      }
    }

    return t;

  }

  private static boolean equalsIgnoreCase(int a, int b) {
    return a == b
        || Character.toLowerCase(a) == Character.toLowerCase(b)
        || Character.toUpperCase(a) == Character.toUpperCase(b);
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

  private List<ChunkTokens> tokenize(final String text) {

    final List<ChunkTokens> t = new LinkedList<>();

    // Segment long input text into overlapping chunks (split on Unicode whitespace) configured by
    // InferenceOptions before feeding each chunk into BERT, keeping each chunk's character span so
    // its decoded spans can be bounded to the region the chunk covers.
    // https://medium.com/analytics-vidhya/text-classification-with-bert-using-transformers-for-long-text-inputs-f54833994dfd
    for (final TextChunk chunk : whitespaceChunkSpans(text, documentSplitSize, splitOverlapSize)) {

      // Now we can tokenize the group and continue.
      final String[] tokens = tokenizer.tokenize(chunk.text());

      final long[] ids = tokenIds(tokens, vocab);

      final long[] mask = new long[ids.length];
      Arrays.fill(mask, 1);

      final long[] types = new long[ids.length];
      Arrays.fill(types, 0);

      t.add(new ChunkTokens(new Tokens(tokens, ids, mask, types), chunk.start(), chunk.end()));

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
