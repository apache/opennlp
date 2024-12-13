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

import opennlp.tools.ml.ArrayMath;

/**
 * Performs line search to find a minimum.
 *
 * @see <a href="https://link.springer.com/book/10.1007/978-0-387-40065-5">
 *   Nocedal & Wright 2006, Numerical Optimization</a>, p. 37)
 */
public class LineSearch {
  private static final double C = 0.0001;
  private static final double RHO = 0.5; // decrease of step size (must be from 0 to 1)

  /**
   * Conducts a backtracking line search.
   *
   * @param function  The {@link Function} to apply.
   * @param direction The {@code double[]} representing the direction to search into.
   * @param lsr       The {@link LineSearchResult} to transport results in.
   * @param initialStepSize The initial step size to apply. Must be greater than {@code 0}.
   */
  public static void doLineSearch(Function function, double[] direction,
                                  LineSearchResult lsr, double initialStepSize) {
    double stepSize      = initialStepSize;
    int currFctEvalCount = lsr.getFctEvalCount();
    double[] x           = lsr.getNextPoint();
    double[] gradAtX     = lsr.getGradAtNext();
    double valueAtX      = lsr.getValueAtNext();
    int dimension        = x.length;

    // Retrieve current points and gradient for array reuse purpose
    double[] nextPoint       = lsr.getCurrPoint();
    double[] gradAtNextPoint = lsr.getGradAtCurr();
    double valueAtNextPoint;

    double dirGradientAtX = ArrayMath.innerProduct(direction, gradAtX);

    // To avoid recomputing in the loop
    double cachedProd = C * dirGradientAtX;

    while (true) {
      // Get next point
      for (int i = 0; i < dimension; i++) {
        nextPoint[i] = x[i] + direction[i] * stepSize;
      }

      // New value
      valueAtNextPoint = function.valueAt(nextPoint);

      currFctEvalCount++;

      // Check Armijo condition
      if (valueAtNextPoint <= valueAtX + cachedProd * stepSize)
        break;

      // Shrink step size
      stepSize *= RHO;
    }

    // Compute and save gradient at the new point
    System.arraycopy(function.gradientAt(nextPoint), 0, gradAtNextPoint, 0,
        gradAtNextPoint.length);

    // Update line search result
    lsr.setAll(stepSize, valueAtX, valueAtNextPoint,
        gradAtX, gradAtNextPoint, x, nextPoint, currFctEvalCount);
  }

  /**
   * Conducts a constrained line search (see section 3.2 in the paper "Scalable Training
   * of L1-Regularized Log-Linear Models", Andrew et al. 2007)
   *
   * @param function  The {@link Function} to apply.
   * @param direction The {@code double[]} representing the direction to search into.
   * @param lsr       The {@link LineSearchResult} to transport results in.
   * @param l1Cost    The L1-regularization costs. Must be equal or greater than {@code 0}.
   * @param initialStepSize The initial step size to apply. Must be greater than {@code 0}.
   */
  public static void doConstrainedLineSearch(Function function,
      double[] direction, LineSearchResult lsr, double l1Cost, double initialStepSize) {
    double stepSize        = initialStepSize;
    int currFctEvalCount   = lsr.getFctEvalCount();
    double[] x             = lsr.getNextPoint();
    double[] signX         = lsr.getSignVector(); // existing sign vector
    double[] gradAtX       = lsr.getGradAtNext();
    double[] pseudoGradAtX = lsr.getPseudoGradAtNext();
    double valueAtX        = lsr.getValueAtNext();
    int dimension          = x.length;

    // Retrieve current points and gradient for array reuse purpose
    double[] nextPoint       = lsr.getCurrPoint();
    double[] gradAtNextPoint = lsr.getGradAtCurr();
    double valueAtNextPoint;

    double dirGradientAtX;

    // New sign vector
    for (int i = 0; i < dimension; i++) {
      signX[i] = x[i] == 0 ? -pseudoGradAtX[i] : x[i];
    }

    while (true) {
      // Get next point
      for (int i = 0; i < dimension; i++) {
        nextPoint[i] = x[i] + direction[i] * stepSize;
      }

      // Projection
      for (int i = 0; i < dimension; i++) {
        if (nextPoint[i] * signX[i] <= 0)
          nextPoint[i] = 0;
      }

      // New value
      valueAtNextPoint = function.valueAt(nextPoint) +
          l1Cost * ArrayMath.l1norm(nextPoint);

      currFctEvalCount++;

      dirGradientAtX = 0;
      for (int i = 0; i < dimension; i++) {
        dirGradientAtX += (nextPoint[i] - x[i]) * pseudoGradAtX[i];
      }

      // Check the sufficient decrease condition
      if (valueAtNextPoint <= valueAtX + C * dirGradientAtX)
        break;

      // Shrink step size
      stepSize *= RHO;
    }

    // Compute and save gradient at the new point
    System.arraycopy(function.gradientAt(nextPoint), 0, gradAtNextPoint, 0,
        gradAtNextPoint.length);

    // Update line search result
    lsr.setAll(stepSize, valueAtX, valueAtNextPoint, gradAtX,
        gradAtNextPoint, pseudoGradAtX, x, nextPoint, signX, currFctEvalCount);
  }

