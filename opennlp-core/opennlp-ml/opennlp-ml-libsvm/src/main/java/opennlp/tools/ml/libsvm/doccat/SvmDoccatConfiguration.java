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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import de.hhn.mi.configuration.SvmConfiguration;
import de.hhn.mi.configuration.SvmConfigurationImpl;

/**
 * Configuration for SVM-based document categorization, combining the underlying
 * SVM classifier settings with text-specific parameters for term weighting,
 * feature selection, and feature scaling.
 *
 * @see DocumentCategorizerSVM
 * @see TermWeightingStrategy
 * @see FeatureSelectionStrategy
 */
public class SvmDoccatConfiguration implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private final SvmConfiguration svmConfiguration;
  private final TermWeightingStrategy termWeightingStrategy;
  private final FeatureSelectionStrategy featureSelectionStrategy;
  private final int maxFeatures;
  private final boolean scaleFeatures;
  private final double scaleLower;
  private final double scaleUpper;

  private SvmDoccatConfiguration(Builder builder) {
    this.svmConfiguration = builder.svmConfiguration;
    this.termWeightingStrategy = builder.termWeightingStrategy;
    this.featureSelectionStrategy = builder.featureSelectionStrategy;
    this.maxFeatures = builder.maxFeatures;
    this.scaleFeatures = builder.scaleFeatures;
    this.scaleLower = builder.scaleLower;
    this.scaleUpper = builder.scaleUpper;
  }

  /**
   * @return The underlying SVM configuration for the classifier/trainer.
   */
  public SvmConfiguration getSvmConfiguration() {
    return svmConfiguration;
  }

  /**
   * @return The term weighting strategy used to compute feature values.
   */
  public TermWeightingStrategy getTermWeightingStrategy() {
    return termWeightingStrategy;
  }

  /**
   * @return The feature selection strategy used to reduce the feature space.
   */
  public FeatureSelectionStrategy getFeatureSelectionStrategy() {
    return featureSelectionStrategy;
  }

  /**
   * @return The maximum number of features to select. A value {@code <= 0} means
   *         all features are retained.
   */
  public int getMaxFeatures() {
    return maxFeatures;
  }

  /**
   * @return {@code true} if feature values should be scaled to the configured range.
   */
  public boolean isScaleFeatures() {
    return scaleFeatures;
  }

  /**
   * @return The lower bound of the scaling range (default: {@code 0.0}).
   */
  public double getScaleLower() {
    return scaleLower;
  }

  /**
   * @return The upper bound of the scaling range (default: {@code 1.0}).
   */
  public double getScaleUpper() {
    return scaleUpper;
  }

  /**
   * A builder for {@link SvmDoccatConfiguration}.
   */
  public static class Builder {

    private SvmConfiguration svmConfiguration = new SvmConfigurationImpl.Builder()
        .setProbability(true)
        .build();
    private TermWeightingStrategy termWeightingStrategy = TermWeightingStrategy.TF_IDF;
    private FeatureSelectionStrategy featureSelectionStrategy = FeatureSelectionStrategy.NONE;
    private int maxFeatures = -1;
    private boolean scaleFeatures = true;
    private double scaleLower = 0.0;
    private double scaleUpper = 1.0;

    /**
     * Sets the underlying SVM configuration.
     * <p>
     * Note: Probability estimates must be enabled in the SVM configuration
     * for the categorizer to produce probability distributions over categories.
     *
     * @param svmConfiguration The SVM configuration. Must not be {@code null}.
     * @return This builder.
     */
    public Builder setSvmConfiguration(SvmConfiguration svmConfiguration) {
      this.svmConfiguration = Objects.requireNonNull(svmConfiguration,
          "svmConfiguration must not be null");
      return this;
    }

    /**
     * Sets the term weighting strategy.
     *
     * @param strategy The weighting strategy. Must not be {@code null}.
     *                 Default: {@link TermWeightingStrategy#TF_IDF}.
     * @return This builder.
     */
    public Builder setTermWeightingStrategy(TermWeightingStrategy strategy) {
      this.termWeightingStrategy = Objects.requireNonNull(strategy,
          "strategy must not be null");
      return this;
    }

    /**
     * Sets the feature selection strategy.
     *
     * @param strategy The selection strategy. Must not be {@code null}.
     *                 Default: {@link FeatureSelectionStrategy#NONE}.
     * @return This builder.
     */
    public Builder setFeatureSelectionStrategy(FeatureSelectionStrategy strategy) {
      this.featureSelectionStrategy = Objects.requireNonNull(strategy,
          "strategy must not be null");
      return this;
    }

    /**
     * Sets the maximum number of features to retain after feature selection.
     * Only applicable when {@link FeatureSelectionStrategy} is not
     * {@link FeatureSelectionStrategy#NONE}.
     *
     * @param maxFeatures The maximum number of features. A value {@code <= 0}
     *                    means all features are retained. Default: {@code -1}.
     * @return This builder.
     */
    public Builder setMaxFeatures(int maxFeatures) {
      this.maxFeatures = maxFeatures;
      return this;
    }

    /**
     * Sets whether feature values should be scaled to the range
     * [{@code lower}, {@code upper}].
     *
     * @param scaleFeatures {@code true} to enable feature scaling. Default: {@code true}.
     * @return This builder.
     */
    public Builder setScaleFeatures(boolean scaleFeatures) {
      this.scaleFeatures = scaleFeatures;
      return this;
    }

    /**
     * Sets the lower and upper bounds of the feature scaling range.
     * Only applicable when {@link #setScaleFeatures(boolean)} is {@code true}.
     *
     * @param lower The lower bound. Default: {@code 0.0}.
     * @param upper The upper bound. Must be greater than {@code lower}.
     *              Default: {@code 1.0}.
     * @return This builder.
     * @throws IllegalArgumentException if {@code upper <= lower}.
     */
    public Builder setScaleRange(double lower, double upper) {
      if (upper <= lower) {
        throw new IllegalArgumentException("upper must be greater than lower");
      }
      this.scaleLower = lower;
      this.scaleUpper = upper;
      return this;
    }

    /**
     * @return A fully configured {@link SvmDoccatConfiguration}.
     */
    public SvmDoccatConfiguration build() {
      return new SvmDoccatConfiguration(this);
    }
  }
}
