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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.OnePassRealValueDataIndexer;
import opennlp.tools.ml.model.RealValueFileEventStream;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class ScaleDoesntMatterTest {

  private DataIndexer testDataIndexer;

  @Before
  public void initIndexer() {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(AbstractTrainer.CUTOFF_PARAM, "0");
    testDataIndexer = new OnePassRealValueDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
  }

  /**
   * This test sets out to prove that the scale you use on real valued
   * predicates doesn't matter when it comes the probability assigned to each
   * outcome. Strangely, if we use (1,2) and (10,20) there's no difference. If
   * we use (0.1,0.2) and (10,20) there is a difference.
   *
   */
  @Test
  public void testScaleResults() throws Exception {
    String smallValues = "predA=0.1 predB=0.2 A\n" + "predB=0.3 predA=0.1 B\n";

    String smallTest = "predA=0.2 predB=0.2";

    String largeValues = "predA=10 predB=20 A\n" + "predB=30 predA=10 B\n";

    String largeTest = "predA=20 predB=20";

    ObjectStream<Event> smallEventStream = new RealBasicEventStream(
        new PlainTextByLineStream(new MockInputStreamFactory(smallValues), StandardCharsets.UTF_8));

    testDataIndexer.index(smallEventStream);
    MaxentModel smallModel = GIS.trainModel(100,
        testDataIndexer, false);
    String[] contexts = smallTest.split(" ");
    float[] values = RealValueFileEventStream.parseContexts(contexts);
    double[] smallResults = smallModel.eval(contexts, values);

    String smallResultString = smallModel.getAllOutcomes(smallResults);
    System.out.println("smallResults: " + smallResultString);

    ObjectStream<Event> largeEventStream = new RealBasicEventStream(
        new PlainTextByLineStream(new MockInputStreamFactory(largeValues), StandardCharsets.UTF_8));

    testDataIndexer.index(largeEventStream);
    MaxentModel largeModel = GIS.trainModel(100,
        testDataIndexer, false);
    contexts = largeTest.split(" ");
    values = RealValueFileEventStream.parseContexts(contexts);
    double[] largeResults = largeModel.eval(contexts, values);

    String largeResultString = smallModel.getAllOutcomes(largeResults);
    System.out.println("largeResults: " + largeResultString);

    Assert.assertEquals(smallResults.length, largeResults.length);
    for (int i = 0; i < smallResults.length; i++) {
      System.out.println(String.format(
          "classifiy with smallModel: %1$s = %2$f", smallModel.getOutcome(i),
          smallResults[i]));
      System.out.println(String.format(
          "classifiy with largeModel: %1$s = %2$f", largeModel.getOutcome(i),
          largeResults[i]));
      Assert.assertEquals(smallResults[i], largeResults[i], 0.01f);
    }
  }
}