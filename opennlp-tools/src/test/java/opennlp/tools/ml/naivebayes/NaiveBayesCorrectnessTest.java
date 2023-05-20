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
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.TrainingParameters;

/**
 * Test for naive bayes classification correctness without smoothing
 */
public class NaiveBayesCorrectnessTest extends AbstractNaiveBayesTest {

  private DataIndexer testDataIndexer;

  @BeforeEach
  void initIndexer() throws IOException {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainingParameters.put(AbstractDataIndexer.SORT_PARAM, false);
    testDataIndexer = new TwoPassDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
    testDataIndexer.index(createTrainingStream());
  }

  @ParameterizedTest
  @MethodSource("provideLabelsWithContextAndProb")
  void testNaiveBayes(String label, String[] context, double expectedProb) {
    NaiveBayesModel model = (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);
    Event event = new Event(label, context);
    
    testModel(model, event, expectedProb);   // Expected value with smoothing
  }

  /*
   * Produces a stream of <label|context> pairs for parameterized unit tests.
   */
  private static Stream<Arguments> provideLabelsWithContextAndProb() {
    return Stream.of(
            // Example 1:
            Arguments.of("politics" , new String[] {"bow=united", "bow=nations"}, 0.9681650180264167),
            Arguments.of("sports",  new String[] {"bow=manchester", "bow=united"}, 0.9658833555831029),
            Arguments.of("politics",  new String[] {"bow=united"}, 0.6655036407766989),
            Arguments.of("politics",  new String[] {}, 7.0 / 12.0)
    );
  }

  private void testModel(MaxentModel model, Event event, double higher_probability) {
    double[] outcomes = model.eval(event.getContext());
    String outcome = model.getBestOutcome(outcomes);
    Assertions.assertEquals(2, outcomes.length);
    Assertions.assertEquals(event.getOutcome(), outcome);
    if (event.getOutcome().equals(model.getOutcome(0))) {
      Assertions.assertEquals(higher_probability, outcomes[0], 0.0001);
    }
    if (!event.getOutcome().equals(model.getOutcome(0))) {
      Assertions.assertEquals(1.0 - higher_probability, outcomes[0], 0.0001);
    }
    if (event.getOutcome().equals(model.getOutcome(1))) {
      Assertions.assertEquals(higher_probability, outcomes[1], 0.0001);
    }
    if (!event.getOutcome().equals(model.getOutcome(1))) {
      Assertions.assertEquals(1.0 - higher_probability, outcomes[1], 0.0001);
    }
  }

}
