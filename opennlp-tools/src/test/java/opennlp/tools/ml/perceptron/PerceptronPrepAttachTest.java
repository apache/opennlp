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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.PrepAttachDataUtil;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.TrainingParameters;

/**
 * Test for perceptron training and use with the ppa data.
 */
public class PerceptronPrepAttachTest {

  @Test
  public void testPerceptronOnPrepAttachData() throws IOException {
    TwoPassDataIndexer indexer = new TwoPassDataIndexer();
    TrainingParameters indexingParameters = new TrainingParameters();
    indexingParameters.put(AbstractTrainer.CUTOFF_PARAM, 1);
    indexingParameters.put(AbstractDataIndexer.SORT_PARAM, false);
    indexer.init(indexingParameters, new HashMap<>());
    indexer.index(PrepAttachDataUtil.createTrainingStream());
    MaxentModel model = new PerceptronTrainer().trainModel(400, indexer, 1);
    PrepAttachDataUtil.testModel(model, 0.7650408516959644);
  }

  @Test
  public void testPerceptronOnPrepAttachDataWithSkippedAveraging() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainParams.put("UseSkippedAveraging", true);

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(PrepAttachDataUtil.createTrainingStream());
    PrepAttachDataUtil.testModel(model, 0.773706362961129);
  }

  @Test
  public void testPerceptronOnPrepAttachDataWithTolerance() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainParams.put(AbstractTrainer.ITERATIONS_PARAM, 500);
    trainParams.put("Tolerance", 0.0001d);

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(PrepAttachDataUtil.createTrainingStream());
    PrepAttachDataUtil.testModel(model, 0.7677642980935875);
  }

  @Test
  public void testPerceptronOnPrepAttachDataWithStepSizeDecrease() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainParams.put(AbstractTrainer.ITERATIONS_PARAM, 500);
    trainParams.put("StepSizeDecrease", 0.06d);

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(PrepAttachDataUtil.createTrainingStream());
    PrepAttachDataUtil.testModel(model, 0.7791532557563754);
  }

  @Test
  public void testModelSerialization() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainParams.put("UseSkippedAveraging", true);

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    AbstractModel model = (AbstractModel) trainer.train(PrepAttachDataUtil.createTrainingStream());

    PrepAttachDataUtil.testModel(model, 0.773706362961129);

    // serialize and load model, then check if it still works as expected
    ByteArrayOutputStream modelBytes = new ByteArrayOutputStream();
    BinaryPerceptronModelWriter writer = new BinaryPerceptronModelWriter(model,
        new DataOutputStream(modelBytes));
    writer.persist();
    writer.close();

    MaxentModel restoredModel = new BinaryPerceptronModelReader(
        new DataInputStream(new ByteArrayInputStream(modelBytes.toByteArray()))).getModel();
    PrepAttachDataUtil.testModel(restoredModel, 0.773706362961129);
  }

  @Test
  public void testModelEquals() throws IOException {
    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainParams.put("UseSkippedAveraging", true);

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    AbstractModel modelA = (AbstractModel) trainer.train(PrepAttachDataUtil.createTrainingStream());
    AbstractModel modelB = (AbstractModel) trainer.train(PrepAttachDataUtil.createTrainingStream());

    Assert.assertEquals(modelA, modelB);
    Assert.assertEquals(modelA.hashCode(), modelB.hashCode());
  }
  
  @Test
  public void verifyReportMap() throws IOException {
    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, PerceptronTrainer.PERCEPTRON_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);
    // Since we are verifying the report map, we don't need to have more than 1 iteration
    trainParams.put(AbstractTrainer.ITERATIONS_PARAM, 1);
    trainParams.put("UseSkippedAveraging", true);
    
    Map<String,String> reportMap = new HashMap<>();
    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, reportMap);
    trainer.train(PrepAttachDataUtil.createTrainingStream());
    Assert.assertTrue("Report Map does not contain the training event hash",
        reportMap.containsKey("Training-Eventhash")); 
  }
}
