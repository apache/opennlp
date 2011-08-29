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

package opennlp.perceptron;

import static opennlp.PrepAttachDataUtil.createTrainingStream;
import static opennlp.PrepAttachDataUtil.testModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.model.TrainUtil;
import opennlp.model.TwoPassDataIndexer;

import org.junit.Test;

/**
 * Test for perceptron training and use with the ppa data.
 */
public class PerceptronPrepAttachTest {

  @Test
  public void testPerceptronOnPrepAttachData() throws IOException {
    AbstractModel model = 
        new PerceptronTrainer().trainModel(400, 
        new TwoPassDataIndexer(createTrainingStream(), 1, false), 1);

    testModel(model, 0.7650408516959644);
  }
  
  @Test
  public void testPerceptronOnPrepAttachDataWithSkippedAveraging() throws IOException {
    
    Map<String, String> trainParams = new HashMap<String, String>();
    trainParams.put(TrainUtil.ALGORITHM_PARAM, TrainUtil.PERCEPTRON_VALUE);
    trainParams.put(TrainUtil.CUTOFF_PARAM, Integer.toString(1));
    trainParams.put("UseSkippedAveraging", Boolean.toString(true));
    
    AbstractModel model = TrainUtil.train(createTrainingStream(), trainParams, null);
    
    testModel(model, 0.773706362961129);
  }
  
  @Test
  public void testPerceptronOnPrepAttachDataWithTolerance() throws IOException {
    
    Map<String, String> trainParams = new HashMap<String, String>();
    trainParams.put(TrainUtil.ALGORITHM_PARAM, TrainUtil.PERCEPTRON_VALUE);
    trainParams.put(TrainUtil.CUTOFF_PARAM, Integer.toString(1));
    trainParams.put(TrainUtil.ITERATIONS_PARAM, Integer.toString(500));
    trainParams.put("Tolerance", Double.toString(0.0001d));
    
    AbstractModel model = TrainUtil.train(createTrainingStream(), trainParams, null);
    
    testModel(model, 0.7677642980935875);
  }
  
  @Test
  public void testPerceptronOnPrepAttachDataWithStepSizeDecrease() throws IOException {
    
    Map<String, String> trainParams = new HashMap<String, String>();
    trainParams.put(TrainUtil.ALGORITHM_PARAM, TrainUtil.PERCEPTRON_VALUE);
    trainParams.put(TrainUtil.CUTOFF_PARAM, Integer.toString(1));
    trainParams.put(TrainUtil.ITERATIONS_PARAM, Integer.toString(500));
    trainParams.put("StepSizeDecrease", Double.toString(0.06d));
    
    AbstractModel model = TrainUtil.train(createTrainingStream(), trainParams, null);
    
    testModel(model, 0.7756870512503095);
  }
}
