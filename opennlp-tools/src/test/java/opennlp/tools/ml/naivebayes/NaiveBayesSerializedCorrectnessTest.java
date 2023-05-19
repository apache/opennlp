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
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.TrainingParameters;

/**
 * Test for naive bayes classification correctness without smoothing.
 */
public class NaiveBayesSerializedCorrectnessTest extends AbstractNaiveBayesTest {

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
  @MethodSource("provideLabelsWithContext")
  void testNaiveBayes(String label, String[] context) throws IOException {
    NaiveBayesModel model1 = (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);
    NaiveBayesModel model2 = persistedModel(model1);
    Event event = new Event(label, context);
    testModelOutcome(model1, model2, event);
  }

  /*
   * Produces a stream of <label|context> pairs for parameterized unit tests.
   */
  private static Stream<Arguments> provideLabelsWithContext() {
    return Stream.of(
            // Example 1:
            Arguments.of("politics" , new String[] {"bow=united", "bow=nations"}),
            Arguments.of("sports",  new String[] {"bow=manchester", "bow=united"}),
            Arguments.of("politics",  new String[] {"bow=united"}),
            Arguments.of("politics",  new String[] {})
    );
  }

  @Test
  void testPlainTextModel() throws IOException {
    NaiveBayesModel model1 = (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);

    StringWriter sw1 = new StringWriter();

    NaiveBayesModelWriter modelWriter = new PlainTextNaiveBayesModelWriter(model1, new BufferedWriter(sw1));
    modelWriter.persist();

    NaiveBayesModelReader reader =
        new PlainTextNaiveBayesModelReader(new BufferedReader(new StringReader(sw1.toString())));
    reader.checkModelType();

    NaiveBayesModel model2 = (NaiveBayesModel) reader.constructModel();

    StringWriter sw2 = new StringWriter();
    modelWriter = new PlainTextNaiveBayesModelWriter(model2, new BufferedWriter(sw2));
    modelWriter.persist();

    Assertions.assertEquals(sw1.toString(), sw2.toString());
  }

  private static NaiveBayesModel persistedModel(NaiveBayesModel model) throws IOException {
    Path tempFilePath = Files.createTempFile("ptnb-", ".bin");
    File file = tempFilePath.toFile();
    try {
      NaiveBayesModelWriter modelWriter = new BinaryNaiveBayesModelWriter(model, file);
      modelWriter.persist();
      NaiveBayesModelReader reader = new BinaryNaiveBayesModelReader(file);
      reader.checkModelType();
      return (NaiveBayesModel) reader.constructModel();
    } finally {
      file.delete();
    }
  }

  private static void testModelOutcome(NaiveBayesModel model1, NaiveBayesModel model2, Event event) {
    String[] labels1 = extractLabels(model1);
    String[] labels2 = extractLabels(model2);

    Assertions.assertArrayEquals(labels1, labels2);
    double[] outcomes1 = model1.eval(event.getContext());
    double[] outcomes2 = model2.eval(event.getContext());

    Assertions.assertArrayEquals(outcomes1, outcomes2, 0.000000000001);
  }

  private static String[] extractLabels(NaiveBayesModel model) {
    String[] labels = new String[model.getNumOutcomes()];
    for (int i = 0; i < model.getNumOutcomes(); i++) {
      labels[i] = model.getOutcome(i);
    }
    return labels;
  }
}
