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

/**
 * Class that performs line search to find minimum
 */
public class LineSearch {
  private static final double INITIAL_STEP_SIZE = 1.0;
  private static final double C = 0.0001;
  private static final double RHO = 0.5; // decrease of step size (must be from 0 to 1)

  /**
   * Backtracking line search (see Nocedal & Wright 2006, Numerical Optimization, p. 37)
   */
  public static void doLineSearch(DifferentiableFunction function, 
      double[] direction, LineSearchResult lsr) 
  {
    double stepSize      = INITIAL_STEP_SIZE;
    int currFctEvalCount = lsr.getFctEvalCount();
    double[] x           = lsr.getNextPoint();
    double[] gradAtX     = lsr.getGradAtNext();
    double valueAtX      = lsr.getValueAtNext();
    
    // Retrieve current points and gradient for array reuse purpose
    double[] nextPoint       = lsr.getCurrPoint();
    double[] gradAtNextPoint = lsr.getGradAtCurr();
    double valueAtNextPoint;

    double dirGradientAtX = ArrayMath.innerProduct(direction, gradAtX);

    // To avoid recomputing in the loop
    double cachedProd = C * dirGradientAtX;
    
    while (true) {
      // Get next point
      for (int i = 0; i < x.length; i++) {
        nextPoint[i] = x[i] + direction[i] * stepSize;
      }
      
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
}
