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

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

public class GISTrainerTest {

  @SuppressWarnings("unchecked")
  @Test
  public void testGaussianSmoothing() throws Exception {
   
    TrainingParameters params = new TrainingParameters();
    params.put("Algorithm", "MAXENT");
    params.put("DataIndexer", "OnePass");
    params.put("Cutoff", 0);
    params.put("Iterations", 5);
    params.put("GaussianSmoothing", true);

    Map<String, String> reportMap = new HashMap<>();
    EventTrainer trainer = TrainerFactory.getEventTrainer(params, reportMap);
    
    ObjectStream<Event> eventStream = new FootballEventStream();
    AbstractModel smoothedModel = (AbstractModel)trainer.train(eventStream);
    Map<String, Context> predMap = (Map<String, Context>)smoothedModel.getDataStructures()[1];

    double[] nevilleFalseExpected = new double[] {-0.17,.10,0.05};
    double[] nevilleTrueExpected = new double[] {0.080,-0.047,-0.080};

    String predicateToTest = "Neville=false";
    Assert.assertArrayEquals(nevilleFalseExpected, predMap.get(predicateToTest).getParameters(), 0.01);
    predicateToTest = "Neville=true";
    Assert.assertArrayEquals(nevilleTrueExpected, predMap.get(predicateToTest).getParameters(), 0.001);
    
    eventStream.reset();
    params.put("GaussianSmoothing", false);
    trainer = TrainerFactory.getEventTrainer(params, reportMap);
    AbstractModel unsmoothedModel = (AbstractModel)trainer.train(eventStream);
    predMap = (Map<String, Context>)unsmoothedModel.getDataStructures()[1];
    
    nevilleFalseExpected = new double[] {-0.19,0.11,0.06};
    nevilleTrueExpected = new double[] {0.081,-0.050,-0.084};

    predicateToTest = "Neville=false";
    Assert.assertArrayEquals(nevilleFalseExpected, predMap.get(predicateToTest).getParameters(), 0.01);
    predicateToTest = "Neville=true";
    Assert.assertArrayEquals(nevilleTrueExpected, predMap.get(predicateToTest).getParameters(), 0.001);

    eventStream.close();
  }
  
}
