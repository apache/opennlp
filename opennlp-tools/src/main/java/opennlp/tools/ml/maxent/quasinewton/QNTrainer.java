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
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.DataIndexer;

/**
 * Maxent model trainer using L-BFGS algorithm.
 */
public class QNTrainer extends AbstractEventTrainer {

  public static final String MAXENT_QN_VALUE = "MAXENT_QN_EXPERIMENTAL";

  // function change rate tolerance
  private static final double CONVERGE_TOLERANCE = 1e-4;
  
  // relative gradient norm tolerance. Currently not being used.
  private static final boolean USE_REL_GRAD_NORM = false;
  private static final double REL_GRAD_NORM_TOL = 1e-8; 

  // minimum step size
  public static final double MIN_STEP_SIZE = 1e-10;
  
  public static final String L2COST_PARAM = "L2Cost";
  public static final double L2COST_DEFAULT = 1.0; 
  
  // number of Hessian updates to store
  private static final String M_PARAM = "numOfUpdates";
  private static final int M_DEFAULT = 15;
  
  private static final String MAX_FCT_EVAL_PARAM = "maxFctEval";
  private static final int MAX_FCT_EVAL_DEFAULT = 30000;

  // L2-regularization cost
  private double l2Cost;
  
  // settings for objective function and optimizer.
  private int dimension;
  private int m;
  private int maxFctEval;
  private double initialGradNorm;
  private QNInfo updateInfo;
  private boolean verbose = true;

  // constructor -- to log. For testing purpose
  public QNTrainer(boolean verbose) {
    this(M_DEFAULT, verbose);
  }

  // constructor -- m : number of hessian updates to store. For testing purpose
  public QNTrainer(int m) {
    this(m, true);
  }

  // constructor -- to log, number of hessian updates to store. For testing purpose
  public QNTrainer(int m, boolean verbose) {
    this(m, MAX_FCT_EVAL_DEFAULT, verbose);
  }

  // for testing purpose
  public QNTrainer(int m, int maxFctEval, boolean verbose) {
    this.verbose    = verbose;
    this.m          = m < 0? M_DEFAULT: m;
    this.maxFctEval = maxFctEval < 0? MAX_FCT_EVAL_DEFAULT: maxFctEval;
    this.l2Cost     = L2COST_DEFAULT;
  }

  // >> members related to AbstractEventTrainer
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
    
    // L2-regularization cost must be >= 0
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

  // << members related to AbstractEventTrainer

