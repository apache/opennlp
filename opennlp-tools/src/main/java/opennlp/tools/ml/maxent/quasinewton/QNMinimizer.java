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

import opennlp.tools.ml.maxent.quasinewton.LineSearch.LineSearchResult;

/**
 * Implementation of L-BFGS which supports L1-, L2-regularization
 * and Elastic Net for solving convex optimization problems. <p>
 * Usage example:
 * <blockquote><pre>
 *  // Quadratic function f(x) = (x-1)^2 + 10
 *  // f obtains its minimum value 10 at x = 1
 *  Function f = new Function() {
 *
 *    {@literal @}Override
 *    public int getDimension() {
 *      return 1;
 *    }
 *
 *    {@literal @}Override
 *    public double valueAt(double[] x) {
 *      return Math.pow(x[0]-1, 2) + 10;
 *    }
 *
 *    {@literal @}Override
 *    public double[] gradientAt(double[] x) {
 *      return new double[] { 2*(x[0]-1) };
 *    }
 *
 *  };
 *
 *  QNMinimizer minimizer = new QNMinimizer();
 *  double[] x = minimizer.minimize(f);
 *  double min = f.valueAt(x);
 * </pre></blockquote>
 */
public class QNMinimizer {

  // Function change rate tolerance
  public static final double CONVERGE_TOLERANCE = 1e-4;

  // Relative gradient norm tolerance
  public static final double REL_GRAD_NORM_TOL = 1e-4;

  // Initial step size
  public static final double INITIAL_STEP_SIZE = 1.0;

  // Minimum step size
  public static final double MIN_STEP_SIZE = 1e-10;

  // Default L1-cost
  public static final double L1COST_DEFAULT = 0;

  // Default L2-cost
  public static final double L2COST_DEFAULT = 0;

  // Default number of iterations
  public static final int NUM_ITERATIONS_DEFAULT = 100;

  // Default number of Hessian updates to store
  public static final int M_DEFAULT = 15;

  // Default maximum number of function evaluations
  public static final int MAX_FCT_EVAL_DEFAULT = 30000;

  // L1-regularization cost
  private double l1Cost;

  // L2-regularization cost
  private double l2Cost;

  // Maximum number of iterations
  private int iterations;

  // Number of Hessian updates to store
  private int m;

  // Maximum number of function evaluations
  private int maxFctEval;

  // Verbose output
  private boolean verbose;

  // Objective function's dimension
  private int dimension;

  // Hessian updates
  private UpdateInfo updateInfo;

  // For evaluating quality of training parameters.
  // This is optional and can be omitted.
  private Evaluator evaluator;

  public QNMinimizer() {
    this(L1COST_DEFAULT, L2COST_DEFAULT);
  }

  public QNMinimizer(double l1Cost, double l2Cost) {
    this(l1Cost, l2Cost, NUM_ITERATIONS_DEFAULT);
  }

  public QNMinimizer(double l1Cost, double l2Cost, int iterations) {
    this(l1Cost, l2Cost, iterations, M_DEFAULT, MAX_FCT_EVAL_DEFAULT);
  }

  public QNMinimizer(double l1Cost, double l2Cost,
      int iterations, int m, int maxFctEval) {
    this(l1Cost, l2Cost, iterations, m, maxFctEval, true);
  }

  /**
   * Constructor
   * @param l1Cost L1-regularization cost
   * @param l2Cost L2-regularization cost
   * @param iterations maximum number of iterations
   * @param m number of Hessian updates to store
   * @param maxFctEval maximum number of function evaluations
   * @param verbose verbose output
   */
  public QNMinimizer(double l1Cost, double l2Cost, int iterations,
      int m, int maxFctEval, boolean verbose)
  {
    // Check arguments
    if (l1Cost < 0 || l2Cost < 0)
      throw new IllegalArgumentException(
          "L1-cost and L2-cost must not be less than zero");

    if (iterations <= 0)
      throw new IllegalArgumentException(
          "Number of iterations must be larger than zero");

    if (m <= 0)
      throw new IllegalArgumentException(
          "Number of Hessian updates must be larger than zero");

    if (maxFctEval <= 0)
      throw new IllegalArgumentException(
          "Maximum number of function evaluations must be larger than zero");

    this.l1Cost     = l1Cost;
    this.l2Cost     = l2Cost;
    this.iterations = iterations;
    this.m          = m;
    this.maxFctEval = maxFctEval;
    this.verbose    = verbose;
  }

  public Evaluator getEvaluator() {
    return evaluator;
  }

  public void setEvaluator(Evaluator evaluator) {
    this.evaluator = evaluator;
  }

