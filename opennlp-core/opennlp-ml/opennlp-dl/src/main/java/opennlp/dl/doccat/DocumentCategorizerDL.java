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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.dl.AbstractDL;
import opennlp.dl.InferenceOptions;
import opennlp.dl.Tokens;
import opennlp.dl.doccat.scoring.ClassificationScoringStrategy;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.doccat.DocumentCategorizer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ParamChecks;


/**
 * An implementation of {@link DocumentCategorizer} that performs document classification
 * using ONNX models.
 *
 * <p>Tokenization performs BERT basic tokenization (text normalization)
 * before wordpiece, see {@link opennlp.tools.tokenize.WordpieceEncoder}. Input
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
  private final InferenceSettings settings;

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
    this.settings = InferenceSettings.from(inferenceOptions);
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
   * @throws InvalidFormatException Thrown if a JSON {@code vocabulary} is malformed, has an
   *     unsupported layout, or sets a lower casing that disagrees with {@code inferenceOptions}.
   * @throws IllegalArgumentException Thrown if {@code model}, {@code vocabulary},
   *     {@code categories}, {@code classificationScoringStrategy} or {@code inferenceOptions} is
   *     {@code null}, if the split sizes of {@code inferenceOptions} are invalid, or if the
   *     vocabulary lacks the classification, separator or unknown token.
   */
  public DocumentCategorizerDL(File model, File vocabulary, Map<Integer, String> categories,
                               ClassificationScoringStrategy classificationScoringStrategy,
                               InferenceOptions inferenceOptions)
      throws IOException, OrtException {

    super(model, vocabulary,
        sessionOptions(validateConstructorArguments(
            inferenceOptions, categories, "categories", classificationScoringStrategy)),
        resolveLowerCase(inferenceOptions, LOWER_CASE_DEFAULT));

    this.categories = Map.copyOf(categories);
    this.classificationScoringStrategy = classificationScoringStrategy;
    this.settings = InferenceSettings.from(inferenceOptions);

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
   * @throws IOException  Thrown if errors occurred loading the {@code model}, the
   *     {@code vocabulary}, or the {@code config}.
   * @throws InvalidFormatException Thrown if a JSON {@code vocabulary} is malformed, has an
   *     unsupported layout, or sets a lower casing that disagrees with {@code inferenceOptions},
   *     or if {@code config} is not well-formed JSON, its {@code id2label} member does not map
   *     keys to strings, or a key is not an integer.
   * @throws IllegalArgumentException Thrown if {@code model}, {@code vocabulary}, {@code config},
   *     {@code classificationScoringStrategy} or {@code inferenceOptions} is {@code null}, if the
   *     split sizes of {@code inferenceOptions} are invalid, or if the vocabulary lacks the
   *     classification, separator or unknown token.
   */
  public DocumentCategorizerDL(File model, File vocabulary, File config,
                               ClassificationScoringStrategy classificationScoringStrategy,
                               InferenceOptions inferenceOptions)
      throws IOException, OrtException {

    super(model, vocabulary,
        sessionOptions(validateConstructorArguments(
            inferenceOptions, config, "config", classificationScoringStrategy)),
        resolveLowerCase(inferenceOptions, LOWER_CASE_DEFAULT));

    this.categories = Map.copyOf(readCategories(config));
    this.classificationScoringStrategy = classificationScoringStrategy;
    this.settings = InferenceSettings.from(inferenceOptions);

  }

  /**
   * Checks the arguments of a public constructor that the base class does not check.
   *
   * @param inferenceOptions The inference options.
   * @param labels The categories or the configuration file that supplies them.
   * @param labelsName The parameter name of {@code labels}, used in the message.
   * @param classificationScoringStrategy The scoring strategy.
   * @return {@code inferenceOptions}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   */
  private static InferenceOptions validateConstructorArguments(
      final InferenceOptions inferenceOptions, final Object labels, final String labelsName,
      final ClassificationScoringStrategy classificationScoringStrategy) {
    ParamChecks.requireNonNullArg(inferenceOptions, "inferenceOptions");
    ParamChecks.requireNonNullArg(labels, labelsName);
    ParamChecks.requireNonNullArg(classificationScoringStrategy, "classificationScoringStrategy");
    return inferenceOptions;
  }


  /**
   * Categorizes the document, failing loudly rather than returning an invalid distribution:
   * malformed input is rejected with {@link IllegalArgumentException}, and any failure executing
   * the model is surfaced as an {@link IllegalStateException} (cause preserved).
   *
   * @param strings The document to categorize; {@code strings[0]} is classified.
   * @return The per-category probabilities.
   * @throws IllegalArgumentException If {@code strings} is {@code null} or empty, if
   *     {@code strings[0]} is {@code null}, or if {@code strings[0]} has no tokens to classify
   *     (it is empty or only whitespace).
   * @throws IllegalStateException    If inference fails or the model returns an unexpected output.
   */
  @Override
  public double[] categorize(String[] strings) {

    if (strings == null || strings.length == 0) {
      throw new IllegalArgumentException(
          "The strings argument must contain at least one document to categorize");
    }

    if (strings[0] == null) {
      throw new IllegalArgumentException("The document to categorize must not be null");
    }

    final List<Tokens> tokens = tokenize(strings[0]);
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException(
          "The document to categorize must contain at least one non-whitespace token");
    }

    final List<double[]> scores = new ArrayList<>(tokens.size());
    for (final Tokens t : tokens) {
      scores.add(softmax(infer(t)));
    }

    final double[] distribution = classificationScoringStrategy.score(scores);
    return requireMatchingCategoryCount(distribution, categories.size());
  }

  // Package-visible so the model/category-count mismatch guard can be exercised without a live model.
  static double[] requireMatchingCategoryCount(final double[] distribution, final int expected) {
    if (distribution.length != expected) {
      throw new IllegalStateException("The model produced " + distribution.length
          + " category scores but the categorizer is configured with " + expected
          + " categories; the model and the category configuration do not match");
    }
    return distribution;
  }

  /**
   * Runs the model on one token window and returns its raw per-category logits.
   *
   * @param t The encoded token window.
   * @return The logits.
   * @throws IllegalStateException Thrown if the model cannot be run or returns an output that is
   *     neither {@code float[][]} nor {@code float[]}.
   */
  private float[] infer(final Tokens t) {
    return logitsFromOutput(runModel(t, settings));
  }

  // Package-visible so the output-shape dispatch, including the null and unexpected-type failures,
  // can be exercised without a live model session.
  static float[] logitsFromOutput(final Object output) {
    // Some models return a 2D array (e.g. BERT), others a 1D array (e.g. RoBERTa). A different
    // shape is a model-contract violation, surfaced on its own rather than as "inference failed".
    if (output instanceof float[][] v) {
      return v[0];
    } else if (output instanceof float[] v) {
      return v;
    }
    throw new IllegalStateException("Unexpected model output type: "
        + (output == null ? "null" : output.getClass().getName()));
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

  private List<Tokens> tokenize(final String input) {

    final String text =
        normalizeInput(input, settings.normalizeWhitespace(), settings.normalizeDashes());

    // Segment long input text into overlapping chunks (split on Unicode whitespace) configured by
    // InferenceOptions before feeding each chunk into BERT.
    // https://medium.com/analytics-vidhya/text-classification-with-bert-using-transformers-for-long-text-inputs-f54833994dfd
    final List<String> groups =
        whitespaceChunks(text, settings.documentSplitSize(), settings.splitOverlapSize());
    final List<Tokens> t = new ArrayList<>(groups.size());
    for (final String group : groups) {

      t.add(encodeTokens(group));

    }

    return t;

  }

  /**
   * Applies {@link AbstractDL#softmaxProbabilities(float[])} to the logits of one document chunk.
   *
   * <p>Any non-finite logit is rejected before the softmax: a {@code +Infinity} logit would make
   * every probability {@code NaN}, so a classification distribution cannot be computed.</p>
   *
   * @param input The logits produced by the model.
   * @return The classification distribution, in the order of {@code input}.
   * @throws IllegalStateException Thrown if any logit is {@code NaN} or infinite.
   */
  static double[] softmax(final float[] input) {
    for (final float value : input) {
      // Reject any non-finite logit, not just NaN: a +Infinity logit makes max == +Inf, so
      // value - max is Inf - Inf == NaN and the whole distribution silently goes NaN. Subtracting
      // the maximum already handles merely-large finite logits, so only NaN/Infinity reach here.
      if (!Float.isFinite(value)) {
        throw new IllegalStateException(
            "The model produced a non-finite logit (NaN or Infinity); cannot compute a "
                + "classification distribution");
      }
    }
    return softmaxProbabilities(input);
  }

  private int maxIndex(double[] arr) {
    return IntStream.range(0, arr.length)
        .reduce((i, j) -> arr[i] > arr[j] ? i : j)
        .orElse(-1);
  }

  /**
   * Reads the categories of a model from the {@code id2label} member of its configuration file.
   *
   * @param config The {@code config.json} file.
   * @return The categories by output index, empty if the configuration has no {@code id2label}.
   * @throws IOException Thrown if the file cannot be read.
   * @throws InvalidFormatException Thrown if the file is not well-formed JSON, its
   *     {@code id2label} member does not map keys to strings, or a key is not an integer. The
   *     message names the file.
   */
  static Map<Integer, String> readCategories(File config) throws IOException {
    final String json = Files.readString(config.toPath(), StandardCharsets.UTF_8);
    return parseJson(config, "Configuration", json, DocumentCategorizerDL::parseCategories);
  }

  /**
   * Reads the categories from the {@code id2label} member of a model configuration.
   *
   * @param json The JSON text of the configuration.
   * @return The categories by output index, empty if the configuration has no {@code id2label}.
   * @throws IllegalArgumentException Thrown if the text is not well-formed JSON, its
   *     {@code id2label} member does not map keys to strings, or a key is not an integer.
   */
  private static Map<Integer, String> parseCategories(String json) {
    final Map<Integer, String> parsed = new HashMap<>();
    for (Map.Entry<String, String> label
        : DocumentCategorizerConfig.fromJson(json).id2label().entrySet()) {
      parsed.put(parseIndex(label.getKey()), label.getValue());
    }
    return parsed;
  }

  /**
   * Parses an {@code id2label} key as an output index.
   *
   * @param key The key to parse.
   * @return The output index.
   * @throws IllegalArgumentException Thrown if {@code key} is not an integer.
   */
  private static int parseIndex(String key) {
    try {
      return Integer.parseInt(key);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("id2label key must be an integer: " + key, e);
    }
  }

}
