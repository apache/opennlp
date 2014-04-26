/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package opennlp.tools.ml.maxent.quasinewton;

import java.util.Arrays;

import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.OnePassRealValueDataIndexer;

/**
 * Evaluate negative log-likelihood and its gradient from DataIndexer.
 */
public class NegLogLikelihood implements Function {
  
  private int dimension;
  private double[] empiricalCount;
  private int numOutcomes;
  private int numFeatures;
  private int numContexts;

  // Information from data index
  private final float[][] values;
  private final int[][] contexts;
  private final int[] outcomeList;
  private final int[] numTimesEventsSeen;

  // For computing negative log-likelihood
  private double[][] voteSum;
  private double[] logSumExp;
  
  // For gradient computation
  private double[] gradient;
  private double[] expectedCount;
  
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

    this.numOutcomes    = indexer.getOutcomeLabels().length;
    this.numFeatures    = indexer.getPredLabels().length;
    this.numContexts    = this.contexts.length;
    this.dimension      = numOutcomes * numFeatures;
    this.empiricalCount = new double[dimension];

    this.voteSum   = new double[numContexts][numOutcomes];
    this.logSumExp = new double[numContexts];
    
    this.gradient      = new double[dimension];
    this.expectedCount = new double[dimension];
    
    computeEmpCount();
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
    
    if (x.length != this.dimension)
      throw new IllegalArgumentException(
          "x is invalid, its dimension is not equal to domain dimension.");

    computeSums(x); // Compute voteSum and logSumExp
    
    double negLogLikelihood = 0.;
    for (int ci = 0; ci < numContexts; ci++) {
      int outcome = this.outcomeList[ci];
      negLogLikelihood += (voteSum[ci][outcome] - logSumExp[ci]) * numTimesEventsSeen[ci];
    }
    negLogLikelihood = -negLogLikelihood;
    
    return negLogLikelihood;
  }  

  /**
   * Compute gradient
   */
  public double[] gradientAt(double[] x) {
    
    if (x.length != this.dimension)
      throw new IllegalArgumentException(
          "x is invalid, its dimension is not equal to the function.");
    
    computeSums(x); // Compute voteSum and logSumExp
    
    // Reset
    Arrays.fill(expectedCount, 0);
    for (int ci = 0; ci < numContexts; ci++) {
      for (int oi = 0; oi < numOutcomes; oi++) {
        for (int af = 0; af < contexts[ci].length; af++) {
          int vectorIndex = indexOf(oi, this.contexts[ci][af]);
          double predValue = 1.;
          if (values != null) predValue = this.values[ci][af];
          if (predValue == 0.) continue;

          expectedCount[vectorIndex] += 
              predValue * Math.exp(voteSum[ci][oi] - logSumExp[ci]) * this.numTimesEventsSeen[ci];
        }
      }
    }

    for (int i = 0; i < dimension; i++) { 
      gradient[i] = expectedCount[i] - this.empiricalCount[i];
    }
    
    return gradient;
  }

  private int indexOf(int outcomeId, int featureId) {
    return outcomeId * numFeatures + featureId;
  }

  /**
   * Compute temporary values
   */
  private void computeSums(double[] x) {
    for (int ci = 0; ci < numContexts; ci++) {
      for (int oi = 0; oi < numOutcomes; oi++) {
        double vecProduct = 0.;
        for (int af = 0; af < this.contexts[ci].length; af++) {
          int vectorIndex = indexOf(oi, contexts[ci][af]);
          double predValue = 1.;
          if (values != null) predValue = this.values[ci][af];
          if (predValue == 0.) continue;
          vecProduct += predValue * x[vectorIndex];
        }
        voteSum[ci][oi] = vecProduct;
      }

      // \log(\sum_{c'=1}^{C} e^{w_c'^T x_i})
      logSumExp[ci] = ArrayMath.logSumOfExps(voteSum[ci]);
    }
  }
  
  /**
   * Compute empirical count
   */
  private void computeEmpCount() {
    for (int ci = 0; ci < numContexts; ci++) {
      for (int af = 0; af < this.contexts[ci].length; af++) {
        int vectorIndex = indexOf(this.outcomeList[ci], contexts[ci][af]);
        if (values != null) {
          empiricalCount[vectorIndex] += this.values[ci][af] * numTimesEventsSeen[ci];
        } else {
          empiricalCount[vectorIndex] += 1. * numTimesEventsSeen[ci];
        }
      }
    }
  }
}