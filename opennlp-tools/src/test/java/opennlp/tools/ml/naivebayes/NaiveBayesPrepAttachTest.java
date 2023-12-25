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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.PrepAttachDataUtil;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Test for Naive Bayes training and use with the ppa data.
 */
public class NaiveBayesPrepAttachTest {

  private ObjectStream<Event> trainingStream;

  @BeforeEach
  void initIndexer() throws IOException {
    trainingStream = PrepAttachDataUtil.createTrainingStream();
    Assertions.assertNotNull(trainingStream);
  }

  @Test
  void testNaiveBayesOnPrepAttachData() throws IOException {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainingParameters.put(AbstractDataIndexer.SORT_PARAM, false);
    DataIndexer testDataIndexer = new TwoPassDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
    testDataIndexer.index(trainingStream);
    
    MaxentModel model = new NaiveBayesTrainer().trainModel(testDataIndexer);
    Assertions.assertInstanceOf(NaiveBayesModel.class, model);
    PrepAttachDataUtil.testModel(model, 0.7897994553107205);
  }

  @Test
  void testNaiveBayesOnPrepAttachDataUsingTrainUtil() throws IOException {
    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(trainingStream);
    Assertions.assertInstanceOf(NaiveBayesModel.class, model);
    PrepAttachDataUtil.testModel(model, 0.7897994553107205);
  }

  @Test
  void testNaiveBayesOnPrepAttachDataUsingTrainUtilWithCutoff5() throws IOException {
    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 5);

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(trainingStream);
    Assertions.assertInstanceOf(NaiveBayesModel.class, model);
    PrepAttachDataUtil.testModel(model, 0.7945035899975241);
  }
}
