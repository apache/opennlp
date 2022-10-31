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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.BinaryFileDataReader;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.GenericModelReader;
import opennlp.tools.ml.model.GenericModelWriter;
import opennlp.tools.ml.model.OnePassRealValueDataIndexer;
import opennlp.tools.ml.model.RealValueFileEventStream;
import opennlp.tools.util.TrainingParameters;

public class QNTrainerTest {

  private static final int ITERATIONS = 50;

  private DataIndexer testDataIndexer;

  @Before
  public void initIndexer() {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(AbstractTrainer.CUTOFF_PARAM, 1);
    testDataIndexer = new OnePassRealValueDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
  }

  @Test
  public void testTrainModelReturnsAQNModel() throws Exception {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt");
    testDataIndexer.index(rvfes1);
    // when
    QNModel trainedModel = new QNTrainer(false).trainModel(ITERATIONS, testDataIndexer);
    // then
    Assert.assertNotNull(trainedModel);
  }

  @Test
  public void testInTinyDevSet() throws Exception {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt");
    testDataIndexer.index(rvfes1);;
    // when
    QNModel trainedModel = new QNTrainer(15, true).trainModel(ITERATIONS, testDataIndexer);
    String[] features2Classify = new String[] {
        "feature2","feature3", "feature3",
        "feature3","feature3", "feature3",
        "feature3","feature3", "feature3",
        "feature3","feature3", "feature3"};
    double[] eval = trainedModel.eval(features2Classify);
    // then
    Assert.assertNotNull(eval);
  }

  @Test
  public void testModel() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt");
    testDataIndexer.index(rvfes1);
    // when
    QNModel trainedModel = new QNTrainer(15, true).trainModel(
        ITERATIONS, testDataIndexer);

    Assert.assertFalse(trainedModel.equals(null));
  }

  @Test
  public void testSerdeModel() throws IOException {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt");
    testDataIndexer.index(rvfes1);
    // when
    QNModel trainedModel = new QNTrainer(5, 700, true).trainModel(ITERATIONS, testDataIndexer);

    ByteArrayOutputStream modelBytes = new ByteArrayOutputStream();
    GenericModelWriter modelWriter = new GenericModelWriter(trainedModel,
        new DataOutputStream(modelBytes));
    modelWriter.persist();
    modelWriter.close();

    GenericModelReader modelReader = new GenericModelReader(new BinaryFileDataReader(
        new ByteArrayInputStream(modelBytes.toByteArray())));
    AbstractModel readModel = modelReader.getModel();
    QNModel deserModel = (QNModel) readModel;

    Assert.assertTrue(trainedModel.equals(deserModel));

    String[] features2Classify = new String[] {
        "feature2","feature3", "feature3",
        "feature3","feature3", "feature3",
        "feature3","feature3", "feature3",
        "feature3","feature3", "feature3"};
    double[] eval01 = trainedModel.eval(features2Classify);
    double[] eval02 = deserModel.eval(features2Classify);

    Assert.assertEquals(eval01.length, eval02.length);
    for (int i = 0; i < eval01.length; i++) {
      Assert.assertEquals(eval01[i], eval02[i], 0.00000001);
    }
  }
}
