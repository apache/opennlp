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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import opennlp.dl.Inference;
import opennlp.dl.InferenceOptions;
import opennlp.tools.doccat.DocumentCategorizer;

/**
 * An implementation of {@link DocumentCategorizer} that performs document classification
 * using ONNX models.
 */
public class DocumentCategorizerDL implements DocumentCategorizer {

  private final Map<Integer, String> categories;
  private final Inference inference;

  /**
   * Creates a new document categorizer using ONNX models.
   * @param model The ONNX model file.
   * @param vocab The model's vocabulary file.
   * @param categories The categories.
   */
  public DocumentCategorizerDL(File model, File vocab, Map<Integer, String> categories) throws Exception {

    this(categories, new DocumentCategorizerInference(model, vocab, new InferenceOptions()));

  }

  /**
   * Creates a new document categorizer using ONNX models.
   * @param model The ONNX model file.
   * @param vocab The model's vocabulary file.
   * @param categories The categories.
   * @param inferenceOptions The {@link InferenceOptions} used to customize the inference process.
   */
  public DocumentCategorizerDL(File model, File vocab, Map<Integer, String> categories,
                               InferenceOptions inferenceOptions) throws Exception {

    this(categories, new DocumentCategorizerInference(model, vocab, inferenceOptions));

  }

  /**
   * Creates a new document categorizer using ONNX models.
   * @param categories The categories.
   * @param inference The {@link Inference} inference implementation.
   */
  public DocumentCategorizerDL(Map<Integer, String> categories, Inference inference) {

    this.categories = categories;
    this.inference = inference;

  }

  @Override
  public double[] categorize(String[] strings) {

    try {

      final Object output = inference.infer(strings[0]);
      final double[][] vectors = inference.convertFloatsToDoubles((float[][]) output);

      final double[] results = inference.softmax(vectors[0]);

      return results;

    } catch (Exception ex) {
      System.err.println("Unload to perform document classification inference: " + ex.getMessage());
    }

    return new double[]{};

  }

  @Override
  public double[] categorize(String[] strings, Map<String, Object> map) {
    return categorize(strings);
  }

  @Override
  public String getBestCategory(double[] doubles) {
    return categories.get(Inference.maxIndex(doubles));
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

}
