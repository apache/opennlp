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

package opennlp.tools.ml.maxent.quasinewton;

import java.util.Arrays;

import opennlp.tools.ml.ArrayMath;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.OnePassRealValueDataIndexer;

/**
 * Evaluate negative log-likelihood and its gradient from DataIndexer.
 */
public class NegLogLikelihood implements Function {

  protected int dimension;
  protected int numOutcomes;
  protected int numFeatures;
  protected int numContexts;

  // Information from data index
  protected final float[][] values;
  protected final int[][] contexts;
  protected final int[] outcomeList;
  protected final int[] numTimesEventsSeen;

  // For calculating negLogLikelihood and gradient
  protected double[] tempSums;
  protected double[] expectation;

  protected double[] gradient;

  public NegLogLikelihood(DataIndexer indexer) {

    // Get data from indexer.
    if (indexer instanceof OnePassRealValueDataIndexer) {
      this.values = indexer.getValues();
    } else {
      this.values = null;
    }

    this.contexts    = indexer.getContexts();
    this.outcomeList = indexer.getOutcomeList();
    this.numTimesEventsSeen = indexer.getNumTimesEventsSeen();

    this.numOutcomes = indexer.getOutcomeLabels().length;
    this.numFeatures = indexer.getPredLabels().length;
    this.numContexts = this.contexts.length;
    this.dimension   = numOutcomes * numFeatures;

    this.expectation = new double[numOutcomes];
    this.tempSums    = new double[numOutcomes];
    this.gradient    = new double[dimension];
  }

  public int getDimension() {
    return this.dimension;
  }

  public double[] getInitialPoint() {
    return new double[dimension];
  }

  /**
   * Negative log-likelihood
   */
  public double valueAt(double[] x) {

    if (x.length != dimension)
      throw new IllegalArgumentException(
          "x is invalid, its dimension is not equal to domain dimension.");

    int ci, oi, ai, vectorIndex, outcome;
    double predValue, logSumOfExps;
    double negLogLikelihood = 0;

    for (ci = 0; ci < numContexts; ci++) {
      for (oi = 0; oi < numOutcomes; oi++) {
        tempSums[oi] = 0;
        for (ai = 0; ai < contexts[ci].length; ai++) {
          vectorIndex = indexOf(oi, contexts[ci][ai]);
          predValue = values != null ? values[ci][ai] : 1.0;
          tempSums[oi] += predValue * x[vectorIndex];
        }
      }

      logSumOfExps = ArrayMath.logSumOfExps(tempSums);

      outcome = outcomeList[ci];
      negLogLikelihood -= (tempSums[outcome] - logSumOfExps) * numTimesEventsSeen[ci];
    }

    return negLogLikelihood;
  }

  /**
   * Compute gradient
   */
  public double[] gradientAt(double[] x) {

    if (x.length != dimension)
      throw new IllegalArgumentException(
          "x is invalid, its dimension is not equal to the function.");

    int ci, oi, ai, vectorIndex;
    double predValue, logSumOfExps;
    int empirical;

    // Reset gradient
    Arrays.fill(gradient, 0);

    for (ci = 0; ci < numContexts; ci++) {
      for (oi = 0; oi < numOutcomes; oi++) {
        expectation[oi] = 0;
        for (ai = 0; ai < contexts[ci].length; ai++) {
          vectorIndex = indexOf(oi, contexts[ci][ai]);
          predValue = values != null ? values[ci][ai] : 1.0;
          expectation[oi] += predValue * x[vectorIndex];
        }
      }

      logSumOfExps = ArrayMath.logSumOfExps(expectation);

      for (oi = 0; oi < numOutcomes; oi++) {
        expectation[oi] = Math.exp(expectation[oi] - logSumOfExps);
      }

      for (oi = 0; oi < numOutcomes; oi++) {
        empirical = outcomeList[ci] == oi ? 1 : 0;
        for (ai = 0; ai < contexts[ci].length; ai++) {
          vectorIndex = indexOf(oi, contexts[ci][ai]);
          predValue = values != null ? values[ci][ai] : 1.0;
          gradient[vectorIndex] +=
              predValue * (expectation[oi] - empirical) * numTimesEventsSeen[ci];
        }
      }
    }

    return gradient;
  }

  protected int indexOf(int outcomeId, int featureId) {
    return outcomeId * numFeatures + featureId;
  }
}