  // ------------------------------------------------------------------------------------- //

  /**
   * Represents a {@link LineSearch} result encapsulating the relevant data
   * at a point in time during computation.
   */
  public static class LineSearchResult {

    private int fctEvalCount;
    private double stepSize;
    private double valueAtCurr;
    private double valueAtNext;
    private double[] gradAtCurr;
    private double[] gradAtNext;
    private double[] pseudoGradAtNext;
    private double[] currPoint;
    private double[] nextPoint;
    private double[] signVector;

    /**
     * Initializes a {@link LineSearchResult} object with the specified parameters.
     */
    public LineSearchResult(double stepSize, double valueAtCurr, double valueAtNext,
                            double[] gradAtCurr, double[] gradAtNext, double[] currPoint,
                            double[] nextPoint, int fctEvalCount)
    {
      setAll(stepSize, valueAtCurr, valueAtNext, gradAtCurr, gradAtNext,
          currPoint, nextPoint, fctEvalCount);
    }

    /**
     * Initializes a {@link LineSearchResult} object with the specified parameters.
     */
    public LineSearchResult(double stepSize, double valueAtCurr, double valueAtNext,
                            double[] gradAtCurr, double[] gradAtNext, double[] pseudoGradAtNext,
                            double[] currPoint, double[] nextPoint, double[] signVector,
                            int fctEvalCount)
    {
      setAll(stepSize, valueAtCurr, valueAtNext, gradAtCurr, gradAtNext,
          pseudoGradAtNext, currPoint, nextPoint, signVector, fctEvalCount);
    }

    /**
     * Updates line search elements.
     */
    public void setAll(double stepSize, double valueAtCurr, double valueAtNext,
                       double[] gradAtCurr, double[] gradAtNext, double[] currPoint,
                       double[] nextPoint, int fctEvalCount)
    {
      setAll(stepSize, valueAtCurr, valueAtNext, gradAtCurr, gradAtNext,
          null, currPoint, nextPoint, null, fctEvalCount);
    }

    /**
     * Updates line search elements.
     */
    public void setAll(double stepSize, double valueAtCurr, double valueAtNext,
                       double[] gradAtCurr, double[] gradAtNext, double[] pseudoGradAtNext,
                       double[] currPoint, double[] nextPoint, double[] signVector,
                       int fctEvalCount)
    {
      this.stepSize         = stepSize;
      this.valueAtCurr      = valueAtCurr;
      this.valueAtNext      = valueAtNext;
      this.gradAtCurr       = gradAtCurr;
      this.gradAtNext       = gradAtNext;
      this.pseudoGradAtNext = pseudoGradAtNext;
      this.currPoint        = currPoint;
      this.nextPoint        = nextPoint;
      this.signVector       = signVector;
      this.fctEvalCount     = fctEvalCount;
    }

