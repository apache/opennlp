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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes feature scores and selects the top-k features for text classification.
 * <p>
 * Supports multiple feature selection strategies:
 * <ul>
 *   <li>{@link FeatureSelectionStrategy#INFORMATION_GAIN} - ranks by entropy reduction</li>
 *   <li>{@link FeatureSelectionStrategy#CHI_SQUARE} - ranks by chi-square independence test</li>
 *   <li>{@link FeatureSelectionStrategy#TERM_FREQUENCY} - ranks by total corpus frequency</li>
 *   <li>{@link FeatureSelectionStrategy#DOCUMENT_FREQUENCY} - ranks by number of containing documents</li>
 * </ul>
 *
 * @see FeatureSelectionStrategy
 */
final class FeatureSelection {

  private FeatureSelection() {
  }

  /**
   * Selects the top-k features according to the given strategy.
   *
   * @param documentFeatures A list where each entry is the set of feature strings
   *                         present in a document.
   * @param documentTfs      A list where each entry maps feature strings to their
   *                         term frequency in that document.
   * @param documentLabels   A list of category labels, one per document. Must have
   *                         the same size as {@code documentFeatures}.
   * @param strategy         The feature selection strategy.
   * @param maxFeatures      The maximum number of features to select. If {@code <= 0},
   *                         all features are returned.
   * @return The set of selected feature strings.
   */
  static Set<String> selectTopFeatures(List<Set<String>> documentFeatures,
                                       List<Map<String, Integer>> documentTfs,
                                       List<String> documentLabels,
                                       FeatureSelectionStrategy strategy,
                                       int maxFeatures) {
    Map<String, Double> scores = switch (strategy) {
      case INFORMATION_GAIN -> computeInformationGain(documentFeatures, documentLabels);
      case CHI_SQUARE -> computeChiSquare(documentFeatures, documentLabels);
      case TERM_FREQUENCY -> computeTermFrequency(documentTfs);
      case DOCUMENT_FREQUENCY -> computeDocumentFrequency(documentFeatures);
      case NONE -> throw new IllegalArgumentException("NONE strategy does not require feature selection");
    };

    if (maxFeatures <= 0 || maxFeatures >= scores.size()) {
      return scores.keySet();
    }

    return scores.entrySet().stream()
        .limit(maxFeatures)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  /**
   * Computes Information Gain scores.
   * <p>
   * {@code IG(F) = H(C) - H(C|F)} where {@code H(C)} is the class entropy and
   * {@code H(C|F)} is the conditional entropy given feature presence/absence.
   */
  static Map<String, Double> computeInformationGain(List<Set<String>> documentFeatures,
                                                     List<String> documentLabels) {
    int n = documentFeatures.size();

    Map<String, Integer> classCounts = new HashMap<>();
    for (String label : documentLabels) {
      classCounts.merge(label, 1, Integer::sum);
    }
    double classEntropy = entropy(classCounts, n);

    Set<String> allFeatures = collectAllFeatures(documentFeatures);
    Map<String, Double> scores = new HashMap<>();

    for (String feature : allFeatures) {
      Map<String, Integer> presentCounts = new HashMap<>();
      Map<String, Integer> absentCounts = new HashMap<>();
      int presentTotal = 0;

      for (int i = 0; i < n; i++) {
        String label = documentLabels.get(i);
        if (documentFeatures.get(i).contains(feature)) {
          presentCounts.merge(label, 1, Integer::sum);
          presentTotal++;
        } else {
          absentCounts.merge(label, 1, Integer::sum);
        }
      }

      int absentTotal = n - presentTotal;
      double condEntropy = 0.0;
      if (presentTotal > 0) {
        condEntropy += ((double) presentTotal / n) * entropy(presentCounts, presentTotal);
      }
      if (absentTotal > 0) {
        condEntropy += ((double) absentTotal / n) * entropy(absentCounts, absentTotal);
      }

      scores.put(feature, classEntropy - condEntropy);
    }

    return sortDescending(scores);
  }

  /**
   * Computes Chi-Square scores.
   * <p>
   * For each feature, computes the maximum chi-square statistic across all classes:
   * <pre>
   *   chi2(F, C) = N * (AD - BC)^2 / ((A+B)(C+D)(A+C)(B+D))
   * </pre>
   * where A = docs with F in class C, B = docs with F not in C,
   * C = docs without F in class C, D = docs without F not in C.
   */
  static Map<String, Double> computeChiSquare(List<Set<String>> documentFeatures,
                                               List<String> documentLabels) {
    int n = documentFeatures.size();

    Set<String> allClasses = new HashSet<>(documentLabels);
    Set<String> allFeatures = collectAllFeatures(documentFeatures);
    Map<String, Double> scores = new HashMap<>();

    for (String feature : allFeatures) {
      double maxChi2 = 0.0;

      for (String clazz : allClasses) {
        int a = 0; // feature present, class match
        int b = 0; // feature present, class no match
        int c = 0; // feature absent, class match
        int d = 0; // feature absent, class no match

        for (int i = 0; i < n; i++) {
          boolean hasFeature = documentFeatures.get(i).contains(feature);
          boolean isClass = documentLabels.get(i).equals(clazz);

          if (hasFeature && isClass) a++;
          else if (hasFeature) b++;
          else if (isClass) c++;
          else d++;
        }

        double numerator = (double) n * Math.pow((double) a * d - (double) b * c, 2);
        double denominator = (double) (a + b) * (c + d) * (a + c) * (b + d);
        if (denominator > 0) {
          maxChi2 = Math.max(maxChi2, numerator / denominator);
        }
      }

      scores.put(feature, maxChi2);
    }

    return sortDescending(scores);
  }

  /**
   * Computes total Term Frequency scores across the corpus.
   */
  static Map<String, Double> computeTermFrequency(List<Map<String, Integer>> documentTfs) {
    Map<String, Double> scores = new HashMap<>();
    for (Map<String, Integer> docTf : documentTfs) {
      for (Map.Entry<String, Integer> entry : docTf.entrySet()) {
        scores.merge(entry.getKey(), (double) entry.getValue(), Double::sum);
      }
    }
    return sortDescending(scores);
  }

  /**
   * Computes Document Frequency scores (number of documents containing each feature).
   */
  static Map<String, Double> computeDocumentFrequency(List<Set<String>> documentFeatures) {
    Map<String, Double> scores = new HashMap<>();
    for (Set<String> docFeats : documentFeatures) {
      for (String feature : docFeats) {
        scores.merge(feature, 1.0, Double::sum);
      }
    }
    return sortDescending(scores);
  }

  private static Set<String> collectAllFeatures(List<Set<String>> documentFeatures) {
    Set<String> all = new HashSet<>();
    for (Set<String> docFeats : documentFeatures) {
      all.addAll(docFeats);
    }
    return all;
  }

  private static double entropy(Map<String, Integer> counts, int total) {
    double h = 0.0;
    for (int count : counts.values()) {
      if (count > 0) {
        double p = (double) count / total;
        h -= p * Math.log(p);
      }
    }
    return h;
  }

  private static Map<String, Double> sortDescending(Map<String, Double> scores) {
    return scores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .collect(Collectors.toMap(
            Map.Entry::getKey, Map.Entry::getValue,
            (a, b) -> a, LinkedHashMap::new));
  }
}
