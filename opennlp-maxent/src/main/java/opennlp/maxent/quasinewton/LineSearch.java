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

/**
 * class that performs line search.
 */
public class LineSearch {
  private static final double INITIAL_STEP_SIZE = 1.0;
  private static final double MIN_STEP_SIZE = 1.0E-10;
  private static final double C1 = 0.0001;
  private static final double C2 = 0.9;
  private static final double TT = 16.0;


  public static LineSearchResult doLineSearch(DifferentiableFunction function, double[] direction, LineSearchResult lsr) {
    return doLineSearch(function, direction, lsr, false);
  }

  public static LineSearchResult doLineSearch(DifferentiableFunction function, double[] direction, LineSearchResult lsr, boolean verbose) {
    int currFctEvalCount = lsr.getFctEvalCount();
    double stepSize = INITIAL_STEP_SIZE;
    double[] x = lsr.getNextPoint();
    double valueAtX = lsr.getValueAtNext();
    double[] gradAtX = lsr.getGradAtNext();
    double[] nextPoint = null;
    double[] gradAtNextPoint = null;
    double valueAtNextPoint = 0.0;

    double mu = 0;
    double upsilon = Double.POSITIVE_INFINITY;

    long startTime = System.currentTimeMillis();    
    while(true) {
      nextPoint = ArrayMath.updatePoint(x, direction, stepSize);
      valueAtNextPoint = function.valueAt(nextPoint);
      currFctEvalCount++;
      gradAtNextPoint = function.gradientAt(nextPoint);

      if (!checkArmijoCond(valueAtX, valueAtNextPoint, gradAtX, direction, stepSize, true)) {
        upsilon = stepSize;
      } else if(!checkCurvature(gradAtNextPoint, gradAtX, direction, x.length, true)) {
        mu = stepSize;
      } else break;

      if (upsilon < Double.POSITIVE_INFINITY)
        stepSize = (mu + upsilon) / TT;
      else
        stepSize *= TT;

      if (stepSize < MIN_STEP_SIZE + mu) {
        stepSize = 0.0;
        break;
      }
    }
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    if (verbose) {
      System.out.print("\t" + valueAtX);
      System.out.print("\t" + (valueAtNextPoint - valueAtX));
      System.out.print("\t" + (duration / 1000.0) + "\n");
    }

    LineSearchResult result = new LineSearchResult(stepSize, valueAtX, valueAtNextPoint, gradAtX, gradAtNextPoint, x, nextPoint, currFctEvalCount);
    return result;
  }

  private static boolean checkArmijoCond(double valueAtX, double valueAtNewPoint, 
      double[] gradAtX, double[] direction, double currStepSize, boolean isMaximizing) {
    // check Armijo rule;
    // f(x_k + a_kp_k) <= f(x_k) + c_1a_kp_k^t grad(xk)
    double armijo = valueAtX + (C1 * ArrayMath.innerProduct(direction, gradAtX) * currStepSize);
    return isMaximizing ? valueAtNewPoint > armijo: valueAtNewPoint <= armijo;
  }

  // check weak wolfe condition
  private static boolean checkCurvature(double[] gradAtNewPoint, double[] gradAtX, 
      double[] direction, int domainDimension, boolean isMaximizing) {
    // check curvature condition.
    double curvature01 = ArrayMath.innerProduct(direction, gradAtNewPoint);
    double curvature02 = C2 * ArrayMath.innerProduct(direction, gradAtX);
    return isMaximizing ? curvature01 < curvature02 : curvature01 >= curvature02;
  }
}
