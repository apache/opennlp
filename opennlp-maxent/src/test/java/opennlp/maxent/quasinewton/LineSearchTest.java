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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


import org.junit.Test;

public class LineSearchTest {
  public static final double TOLERANCE = 0.01;

  @Test
  public void testLineSearchDeterminesSaneStepLength01() {
    DifferentiableFunction objectiveFunction = new QuadraticFunction();
    // given
    double[] testX = new double[] { 0 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    double stepSize = LineSearch.doLineSearch(objectiveFunction, testDirection, lsr).getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    assertTrue(succCond);
  }

  @Test
  public void testLineSearchDeterminesSaneStepLength02() {
    DifferentiableFunction objectiveFunction = new QuadraticFunction02();
    // given
    double[] testX = new double[] { -2 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    double stepSize = LineSearch.doLineSearch(objectiveFunction, testDirection, lsr).getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    assertTrue(succCond);
  }

  @Test
  public void testLineSearchFailsWithWrongDirection01() {
    DifferentiableFunction objectiveFunction = new QuadraticFunction();
    // given
    double[] testX = new double[] { 0 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { -1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    double stepSize = LineSearch.doLineSearch(objectiveFunction, testDirection, lsr).getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    assertFalse(succCond);
    assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsWithWrongDirection02() {
    DifferentiableFunction objectiveFunction = new QuadraticFunction02();
    // given
    double[] testX = new double[] { -2 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { -1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    double stepSize = LineSearch.doLineSearch(objectiveFunction, testDirection, lsr).getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    assertFalse(succCond);
    assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsWithWrongDirection03() {
    DifferentiableFunction objectiveFunction = new QuadraticFunction();
    // given
    double[] testX = new double[] { 4 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    double stepSize = LineSearch.doLineSearch(objectiveFunction, testDirection, lsr).getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    assertFalse(succCond);
    assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsWithWrongDirection04() {
    DifferentiableFunction objectiveFunction = new QuadraticFunction02();
    // given
    double[] testX = new double[] { 2 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    double stepSize = LineSearch.doLineSearch(objectiveFunction, testDirection, lsr).getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    assertFalse(succCond);
    assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsAtMaxima01() {
    DifferentiableFunction objectiveFunction = new QuadraticFunction02();
    // given
    double[] testX = new double[] { 0 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { -1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    double stepSize = LineSearch.doLineSearch(objectiveFunction, testDirection, lsr).getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    assertFalse(succCond);
    assertEquals(0.0, stepSize, TOLERANCE);
  }

  @Test
  public void testLineSearchFailsAtMaxima02() {
    DifferentiableFunction objectiveFunction = new QuadraticFunction02();
    // given
    double[] testX = new double[] { 0 };
    double testValueX = objectiveFunction.valueAt(testX);
    double[] testGradX = objectiveFunction.gradientAt(testX);
    double[] testDirection = new double[] { 1 };
    // when
    LineSearchResult lsr = LineSearchResult.getInitialObject(testValueX, testGradX, testX);
    double stepSize = LineSearch.doLineSearch(objectiveFunction, testDirection, lsr).getStepSize();
    // then
    boolean succCond = TOLERANCE < stepSize && stepSize <= 1;
    assertFalse(succCond);
    assertEquals(0.0, stepSize, TOLERANCE);
  }
}