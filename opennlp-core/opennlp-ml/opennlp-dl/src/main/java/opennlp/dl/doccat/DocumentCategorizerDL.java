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

package opennlp.dl.doccat;

import java.io.File;
import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.dl.AbstractDL;
import opennlp.dl.InferenceOptions;
import opennlp.dl.Tokens;
import opennlp.dl.doccat.scoring.ClassificationScoringStrategy;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.doccat.DocumentCategorizer;


/**
 * An implementation of {@link DocumentCategorizer} that performs document classification
 * using ONNX models.
 *
 * <p>Tokenization performs BERT basic tokenization (text normalization)
 * before wordpiece, see {@link opennlp.tools.tokenize.BertTokenizer}. Input
 * text is lower cased and accent stripped by default, matching the uncased
 * models commonly used for classification. For cased models, set
 * {@link InferenceOptions#setLowerCase(boolean)} to {@code false}.</p>
 *
 * <p>This class is thread-safe and may be shared across threads, provided the supplied
 * {@link ClassificationScoringStrategy} is thread-safe (the built-in
 * {@link opennlp.dl.doccat.scoring.AverageClassificationScoringStrategy} is stateless).
 * Inference holds no per-call instance state, the relevant {@link InferenceOptions} values
 * are snapshotted into final fields at construction (so mutating the passed options
 * afterwards does not affect a shared instance), and the underlying {@link OrtSession}
 * supports concurrent execution. This thread-safety guarantee applies until
 * {@link #close()} is called; callers must not race {@code close()} with inference
 * methods.</p>
 *
 * @see DocumentCategorizer
 * @see InferenceOptions
 * @see ClassificationScoringStrategy
 */
@ThreadSafe
public class DocumentCategorizerDL extends AbstractDL implements DocumentCategorizer {

  /** Classification models are commonly uncased, so lower casing is the default. */
  private static final boolean LOWER_CASE_DEFAULT = true;

  private final Map<Integer, String> categories;
  private final ClassificationScoringStrategy classificationScoringStrategy;
  // Inference options are snapshotted into final fields at construction so a shared
  // instance never reads the caller's mutable InferenceOptions during inference.
  private final boolean includeAttentionMask;
  private final boolean includeTokenTypeIds;
  private final int documentSplitSize;
  private final int splitOverlapSize;

  /**
   * Test-only constructor that injects an already-built {@link OrtSession} (or {@code null}),
   * bypassing model loading so inference paths can be exercised in unit tests.
   */
  DocumentCategorizerDL(OrtEnvironment env, OrtSession session, Map<String, Integer> vocab,
                        Map<Integer, String> categories,
                        ClassificationScoringStrategy classificationScoringStrategy,
                        InferenceOptions inferenceOptions) {
    super(env, session, vocab, resolveLowerCase(inferenceOptions, LOWER_CASE_DEFAULT));
    this.categories = Map.copyOf(categories);
    this.classificationScoringStrategy = classificationScoringStrategy;
    this.includeAttentionMask = inferenceOptions.isIncludeAttentionMask();
    this.includeTokenTypeIds = inferenceOptions.isIncludeTokenTypeIds();
    this.documentSplitSize = inferenceOptions.getDocumentSplitSize();
    this.splitOverlapSize = inferenceOptions.getSplitOverlapSize();
  }

  /**
   * Instantiates a {@link DocumentCategorizer document categorizer} using ONNX models.
   *
   * @param model                         The ONNX model file.
   * @param vocabulary                    The model file's vocabulary file.
   * @param categories                    The categories.
   * @param classificationScoringStrategy Implementation of {@link ClassificationScoringStrategy} used
   *                                      to calculate the classification scores given the score of each
   *                                      individual document part.
   * @param inferenceOptions              {@link InferenceOptions} to control the inference.
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException  Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   */
  public DocumentCategorizerDL(File model, File vocabulary, Map<Integer, String> categories,
                               ClassificationScoringStrategy classificationScoringStrategy,
                               InferenceOptions inferenceOptions)
      throws IOException, OrtException {

    super(model, vocabulary,
        sessionOptions(validateConstructorArguments(
            inferenceOptions, categories, classificationScoringStrategy)),
        resolveLowerCase(inferenceOptions, LOWER_CASE_DEFAULT));

    this.categories = Map.copyOf(categories);
    this.classificationScoringStrategy = classificationScoringStrategy;
    this.includeAttentionMask = inferenceOptions.isIncludeAttentionMask();
    this.includeTokenTypeIds = inferenceOptions.isIncludeTokenTypeIds();
    this.documentSplitSize = inferenceOptions.getDocumentSplitSize();
    this.splitOverlapSize = inferenceOptions.getSplitOverlapSize();

  }

