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
public class NegLogLikelihoodFunction implements DifferentiableFunction {
  
  private int domainDimension;
  private double[] empiricalCount;
  private int numOutcomes;
  private int numFeatures;
  private int numContexts;

  // Information from data index
  private final float[][] values;
  private final int[][] contexts;
  private final int[] outcomeList;
  private final int[] numTimesEventsSeen;

  // L2-regularization cost
  private double l2Cost;
  
  // For computing log-likelihood
  private double[][] voteSum;
  private double[] logSumExp;
  
  // For gradient computation
  private double[] gradient;
  private double[] expectedCount;
  
  public NegLogLikelihoodFunction(DataIndexer indexer) {
    this(indexer, QNTrainer.L2COST_DEFAULT);
  }
  
  public NegLogLikelihoodFunction(DataIndexer indexer, double l2Cost) {
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
    this.domainDimension = numOutcomes * numFeatures;
    this.empiricalCount = new double[domainDimension];

    this.l2Cost = l2Cost;
    
    this.voteSum   = new double[numContexts][numOutcomes];
    this.logSumExp = new double[numContexts];
    
    this.gradient      = new double[domainDimension];
    this.expectedCount = new double[domainDimension];
    
    initEmpCount();
  }

  public int getDomainDimension() {
    return this.domainDimension;
  }

  public double[] getInitialPoint() {
    return new double[domainDimension];
  }
  
  /**
   * Negative log-likelihood
   */
  public double valueAt(double[] x) {
    
    if (x.length != this.domainDimension) {
      throw new IllegalArgumentException("x is invalid, its dimension is not equal to domain dimension.");
    }

    double negLogLikelihood = 0.0;

    for (int ci = 0; ci < numContexts; ci++) {
      for (int oi = 0; oi < numOutcomes; oi++) {
        double vecProduct = 0.0;
        for (int af = 0; af < this.contexts[ci].length; af++) {
          int vectorIndex = indexOf(oi, contexts[ci][af]);
          double predValue = 1.0;
          if (values != null) predValue = this.values[ci][af];
          if (predValue == 0.0) continue;
          vecProduct += predValue * x[vectorIndex];
        }
        voteSum[ci][oi] = vecProduct;
      }

      // \log(\sum_{c'=1}^{C} e^{w_c'^T x_i})
      logSumExp[ci] = ArrayMath.logSumOfExps(voteSum[ci]);
      
      int outcome = this.outcomeList[ci];
      negLogLikelihood += (voteSum[ci][outcome] - logSumExp[ci]) * numTimesEventsSeen[ci];
    }

    negLogLikelihood = -negLogLikelihood;
    
    if (l2Cost > 0) {
      for (int i = 0; i < x.length; i++) {
        negLogLikelihood += l2Cost * x[i] * x[i];
      }
    }
    
    return negLogLikelihood;
  }  

  /**
   * Compute gradient. <br>For the same value x, gradientAt(x) must be called after 
   * valueAt(x) is called. <br>Otherwise, the output will be incorrect.   
   */
  public double[] gradientAt(double[] x) {
    
    if (x.length != this.domainDimension) {
      throw new IllegalArgumentException("x is invalid, its dimension is not equal to the function.");
    }
    
    /**
     * Here, we assume that valueAt(x) is called before this function
     * so that we can reuse voteSum and logSumExp computed in the function valueAt(x) 
     */
    
    // Reset
    Arrays.fill(expectedCount, 0);
    for (int ci = 0; ci < numContexts; ci++) {
      for (int oi = 0; oi < numOutcomes; oi++) {
        for (int af = 0; af < contexts[ci].length; af++) {
          int vectorIndex = indexOf(oi, this.contexts[ci][af]);
          double predValue = 1.0;
          if (values != null) predValue = this.values[ci][af];
          if (predValue == 0.0) continue;

          expectedCount[vectorIndex] += 
              predValue * Math.exp(voteSum[ci][oi] - logSumExp[ci]) * this.numTimesEventsSeen[ci];
        }
      }
    }

    if (l2Cost > 0) {
      for (int i = 0; i < domainDimension; i++) { 
        gradient[i] = expectedCount[i] - this.empiricalCount[i] + 2 * l2Cost * x[i];
      }
    } 
    else {
      for (int i = 0; i < domainDimension; i++) { 
        gradient[i] = expectedCount[i] - this.empiricalCount[i];
      }
    }
    
    return gradient;
  }

  private int indexOf(int outcomeId, int featureId) {
    return outcomeId * numFeatures + featureId;
  }

  private void initEmpCount() {
    for (int ci = 0; ci < numContexts; ci++) {
      for (int af = 0; af < this.contexts[ci].length; af++) {
        int vectorIndex = indexOf(this.outcomeList[ci], contexts[ci][af]);
        if (values != null) {
          empiricalCount[vectorIndex] += this.values[ci][af] * numTimesEventsSeen[ci];
        } else {
          empiricalCount[vectorIndex] += 1.0 * numTimesEventsSeen[ci];
        }
      }
    }
  }
}