  /**
   * Find the parameters that minimize the objective function
   * @param function objective function
   * @return minimizing parameters
   */
  public double[] minimize(Function function) {

    Function l2RegFunction = new L2RegFunction(function, l2Cost);
    this.dimension  = l2RegFunction.getDimension();
    this.updateInfo = new UpdateInfo(this.m, this.dimension);

    // Current point is at the origin
    double[] currPoint = new double[dimension];

    double currValue = l2RegFunction.valueAt(currPoint);

    // Gradient at the current point
    double[] currGrad = new double[dimension];
    System.arraycopy(l2RegFunction.gradientAt(currPoint), 0,
        currGrad, 0, dimension);

    // Pseudo-gradient - only use when L1-regularization is enabled
    double[] pseudoGrad = null;
    if (l1Cost > 0) {
      currValue += l1Cost * ArrayMath.l1norm(currPoint);
      pseudoGrad = new double[dimension];
      computePseudoGrad(currPoint, currGrad, pseudoGrad);
    }

    LineSearchResult lsr;
    if (l1Cost > 0) {
      lsr = LineSearchResult.getInitialObjectForL1(
          currValue, currGrad, pseudoGrad, currPoint);
    } else {
      lsr = LineSearchResult.getInitialObject(
          currValue, currGrad, currPoint);
    }

    if (verbose) {
      display("\nSolving convex optimization problem.");
      display("\nObjective function has " + dimension + " variable(s).");
      display("\n\nPerforming " + iterations + " iterations with " +
          "L1Cost=" + l1Cost + " and L2Cost=" + l2Cost + "\n");
    }

    double[] direction = new double[dimension];
    long startTime = System.currentTimeMillis();

    // Initial step size for the 1st iteration
    double initialStepSize = l1Cost > 0 ?
        ArrayMath.invL2norm(lsr.getPseudoGradAtNext()) :
          ArrayMath.invL2norm(lsr.getGradAtNext());

    for (int iter = 1; iter <= iterations; iter++) {
      // Find direction
      if (l1Cost > 0) {
        System.arraycopy(lsr.getPseudoGradAtNext(), 0, direction, 0, direction.length);
      } else {
        System.arraycopy(lsr.getGradAtNext(), 0, direction, 0, direction.length);
      }
      computeDirection(direction);

      // Line search
      if (l1Cost > 0) {
        // Constrain the search direction
        pseudoGrad = lsr.getPseudoGradAtNext();
        for (int i = 0; i < dimension; i++) {
          if (direction[i] * pseudoGrad[i] >= 0) {
            direction[i] = 0;
          }
        }
        LineSearch.doConstrainedLineSearch(l2RegFunction, direction, lsr, l1Cost, initialStepSize);
        computePseudoGrad(lsr.getNextPoint(), lsr.getGradAtNext(), pseudoGrad);
        lsr.setPseudoGradAtNext(pseudoGrad);
      }
      else {
        LineSearch.doLineSearch(l2RegFunction, direction, lsr, initialStepSize);
      }

      // Save Hessian updates
      updateInfo.update(lsr);

      if (verbose) {
        if (iter < 10)
          display("  " + iter + ":  ");
        else if (iter < 100)
          display(" " + iter + ":  ");
        else
          display(iter + ":  ");

        if (evaluator != null) {
          display("\t" + lsr.getValueAtNext() + "\t" + lsr.getFuncChangeRate()
              + "\t" + evaluator.evaluate(lsr.getNextPoint()) + "\n");
        } else {
          display("\t " + lsr.getValueAtNext() +
              "\t" + lsr.getFuncChangeRate() + "\n");
        }
      }
      if (isConverged(lsr))
        break;

      initialStepSize = INITIAL_STEP_SIZE;
    }

    // Undo L2-shrinkage if Elastic Net is used (since
    // in that case, the shrinkage is done twice)
    if (l1Cost > 0 && l2Cost > 0) {
      double[] x = lsr.getNextPoint();
      for (int i = 0; i < dimension; i++) {
        x[i] = Math.sqrt(1 + l2Cost) * x[i];
      }
    }

    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    display("Running time: " + (duration / 1000.) + "s\n");

    // Release memory
    this.updateInfo = null;
    System.gc();

    // Avoid returning the reference to LineSearchResult's member so that GC can
    // collect memory occupied by lsr after this function completes (is it necessary?)
    double[] parameters = new double[dimension];
    System.arraycopy(lsr.getNextPoint(), 0, parameters, 0, dimension);

    return parameters;
  }

  /**
   * Pseudo-gradient for L1-regularization (see equation 4 in the paper
   * "Scalable Training of L1-Regularized Log-Linear Models", Andrew et al. 2007)
   *
   * @param x current point
   * @param g gradient at x
   * @param pg pseudo-gradient at x which is to be computed
   */
  private void computePseudoGrad(double[] x, double[] g, double[] pg) {
    for (int i = 0; i < dimension; i++) {
      if (x[i] < 0) {
        pg[i] = g[i] - l1Cost;
      }
      else if (x[i] > 0) {
        pg[i] = g[i] + l1Cost;
      }
      else {
        if (g[i] < -l1Cost) {
          // right partial derivative
          pg[i] = g[i] + l1Cost;
        }
        else if (g[i] > l1Cost) {
          // left partial derivative
          pg[i] = g[i] - l1Cost;
        }
        else {
          pg[i] = 0;
        }
      }
    }
  }