  /**
   * Instantiates a {@link DocumentCategorizer document categorizer} using ONNX models.
   *
   * @param model                         The ONNX model file.
   * @param vocabulary                    The model file's vocabulary file.
   * @param config                        The model's config file. The file will be used to
   *                                      determine the classification categories.
   * @param classificationScoringStrategy Implementation of {@link ClassificationScoringStrategy} used
   *                                      to calculate the classification scores given the score of each
   *                                      individual document part.
   * @param inferenceOptions              {@link InferenceOptions} to control the inference.
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException  Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   */
  public DocumentCategorizerDL(File model, File vocabulary, File config,
                               ClassificationScoringStrategy classificationScoringStrategy,
                               InferenceOptions inferenceOptions)
      throws IOException, OrtException {

    super(model, vocabulary,
        sessionOptions(validateConstructorArguments(
            inferenceOptions, config, classificationScoringStrategy)),
        resolveLowerCase(inferenceOptions, LOWER_CASE_DEFAULT));

    this.categories = Map.copyOf(readCategoriesFromFile(config));
    this.classificationScoringStrategy = classificationScoringStrategy;
    this.includeAttentionMask = inferenceOptions.isIncludeAttentionMask();
    this.includeTokenTypeIds = inferenceOptions.isIncludeTokenTypeIds();
    this.documentSplitSize = inferenceOptions.getDocumentSplitSize();
    this.splitOverlapSize = inferenceOptions.getSplitOverlapSize();

  }

  private static InferenceOptions validateConstructorArguments(
      final InferenceOptions inferenceOptions, final Object categoriesOrConfig,
      final ClassificationScoringStrategy classificationScoringStrategy) {
    Objects.requireNonNull(categoriesOrConfig, "categoriesOrConfig");
    Objects.requireNonNull(classificationScoringStrategy, "classificationScoringStrategy");
    return inferenceOptions;
  }

  /**
   * Categorizes the document, failing loudly rather than returning an invalid distribution:
   * malformed input is rejected with {@link IllegalArgumentException}, and any failure executing
   * the model is surfaced as an {@link IllegalStateException} (cause preserved).
   *
   * @param strings The document to categorize; {@code strings[0]} is classified.
   * @return The per-category probabilities.
   * @throws IllegalArgumentException If {@code strings} is {@code null} or empty.
   * @throws IllegalStateException    If inference fails or the model returns an unexpected output.
   */
  @Override
  public double[] categorize(String[] strings) {

    if (strings == null || strings.length == 0) {
      throw new IllegalArgumentException("strings must contain at least one document to categorize");
    }

    final List<Tokens> tokens = tokenize(strings[0]);

    final List<double[]> scores = new LinkedList<>();
    for (final Tokens t : tokens) {
      scores.add(softmax(infer(t)));
    }

    return classificationScoringStrategy.score(scores);
  }