    public double getFuncChangeRate() {
      return (valueAtCurr - valueAtNext) / valueAtCurr;
    }

    public double getStepSize() {
      return stepSize;
    }

    public void setStepSize(double stepSize) {
      this.stepSize = stepSize;
    }

    public double getValueAtCurr() {
      return valueAtCurr;
    }

    public void setValueAtCurr(double valueAtCurr) {
      this.valueAtCurr = valueAtCurr;
    }

    public double getValueAtNext() {
      return valueAtNext;
    }

    public void setValueAtNext(double valueAtNext) {
      this.valueAtNext = valueAtNext;
    }

    public double[] getGradAtCurr() {
      return gradAtCurr;
    }

    public void setGradAtCurr(double[] gradAtCurr) {
      this.gradAtCurr = gradAtCurr;
    }

    public double[] getGradAtNext() {
      return gradAtNext;
    }

    public void setGradAtNext(double[] gradAtNext) {
      this.gradAtNext = gradAtNext;
    }

    public double[] getPseudoGradAtNext() {
      return pseudoGradAtNext;
    }

    public void setPseudoGradAtNext(double[] pseudoGradAtNext) {
      this.pseudoGradAtNext = pseudoGradAtNext;
    }

    public double[] getCurrPoint() {
      return currPoint;
    }

    public void setCurrPoint(double[] currPoint) {
      this.currPoint = currPoint;
    }

    public double[] getNextPoint() {
      return nextPoint;
    }

    public void setNextPoint(double[] nextPoint) {
      this.nextPoint = nextPoint;
    }

    public double[] getSignVector() {
      return signVector;
    }

    public void setSignVector(double[] signVector) {
      this.signVector = signVector;
    }

    public int getFctEvalCount() {
      return fctEvalCount;
    }

    public void setFctEvalCount(int fctEvalCount) {
      this.fctEvalCount = fctEvalCount;
    }

    /**
     * Initial linear search object for L1-regularization.
     *
     * @param valueAtX        The value at {@code x}.
     * @param gradAtX         The gradient at {@code x}.
     * @param x               The input {@code double[]} vector.
     *
     * @return The {@link LineSearchResult} holding the results.
     */
    public static LineSearchResult getInitialObject(double valueAtX, double[] gradAtX,
                                                    double[] x) {
      return getInitialObject(valueAtX, gradAtX, null, x, null, 0);
    }

    /**
     * Initial linear search object for L1-regularization.
     *
     * @param valueAtX        The value at {@code x}.
     * @param gradAtX         The gradient at {@code x}.
     * @param pseudoGradAtX   The pseudo-gradient at {@code x}.
     * @param x               The input {@code double[]} vector.
     *
     * @return The {@link LineSearchResult} holding the results.
     */
    public static LineSearchResult getInitialObjectForL1(double valueAtX, double[] gradAtX,
                                                         double[] pseudoGradAtX, double[] x) {
      return getInitialObject(valueAtX, gradAtX, pseudoGradAtX, x, new double[x.length], 0);
    }

    /**
     * Initial linear search object for L1-regularization.
     *
     * @param valueAtX        The value at {@code x}.
     * @param gradAtX         The gradient at {@code x}.
     * @param pseudoGradAtX   The pseudo-gradient at {@code x}.
     * @param x               The input {@code double[]} vector.
     * @param signX           The sign {@code double[]} vector for {@code x}.
     * @param fctEvalCount    The number of function evaluations.
     *                        Must be equal to or greater than {@code 0}.
     *
     * @return The {@link LineSearchResult} holding the results.
     */
    public static LineSearchResult getInitialObject(double valueAtX, double[] gradAtX,
                                                    double[] pseudoGradAtX, double[] x,
                                                    double[] signX, int fctEvalCount) {
      return new LineSearchResult(0.0, 0.0, valueAtX, new double[x.length], gradAtX,
          pseudoGradAtX, new double[x.length], x, signX, fctEvalCount);
    }
  }
}
