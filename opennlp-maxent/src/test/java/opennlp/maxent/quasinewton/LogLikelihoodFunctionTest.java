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
package opennlp.maxent.quasinewton;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import opennlp.model.DataIndexer;
import opennlp.model.OnePassRealValueDataIndexer;
import opennlp.model.RealValueFileEventStream;

import org.junit.Test;

public class LogLikelihoodFunctionTest {
  public final double TOLERANCE01 = 1.0E-06;
  public final double TOLERANCE02 = 1.0E-10;

  @Test
  public void testDomainDimensionSanity() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");  
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    LogLikelihoodFunction objectFunction = new LogLikelihoodFunction(testDataIndexer);
    // when
    int correctDomainDimension = testDataIndexer.getPredLabels().length * testDataIndexer.getOutcomeLabels().length;
    // then
    assertEquals(correctDomainDimension, objectFunction.getDomainDimension());
  }

  @Test
  public void testInitialSanity() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");  
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    LogLikelihoodFunction objectFunction = new LogLikelihoodFunction(testDataIndexer);
    // when
    double[] initial = objectFunction.getInitialPoint();
    // then
    for (int i = 0; i < initial.length; i++) {
      assertEquals(0.0, initial[i], TOLERANCE01);
    }
  }

  @Test
  public void testGradientSanity() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");  
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    LogLikelihoodFunction objectFunction = new LogLikelihoodFunction(testDataIndexer);
    // when
    double[] initial = objectFunction.getInitialPoint();
    double[] gradientAtInitial = objectFunction.gradientAt(initial);
    // then
    assertNotNull(gradientAtInitial);
  }

  @Test
  public void testValueAtInitialPoint() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    LogLikelihoodFunction objectFunction = new LogLikelihoodFunction(testDataIndexer);
    // when
    double value = objectFunction.valueAt(objectFunction.getInitialPoint());
    double expectedValue = -13.86294361;
    // then
    assertEquals(expectedValue, value, TOLERANCE01);
  }

  @Test
  public void testValueAtNonInitialPoint01() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    LogLikelihoodFunction objectFunction = new LogLikelihoodFunction(testDataIndexer);
    // when
    double[] nonInitialPoint = new double[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
    double value = objectFunction.valueAt(nonInitialPoint);
    double expectedValue = -0.000206886;
    // then
    assertEquals(expectedValue, value, TOLERANCE01);
  }

  @Test
  public void testValueAtNonInitialPoint02() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    LogLikelihoodFunction objectFunction = new LogLikelihoodFunction(testDataIndexer);
    // when
    double[] nonInitialPoint = new double[] { 2, 3, 2, 3, 3, 3, 2, 3, 2, 2 };
    double value = objectFunction.valueAt(nonInitialPoint);
    double expectedValue = -0.00000000285417;
    // then
    assertEquals(expectedValue, value, TOLERANCE02);
  }

  @Test 
  public void testGradientAtInitialPoint() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    LogLikelihoodFunction objectFunction = new LogLikelihoodFunction(testDataIndexer);
    // when
    double[] gradientAtInitialPoint = objectFunction.gradientAt(objectFunction.getInitialPoint());
    double[] expectedGradient = new double[] { 20, 8.5, -14, -17, -9, -20, -8.5, 14, 17, 9 };
    // then
    assertTrue(expectedGradient.length == gradientAtInitialPoint.length);
    for (int i = 0; i < expectedGradient.length; i++) {
      assertEquals(expectedGradient[i], gradientAtInitialPoint[i], TOLERANCE01);
    }
  }

  @Test
  public void testGradientAtNonInitialPoint() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    LogLikelihoodFunction objectFunction = new LogLikelihoodFunction(testDataIndexer);
    // when
    double[] nonInitialPoint = new double[] { 2, 3, 2, 3, 3, 3, 2, 3, 2, 2 };
    double[] gradientAtInitialPoint = objectFunction.gradientAt(nonInitialPoint);
    double[] expectedGradient = 
        new double[] { 6.19368E-09, -3.04514E-16, 7.48224E-09,  -7.15239E-09, 4.14274E-09, 
        -6.19368E-09, 0.0, -7.48225E-09, 7.15239E-09, -4.14274E-09};
    // then
    assertTrue(expectedGradient.length == gradientAtInitialPoint.length);
    for (int i = 0; i < expectedGradient.length; i++) {
      assertEquals(expectedGradient[i], gradientAtInitialPoint[i], TOLERANCE01);
    }
  }
}