  /**
   * Runs the model on one token window and returns its raw per-category logits. A failure executing
   * the model (an {@link OrtException} or any runtime fault) is wrapped as an
   * {@link IllegalStateException}; an unexpected output shape is its own loud failure.
   */
  private float[] infer(final Tokens t) {

    final Map<String, OnnxTensor> inputs = new HashMap<>();
    final Object output;
    try {
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(env,
          LongBuffer.wrap(t.ids()), new long[] {1, t.ids().length}));

      if (includeAttentionMask) {
        inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env,
            LongBuffer.wrap(t.mask()), new long[] {1, t.mask().length}));
      }

      if (includeTokenTypeIds) {
        inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env,
            LongBuffer.wrap(t.types()), new long[] {1, t.types().length}));
      }

      // getValue() copies the tensor into Java arrays, so the result can be closed safely.
      try (OrtSession.Result result = session.run(inputs)) {
        output = result.get(0).getValue();
      }
    } catch (OrtException | RuntimeException ex) {
      throw new IllegalStateException("Unable to perform document classification inference", ex);
    } finally {
      inputs.values().forEach(OnnxTensor::close);
    }

    // Some models return a 2D array (e.g. BERT), others a 1D array (e.g. RoBERTa). A different
    // shape is a model-contract violation, surfaced on its own rather than as "inference failed".
    if (output instanceof float[][] v) {
      return v[0];
    } else if (output instanceof float[] v) {
      return v;
    }
    throw new IllegalStateException("Unexpected model output type: " + output.getClass().getName());
  }

  @Override
  public double[] categorize(String[] strings, Map<String, Object> map) {
    return categorize(strings);
  }

  @Override
  public String getBestCategory(double[] doubles) {
    return categories.get(maxIndex(doubles));
  }

  @Override
  public int getIndex(String s) {
    return getKey(s);
  }

  @Override
  public String getCategory(int i) {
    return categories.get(i);
  }

  @Override
  public int getNumberOfCategories() {
    return categories.size();
  }

  @Override
  public String getAllResults(double[] doubles) {
    return null;
  }

  @Override
  public Map<String, Double> scoreMap(String[] strings) {

    final double[] scores = categorize(strings);

    final Map<String, Double> scoreMap = new HashMap<>();

    for (int x : categories.keySet()) {
      scoreMap.put(categories.get(x), scores[x]);
    }

    return scoreMap;

  }

  @Override
  public SortedMap<Double, Set<String>> sortedScoreMap(String[] strings) {

    final double[] scores = categorize(strings);

    final SortedMap<Double, Set<String>> scoreMap = new TreeMap<>();

    for (int x : categories.keySet()) {

      if (scoreMap.get(scores[x]) == null) {
        scoreMap.put(scores[x], new HashSet<>());
      }

      scoreMap.get(scores[x]).add(categories.get(x));

    }

    return scoreMap;

  }

  private int getKey(String value) {

    for (Map.Entry<Integer, String> entry : categories.entrySet()) {

      if (entry.getValue().equals(value)) {
        return entry.getKey();
      }

    }

    // The String wasn't found as a value in the map.
    return -1;

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

  /**
   * Applies softmax to an array of values.
   *
   * @param input An array of values.
   * @return The output array.
   */
  static double[] softmax(final float[] input) {

    // Subtract the maximum before exponentiating (numerically stable softmax): exp() of a
    // large logit otherwise overflows to +Infinity, yielding NaN scores. Mathematically
    // identical to the naive form. Results are kept in double precision throughout.
    double max = Double.NEGATIVE_INFINITY;
    for (final float value : input) {
      max = Math.max(max, value);
    }

    final double[] t = new double[input.length];
    double sum = 0.0;

    for (int x = 0; x < input.length; x++) {
      final double val = Math.exp(input[x] - max);
      sum += val;
      t[x] = val;
    }

    final double[] output = new double[input.length];

    for (int x = 0; x < output.length; x++) {
      output[x] = t[x] / sum;
    }

    return output;

  }

  private int maxIndex(double[] arr) {
    return IntStream.range(0, arr.length)
        .reduce((i, j) -> arr[i] > arr[j] ? i : j)
        .orElse(-1);
  }

  private Map<Integer, String> readCategoriesFromFile(File config) throws IOException {
    final DocumentCategorizerConfig documentCategorizerConfig =
        DocumentCategorizerConfig.fromJson(Files.readString(config.toPath(), StandardCharsets.UTF_8));

    final Map<Integer, String> categories = new HashMap<>();
    for (final String key : documentCategorizerConfig.id2label().keySet()) {
      categories.put(Integer.valueOf(key), documentCategorizerConfig.id2label().get(key));
    }

    return categories;

  }

}
