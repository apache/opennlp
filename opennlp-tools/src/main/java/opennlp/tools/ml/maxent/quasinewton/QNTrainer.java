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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.maxent.quasinewton.QNMinimizer.Evaluator;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.DataIndexer;

/**
 * Maxent model trainer using L-BFGS algorithm.
 */
public class QNTrainer extends AbstractEventTrainer {

  public static final String MAXENT_QN_VALUE = "MAXENT_QN";
  
  public static final String THREADS_PARAM = "Threads";
  public static final int THREADS_DEFAULT  = 1;
  
  public static final String L1COST_PARAM   = "L1Cost";
  public static final double L1COST_DEFAULT = 0.1; 
  
  public static final String L2COST_PARAM   = "L2Cost";
  public static final double L2COST_DEFAULT = 0.1; 
  
  // Number of Hessian updates to store
  public static final String M_PARAM = "NumOfUpdates";
  public static final int M_DEFAULT  = 15;
  
  // Maximum number of function evaluations
  public static final String MAX_FCT_EVAL_PARAM = "MaxFctEval";
  public static final int MAX_FCT_EVAL_DEFAULT  = 30000;

  // Number of threads
  private int threads;
  
  // L1-regularization cost
  private double l1Cost;
  
  // L2-regularization cost
  private double l2Cost;
  
  // Settings for QNMinimizer
  private int m;
  private int maxFctEval;
  private boolean verbose = true;

  // Constructor -- to log. For testing purpose
  public QNTrainer(boolean verbose) {
    this(M_DEFAULT, verbose);
  }

  // Constructor -- m : number of hessian updates to store. For testing purpose
  public QNTrainer(int m) {
    this(m, true);
  }

  // Constructor -- to log, number of hessian updates to store. For testing purpose
  public QNTrainer(int m, boolean verbose) {
    this(m, MAX_FCT_EVAL_DEFAULT, verbose);
  }

  // For testing purpose
  public QNTrainer(int m, int maxFctEval, boolean verbose) {
    this.verbose    = verbose;
    this.m          = m < 0? M_DEFAULT: m;
    this.maxFctEval = maxFctEval < 0? MAX_FCT_EVAL_DEFAULT: maxFctEval;
    this.threads    = THREADS_DEFAULT;
    this.l1Cost     = L1COST_DEFAULT;
    this.l2Cost     = L2COST_DEFAULT;
  }

  // >> Members related to AbstractEventTrainer
  public QNTrainer() {
  }

  public boolean isValid() {

    if (!super.isValid()) {
      return false;
    }

    String algorithmName = getAlgorithm();
    if (algorithmName != null && !(MAXENT_QN_VALUE.equals(algorithmName))) {
      return false;
    }

    // Number of Hessian updates to remember
    int m = getIntParam(M_PARAM, M_DEFAULT);
    if (m < 0) {
      return false;
    }
    this.m = m;
    
    // Maximum number of function evaluations
    int maxFctEval = getIntParam(MAX_FCT_EVAL_PARAM, MAX_FCT_EVAL_DEFAULT);
    if (maxFctEval < 0) {
      return false;
    }
    this.maxFctEval = maxFctEval;
    
    // Number of threads must be >= 1
    int threads = getIntParam(THREADS_PARAM, THREADS_DEFAULT);
    if (threads < 1) {
      return false;
    }
    this.threads = threads;
    
    // Regularization costs must be >= 0
    double l1Cost = getDoubleParam(L1COST_PARAM, L1COST_DEFAULT);
    if (l1Cost < 0) {
      return false;
    }
    this.l1Cost = l1Cost;
    
    double l2Cost = getDoubleParam(L2COST_PARAM, L2COST_DEFAULT); 
    if (l2Cost < 0) {
      return false;
    }
    this.l2Cost = l2Cost;
    
    return true;
  }

  public boolean isSortAndMerge() {
    return true;
  }

  public AbstractModel doTrain(DataIndexer indexer) throws IOException {
    int iterations = getIterations();
    return trainModel(iterations, indexer);
  }

  // << Members related to AbstractEventTrainer
  public QNModel trainModel(int iterations, DataIndexer indexer) {
    
    // Train model's parameters
    Function objectiveFunction = null;
    if (threads == 1) {
      System.out.println("Computing model parameters ...");
      objectiveFunction = new NegLogLikelihood(indexer);
    } else {
      System.out.println("Computing model parameters in " + threads + " threads ...");
      objectiveFunction = new ParallelNegLogLikelihood(indexer, threads);
    }
    
    QNMinimizer minimizer = new QNMinimizer(
        l1Cost, l2Cost, iterations, m, maxFctEval, verbose);
    minimizer.setEvaluator(new ModelEvaluator(indexer));

    double[] parameters = minimizer.minimize(objectiveFunction);

    // Construct model with trained parameters
    String[] predLabels = indexer.getPredLabels(); 
    int nPredLabels = predLabels.length;

    String[] outcomeNames = indexer.getOutcomeLabels();
    int nOutcomes = outcomeNames.length;
    
    Context[] params = new Context[nPredLabels];
    for (int ci = 0; ci < params.length; ci++) {
      List<Integer> outcomePattern = new ArrayList<Integer>(nOutcomes);
      List<Double> alpha = new ArrayList<Double>(nOutcomes); 
      for (int oi = 0; oi < nOutcomes; oi++) {
        double val = parameters[oi * nPredLabels + ci];
        outcomePattern.add(oi);
        alpha.add(val);
      }
      params[ci] = new Context(ArrayMath.toIntArray(outcomePattern), 
          ArrayMath.toDoubleArray(alpha));
    }
    
    return new QNModel(params, predLabels, outcomeNames);
  }

  /**
   * For measuring model's training accuracy
   */
  private class ModelEvaluator implements Evaluator {

    private DataIndexer indexer;

    public ModelEvaluator(DataIndexer indexer) {
      this.indexer = indexer;
    }

    /**
     * Evaluate the current model on training data set 
     * @return model's training accuracy
     */
    @Override
    public double evaluate(double[] parameters) {
      int[][] contexts  = indexer.getContexts();
      float[][] values  = indexer.getValues();
      int[] nEventsSeen = indexer.getNumTimesEventsSeen();
      int[] outcomeList = indexer.getOutcomeList(); 
      int nOutcomes     = indexer.getOutcomeLabels().length;
      int nPredLabels   = indexer.getPredLabels().length;
      
      int nCorrect     = 0;
      int nTotalEvents = 0;
      
      for (int ei = 0; ei < contexts.length; ei++) {
        int[] context  = contexts[ei];
        float[] value  = values == null? null: values[ei];
        
        double[] probs = new double[nOutcomes];
        QNModel.eval(context, value, probs, nOutcomes, nPredLabels, parameters);
        int outcome = ArrayMath.maxIdx(probs);
        if (outcome == outcomeList[ei]) {
          nCorrect += nEventsSeen[ei];
        }
        nTotalEvents += nEventsSeen[ei];
      }
      
      return (double) nCorrect / nTotalEvents;
    }
  }
}