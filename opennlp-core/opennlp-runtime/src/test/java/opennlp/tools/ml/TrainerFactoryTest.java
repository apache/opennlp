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

package opennlp.tools.ml;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.TrainerFactory.TrainerType;
import opennlp.tools.ml.maxent.GISTrainer;
import opennlp.tools.ml.perceptron.SimplePerceptronSequenceTrainer;
import opennlp.tools.monitoring.DefaultTrainingProgressMonitor;
import opennlp.tools.monitoring.LogLikelihoodThresholdBreached;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingConfiguration;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class TrainerFactoryTest {

  private TrainingParameters mlParams;

  @BeforeEach
  void setup() {
    mlParams = new TrainingParameters();
    mlParams.put(Parameters.ALGORITHM_PARAM, GISTrainer.MAXENT_VALUE);
    mlParams.put(Parameters.ITERATIONS_PARAM, 10);
    mlParams.put(Parameters.CUTOFF_PARAM, 5);
  }

  @Test
  void testBuiltInValid() {
    Assertions.assertTrue(TrainerFactory.isValid(mlParams));
  }

  @Test
  void testSequenceTrainerValid() {
    mlParams.put(Parameters.ALGORITHM_PARAM, MockSequenceTrainer.class.getCanonicalName());
    Assertions.assertTrue(TrainerFactory.isValid(mlParams));
  }

  @Test
  void testEventTrainerValid() {
    mlParams.put(Parameters.ALGORITHM_PARAM, MockEventTrainer.class.getCanonicalName());
    Assertions.assertTrue(TrainerFactory.isValid(mlParams));
  }

  @Test
  void testInvalidTrainer() {
    mlParams.put(Parameters.ALGORITHM_PARAM, "xyz");
    Assertions.assertFalse(TrainerFactory.isValid(mlParams));
  }

  @Test
  void testIsSequenceTrainerTrue() {
    mlParams.put(Parameters.ALGORITHM_PARAM,
        SimplePerceptronSequenceTrainer.PERCEPTRON_SEQUENCE_VALUE);

    TrainerType trainerType = TrainerFactory.getTrainerType(mlParams);

    Assertions.assertEquals(TrainerType.EVENT_MODEL_SEQUENCE_TRAINER, trainerType);
  }

  @Test
  void testIsSequenceTrainerFalse() {
    mlParams.put(Parameters.ALGORITHM_PARAM, GISTrainer.MAXENT_VALUE);
    TrainerType trainerType = TrainerFactory.getTrainerType(mlParams);
    Assertions.assertNotEquals(TrainerType.EVENT_MODEL_SEQUENCE_TRAINER, trainerType);
  }

  @Test
  void testGetEventTrainerConfiguration() {
    mlParams.put(Parameters.ALGORITHM_PARAM, GISTrainer.MAXENT_VALUE);

    TrainingConfiguration config = new TrainingConfiguration(new DefaultTrainingProgressMonitor(),
        new LogLikelihoodThresholdBreached(mlParams));

    AbstractTrainer<TrainingParameters> trainer =
            (AbstractTrainer<TrainingParameters>) TrainerFactory.getEventTrainer(mlParams, null, config);
    TrainingConfiguration configuration = trainer.getTrainingConfiguration();
    assertAll(
            () -> assertInstanceOf(DefaultTrainingProgressMonitor.class, configuration.progMon()),
            () -> assertInstanceOf(LogLikelihoodThresholdBreached.class, configuration.stopCriteria()));
  }
}
