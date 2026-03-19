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

package opennlp.tools.ml.libsvm.doccat;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.hhn.mi.domain.SvmModel;

/**
 * A model for SVM-based document categorization. This model wraps a zlibsvm
 * {@link SvmModel} together with the feature vocabulary, category label
 * mappings, corpus statistics, and configuration required for classification.
 *
 * @see DocumentCategorizerSVM
 */
public class SvmDoccatModel implements Serializable {

  @Serial
  private static final long serialVersionUID = 2L;

  private final SvmModel svmModel;
  private final HashMap<String, Integer> featureVocabulary;
  private final HashMap<Integer, String> indexToCategory;
  private final HashMap<String, Integer> categoryToIndex;
  private final HashMap<String, Double> idfValues;
  private final HashMap<Integer, Double> featureMinValues;
  private final HashMap<Integer, Double> featureMaxValues;
  private final SvmDoccatConfiguration configuration;
  private final String languageCode;

  /**
   * Instantiates a {@link SvmDoccatModel} with the given parameters.
   *
   * @param svmModel          The trained {@link SvmModel}. Must not be {@code null}.
   * @param featureVocabulary  A mapping from feature strings to their numeric indices.
   *                           Must not be {@code null}.
   * @param indexToCategory    A mapping from numeric category labels to category names.
   *                           Must not be {@code null}.
   * @param categoryToIndex   A mapping from category names to numeric labels.
   *                           Must not be {@code null}.
   * @param idfValues          A mapping from feature strings to their IDF values.
   *                           Must not be {@code null}.
   * @param featureMinValues   A mapping from feature index to its minimum value in the
   *                           training corpus (for scaling). Must not be {@code null}.
   * @param featureMaxValues   A mapping from feature index to its maximum value in the
   *                           training corpus (for scaling). Must not be {@code null}.
   * @param configuration      The {@link SvmDoccatConfiguration} used for training.
   *                           Must not be {@code null}.
   * @param languageCode      An ISO conform language code.
   */
  SvmDoccatModel(SvmModel svmModel,
                  Map<String, Integer> featureVocabulary,
                  Map<Integer, String> indexToCategory,
                  Map<String, Integer> categoryToIndex,
                  Map<String, Double> idfValues,
                  Map<Integer, Double> featureMinValues,
                  Map<Integer, Double> featureMaxValues,
                  SvmDoccatConfiguration configuration,
                  String languageCode) {
    this.svmModel = Objects.requireNonNull(svmModel, "svmModel must not be null");
    this.featureVocabulary = new HashMap<>(
        Objects.requireNonNull(featureVocabulary, "featureVocabulary must not be null"));
    this.indexToCategory = new HashMap<>(
        Objects.requireNonNull(indexToCategory, "indexToCategory must not be null"));
    this.categoryToIndex = new HashMap<>(
        Objects.requireNonNull(categoryToIndex, "categoryToIndex must not be null"));
    this.idfValues = new HashMap<>(
        Objects.requireNonNull(idfValues, "idfValues must not be null"));
    this.featureMinValues = new HashMap<>(
        Objects.requireNonNull(featureMinValues, "featureMinValues must not be null"));
    this.featureMaxValues = new HashMap<>(
        Objects.requireNonNull(featureMaxValues, "featureMaxValues must not be null"));
    this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    this.languageCode = languageCode;
  }

  /**
   * @return The underlying {@link SvmModel}.
   */
  public SvmModel getSvmModel() {
    return svmModel;
  }

  /**
   * @return An unmodifiable mapping from feature strings to their numeric indices.
   */
  public Map<String, Integer> getFeatureVocabulary() {
    return Collections.unmodifiableMap(featureVocabulary);
  }

  /**
   * @return An unmodifiable mapping from numeric category labels to category names.
   */
  public Map<Integer, String> getIndexToCategory() {
    return Collections.unmodifiableMap(indexToCategory);
  }

  /**
   * @return An unmodifiable mapping from category names to numeric labels.
   */
  public Map<String, Integer> getCategoryToIndex() {
    return Collections.unmodifiableMap(categoryToIndex);
  }

  /**
   * @return An unmodifiable mapping from feature strings to their IDF values.
   */
  public Map<String, Double> getIdfValues() {
    return Collections.unmodifiableMap(idfValues);
  }

  /**
   * @return An unmodifiable mapping from feature index to its minimum value in the
   *         training corpus.
   */
  public Map<Integer, Double> getFeatureMinValues() {
    return Collections.unmodifiableMap(featureMinValues);
  }

  /**
   * @return An unmodifiable mapping from feature index to its maximum value in the
   *         training corpus.
   */
  public Map<Integer, Double> getFeatureMaxValues() {
    return Collections.unmodifiableMap(featureMaxValues);
  }

  /**
   * @return The {@link SvmDoccatConfiguration} used for this model.
   */
  public SvmDoccatConfiguration getConfiguration() {
    return configuration;
  }

  /**
   * @return The ISO language code associated with this model.
   */
  public String getLanguageCode() {
    return languageCode;
  }

  /**
   * @return The number of categories in this model.
   */
  public int getNumberOfCategories() {
    return indexToCategory.size();
  }

  /**
   * Serializes this model to the given {@link OutputStream}.
   *
   * @param out The {@link OutputStream} to write to. Must not be {@code null}.
   * @throws IOException Thrown if IO errors occurred during serialization.
   */
  public void serialize(OutputStream out) throws IOException {
    try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
      oos.writeObject(this);
    }
  }

  /**
   * Deserializes a {@link SvmDoccatModel} from the given {@link InputStream}.
   *
   * @param in The {@link InputStream} to read from. Must not be {@code null}.
   * @return A valid {@link SvmDoccatModel} instance.
   * @throws IOException Thrown if IO errors occurred during deserialization.
   * @throws ClassNotFoundException Thrown if required classes are not found.
   */
  public static SvmDoccatModel deserialize(InputStream in) throws IOException, ClassNotFoundException {
    try (ObjectInputStream ois = new ObjectInputStream(in)) {
      return (SvmDoccatModel) ois.readObject();
    }
  }
}
