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

import static opennlp.tools.ml.PrepAttachDataUtil.createTrainingStream;
import static opennlp.tools.ml.PrepAttachDataUtil.testModel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test for perceptron training and use with the ppa data.
 */
public class PerceptronPrepAttachTest {

  @Test
  public void testPerceptronOnPrepAttachData() throws IOException {
    MaxentModel model =
        new PerceptronTrainer().trainModel(400,
        new TwoPassDataIndexer(createTrainingStream(), 1, false), 1);

    testModel(model, 0.7650408516959644);
  }

  @Test
  public void testPerceptronOnPrepAttachDataWithSkippedAveraging() throws IOException {

    Map<String, String> trainParams = new HashMap<String, String>();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, Integer.toString(1));
    trainParams.put("UseSkippedAveraging", Boolean.toString(true));

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(createTrainingStream());

    testModel(model, 0.773706362961129);
  }

  @Test
  public void testPerceptronOnPrepAttachDataWithTolerance() throws IOException {

    Map<String, String> trainParams = new HashMap<String, String>();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, Integer.toString(1));
    trainParams.put(AbstractTrainer.ITERATIONS_PARAM, Integer.toString(500));
    trainParams.put("Tolerance", Double.toString(0.0001d));

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(createTrainingStream());

    testModel(model, 0.7677642980935875);
  }

  @Test
  public void testPerceptronOnPrepAttachDataWithStepSizeDecrease() throws IOException {

    Map<String, String> trainParams = new HashMap<String, String>();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, Integer.toString(1));
    trainParams.put(AbstractTrainer.ITERATIONS_PARAM, Integer.toString(500));
    trainParams.put("StepSizeDecrease", Double.toString(0.06d));

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(createTrainingStream());

    testModel(model, 0.7791532557563754);
  }

  @Test
  public void testModelSerialization() throws IOException {

    Map<String, String> trainParams = new HashMap<>();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, Integer.toString(1));
    trainParams.put("UseSkippedAveraging", Boolean.toString(true));

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    AbstractModel model = (AbstractModel) trainer.train(createTrainingStream());

    testModel(model, 0.773706362961129);

    // serialize and load model, then check if it still works as expected
    ByteArrayOutputStream modelBytes = new ByteArrayOutputStream();
    BinaryPerceptronModelWriter writer = new BinaryPerceptronModelWriter(model,
        new DataOutputStream(modelBytes));
    writer.persist();
    writer.close();

    MaxentModel restoredModel = new BinaryPerceptronModelReader(
        new DataInputStream(new ByteArrayInputStream(modelBytes.toByteArray()))).getModel();

    testModel(restoredModel, 0.773706362961129);
  }

  @Test
  public void testModelEquals() throws IOException {
    Map<String, String> trainParams = new HashMap<>();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, Integer.toString(1));
    trainParams.put("UseSkippedAveraging", Boolean.toString(true));

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);

    AbstractModel modelA = (AbstractModel) trainer.train(createTrainingStream());
    AbstractModel modelB = (AbstractModel) trainer.train(createTrainingStream());

    Assert.assertEquals(modelA, modelB);
    Assert.assertEquals(modelA.hashCode(), modelB.hashCode());
  }
}
