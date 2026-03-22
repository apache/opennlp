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
 * Defines strategies for weighting term features in SVM-based text classification.
 * <p>
 * The weighting strategy determines how raw term occurrences are converted
 * into numeric feature values for the SVM feature vectors.
 *
 * @see DocumentCategorizerSVM
 */
public enum TermWeightingStrategy {

  /**
   * Binary weighting: {@code 1.0} if the term is present in the document,
   * {@code 0.0} otherwise. Ignores term frequency.
   */
  BINARY {
    @Override
    public double weight(int termFrequency, double inverseDocumentFrequency) {
      return termFrequency > 0 ? 1.0 : 0.0;
    }
  },

  /**
   * Raw term frequency: the number of times a term occurs in a document.
   */
  TERM_FREQUENCY {
    @Override
    public double weight(int termFrequency, double inverseDocumentFrequency) {
      return termFrequency;
    }
  },

  /**
   * TF-IDF (Term Frequency - Inverse Document Frequency):
   * {@code tf * log(N / df)}, where {@code N} is the total number of documents
   * and {@code df} is the number of documents containing the term.
   * <p>
   * This downweights terms that appear in many documents (common terms)
   * and upweights terms that are discriminative.
   */
  TF_IDF {
    @Override
    public double weight(int termFrequency, double inverseDocumentFrequency) {
      return termFrequency * inverseDocumentFrequency;
    }
  },

  /**
   * Logarithmically normalized term frequency: {@code 1 + log(tf)} for terms
   * that appear at least once, {@code 0.0} otherwise.
   * <p>
   * Dampens the effect of high term frequencies while still distinguishing
   * between present and absent terms.
   */
  LOG_NORMALIZED_TF {
    @Override
    public double weight(int termFrequency, double inverseDocumentFrequency) {
      return termFrequency > 0 ? 1.0 + Math.log(termFrequency) : 0.0;
    }
  };

  /**
   * Computes the feature weight for a term.
   *
   * @param termFrequency            The number of occurrences of the term in the document.
   * @param inverseDocumentFrequency The IDF value for the term (only used by {@link #TF_IDF}).
   * @return The computed feature weight.
   */
  public abstract double weight(int termFrequency, double inverseDocumentFrequency);
}
