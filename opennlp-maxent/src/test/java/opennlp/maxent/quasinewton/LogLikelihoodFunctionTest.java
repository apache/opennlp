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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
    double[] nonInitialPoint = new double[] { 3, 2, 3, 2, 3, 2, 3, 2, 3, 2 };
    double value = objectFunction.valueAt(dealignDoubleArrayForTestData(nonInitialPoint,
			testDataIndexer.getPredLabels(), 
			testDataIndexer.getOutcomeLabels()));
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
    double[] expectedGradient = new double[] { -9, -14, -17, 20, 8.5, 9, 14, 17, -20, -8.5 };
    // then
    assertTrue(compareDoubleArray(expectedGradient, gradientAtInitialPoint, testDataIndexer, TOLERANCE01));
  }

  @Test
  public void testGradientAtNonInitialPoint() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    LogLikelihoodFunction objectFunction = new LogLikelihoodFunction(testDataIndexer);
    // when
    double[] nonInitialPoint = new double[] { 0.2, 0.5, 0.2, 0.5, 0.2,
    		0.5, 0.2, 0.5, 0.2, 0.5 };
    double[] gradientAtNonInitialPoint = 
    		objectFunction.gradientAt(dealignDoubleArrayForTestData(nonInitialPoint,
    				testDataIndexer.getPredLabels(), 
    				testDataIndexer.getOutcomeLabels()));
    double[] expectedGradient = 
            new double[] { -0.311616214, -0.211771052, -1.324041847, 0.93340278, 0.317407069, 
    		0.311616214, 0.211771052, 1.324041847, -0.93340278, -0.317407069 };   
    // then
    assertTrue(compareDoubleArray(expectedGradient, gradientAtNonInitialPoint, testDataIndexer, TOLERANCE01));
  }
  
  private double[] alignDoubleArrayForTestData(double[] expected, String[] predLabels, String[] outcomeLabels) {
	double[] aligned = new double[predLabels.length * outcomeLabels.length];
	
	String[] sortedPredLabels = predLabels.clone();
	String[] sortedOutcomeLabels =  outcomeLabels.clone();
	Arrays.sort(sortedPredLabels);
	Arrays.sort(sortedOutcomeLabels);
	
	Map<String, Integer> invertedPredIndex = new HashMap<String, Integer>();
	Map<String, Integer> invertedOutcomeIndex = new HashMap<String, Integer>();
    for (int i = 0; i < predLabels.length; i++) {
      invertedPredIndex.put(predLabels[i], i);
    }
    for (int i = 0; i < outcomeLabels.length; i++) {
      invertedOutcomeIndex.put(outcomeLabels[i], i);
    }
	
    for (int i = 0; i < sortedOutcomeLabels.length; i++) {
      for (int j = 0; j < sortedPredLabels.length; j++) {
        aligned[i * sortedPredLabels.length + j] = expected[invertedOutcomeIndex
            .get(sortedOutcomeLabels[i])
            * sortedPredLabels.length
            + invertedPredIndex.get(sortedPredLabels[j])];
      }
    }
	return aligned;
  }
  
  private double[] dealignDoubleArrayForTestData(double[] expected,
      String[] predLabels, String[] outcomeLabels) {
    double[] dealigned = new double[predLabels.length * outcomeLabels.length];

    String[] sortedPredLabels = predLabels.clone();
    String[] sortedOutcomeLabels = outcomeLabels.clone();
    Arrays.sort(sortedPredLabels);
    Arrays.sort(sortedOutcomeLabels);

    Map<String, Integer> invertedPredIndex = new HashMap<String, Integer>();
    Map<String, Integer> invertedOutcomeIndex = new HashMap<String, Integer>();
    for (int i = 0; i < predLabels.length; i++) {
      invertedPredIndex.put(predLabels[i], i);
    }
    for (int i = 0; i < outcomeLabels.length; i++) {
      invertedOutcomeIndex.put(outcomeLabels[i], i);
    }

    for (int i = 0; i < sortedOutcomeLabels.length; i++) {
      for (int j = 0; j < sortedPredLabels.length; j++) {
        dealigned[invertedOutcomeIndex.get(sortedOutcomeLabels[i])
            * sortedPredLabels.length
            + invertedPredIndex.get(sortedPredLabels[j])] = expected[i
            * sortedPredLabels.length + j];
      }
    }

    return dealigned;
  }
  
  private boolean compareDoubleArray(double[] expected, double[] actual, DataIndexer indexer, double tolerance) {
	double[] alignedActual = alignDoubleArrayForTestData(actual, indexer.getPredLabels(), indexer.getOutcomeLabels());
	  
    if (expected.length != alignedActual.length) {
      return false;
    }
    
    for (int i = 0; i < alignedActual.length; i++) {
      if (Math.abs(alignedActual[i] - expected[i]) > tolerance) {
        return false;
      }
    }
    return true;
  }
}
