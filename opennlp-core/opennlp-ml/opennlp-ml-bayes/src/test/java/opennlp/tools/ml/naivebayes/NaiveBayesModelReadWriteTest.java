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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for persisting and reading naive bayes models.
 */
public class NaiveBayesModelReadWriteTest extends AbstractNaiveBayesTest {

  private DataIndexer<TrainingParameters> testDataIndexer;

  @BeforeEach
  void initIndexer() throws IOException {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(Parameters.CUTOFF_PARAM, 1);
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

  @ParameterizedTest
  @ValueSource(strings = {".txt", ".txt.gz"})
  void testTextModelPersistenceKeepsNonAsciiNames(String suffix) throws Exception {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(Parameters.CUTOFF_PARAM, 1);
    DataIndexer<TrainingParameters> indexer = new TwoPassDataIndexer();
    indexer.init(trainingParameters, new HashMap<>());
    indexer.index(ObjectStreamUtils.createObjectStream(List.of(
        new Event("Ñandú", new String[] {"stadt=München"}),
        new Event("Kölsch", new String[] {"stadt=Köln"}))));
    NaiveBayesModel model = (NaiveBayesModel) new NaiveBayesTrainer().trainModel(indexer);

    File file = Files.createTempFile("ptnb-", suffix).toFile();
    try {
      new PlainTextNaiveBayesModelWriter(model, file).persist();
      try (InputStream raw = Files.newInputStream(file.toPath());
           InputStream in = suffix.endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        Assertions.assertTrue(text.contains("Ñandú") && text.contains("stadt=München"),
            "the model is written as UTF-8");
      }
      AbstractModel read = new PlainTextNaiveBayesModelReader(file).getModel();
      Assertions.assertEquals(model.getIndex("Ñandú"), read.getIndex("Ñandú"));
      Assertions.assertEquals(model.getIndex("Kölsch"), read.getIndex("Kölsch"));
      Assertions.assertArrayEquals(model.eval(new String[] {"stadt=München"}),
          read.eval(new String[] {"stadt=München"}), 1e-9);
    } finally {
      file.delete();
    }
  }
}
