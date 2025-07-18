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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.OnePassRealValueDataIndexer;
import opennlp.tools.ml.model.RealValueFileEventStream;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class ScaleDoesntMatterTest {

  private DataIndexer<TrainingParameters> testDataIndexer;

  @BeforeEach
  void initIndexer() {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(Parameters.CUTOFF_PARAM, 0);
    testDataIndexer = new OnePassRealValueDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
  }

  /**
   * This test sets out to prove that the scale you use on real valued
   * predicates doesn't matter when it comes the probability assigned to each
   * outcome. Strangely, if we use (1,2) and (10,20) there's no difference. If
   * we use (0.1,0.2) and (10,20) there is a difference.
   * <p>
   * Note: The textual event format is:
   * <br/>
   * {@code outcome context1 context2 context3 ...}
   * <p>
   * This was changed when
   * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-589">OPENNLP-589</a> was resolved.
   */
  @Test
  void testScaleResults() throws Exception {
    final String smallValues = "A predA=0.1 predB=0.2\n" + "B predB=0.3 predA=0.1\n";
    final String smallTest = "predA=0.2 predB=0.2";
    final String largeValues = "A predA=10 predB=20\n" + "B predB=30 predA=10\n";
    final String largeTest = "predA=20 predB=20";

    ObjectStream<Event> smallEventStream = new RealBasicEventStream(
        new PlainTextByLineStream(new MockInputStreamFactory(smallValues), StandardCharsets.UTF_8));
    testDataIndexer.index(smallEventStream);

    EventTrainer<TrainingParameters> smallModelTrainer = new GISTrainer();
    smallModelTrainer.init(createDefaultTrainingParameters(), null);

    MaxentModel smallModel = smallModelTrainer.train(testDataIndexer);
    String[] contexts = smallTest.split("\\s+");
    float[] values = RealValueFileEventStream.parseContexts(contexts);
    double[] smallResults = smallModel.eval(contexts, values);

    String smallResultString = smallModel.getAllOutcomes(smallResults);
    Assertions.assertNotNull(smallResultString);

    ObjectStream<Event> largeEventStream = new RealBasicEventStream(
        new PlainTextByLineStream(new MockInputStreamFactory(largeValues), StandardCharsets.UTF_8));
    testDataIndexer.index(largeEventStream);

    EventTrainer<TrainingParameters> largeModelTrainer = new GISTrainer();
    largeModelTrainer.init(createDefaultTrainingParameters(), null);

    MaxentModel largeModel = largeModelTrainer.train(testDataIndexer);
    contexts = largeTest.split("\\s+");
    values = RealValueFileEventStream.parseContexts(contexts);
    double[] largeResults = largeModel.eval(contexts, values);

    String largeResultString = largeModel.getAllOutcomes(largeResults);
    Assertions.assertNotNull(largeResultString);
    Assertions.assertEquals(smallResults.length, largeResults.length);

    for (int i = 0; i < smallResults.length; i++) {
      Assertions.assertEquals(largeResults[i], smallResults[i], 0.01f);
    }
  }

  private static TrainingParameters createDefaultTrainingParameters() {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(Parameters.ALGORITHM_PARAM, Parameters.ALGORITHM_DEFAULT_VALUE);
    mlParams.put(Parameters.ITERATIONS_PARAM, 100);
    mlParams.put(Parameters.CUTOFF_PARAM, 5);

    return mlParams;
  }
}