  public QNModel trainModel(int iterations, DataIndexer indexer) {
    NegLogLikelihoodFunction objectiveFunction = new NegLogLikelihoodFunction(indexer, l2Cost);
    this.dimension  = objectiveFunction.getDomainDimension();
    this.updateInfo = new QNInfo(this.m, this.dimension);

    // current point is at the origin
    double[] currPoint = new double[dimension];
    
    double currValue = objectiveFunction.valueAt(currPoint);
    
    // gradient at the current point
    double[] currGrad = new double[dimension]; 
    System.arraycopy(objectiveFunction.gradientAt(currPoint), 0, 
        currGrad, 0, dimension);
    
    // initial L2-norm of the gradient
    this.initialGradNorm = ArrayMath.norm(currGrad); 
    
    LineSearchResult lsr = LineSearchResult.getInitialObject(
        currValue, currGrad, currPoint, 0);

    if (verbose) 
      display("\nPerforming " + iterations + " iterations with " +
      		"L2-cost = " + l2Cost + "\n");
    
    double[] direction = new double[this.dimension];
    long startTime = System.currentTimeMillis();
    
    for (int iter = 1; iter <= iterations; iter++) {
      computeDirection(lsr, direction);
      LineSearch.doLineSearch(objectiveFunction, direction, lsr);
      updateInfo.updateInfo(lsr);
      
      if (verbose) {
        double accurarcy = evaluateModel(indexer, lsr.getNextPoint());
        if (iter < 10)
          display("  " + iter + ":  ");
        else if (iter < 100)
          display(" " + iter + ":  ");
        else
          display(iter + ":  ");
        
        display("\t " + lsr.getValueAtCurr());
        display("\t" + lsr.getFuncChangeRate());
        display("\t" + accurarcy);
        display("\n");
      }
      if (isConverged(lsr))
        break;
    }
    
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    display("Training time: " + (duration / 1000.) + "s\n");
    
    double[] parameters = lsr.getNextPoint();
    
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
        // Only save data corresponding to non-zero values
        if (val != 0) {
          outcomePattern.add(oi);
          alpha.add(val);
        }
      }
      params[ci] = new Context(ArrayMath.toIntArray(outcomePattern), 
          ArrayMath.toDoubleArray(alpha));
    }
    
    return new QNModel(params, predLabels, outcomeNames);
  }

  /**
   * L-BFGS two-loop recursion (see Nocedal & Wright 2006, Numerical Optimization, p. 178) 
   */
  private void computeDirection(LineSearchResult lsr, double[] direction) {
    
    // implemented two-loop Hessian update method.
    System.arraycopy(lsr.getGradAtNext(), 0, direction, 0, direction.length);

    int k = updateInfo.kCounter;
    double[] rho    = updateInfo.rho;
    double[] alpha  = updateInfo.alpha; // just to avoid recreating alpha
    double[][] S    = updateInfo.S;
    double[][] Y    = updateInfo.Y;
    
    // first loop
    for (int i = k - 1; i >= 0; i--) {
      alpha[i] = rho[i] * ArrayMath.innerProduct(S[i], direction);
      for (int j = 0; j < dimension; j++) {
        direction[j] = direction[j] - alpha[i] * Y[i][j];
      }
    }

    // second loop
    for (int i = 0; i < k; i++) {
      double beta = rho[i] * ArrayMath.innerProduct(Y[i], direction);
      for (int j = 0; j < dimension; j++) {
        direction[j] = direction[j] + S[i][j] * (alpha[i] - beta);
      }
    }

    for (int i = 0; i < dimension; i++) {
      direction[i] = -direction[i];
    }
  }
  
  // TODO: Need an improvement in convergence condition
  private boolean isConverged(LineSearchResult lsr) {
    
      if (lsr.getFuncChangeRate() < CONVERGE_TOLERANCE) {
        if (verbose)
          display("Function change rate is smaller than the threshold " 
                    + CONVERGE_TOLERANCE + ".\nTraining will stop.\n\n");
        return true;
      }
      
      if (USE_REL_GRAD_NORM) {
        double gradNorm = ArrayMath.norm(lsr.getGradAtNext());
        if (gradNorm / initialGradNorm < REL_GRAD_NORM_TOL) {
          if (verbose)
            display("Relative L2-norm of the gradient is smaller than the threshold " 
                + REL_GRAD_NORM_TOL + ".\nTraining will stop.\n\n");
          return true;
        }
      }
      
      if (lsr.getStepSize() < MIN_STEP_SIZE) {
        if (verbose) 
          display("Step size is smaller than the minimum step size " 
              + MIN_STEP_SIZE + ".\nTraining will stop.\n\n");
        return true;
      }
        
      if (lsr.getFctEvalCount() > this.maxFctEval) {
        if (verbose)
          display("Maximum number of function evaluations has exceeded the threshold " 
              + this.maxFctEval + ".\nTraining will stop.\n\n");
        return true;
      }
    
    return false;  
  }
  
  /**
   * Evaluate the current model on training data set 
   * @return model's training accuracy
   */
  private double evaluateModel(DataIndexer indexer, double[] parameters) {
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
  
  /**
   * Shorthand for System.out.print
   */
  private void display(String s) {
    System.out.print(s);
  }
  
  /**
   * Class to store vectors for Hessian approximation update.
   */
  private class QNInfo {
    private double[][] S;
    private double[][] Y;
    private double[] rho;
    private double[] alpha;
    private int m;

    private int kCounter;

    // constructor
    QNInfo(int numCorrection, int dimension) {
      this.m = numCorrection;
      this.kCounter = 0;
      S     = new double[this.m][dimension];
      Y     = new double[this.m][dimension];
      rho   = new double[this.m];
      alpha = new double[this.m];
    }
    
    public void updateInfo(LineSearchResult lsr) {
      double[] currPoint  = lsr.getCurrPoint();
      double[] gradAtCurr = lsr.getGradAtCurr(); 
      double[] nextPoint  = lsr.getNextPoint();
      double[] gradAtNext = lsr.getGradAtNext(); 
      
      // inner product of S_k and Y_k
      double SYk = 0.0; 
      
      // add new ones.
      if (kCounter < m) {
        for (int j = 0; j < dimension; j++) {
          S[kCounter][j] = nextPoint[j] - currPoint[j];
          Y[kCounter][j] = gradAtNext[j] - gradAtCurr[j];
          SYk += S[kCounter][j] * Y[kCounter][j];
        }
        rho[kCounter] = 1.0 / SYk;
      } 
      else if (m > 0) {
        // discard oldest vectors and add new ones.
        for (int i = 0; i < m - 1; i++) {
          S[i] = S[i + 1];
          Y[i] = Y[i + 1];
          rho[i] = rho[i + 1];
        }
        for (int j = 0; j < dimension; j++) {
          S[m - 1][j] = nextPoint[j] - currPoint[j];
          Y[m - 1][j] = gradAtNext[j] - gradAtCurr[j];
          SYk += S[m - 1][j] * Y[m - 1][j];  
        }
        rho[m - 1] = 1.0 / SYk;
      }
      
      if (kCounter < m) 
        kCounter++;
    }
  }
}