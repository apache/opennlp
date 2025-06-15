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

import static opennlp.tools.ml.maxent.GISTrainer.LOG_LIKELIHOOD_THRESHOLD_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;


class LogLikelihoodThresholdBreachedTest {

  private StopCriteria<Double> stopCriteria;

  @BeforeEach
  public void setup() {
    stopCriteria = new LogLikelihoodThresholdBreached(
        new TrainingParameters(Map.of(LOG_LIKELIHOOD_THRESHOLD_PARAM,5.)));
  }

  @ParameterizedTest
  @CsvSource({"0.01,true", "-0.01,true", "6.0,false", "-6.0,true"})
  void testCriteria(double val, String expectedVal) {
    assertEquals(Boolean.parseBoolean(expectedVal), stopCriteria.test(val));
  }

  @Test
  void testMessageIfSatisfied() {
    assertEquals("Stopping: Difference between log likelihood of current" +
            " and previous iteration is less than threshold 5.0 .",
        stopCriteria.getMessageIfSatisfied());
  }

}
