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

package opennlp.tools.ml.maxent;

import java.io.IOException;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.PrepAttachDataUtil;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.ml.model.UniformPrior;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

public class MaxentPrepAttachTest {

  private DataIndexer<TrainingParameters> testDataIndexer;

  @BeforeEach
  void initIndexer() {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(Parameters.CUTOFF_PARAM, 1);
    trainingParameters.put(AbstractDataIndexer.SORT_PARAM, false);
    testDataIndexer = new TwoPassDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
  }

  @Test
  void testMaxentOnPrepAttachData() throws IOException {
    testDataIndexer.index(PrepAttachDataUtil.createTrainingStream());
    // this shows why the GISTrainer should be a AbstractEventTrainer.
    // TODO: make sure that the trainingParameter cutoff and the 
    // cutoff value passed here are equal.
    AbstractModel model = new GISTrainer().trainModel(100,
            testDataIndexer, new UniformPrior(), 1);
    PrepAttachDataUtil.testModel(model, 0.7997028967566229);
  }

  @Test
  void testMaxentOnPrepAttachData2Threads() throws IOException {
    testDataIndexer.index(PrepAttachDataUtil.createTrainingStream());
    AbstractModel model =
        new GISTrainer().trainModel(100,
            testDataIndexer, new UniformPrior(), 2);
    PrepAttachDataUtil.testModel(model, 0.7997028967566229);
  }

  @Test
  void testMaxentOnPrepAttachDataWithParams() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(Parameters.ALGORITHM_PARAM, Parameters.ALGORITHM_DEFAULT_VALUE);
    trainParams.put(AbstractEventTrainer.DATA_INDEXER_PARAM,
        AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);
    trainParams.put(Parameters.CUTOFF_PARAM, 1);

    EventTrainer<TrainingParameters> trainer = new GISTrainer();
    trainer.init(trainParams, null);
    MaxentModel model = trainer.train(PrepAttachDataUtil.createTrainingStream());

    PrepAttachDataUtil.testModel(model, 0.7997028967566229);
  }

  @Test
  void testMaxentOnPrepAttachDataWithParamsDefault() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(Parameters.ALGORITHM_PARAM, Parameters.ALGORITHM_DEFAULT_VALUE);

    EventTrainer<TrainingParameters> trainer = new GISTrainer();
    trainer.init(trainParams, null);
    MaxentModel model = trainer.train(PrepAttachDataUtil.createTrainingStream());

    PrepAttachDataUtil.testModel(model, 0.8086159940579352);
  }

  @Test
  void testMaxentOnPrepAttachDataWithParamsLLThreshold() throws IOException {
    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(Parameters.ALGORITHM_PARAM, Parameters.ALGORITHM_DEFAULT_VALUE);
    trainParams.put(GISTrainer.LOG_LIKELIHOOD_THRESHOLD_PARAM, 5.);

    EventTrainer<TrainingParameters> trainer = new GISTrainer();
    trainer.init(trainParams, null);
    MaxentModel model = trainer.train(PrepAttachDataUtil.createTrainingStream());

    PrepAttachDataUtil.testModel(model, 0.8103490963109681);
  }
}
