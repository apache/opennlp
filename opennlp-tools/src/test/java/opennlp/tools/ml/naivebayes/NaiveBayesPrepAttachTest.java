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

import static opennlp.tools.ml.PrepAttachDataUtil.createTrainingStream;
import static opennlp.tools.ml.PrepAttachDataUtil.testModel;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;

import org.junit.Test;

/**
 * Test for Naive Bayes training and use with the ppa data.
 */
public class NaiveBayesPrepAttachTest {

  @Test
  public void testNaiveBayesOnPrepAttachData() throws IOException {
    MaxentModel model =
        new NaiveBayesTrainer().trainModel(new TwoPassDataIndexer(createTrainingStream(), 1, false));

    assertTrue(model instanceof NaiveBayesModel);

    testModel(model, 0.7897994553107205);
  }

  @Test
  public void testNaiveBayesOnPrepAttachDataUsingTrainUtil() throws IOException {

    Map<String, String> trainParams = new HashMap<String, String>();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, Integer.toString(1));

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(createTrainingStream());

    assertTrue(model instanceof NaiveBayesModel);

    testModel(model, 0.7897994553107205);
  }

  @Test
  public void testNaiveBayesOnPrepAttachDataUsingTrainUtilWithCutoff5() throws IOException {

    Map<String, String> trainParams = new HashMap<String, String>();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, Integer.toString(5));

    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, null);
    MaxentModel model = trainer.train(createTrainingStream());

    assertTrue(model instanceof NaiveBayesModel);

    testModel(model, 0.7945035899975241);
  }
}
