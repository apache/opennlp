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

import java.util.Arrays;

import opennlp.model.DataIndexer;

/**
 * maxent model trainer using l-bfgs algorithm.
 */
public class QNTrainer {
  // constants for optimization.
  private static final double CONVERGE_TOLERANCE = 1.0E-10;
  private static final int MAX_M = 15;
  public static final int DEFAULT_M = 7;
  public static final int MAX_FCT_EVAL = 3000;
  public static final int DEFAULT_MAX_FCT_EVAL = 300;

  // settings for objective function and optimizer.
  private int dimension;
  private int m;
  private int maxFctEval;
  private QNInfo updateInfo;
  private boolean verbose;

  // default constructor -- no log.
  public QNTrainer() {
    this(true);
  }

  // constructor -- to log.
  public QNTrainer(boolean verbose) {
	this(DEFAULT_M, verbose);
  }

  // constructor -- m : number of hessian updates to store.
  public QNTrainer(int m) {
    this(m, true);
  }

  // constructor -- to log, number of hessian updates to store.
  public QNTrainer(int m, boolean verbose) {
	this(m, DEFAULT_MAX_FCT_EVAL, verbose);
  }

  public QNTrainer(int m, int maxFctEval, boolean verbose) {
    this.verbose = verbose;
    if (m > MAX_M) {
      this.m = MAX_M;
    } else {
      this.m = m;
    }
    if (maxFctEval < 0) {
      this.maxFctEval = DEFAULT_MAX_FCT_EVAL;
    } else if (maxFctEval > MAX_FCT_EVAL) {
      this.maxFctEval = MAX_FCT_EVAL;
    } else {
      this.maxFctEval = maxFctEval;
    }
  }

  public QNModel trainModel(DataIndexer indexer) {
    LogLikelihoodFunction objectiveFunction = generateFunction(indexer);
    this.dimension = objectiveFunction.getDomainDimension();
    this.updateInfo = new QNInfo(this.m, this.dimension);

    double[] initialPoint = objectiveFunction.getInitialPoint();
    double initialValue = objectiveFunction.valueAt(initialPoint);
    double[] initialGrad = objectiveFunction.gradientAt(initialPoint);

    LineSearchResult lsr = LineSearchResult.getInitialObject(initialValue, initialGrad, initialPoint, 0);

    int z = 0;
    while (true) {
      if (verbose) {
        System.out.print(z++);
      }
      double[] direction = null;

      direction = computeDirection(objectiveFunction, lsr);
      lsr = LineSearch.doLineSearch(objectiveFunction, direction, lsr, verbose);
      
      updateInfo.updateInfo(lsr);
      
      if (isConverged(lsr)) 
        break;
    }
    return new QNModel(objectiveFunction, lsr.getNextPoint());
  }


  private LogLikelihoodFunction generateFunction(DataIndexer indexer) {
    return new LogLikelihoodFunction(indexer);
  }

  private double[] computeDirection(DifferentiableFunction monitor, LineSearchResult lsr) {
    // implemented two-loop hessian update method.
    double[] direction = lsr.getGradAtNext().clone();
    double[] as = new double[m];
  
    // first loop
    for (int i = updateInfo.kCounter - 1; i >= 0; i--) {
      as[i] = updateInfo.getRho(i) * ArrayMath.innerProduct(updateInfo.getS(i), direction);
      for (int ii = 0; ii < dimension; ii++) {
        direction[ii] = direction[ii] - as[i] * updateInfo.getY(i)[ii];
      }
    }

    // second loop
    for (int i = 0; i < updateInfo.kCounter; i++) {
      double b = updateInfo.getRho(i) * ArrayMath.innerProduct(updateInfo.getY(i), direction);
      for (int ii = 0; ii < dimension; ii++) {
        direction[ii] = direction[ii] + (as[i] - b) * updateInfo.getS(i)[ii];
      }
    }

    for (int i = 0; i < dimension; i++) {
      direction[i] *= -1.0;
    }

    return direction;
  }
  
  // FIXME need an improvement in convergence condition
  private boolean isConverged(LineSearchResult lsr) {
    return CONVERGE_TOLERANCE > Math.abs(lsr.getValueAtNext() - lsr.getValueAtCurr())
        || lsr.getFctEvalCount() > this.maxFctEval;
  }
  
  /**
   * class to store vectors for hessian approximation update.
   */
  private class QNInfo {
    private double[][] S;
    private double[][] Y;
    private double[] rho;
    private int m;
    private double[] diagonal;

    private int kCounter;

    // constructor
    QNInfo(int numCorrection, int dimension) {
      this.m = numCorrection;
      this.kCounter = 0;
      S = new double[this.m][];
      Y = new double[this.m][];
      rho = new double[this.m];
      Arrays.fill(rho, Double.NaN);
      diagonal = new double[dimension];
      Arrays.fill(diagonal, 1.0);
    }

    public void updateInfo(LineSearchResult lsr) {
      double[] s_k = new double[dimension];
      double[] y_k = new double[dimension];
      for (int i = 0; i < dimension; i++) {
        s_k[i] = lsr.getNextPoint()[i] - lsr.getCurrPoint()[i];
        y_k[i] = lsr.getGradAtNext()[i] - lsr.getGradAtCurr()[i];
      }
      this.updateSYRoh(s_k, y_k);
      kCounter = kCounter < m ? kCounter + 1 : kCounter;
    }

    private void updateSYRoh(double[] s_k, double[] y_k) {
      double newRoh = 1.0 / ArrayMath.innerProduct(y_k, s_k);
      // add new ones.
      if (kCounter < m) {
        S[kCounter] = s_k.clone();
        Y[kCounter] = y_k.clone();
        rho[kCounter] = newRoh;
      } else if (m > 0) {
      // discard oldest vectors and add new ones.
        for (int i = 0; i < m - 1; i++) {
          S[i] = S[i + 1];
          Y[i] = Y[i + 1];
          rho[i] = rho[i + 1];
        }
        S[m - 1] = s_k.clone();
        Y[m - 1] = y_k.clone();
        rho[m - 1] = newRoh;
      }
    }
    
    public double getRho(int updateIndex) {
      return this.rho[updateIndex];
    }
    
    public double[] getS(int updateIndex) {
      return S[updateIndex];
    }
    
    public double[] getY(int updateIndex) {
      return Y[updateIndex];
    }
  }
}