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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for persisting and reading naive bayes models.
 */
public class NaiveBayesModelReadWriteTest extends AbstractNaiveBayesTest {

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

  @Test
  void testBinaryModelPersistence() throws IOException {
    NaiveBayesModel model = (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);
    Path tempFile = Files.createTempFile("bnb-", ".bin");
    File file = tempFile.toFile();
    try {
      NaiveBayesModelWriter modelWriter = new BinaryNaiveBayesModelWriter(model, file);
      modelWriter.persist();
      NaiveBayesModelReader reader = new BinaryNaiveBayesModelReader(file);
      reader.checkModelType();
      AbstractModel abstractModel = reader.constructModel();
      Assertions.assertNotNull(abstractModel);
    } finally {
      file.delete();
    }
  }

  @Test
  void testTextModelPersistence() throws Exception {
    NaiveBayesModel model = (NaiveBayesModel) new NaiveBayesTrainer().trainModel(testDataIndexer);
    Path tempFile = Files.createTempFile("ptnb-", ".txt");
    File file = tempFile.toFile();
    try {
      NaiveBayesModelWriter modelWriter = new PlainTextNaiveBayesModelWriter(model, file);
      modelWriter.persist();
      NaiveBayesModelReader reader = new PlainTextNaiveBayesModelReader(file);
      reader.checkModelType();
      AbstractModel abstractModel = reader.constructModel();
      Assertions.assertNotNull(abstractModel);
    } finally {
      file.delete();
    }
  }
}
