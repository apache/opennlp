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
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.dl.AbstractDL;
import opennlp.dl.InferenceOptions;
import opennlp.dl.Tokens;
import opennlp.dl.doccat.scoring.ClassificationScoringStrategy;
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
 * @see DocumentCategorizer
 * @see InferenceOptions
 * @see ClassificationScoringStrategy
 */
public class DocumentCategorizerDL extends AbstractDL implements DocumentCategorizer {

  private static final Logger logger = LoggerFactory.getLogger(DocumentCategorizerDL.class);

  /** Classification models are commonly uncased, so lower casing is the default. */
  private static final boolean LOWER_CASE_DEFAULT = true;

  private final Map<Integer, String> categories;
  private final ClassificationScoringStrategy classificationScoringStrategy;
  private final InferenceOptions inferenceOptions;

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

    this.env = OrtEnvironment.getEnvironment();

    final OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
    if (inferenceOptions.isGpu()) {
      sessionOptions.addCUDA(inferenceOptions.getGpuDeviceId());
    }

    this.session = env.createSession(model.getPath(), sessionOptions);
    this.vocab = loadVocab(vocabulary);
    this.tokenizer = createTokenizer(vocab, resolveLowerCase(inferenceOptions, LOWER_CASE_DEFAULT));
    this.categories = categories;
    this.classificationScoringStrategy = classificationScoringStrategy;
    this.inferenceOptions = inferenceOptions;

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

    this.env = OrtEnvironment.getEnvironment();

    final OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
    if (inferenceOptions.isGpu()) {
      sessionOptions.addCUDA(inferenceOptions.getGpuDeviceId());
    }

    this.session = env.createSession(model.getPath(), sessionOptions);
    this.vocab = loadVocab(vocabulary);
    this.tokenizer = createTokenizer(vocab, resolveLowerCase(inferenceOptions, LOWER_CASE_DEFAULT));
    this.categories = readCategoriesFromFile(config);
    this.classificationScoringStrategy = classificationScoringStrategy;
    this.inferenceOptions = inferenceOptions;

  }

  @Override
  public double[] categorize(String[] strings) {

    try {

      final List<Tokens> tokens = tokenize(strings[0]);

      final List<double[]> scores = new LinkedList<>();

      for (final Tokens t : tokens) {

        final Map<String, OnnxTensor> inputs = new HashMap<>();

        final Object output;
        try {
          inputs.put(INPUT_IDS, OnnxTensor.createTensor(env,
              LongBuffer.wrap(t.ids()), new long[] {1, t.ids().length}));

          if (inferenceOptions.isIncludeAttentionMask()) {
            inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env,
                LongBuffer.wrap(t.mask()), new long[] {1, t.mask().length}));
          }

          if (inferenceOptions.isIncludeTokenTypeIds()) {
            inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env,
                LongBuffer.wrap(t.types()), new long[] {1, t.types().length}));
          }

          // The outputs from the model. Some models return a 2D array (e.g. BERT),
          // while others return a 1D array (e.g. RoBERTa).
          try (OrtSession.Result result = session.run(inputs)) {
            // getValue() copies the tensor into Java arrays, so the result can be closed safely.
            output = result.get(0).getValue();
          }
        } finally {
          inputs.values().forEach(OnnxTensor::close);
        }

        final float[] rawScores;
        if (output instanceof float[][] v) {
          rawScores = v[0];
        } else if (output instanceof float[] v) {
          rawScores = v;
        } else {
          throw new IllegalStateException(
              "Unexpected model output type: " + output.getClass().getName());
        }

        // Keep track of all scores.
        final double[] categoryScoresForTokens = softmax(rawScores);
        scores.add(categoryScoresForTokens);

      }

      return classificationScoringStrategy.score(scores);

    } catch (Exception ex) {
      logger.error("Unload to perform document classification inference", ex);
    }

    return new double[] {};

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

    // In this article as the paper suggests, we are going to segment the input into smaller text and feed
    // each of them into BERT, it means for each row, we will split the text in order to have some
    // smaller text (200 words long each)
    // https://medium.com/analytics-vidhya/text-classification-with-bert-using-transformers-for-long-text-inputs-f54833994dfd

    // Split the input text into 200 word chunks with 50 overlapping between chunks.
    final String[] whitespaceTokenized = text.split("\\s+");

    for (int start = 0; start < whitespaceTokenized.length;
         start = start + inferenceOptions.getDocumentSplitSize()) {

      // 200 word length chunk
      // Check the end do don't go past and get a StringIndexOutOfBoundsException
      int end = start + inferenceOptions.getDocumentSplitSize();
      if (end > whitespaceTokenized.length) {
        end = whitespaceTokenized.length;
      }

      // The group is that subsection of string.
      final String group = String.join(" ", Arrays.copyOfRange(whitespaceTokenized, start, end));

      // We want to overlap each chunk by 50 words so scoot back 50 words for the next iteration.
      start = start - inferenceOptions.getSplitOverlapSize();

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
  private double[] softmax(final float[] input) {

    final double[] t = new double[input.length];
    double sum = 0.0;

    for (int x = 0; x < input.length; x++) {
      double val = Math.exp(input[x]);
      sum += val;
      t[x] = val;
    }

    final double[] output = new double[input.length];

    for (int x = 0; x < output.length; x++) {
      output[x] = (float) (t[x] / sum);
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
