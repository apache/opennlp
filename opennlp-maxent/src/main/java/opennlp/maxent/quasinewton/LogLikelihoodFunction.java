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
package opennlp.maxent.quasinewton;

import java.util.ArrayList;
import java.util.Arrays;

import opennlp.model.DataIndexer;
import opennlp.model.OnePassRealValueDataIndexer;

/**
 * Evaluate log likelihood and its gradient from DataIndexer.
 */
public class LogLikelihoodFunction implements DifferentiableFunction {
  private int domainDimension;
  private double value;
  private double[] gradient;
  private double[] lastX;
  private double[] empiricalCount;
  private int numOutcomes;
  private int numFeatures;
  private int numContexts;
  private double[][] probModel;

  private String[] outcomeLabels;
  private String[] predLabels;
  
  private int[][] outcomePatterns;

  // infos from data index;
  private final float[][] values;
  private final int[][] contexts;
  private final int[] outcomeList;
  private final int[] numTimesEventsSeen;

  public LogLikelihoodFunction(DataIndexer indexer) {
    // get data from indexer.
    if (indexer instanceof OnePassRealValueDataIndexer) {
      this.values = indexer.getValues();
    } else {
      this.values = null;
    }

    this.contexts = indexer.getContexts();
    this.outcomeList = indexer.getOutcomeList();
    this.numTimesEventsSeen = indexer.getNumTimesEventsSeen();

    this.outcomeLabels = indexer.getOutcomeLabels();
    this.predLabels = indexer.getPredLabels();

    this.numOutcomes = indexer.getOutcomeLabels().length;
    this.numFeatures = indexer.getPredLabels().length;
    this.numContexts = this.contexts.length;
    this.domainDimension = numOutcomes * numFeatures;
    this.probModel = new double[numContexts][numOutcomes];
    this.gradient = null;
  }

  public double valueAt(double[] x) {
    if (!checkLastX(x)) calculate(x);
    return value;
  }

  public double[] gradientAt(double[] x) {
    if (!checkLastX(x)) calculate(x);
    return gradient;
  }

  public int getDomainDimension() {
    return this.domainDimension;
  }

  public double[] getInitialPoint() {
    return new double[domainDimension];
  }

  public String[] getPredLabels() {
    return this.predLabels;
  }

  public String[] getOutcomeLabels() {
    return this.outcomeLabels;
  }
  
  public int[][] getOutcomePatterns() {
	  return this.outcomePatterns;
  }

  private void calculate(double[] x) {
    if (x.length != this.domainDimension) {
      throw new IllegalArgumentException("x is invalid, its dimension is not equal to the function.");
    }

    initProbModel();
    if (this.empiricalCount == null) 
      initEmpCount();

    // sum up log likelihood and empirical feature count for gradient calculation.
    double logLikelihood = 0.0;

    for (int ci = 0; ci < numContexts; ci++) {
      double voteSum = 0.0;

      for (int af = 0; af < this.contexts[ci].length; af++) {
        int vectorIndex = indexOf(this.outcomeList[ci], contexts[ci][af]);
        double predValue = 1.0;
        if (values != null) predValue = this.values[ci][af];
        if (predValue == 0.0) continue;

        voteSum += predValue * x[vectorIndex];
      }
      probModel[ci][this.outcomeList[ci]] = Math.exp(voteSum);

      double totalVote = 0.0;
      for (int i = 0; i < numOutcomes; i++) {
        totalVote += probModel[ci][i];
      }
      for (int i = 0; i < numOutcomes; i++) {
        probModel[ci][i] /= totalVote;
      }
      for (int i = 0; i < numTimesEventsSeen[ci]; i++) {
        logLikelihood += Math.log(probModel[ci][this.outcomeList[ci]]);
      }
    }
    this.value = logLikelihood;

    // calculate gradient.
    double[] expectedCount = new double[numOutcomes * numFeatures];
    for (int ci = 0; ci < numContexts; ci++) {
      for (int oi = 0; oi < numOutcomes; oi++) {
        for (int af = 0; af < contexts[ci].length; af++) {
          int vectorIndex = indexOf(oi, this.contexts[ci][af]);
          double predValue = 1.0;
          if (values != null) predValue = this.values[ci][af];
          if (predValue == 0.0) continue;

          expectedCount[vectorIndex] += predValue * probModel[ci][oi] * this.numTimesEventsSeen[ci];
        }
      }
    }

    double[] gradient = new double[domainDimension];
    for (int i = 0; i < numOutcomes * numFeatures; i++) { 
      gradient[i] = expectedCount[i] - this.empiricalCount[i]; 
    }
    this.gradient = gradient;

    // update last evaluated x.
    this.lastX = x.clone();
  }

  /**
   * @param x vector that represents point to evaluate at.
   * @return check x is whether last evaluated point or not.
   */
  private boolean checkLastX(double[] x) {
    if (this.lastX == null) return false;

    for (int i = 0; i < x.length; i++) {
      if (lastX[i] != x[i]) return false;
    }
    return true;
  }

  private int indexOf(int outcomeId, int featureId) {
    return outcomeId * numFeatures + featureId;
  }

  private void initProbModel() {
    for (int i = 0; i < this.probModel.length; i++) {
      Arrays.fill(this.probModel[i], 1.0);
    }
  }

  private void initEmpCount() {
    this.empiricalCount = new double[numOutcomes * numFeatures];
    this.outcomePatterns = new int[predLabels.length][];
    
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
    
    for (int fi = 0; fi < this.outcomePatterns.length; fi++) {
        ArrayList<Integer> pattern = new ArrayList<Integer>();
        for (int oi = 0; oi < outcomeLabels.length; oi++) {
      	int countIndex = fi + (this.predLabels.length * oi);
      	if (this.empiricalCount[countIndex] > 0) {
      		pattern.add(oi);
      	}
        }
        outcomePatterns[fi] = new int[pattern.size()];
        for (int i = 0; i < pattern.size(); i++) {
         	outcomePatterns[fi][i] = pattern.get(i);
        }
      }
  }
}