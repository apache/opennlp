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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.hhn.mi.domain.SvmClassLabel;
import de.hhn.mi.domain.SvmDocument;
import de.hhn.mi.domain.SvmFeature;
import de.hhn.mi.domain.SvmFeatureImpl;
import de.hhn.mi.domain.SvmModel;
import de.hhn.mi.process.SvmClassifier;
import de.hhn.mi.process.SvmClassifierImpl;
import de.hhn.mi.process.SvmTrainer;
import de.hhn.mi.process.SvmTrainerImpl;

import opennlp.tools.doccat.DocumentCategorizer;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.util.ObjectStream;

/**
 * An implementation of {@link DocumentCategorizer} that uses Support Vector Machines
 * (SVM) via the zlibsvm library for document classification.
 * <p>
 * This categorizer supports configurable:
 * <ul>
 *   <li>{@link TermWeightingStrategy Term weighting} (binary, TF, TF-IDF, log-normalized TF)</li>
 *   <li>{@link FeatureSelectionStrategy Feature selection} (information gain, chi-square,
 *       term frequency, document frequency)</li>
 *   <li>Feature scaling to a configurable range (e.g., [0, 1])</li>
 *   <li>SVM classifier parameters (kernel, cost, gamma, etc.) via
 *       {@link de.hhn.mi.configuration.SvmConfiguration}</li>
 * </ul>
 *
 * @see DocumentCategorizer
 * @see SvmDoccatModel
 * @see SvmDoccatConfiguration
 */
public class DocumentCategorizerSVM implements DocumentCategorizer {

  private final SvmDoccatModel model;
  private final FeatureGenerator[] featureGenerators;
  private final SvmClassifier classifier;

  /**
   * Instantiates a {@link DocumentCategorizerSVM} with a trained model and feature generators.
   *
   * @param model             The trained {@link SvmDoccatModel}. Must not be {@code null}.
   * @param featureGenerators The {@link FeatureGenerator} instances used to extract features.
   *                          Must not be {@code null} or empty.
   */
  public DocumentCategorizerSVM(SvmDoccatModel model, FeatureGenerator... featureGenerators) {
    this.model = Objects.requireNonNull(model, "model must not be null");
    Objects.requireNonNull(featureGenerators, "featureGenerators must not be null");
    if (featureGenerators.length == 0) {
      throw new IllegalArgumentException("At least one FeatureGenerator is required");
    }
    this.featureGenerators = featureGenerators;
    this.classifier = new SvmClassifierImpl(model.getSvmModel());
  }

  @Override
  public double[] categorize(String[] text, Map<String, Object> extraInformation) {
    SvmDocument doc = textToSvmDocument(text, extraInformation);
    List<SvmDocument> classified = classifier.classify(List.of(doc), true);
    return toOutcomeProbabilities(classified.get(0));
  }

  @Override
  public double[] categorize(String[] text) {
    return categorize(text, Collections.emptyMap());
  }

  @Override
  public String getBestCategory(double[] outcome) {
    int bestIndex = 0;
    for (int i = 1; i < outcome.length; i++) {
      if (outcome[i] > outcome[bestIndex]) {
        bestIndex = i;
      }
    }
    return model.getIndexToCategory().get(bestIndex);
  }

  @Override
  public int getIndex(String category) {
    Integer idx = model.getCategoryToIndex().get(category);
    return idx != null ? idx : -1;
  }

  @Override
  public String getCategory(int index) {
    return model.getIndexToCategory().get(index);
  }

  @Override
  public int getNumberOfCategories() {
    return model.getNumberOfCategories();
  }

