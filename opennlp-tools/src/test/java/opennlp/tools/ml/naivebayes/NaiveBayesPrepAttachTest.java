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

package opennlp.tools.ml.naivebayes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.PrepAttachDataUtil;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.TrainingParameters;

/**
 * Test for Naive Bayes training and use with the ppa data.
 */
public class NaiveBayesPrepAttachTest {

  private DataIndexer testDataIndexer;

  @Before
  public void initIndexer() {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(AbstractTrainer.CUTOFF_PARAM, "1");
    trainingParameters.put(AbstractDataIndexer.SORT_PARAM, "false");
    testDataIndexer = new TwoPassDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
  }

  @Test
  public void testNaiveBayesOnPrepAttachData() throws IOException {
    testDataIndexer.index(PrepAttachDataUtil.createTrainingStream());
    MaxentModel model = new NaiveBayesTrainer().trainModel(testDataIndexer);
    Assert.assertTrue(model instanceof NaiveBayesModel);
    PrepAttachDataUtil.testModel(model, 0.7897994553107205);
  }

  @Test
  public void testNaiveBayesOnPrepAttachDataUsingTrainUtil() throws IOException {
    Map<String, String> trainParams = new HashMap<>();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, Integer.toString(1));

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(PrepAttachDataUtil.createTrainingStream());
    Assert.assertTrue(model instanceof NaiveBayesModel);
    PrepAttachDataUtil.testModel(model, 0.7897994553107205);
  }

  @Test
  public void testNaiveBayesOnPrepAttachDataUsingTrainUtilWithCutoff5() throws IOException {
    Map<String, String> trainParams = new HashMap<>();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, Integer.toString(5));

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(PrepAttachDataUtil.createTrainingStream());
    Assert.assertTrue(model instanceof NaiveBayesModel);
    PrepAttachDataUtil.testModel(model, 0.7945035899975241);
  }
}
