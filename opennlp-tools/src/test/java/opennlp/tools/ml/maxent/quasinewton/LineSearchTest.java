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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.maxent.quasinewton.LineSearch.LineSearchResult;

public class LineSearchTest {
  private static final double TOLERANCE = 0.01;

  @Test
  public void testLineSearchDeterminesSaneStepLength1() {
    Function objectiveFunction = new QuadraticFunction1();
    // given
    double[] testX = new double[] { 0 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    LineSearch.doLineSearch(objectiveFunction, testDirection, lsr, 1.0);
    double stepSize = lsr.getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    Assert.assertTrue(succCond);
  }

  @Test
  public void testLineSearchDeterminesSaneStepLength2() {
    Function objectiveFunction = new QuadraticFunction2();
    // given
    double[] testX = new double[] { -2 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    LineSearch.doLineSearch(objectiveFunction, testDirection, lsr, 1.0);
    double stepSize = lsr.getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    Assert.assertTrue(succCond);
  }

  @Test
  public void testLineSearchFailsWithWrongDirection1() {
    Function  objectiveFunction = new QuadraticFunction1();
    // given
    double[] testX = new double[] { 0 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { -1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    LineSearch.doLineSearch(objectiveFunction, testDirection, lsr, 1.0);
    double stepSize = lsr.getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    Assert.assertFalse(succCond);
    Assert.assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsWithWrongDirection2() {
    Function objectiveFunction = new QuadraticFunction2();
    // given
    double[] testX = new double[] { -2 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { -1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    LineSearch.doLineSearch(objectiveFunction, testDirection, lsr, 1.0);
    double stepSize = lsr.getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    Assert.assertFalse(succCond);
    Assert.assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsWithWrongDirection3() {
    Function objectiveFunction = new QuadraticFunction1();
    // given
    double[] testX = new double[] { 4 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    LineSearch.doLineSearch(objectiveFunction, testDirection, lsr, 1.0);
    double stepSize = lsr.getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    Assert.assertFalse(succCond);
    Assert.assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsWithWrongDirection4() {
    Function objectiveFunction = new QuadraticFunction2();
    // given
    double[] testX = new double[] { 2 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    LineSearch.doLineSearch(objectiveFunction, testDirection, lsr, 1.0);
    double stepSize = lsr.getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    Assert.assertFalse(succCond);
    Assert.assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsAtMinimum1() {
    Function objectiveFunction = new QuadraticFunction2();
    // given
    double[] testX = new double[] { 0 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { -1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    LineSearch.doLineSearch(objectiveFunction, testDirection, lsr, 1.0);
    double stepSize = lsr.getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    Assert.assertFalse(succCond);
    Assert.assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsAtMinimum2() {
    Function objectiveFunction = new QuadraticFunction2();
    // given
    double[] testX = new double[] { 0 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    LineSearch.doLineSearch(objectiveFunction, testDirection, lsr, 1.0);
    double stepSize = lsr.getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    Assert.assertFalse(succCond);
    Assert.assertEquals(0.0, stepSize, TOLERANCE);
  }

  /**
   * Quadratic function: f(x) = (x-2)^2 + 4
   */
  public class QuadraticFunction1 implements Function {

    public double valueAt(double[] x) {
      // (x-2)^2 + 4;
      return Math.pow(x[0] - 2, 2) + 4;
    }

    public double[] gradientAt(double[] x) {
      // 2(x-2)
      return new double[] {2 * (x[0] - 2)};
    }

    public int getDimension() {
      return 1;
    }
  }

  /**
   * Quadratic function: f(x) = x^2
   */
  public class QuadraticFunction2 implements Function {

    public double valueAt(double[] x) {
      // x^2;
      return Math.pow(x[0], 2);
    }

    public double[] gradientAt(double[] x) {
      // 2x
      return new double[] {2 * x[0]};
    }

    public int getDimension() {
      return 1;
    }
  }
}