  @Override
  public String getAllResults(double[] results) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < results.length; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(model.getIndexToCategory().get(i)).append('[').append(results[i]).append(']');
    }
    return sb.toString();
  }

  @Override
  public Map<String, Double> scoreMap(String[] text) {
    double[] scores = categorize(text);
    Map<String, Double> scoreMap = new HashMap<>();
    for (int i = 0; i < scores.length; i++) {
      scoreMap.put(getCategory(i), scores[i]);
    }
    return scoreMap;
  }

  @Override
  public SortedMap<Double, Set<String>> sortedScoreMap(String[] text) {
    double[] scores = categorize(text);
    SortedMap<Double, Set<String>> sortedMap = new TreeMap<>();
    for (int i = 0; i < scores.length; i++) {
      String category = getCategory(i);
      sortedMap.computeIfAbsent(scores[i], k -> new HashSet<>()).add(category);
    }
    return sortedMap;
  }

  /**
   * Trains an SVM-based document categorization model using default configuration
   * (TF-IDF weighting, no feature selection, scaling to [0, 1]).
   *
   * @param lang              The ISO conform language code.
   * @param samples           The {@link ObjectStream} of {@link DocumentSample} used as input
   *                          for training.
   * @param featureGenerators The {@link FeatureGenerator} instances used to extract features.
   * @return A trained {@link SvmDoccatModel}.
   * @throws IOException Thrown if IO errors occurred during training.
   */
  public static SvmDoccatModel train(String lang,
                                     ObjectStream<DocumentSample> samples,
                                     FeatureGenerator... featureGenerators) throws IOException {
    return train(lang, samples, new SvmDoccatConfiguration.Builder().build(), featureGenerators);
  }

  /**
   * Trains an SVM-based document categorization model with a custom configuration.
   *
   * @param lang              The ISO conform language code.
   * @param samples           The {@link ObjectStream} of {@link DocumentSample} used as input
   *                          for training.
   * @param config            The {@link SvmDoccatConfiguration} controlling term weighting,
   *                          feature selection, scaling, and SVM parameters.
   * @param featureGenerators The {@link FeatureGenerator} instances used to extract features.
   * @return A trained {@link SvmDoccatModel}.
   * @throws IOException Thrown if IO errors occurred during training.
   */
  public static SvmDoccatModel train(String lang,
                                     ObjectStream<DocumentSample> samples,
                                     SvmDoccatConfiguration config,
                                     FeatureGenerator... featureGenerators) throws IOException {
    Objects.requireNonNull(samples, "samples must not be null");
    Objects.requireNonNull(config, "config must not be null");
    Objects.requireNonNull(featureGenerators, "featureGenerators must not be null");
    if (featureGenerators.length == 0) {
      throw new IllegalArgumentException("At least one FeatureGenerator is required");
    }

    // --- Pass 1: Collect all samples, build vocabulary + category mappings ---
    Map<String, Integer> categoryToIndex = new LinkedHashMap<>();
    Map<Integer, String> indexToCategory = new HashMap<>();
    List<DocumentSample> allSamples = new ArrayList<>();

    // Per-document: feature string set and term frequency map
    List<Set<String>> docFeatureSets = new ArrayList<>();
    List<Map<String, Integer>> docTfMaps = new ArrayList<>();
    List<String> docLabels = new ArrayList<>();

    // Corpus-level document frequency
    Map<String, Integer> documentFrequency = new HashMap<>();
    int totalDocs = 0;

    DocumentSample sample;
    while ((sample = samples.read()) != null) {
      allSamples.add(sample);
      totalDocs++;

      // Register category
      String category = sample.getCategory();
      if (!categoryToIndex.containsKey(category)) {
        int catIdx = categoryToIndex.size();
        categoryToIndex.put(category, catIdx);
        indexToCategory.put(catIdx, category);
      }
      docLabels.add(category);

      // Extract features
      String[] context = generateContext(sample.getText(), sample.getExtraInformation(),
          featureGenerators);

      // Compute term frequency for this document
      Map<String, Integer> tf = new HashMap<>();
      for (String feature : context) {
        tf.merge(feature, 1, Integer::sum);
      }
      docTfMaps.add(tf);
      docFeatureSets.add(tf.keySet());

      // Update document frequency
      for (String feature : tf.keySet()) {
        documentFrequency.merge(feature, 1, Integer::sum);
      }
    }

    // --- Pass 2: Feature selection ---
    Set<String> selectedFeatures;
    if (config.getFeatureSelectionStrategy() != FeatureSelectionStrategy.NONE) {
      selectedFeatures = FeatureSelection.selectTopFeatures(
          docFeatureSets, docTfMaps, docLabels,
          config.getFeatureSelectionStrategy(), config.getMaxFeatures());
    } else {
      selectedFeatures = new HashSet<>();
      for (Set<String> fs : docFeatureSets) {
        selectedFeatures.addAll(fs);
      }
    }

    // Build final vocabulary (only selected features)
    Map<String, Integer> featureVocabulary = new LinkedHashMap<>();
    for (String feature : selectedFeatures) {
      featureVocabulary.put(feature, featureVocabulary.size() + 1);
    }

    // --- Pass 3: Compute IDF values ---
    Map<String, Double> idfValues = new HashMap<>();
    for (String feature : featureVocabulary.keySet()) {
      int df = documentFrequency.getOrDefault(feature, 0);
      idfValues.put(feature, df > 0 ? Math.log((double) totalDocs / df) : 0.0);
    }

    // --- Pass 4: Compute weighted features & collect min/max for scaling ---
    TermWeightingStrategy weighting = config.getTermWeightingStrategy();
    List<Map<Integer, Double>> docWeightedFeatures = new ArrayList<>(allSamples.size());

    // Initialize min to 0.0 for all features (absent features have implicit value 0)
    Map<Integer, Double> featureMin = new HashMap<>();
    Map<Integer, Double> featureMax = new HashMap<>();
    for (Integer idx : featureVocabulary.values()) {
      featureMin.put(idx, 0.0);
      featureMax.put(idx, 0.0);
    }

    for (Map<String, Integer> tf : docTfMaps) {
      Map<Integer, Double> weighted = new TreeMap<>();
      for (Map.Entry<String, Integer> entry : tf.entrySet()) {
        String feature = entry.getKey();
        Integer idx = featureVocabulary.get(feature);
        if (idx != null) {
          double idf = idfValues.getOrDefault(feature, 0.0);
          double value = weighting.weight(entry.getValue(), idf);
          weighted.put(idx, value);

          featureMin.merge(idx, value, Math::min);
          featureMax.merge(idx, value, Math::max);
        }
      }
      docWeightedFeatures.add(weighted);
    }

    // --- Pass 5: Scale and build SVM documents ---
    List<SvmDocument> svmDocuments = new ArrayList<>(allSamples.size());
    for (int i = 0; i < allSamples.size(); i++) {
      Map<Integer, Double> weighted = docWeightedFeatures.get(i);
      List<SvmFeature> features = buildFeatureVector(weighted, config, featureMin, featureMax);

      SvmDocument svmDoc = new TrainingSvmDocument(features);
      int categoryLabel = categoryToIndex.get(allSamples.get(i).getCategory());
      svmDoc.addClassLabel(new TrainingSvmClassLabel(categoryLabel,
          allSamples.get(i).getCategory(), 1.0));
      svmDocuments.add(svmDoc);
    }

    // --- Train SVM ---
    SvmTrainer trainer = new SvmTrainerImpl(config.getSvmConfiguration(), "opennlp-doccat-svm");
    SvmModel svmModel = trainer.train(svmDocuments);

    return new SvmDoccatModel(svmModel, featureVocabulary, indexToCategory, categoryToIndex,
        idfValues, featureMin, featureMax, config, lang);
  }

  // --- Private helpers ---

  private SvmDocument textToSvmDocument(String[] text, Map<String, Object> extraInformation) {
    String[] context = generateContext(text, extraInformation, featureGenerators);
    SvmDoccatConfiguration config = model.getConfiguration();
    TermWeightingStrategy weighting = config.getTermWeightingStrategy();
    Map<String, Integer> vocab = model.getFeatureVocabulary();
    Map<String, Double> idf = model.getIdfValues();

    // Compute term frequencies
    Map<Integer, Double> weighted = new TreeMap<>();
    Map<String, Integer> tf = new HashMap<>();
    for (String feature : context) {
      tf.merge(feature, 1, Integer::sum);
    }

    for (Map.Entry<String, Integer> entry : tf.entrySet()) {
      Integer idx = vocab.get(entry.getKey());
      if (idx != null) {
        double idfVal = idf.getOrDefault(entry.getKey(), 0.0);
        double value = weighting.weight(entry.getValue(), idfVal);
        if (Double.compare(value, 0.0) != 0) {
          weighted.put(idx, value);
        }
      }
    }

    List<SvmFeature> features = buildFeatureVector(weighted, config,
        model.getFeatureMinValues(), model.getFeatureMaxValues());
    return new TrainingSvmDocument(features);
  }

  private static List<SvmFeature> buildFeatureVector(Map<Integer, Double> weighted,
                                                      SvmDoccatConfiguration config,
                                                      Map<Integer, Double> featureMin,
                                                      Map<Integer, Double> featureMax) {
    // Only emit non-zero features (sparse representation required by libsvm)
    List<SvmFeature> features = new ArrayList<>(weighted.size());
    for (Map.Entry<Integer, Double> entry : weighted.entrySet()) {
      double value = entry.getValue();
      if (config.isScaleFeatures()) {
        value = scale(value, entry.getKey(), featureMin, featureMax,
            config.getScaleLower(), config.getScaleUpper());
      }
      if (Double.compare(value, 0.0) != 0) {
        features.add(new SvmFeatureImpl(entry.getKey(), value));
      }
    }
    return features;
  }

  private static double scale(double value, int featureIdx,
                               Map<Integer, Double> featureMin,
                               Map<Integer, Double> featureMax,
                               double lower, double upper) {
    double min = featureMin.getOrDefault(featureIdx, 0.0);
    double max = featureMax.getOrDefault(featureIdx, 0.0);
    if (Double.compare(max, min) == 0) {
      return lower;
    }
    // Clamp to training range, then scale
    double clamped = Math.max(min, Math.min(max, value));
    return lower + (upper - lower) * (clamped - min) / (max - min);
  }

  private double[] toOutcomeProbabilities(SvmDocument classifiedDoc) {
    int numCategories = model.getNumberOfCategories();
    double[] probs = new double[numCategories];
    for (SvmClassLabel label : classifiedDoc.getAllClassLabels()) {
      int numericLabel = (int) label.getNumeric();
      if (numericLabel >= 0 && numericLabel < numCategories) {
        probs[numericLabel] = label.getProbability();
      }
    }
    return probs;
  }

  private static String[] generateContext(String[] text, Map<String, Object> extraInformation,
                                          FeatureGenerator[] generators) {
    List<String> context = new ArrayList<>();
    for (FeatureGenerator gen : generators) {
      Collection<String> features = gen.extractFeatures(text, extraInformation);
      context.addAll(features);
    }
    return context.toArray(new String[0]);
  }
}
