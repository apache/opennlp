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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.dl.AbstractDL;
import opennlp.dl.InferenceOptions;
import opennlp.dl.Tokens;
import opennlp.dl.doccat.scoring.ClassificationScoringStrategy;
import opennlp.tools.doccat.DocumentCategorizer;
import opennlp.tools.tokenize.WordpieceTokenizer;

/**
 * An implementation of {@link DocumentCategorizer} that performs document classification
 * using ONNX models.
 *
 * @see DocumentCategorizer
 * @see InferenceOptions
 * @see ClassificationScoringStrategy
 */
public class DocumentCategorizerDL extends AbstractDL implements DocumentCategorizer {

  private static final Logger logger = LoggerFactory.getLogger(DocumentCategorizerDL.class);

  private final Map<Integer, String> categories;
  private final ClassificationScoringStrategy classificationScoringStrategy;
  private final InferenceOptions inferenceOptions;

  /**
   * Instantiates a {@link DocumentCategorizer document categorizer} using ONNX models.
   *
   * @param model The ONNX model file.
   * @param vocabulary The model file's vocabulary file.
   * @param categories The categories.
   * @param classificationScoringStrategy Implementation of {@link ClassificationScoringStrategy} used
   *                                      to calculate the classification scores given the score of each
   *                                      individual document part.
   * @param inferenceOptions {@link InferenceOptions} to control the inference.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
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
    this.tokenizer = new WordpieceTokenizer(vocab.keySet());
    this.categories = categories;
    this.classificationScoringStrategy = classificationScoringStrategy;
    this.inferenceOptions = inferenceOptions;

  }

  /**
   * Instantiates a {@link DocumentCategorizer document categorizer} using ONNX models.
   *
   * @param model The ONNX model file.
   * @param vocabulary The model file's vocabulary file.
   * @param config The model's config file. The file will be used to determine the classification categories.
   * @param classificationScoringStrategy Implementation of {@link ClassificationScoringStrategy} used
   *                                      to calculate the classification scores given the score of each
   *                                      individual document part.
   * @param inferenceOptions {@link InferenceOptions} to control the inference.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
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
    this.tokenizer = new WordpieceTokenizer(vocab.keySet());
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

        // The outputs from the model.
        final float[][] v = (float[][]) session.run(inputs).get(0).getValue();

        // Keep track of all scores.
        final double[] categoryScoresForTokens = softmax(v[0]);
        scores.add(categoryScoresForTokens);

      }

      return classificationScoringStrategy.score(scores);

    } catch (Exception ex) {
      logger.error("Unload to perform document classification inference", ex);
    }

    return new double[]{};

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

      final int[] ids = new int[tokens.length];

      for (int x = 0; x < tokens.length; x++) {
        ids[x] = vocab.get(tokens[x]);
      }

      final long[] lids = Arrays.stream(ids).mapToLong(i -> i).toArray();

      final long[] mask = new long[ids.length];
      Arrays.fill(mask, 1);

      final long[] types = new long[ids.length];
      Arrays.fill(types, 0);

      t.add(new Tokens(tokens, lids, mask, types));

    }

    return t;

  }

  /**
   * Applies softmax to an array of values.
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

    final String json = new String(Files.readAllBytes(config.toPath()));

    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    final DocumentCategorizerConfig documentCategorizerConfig =
        objectMapper.readValue(json, DocumentCategorizerConfig.class);

    final Map<Integer, String> categories = new HashMap<>();
    for (final String key : documentCategorizerConfig.getId2label().keySet()) {
      categories.put(Integer.valueOf(key), documentCategorizerConfig.getId2label().get(key));
    }

    return categories;

  }

}
