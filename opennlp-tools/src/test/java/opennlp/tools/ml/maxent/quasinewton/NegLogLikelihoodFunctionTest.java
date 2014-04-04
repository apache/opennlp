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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.OnePassRealValueDataIndexer;
import opennlp.tools.ml.model.RealValueFileEventStream;

import org.junit.Test;

public class NegLogLikelihoodFunctionTest {
  public final double TOLERANCE01 = 1.0E-06;
  public final double TOLERANCE02 = 1.0E-10;

  @Test
  public void testDomainDimensionSanity() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");  
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    NegLogLikelihoodFunction objectFunction = new NegLogLikelihoodFunction(testDataIndexer);
    // when
    int correctDomainDimension = testDataIndexer.getPredLabels().length 
        * testDataIndexer.getOutcomeLabels().length;
    // then
    assertEquals(correctDomainDimension, objectFunction.getDomainDimension());
  }

  @Test
  public void testInitialSanity() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");  
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    NegLogLikelihoodFunction objectFunction = new NegLogLikelihoodFunction(testDataIndexer);
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
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");  
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    NegLogLikelihoodFunction objectFunction = new NegLogLikelihoodFunction(testDataIndexer);
    // when
    double[] initial = objectFunction.getInitialPoint();
    /** valueAt() must be always called before gradientAt() */
    objectFunction.valueAt(initial); 
    double[] gradientAtInitial = objectFunction.gradientAt(initial);
    // then
    assertNotNull(gradientAtInitial);
  }

  @Test
  public void testValueAtInitialPoint() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    NegLogLikelihoodFunction objectFunction = new NegLogLikelihoodFunction(testDataIndexer);
    // when
    double value = objectFunction.valueAt(objectFunction.getInitialPoint());
    double expectedValue = 13.86294361;
    // then
    assertEquals(expectedValue, value, TOLERANCE01);
  }

  @Test
  public void testValueAtNonInitialPoint01() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    NegLogLikelihoodFunction objectFunction = new NegLogLikelihoodFunction(testDataIndexer);
    // when
    double[] nonInitialPoint = new double[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
    double value = objectFunction.valueAt(nonInitialPoint);
    double expectedValue = 23.862943611198894;
    // then
    assertEquals(expectedValue, value, TOLERANCE01);
  }

  @Test
  public void testValueAtNonInitialPoint02() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    NegLogLikelihoodFunction objectFunction = new NegLogLikelihoodFunction(testDataIndexer);
    // when
    double[] nonInitialPoint = new double[] { 3, 2, 3, 2, 3, 2, 3, 2, 3, 2 };
    double value = objectFunction.valueAt(dealignDoubleArrayForTestData(nonInitialPoint,
			testDataIndexer.getPredLabels(), 
			testDataIndexer.getOutcomeLabels()));
    double expectedValue = 118.16321972109903;
    // then
    assertEquals(expectedValue, value, TOLERANCE02);
  }

  @Test 
  public void testGradientAtInitialPoint() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    NegLogLikelihoodFunction objectFunction = new NegLogLikelihoodFunction(testDataIndexer);
    // when
    /** valueAt() must be always called before gradientAt() */
    objectFunction.valueAt(objectFunction.getInitialPoint()); 
    double[] gradientAtInitialPoint = objectFunction.gradientAt(objectFunction.getInitialPoint());
    double[] expectedGradient = new double[] { -9.0, -14.0, -17.0, 20.0, 8.5, 9.0, 14.0, 17.0, -20.0, -8.5 };
    // then
    assertTrue(compareDoubleArray(expectedGradient, gradientAtInitialPoint, 
        testDataIndexer, TOLERANCE01));
  }

  @Test
  public void testGradientAtNonInitialPoint() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt", "UTF-8");
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    NegLogLikelihoodFunction objectFunction = new NegLogLikelihoodFunction(testDataIndexer);
    // when
    double[] nonInitialPoint = new double[] { 0.2, 0.5, 0.2, 0.5, 0.2, 0.5, 0.2, 0.5, 0.2, 0.5 };
    /** valueAt() must be always called before gradientAt() */
    objectFunction.valueAt(nonInitialPoint); 
    double[] gradientAtNonInitialPoint = 
    		objectFunction.gradientAt(dealignDoubleArrayForTestData(nonInitialPoint,
    				testDataIndexer.getPredLabels(), 
    				testDataIndexer.getOutcomeLabels()));
    double[] expectedGradient = 
            new double[] { -8.742040938155275, -10.81798632847702, 
                           30.311389857093182, 1.5513000872985572, 
                            2.0513491106450754, 10.142040938155278, 
                           12.217986328477027, -28.911389857093162, 
                          -0.15130008729855715, -0.6513491106450751 };   
    // then
    assertTrue(compareDoubleArray(expectedGradient, gradientAtNonInitialPoint, 
        testDataIndexer, TOLERANCE01));
  }
  
  private double[] alignDoubleArrayForTestData(double[] expected, 
      String[] predLabels, String[] outcomeLabels) {
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
  
  private boolean compareDoubleArray(double[] expected, double[] actual, 
      DataIndexer indexer, double tolerance) {
    double[] alignedActual = alignDoubleArrayForTestData(
        actual, indexer.getPredLabels(), indexer.getOutcomeLabels());
	  
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
