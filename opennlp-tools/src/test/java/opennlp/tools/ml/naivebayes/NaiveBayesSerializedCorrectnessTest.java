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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.TrainingParameters;

/**
 * Test for naive bayes classification correctness without smoothing
 */
public class NaiveBayesSerializedCorrectnessTest {

  private DataIndexer testDataIndexer;

  @Before
  public void initIndexer() {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(AbstractTrainer.CUTOFF_PARAM, 1);
    trainingParameters.put(AbstractDataIndexer.SORT_PARAM, false);;
    testDataIndexer = new TwoPassDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
  }

  @Test
  public void testNaiveBayes1() throws IOException {

    testDataIndexer.index(NaiveBayesCorrectnessTest.createTrainingStream());
    NaiveBayesModel model1 =
        (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);

    NaiveBayesModel model2 = persistedModel(model1);

    String label = "politics";
    String[] context = {"bow=united", "bow=nations"};
    Event event = new Event(label, context);

    testModelOutcome(model1, model2, event);

  }

  @Test
  public void testNaiveBayes2() throws IOException {

    testDataIndexer.index(NaiveBayesCorrectnessTest.createTrainingStream());
    NaiveBayesModel model1 =
        (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);

    NaiveBayesModel model2 = persistedModel(model1);

    String label = "sports";
    String[] context = {"bow=manchester", "bow=united"};
    Event event = new Event(label, context);

    testModelOutcome(model1, model2, event);

  }

  @Test
  public void testNaiveBayes3() throws IOException {

    testDataIndexer.index(NaiveBayesCorrectnessTest.createTrainingStream());
    NaiveBayesModel model1 =
        (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);

    NaiveBayesModel model2 = persistedModel(model1);

    String label = "politics";
    String[] context = {"bow=united"};
    Event event = new Event(label, context);

    testModelOutcome(model1, model2, event);

  }

  @Test
  public void testNaiveBayes4() throws IOException {

    testDataIndexer.index(NaiveBayesCorrectnessTest.createTrainingStream());
    NaiveBayesModel model1 =
        (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);

    NaiveBayesModel model2 = persistedModel(model1);

    String label = "politics";
    String[] context = {};
    Event event = new Event(label, context);

    testModelOutcome(model1, model2, event);

  }


  @Test
  public void testPlainTextModel() throws IOException {
    testDataIndexer.index(NaiveBayesCorrectnessTest.createTrainingStream());
    NaiveBayesModel model1 =
        (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);


    StringWriter sw1 = new StringWriter();

    NaiveBayesModelWriter modelWriter =
        new PlainTextNaiveBayesModelWriter(model1, new BufferedWriter(sw1));
    modelWriter.persist();

    NaiveBayesModelReader reader =
        new PlainTextNaiveBayesModelReader(new BufferedReader(new StringReader(sw1.toString())));
    reader.checkModelType();

    NaiveBayesModel model2 = (NaiveBayesModel)reader.constructModel();

    StringWriter sw2 = new StringWriter();
    modelWriter = new PlainTextNaiveBayesModelWriter(model2, new BufferedWriter(sw2));
    modelWriter.persist();

    System.out.println(sw1.toString());
    Assert.assertEquals(sw1.toString(), sw2.toString());

  }

  protected static NaiveBayesModel persistedModel(NaiveBayesModel model) throws IOException {
    Path tempFilePath = Files.createTempFile("ptnb-", ".bin");
    File file = tempFilePath.toFile();
    NaiveBayesModelWriter modelWriter = new BinaryNaiveBayesModelWriter(model, tempFilePath.toFile());
    modelWriter.persist();
    NaiveBayesModelReader reader = new BinaryNaiveBayesModelReader(file);
    reader.checkModelType();
    return (NaiveBayesModel)reader.constructModel();
  }

  protected static void testModelOutcome(NaiveBayesModel model1, NaiveBayesModel model2, Event event) {
    String[] labels1 = extractLabels(model1);
    String[] labels2 = extractLabels(model2);

    Assert.assertArrayEquals(labels1, labels2);

    double[] outcomes1 = model1.eval(event.getContext());
    double[] outcomes2 = model2.eval(event.getContext());

    Assert.assertArrayEquals(outcomes1, outcomes2, 0.000000000001);

  }

  private static String[] extractLabels(NaiveBayesModel model) {
    String[] labels = new String[model.getNumOutcomes()];
    for (int i = 0; i < model.getNumOutcomes(); i++) {
      labels[i] = model.getOutcome(i);
    }
    return labels;
  }
}