  /**
   * L-BFGS two-loop recursion (see Nocedal & Wright 2006, Numerical Optimization, p. 178)
   */
  private void computeDirection(double[] direction) {

    // Implemented two-loop Hessian update method.
    int k = updateInfo.kCounter;
    double[] rho    = updateInfo.rho;
    double[] alpha  = updateInfo.alpha; // just to avoid recreating alpha
    double[][] S    = updateInfo.S;
    double[][] Y    = updateInfo.Y;

    // First loop
    for (int i = k - 1; i >= 0; i--) {
      alpha[i] = rho[i] * ArrayMath.innerProduct(S[i], direction);
      for (int j = 0; j < dimension; j++) {
        direction[j] = direction[j] - alpha[i] * Y[i][j];
      }
    }

    // Second loop
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

  private boolean isConverged(LineSearchResult lsr) {

    // Check function's change rate
    if (lsr.getFuncChangeRate() < CONVERGE_TOLERANCE) {
      if (verbose)
        display("Function change rate is smaller than the threshold "
            + CONVERGE_TOLERANCE + ".\nTraining will stop.\n\n");
      return true;
    }

    // Check gradient's norm using the criteria: ||g(x)|| / max(1, ||x||) < threshold
    double xNorm = Math.max(1, ArrayMath.l2norm(lsr.getNextPoint()));
    double gradNorm = l1Cost > 0 ?
        ArrayMath.l2norm(lsr.getPseudoGradAtNext()) : ArrayMath.l2norm(lsr.getGradAtNext());
    if (gradNorm / xNorm < REL_GRAD_NORM_TOL) {
      if (verbose)
        display("Relative L2-norm of the gradient is smaller than the threshold "
            + REL_GRAD_NORM_TOL + ".\nTraining will stop.\n\n");
      return true;
    }

    // Check step size
    if (lsr.getStepSize() < MIN_STEP_SIZE) {
      if (verbose)
        display("Step size is smaller than the minimum step size "
            + MIN_STEP_SIZE + ".\nTraining will stop.\n\n");
      return true;
    }

    // Check number of function evaluations
    if (lsr.getFctEvalCount() > this.maxFctEval) {
      if (verbose)
        display("Maximum number of function evaluations has exceeded the threshold "
            + this.maxFctEval + ".\nTraining will stop.\n\n");
      return true;
    }

    return false;
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
  private class UpdateInfo {
    private double[][] S;
    private double[][] Y;
    private double[] rho;
    private double[] alpha;
    private int m;

    private int kCounter;

    // Constructor
    UpdateInfo(int numCorrection, int dimension) {
      this.m = numCorrection;
      this.kCounter = 0;
      S     = new double[this.m][dimension];
      Y     = new double[this.m][dimension];
      rho   = new double[this.m];
      alpha = new double[this.m];
    }

    public void update(LineSearchResult lsr) {
      double[] currPoint  = lsr.getCurrPoint();
      double[] gradAtCurr = lsr.getGradAtCurr();
      double[] nextPoint  = lsr.getNextPoint();
      double[] gradAtNext = lsr.getGradAtNext();

      // Inner product of S_k and Y_k
      double SYk = 0.0;

      // Add new ones.
      if (kCounter < m) {
        for (int j = 0; j < dimension; j++) {
          S[kCounter][j] = nextPoint[j] - currPoint[j];
          Y[kCounter][j] = gradAtNext[j] - gradAtCurr[j];
          SYk += S[kCounter][j] * Y[kCounter][j];
        }
        rho[kCounter] = 1.0 / SYk;
      }
      else {
        // Discard oldest vectors and add new ones.
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

  /**
   * L2-regularized objective function
   */
  public static class L2RegFunction implements Function {
    private Function f;
    private double l2Cost;

    public L2RegFunction(Function f, double l2Cost) {
      this.f = f;
      this.l2Cost = l2Cost;
    }

    @Override
    public int getDimension() {
      return f.getDimension();
    }

    @Override
    public double valueAt(double[] x) {
      checkDimension(x);
      double value = f.valueAt(x);
      if (l2Cost > 0) {
        value += l2Cost * ArrayMath.innerProduct(x, x);
      }
      return value;
    }

    @Override
    public double[] gradientAt(double[] x) {
      checkDimension(x);
      double[] gradient = f.gradientAt(x);
      if (l2Cost > 0) {
        for (int i = 0; i < x.length; i++) {
          gradient[i] += 2 * l2Cost * x[i];
        }
      }
      return gradient;
    }

    private void checkDimension(double[] x) {
      if (x.length != getDimension())
        throw new IllegalArgumentException(
            "x's dimension is not the same as function's dimension");
    }
  }

  /**
   * Evaluate quality of training parameters. For example,
   * it can be used to report model's training accuracy when
   * we train a Maximum Entropy classifier.
   */
  public interface Evaluator {
    /**
     * Measure quality of the training parameters
     * @param parameters
     * @return evaluated result
     */
    double evaluate(double[] parameters);
  }
}
