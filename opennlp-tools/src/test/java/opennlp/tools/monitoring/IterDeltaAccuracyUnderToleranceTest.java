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

package opennlp.tools.monitoring;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IterDeltaAccuracyUnderToleranceTest {

  private StopCriteria<Double> stopCriteria;

  @BeforeEach
  public void setup() {
    stopCriteria = new IterDeltaAccuracyUnderTolerance(new TrainingParameters(Map.of("Tolerance",
        .00002)));
  }

  @ParameterizedTest
  @CsvSource( {"0.01,false", "-0.01,false", "0.00001,true", "-0.00001,true"})
  void testCriteria(double val, String expectedVal) {
    assertEquals(Boolean.parseBoolean(expectedVal), stopCriteria.test(val));
  }

  @Test
  void testMessageIfSatisfied() {
    assertEquals("Stopping: change in training set accuracy less than {2.0E-5}",
        stopCriteria.getMessageIfSatisfied());
  }
}
