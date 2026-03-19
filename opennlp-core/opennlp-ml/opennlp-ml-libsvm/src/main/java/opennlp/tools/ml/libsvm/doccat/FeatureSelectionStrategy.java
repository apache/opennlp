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

/**
 * Defines strategies for selecting the most informative features for
 * SVM-based text classification.
 * <p>
 * Feature selection reduces the dimensionality of the feature space by
 * retaining only the features that are most useful for distinguishing
 * between categories.
 *
 * @see DocumentCategorizerSVM
 * @see FeatureSelection
 */
public enum FeatureSelectionStrategy {

  /**
   * No feature selection: all features from the vocabulary are used.
   */
  NONE,

  /**
   * Information Gain based feature selection: features are ranked by their
   * information gain score, and only the top-k features are retained.
   * <p>
   * Information gain measures the reduction in entropy of the class variable
   * achieved by observing the presence or absence of a feature.
   */
  INFORMATION_GAIN,

  /**
   * Chi-Square based feature selection: features are ranked by the maximum
   * chi-square statistic across all categories, and only the top-k features
   * are retained.
   * <p>
   * Chi-square measures the statistical dependence between a feature and
   * a class label. A high chi-square value indicates that the feature
   * and the class are not independent.
   */
  CHI_SQUARE,

  /**
   * Term Frequency based feature selection: features are ranked by their
   * total occurrence count across all documents in the corpus, and only
   * the top-k features are retained.
   * <p>
   * This is a simple baseline strategy that favors frequent terms.
   * It can be useful to filter out very rare features that may be noise.
   */
  TERM_FREQUENCY,

  /**
   * Document Frequency based feature selection: features are ranked by the
   * number of documents they appear in, and only the top-k features are
   * retained.
   * <p>
   * Unlike {@link #TERM_FREQUENCY}, this counts each feature at most once
   * per document, regardless of how often it occurs within that document.
   */
  DOCUMENT_FREQUENCY
}
