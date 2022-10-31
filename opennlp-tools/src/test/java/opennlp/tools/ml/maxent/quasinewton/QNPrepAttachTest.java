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

import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.PrepAttachDataUtil;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.TrainingParameters;

public class QNPrepAttachTest {

  @Test
  public void testQNOnPrepAttachData() throws IOException {
    DataIndexer indexer = new TwoPassDataIndexer();
    TrainingParameters indexingParameters = new TrainingParameters();
    indexingParameters.put(AbstractTrainer.CUTOFF_PARAM, 1);
    indexingParameters.put(AbstractDataIndexer.SORT_PARAM, false);
    indexer.init(indexingParameters, new HashMap<>());
    indexer.index(PrepAttachDataUtil.createTrainingStream());

    AbstractModel model = new QNTrainer(true).trainModel(100, indexer );

    PrepAttachDataUtil.testModel(model, 0.8155484030700668);
  }

  @Test
  public void testQNOnPrepAttachDataWithParamsDefault() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, QNTrainer.MAXENT_QN_VALUE);

    MaxentModel model = TrainerFactory.getEventTrainer(trainParams, null)
                                      .train(PrepAttachDataUtil.createTrainingStream());

    PrepAttachDataUtil.testModel(model, 0.8115870264917059);
  }

  @Test
  public void testQNOnPrepAttachDataWithElasticNetParams() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, QNTrainer.MAXENT_QN_VALUE);
    trainParams.put(AbstractEventTrainer.DATA_INDEXER_PARAM,
        AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainParams.put(QNTrainer.L1COST_PARAM, 0.25);
    trainParams.put(QNTrainer.L2COST_PARAM, 1.0D);

    MaxentModel model = TrainerFactory.getEventTrainer(trainParams, null)
                                      .train(PrepAttachDataUtil.createTrainingStream());

    PrepAttachDataUtil.testModel(model, 0.8229759841544937);
  }

  @Test
  public void testQNOnPrepAttachDataWithL1Params() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, QNTrainer.MAXENT_QN_VALUE);
    trainParams.put(AbstractEventTrainer.DATA_INDEXER_PARAM,
        AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainParams.put(QNTrainer.L1COST_PARAM, 1.0D);
    trainParams.put(QNTrainer.L2COST_PARAM, 0D);

    MaxentModel model = TrainerFactory.getEventTrainer(trainParams, null)
                                      .train(PrepAttachDataUtil.createTrainingStream());

    PrepAttachDataUtil.testModel(model, 0.8180242634315424);
  }

  @Test
  public void testQNOnPrepAttachDataWithL2Params() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, QNTrainer.MAXENT_QN_VALUE);
    trainParams.put(AbstractEventTrainer.DATA_INDEXER_PARAM,
        AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);
    trainParams.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainParams.put(QNTrainer.L1COST_PARAM, 0D);
    trainParams.put(QNTrainer.L2COST_PARAM, 1.0D);

    MaxentModel model = TrainerFactory.getEventTrainer(trainParams, null)
                                      .train(PrepAttachDataUtil.createTrainingStream());

    PrepAttachDataUtil.testModel(model, 0.8227283981183461);
  }

  @Test
  public void testQNOnPrepAttachDataInParallel() throws IOException {

    TrainingParameters trainParams = new TrainingParameters();
    trainParams.put(AbstractTrainer.ALGORITHM_PARAM, QNTrainer.MAXENT_QN_VALUE);
    trainParams.put(QNTrainer.THREADS_PARAM, 2);

    MaxentModel model = TrainerFactory.getEventTrainer(trainParams, null)
                                      .train(PrepAttachDataUtil.createTrainingStream());

    PrepAttachDataUtil.testModel(model, 0.8115870264917059);
  }
}

