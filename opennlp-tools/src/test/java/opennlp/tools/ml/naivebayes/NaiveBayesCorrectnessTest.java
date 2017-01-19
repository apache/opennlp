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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;

/**
 * Test for naive bayes classification correctness without smoothing
 */
public class NaiveBayesCorrectnessTest {

  private DataIndexer testDataIndexer;

  @Before
  public void initIndexer() {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(AbstractTrainer.CUTOFF_PARAM, "1");
    trainingParameters.put(AbstractDataIndexer.SORT_PARAM, "false");;
    testDataIndexer = new TwoPassDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
  }

  @Test
  public void testNaiveBayes1() throws IOException {

    testDataIndexer.index(createTrainingStream());
    NaiveBayesModel model =
        (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);

    String label = "politics";
    String[] context = {"bow=united", "bow=nations"};
    Event event = new Event(label, context);

    // testModel(model, event, 1.0);  // Expected value without smoothing
    testModel(model, event, 0.9681650180264167);   // Expected value with smoothing

  }

  @Test
  public void testNaiveBayes2() throws IOException {

    testDataIndexer.index(createTrainingStream());
    NaiveBayesModel model =
        (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);

    String label = "sports";
    String[] context = {"bow=manchester", "bow=united"};
    Event event = new Event(label, context);

    // testModel(model, event, 1.0);  // Expected value without smoothing
    testModel(model, event, 0.9658833555831029);   // Expected value with smoothing

  }

  @Test
  public void testNaiveBayes3() throws IOException {

    testDataIndexer.index(createTrainingStream());
    NaiveBayesModel model =
        (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);

    String label = "politics";
    String[] context = {"bow=united"};
    Event event = new Event(label, context);

    //testModel(model, event, 2.0/3.0);  // Expected value without smoothing
    testModel(model, event, 0.6655036407766989);  // Expected value with smoothing

  }

  @Test
  public void testNaiveBayes4() throws IOException {

    testDataIndexer.index(createTrainingStream());
    NaiveBayesModel model =
        (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);

    String label = "politics";
    String[] context = {};
    Event event = new Event(label, context);

    testModel(model, event, 7.0 / 12.0);

  }

  private void testModel(MaxentModel model, Event event, double higher_probability) {
    double[] outcomes = model.eval(event.getContext());
    String outcome = model.getBestOutcome(outcomes);
    Assert.assertEquals(2, outcomes.length);
    Assert.assertEquals(event.getOutcome(), outcome);
    if (event.getOutcome().equals(model.getOutcome(0))) {
      Assert.assertEquals(higher_probability, outcomes[0], 0.0001);
    }
    if (!event.getOutcome().equals(model.getOutcome(0))) {
      Assert.assertEquals(1.0 - higher_probability, outcomes[0], 0.0001);
    }
    if (event.getOutcome().equals(model.getOutcome(1))) {
      Assert.assertEquals(higher_probability, outcomes[1], 0.0001);
    }
    if (!event.getOutcome().equals(model.getOutcome(1))) {
      Assert.assertEquals(1.0 - higher_probability, outcomes[1], 0.0001);
    }
  }

  public static ObjectStream<Event> createTrainingStream() throws IOException {
    List<Event> trainingEvents = new ArrayList<>();

    String label1 = "politics";
    String[] context1 = {"bow=the", "bow=united", "bow=nations"};
    trainingEvents.add(new Event(label1, context1));

    String label2 = "politics";
    String[] context2 = {"bow=the", "bow=united", "bow=states", "bow=and"};
    trainingEvents.add(new Event(label2, context2));

    String label3 = "sports";
    String[] context3 = {"bow=manchester", "bow=united"};
    trainingEvents.add(new Event(label3, context3));

    String label4 = "sports";
    String[] context4 = {"bow=manchester", "bow=and", "bow=barca"};
    trainingEvents.add(new Event(label4, context4));

    return ObjectStreamUtils.createObjectStream(trainingEvents);
  }

}
