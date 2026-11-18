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

package opennlp.tools.ml.perceptron;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.PrepAttachDataUtil;
import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

public class PerceptronTrainerTest {

  @ParameterizedTest
  @ValueSource(doubles = {-0.01, 0, 1.01, 6, 100, Double.NaN})
  void testSetStepSizeDecreaseRejectsValuesOutsideZeroExclusiveToOne(double decrease) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new PerceptronTrainer().setStepSizeDecrease(decrease));
    Assertions.assertEquals("decrease must be in (0, 1]", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.06, 1})
  void testSetStepSizeDecreaseAcceptsFractions(double decrease) {
    Assertions.assertDoesNotThrow(() -> new PerceptronTrainer().setStepSizeDecrease(decrease));
  }

  @Test
  void testTrainingParametersRejectPercentStepSizeDecrease() throws IOException {
    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(Parameters.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(Parameters.CUTOFF_PARAM, 1);
    trainParams.put("StepSizeDecrease", 6d);

    EventTrainer<TrainingParameters> trainer = new PerceptronTrainer();
    trainer.init(trainParams, null);
    try (ObjectStream<Event> events = PrepAttachDataUtil.createTrainingStream()) {
      IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
          () -> trainer.train(events));
      Assertions.assertEquals("decrease must be in (0, 1]", e.getMessage());
    }
  }
}
