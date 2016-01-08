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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * Tests for persisting and reading naive bayes models
 */
public class NaiveBayesModelReadWriteTest {
  @Test
  public void testBinaryModelPersistence() throws Exception {
    NaiveBayesModel model = (NaiveBayesModel) new NaiveBayesTrainer().trainModel(new TwoPassDataIndexer(
        NaiveBayesCorrectnessTest.createTrainingStream(), 1, false));
    Path tempFile = Files.createTempFile("bnb-", ".bin");
    File file = tempFile.toFile();
    NaiveBayesModelWriter modelWriter = new BinaryNaiveBayesModelWriter(model, file);
    modelWriter.persist();
    NaiveBayesModelReader reader = new BinaryNaiveBayesModelReader(file);
    reader.checkModelType();
    AbstractModel abstractModel = reader.constructModel();
    assertNotNull(abstractModel);
  }

  @Test
  public void testTextModelPersistence() throws Exception {
    NaiveBayesModel model = (NaiveBayesModel) new NaiveBayesTrainer().trainModel(new TwoPassDataIndexer(
        NaiveBayesCorrectnessTest.createTrainingStream(), 1, false));
    Path tempFile = Files.createTempFile("ptnb-", ".txt");
    File file = tempFile.toFile();
    NaiveBayesModelWriter modelWriter = new PlainTextNaiveBayesModelWriter(model, file);
    modelWriter.persist();
    NaiveBayesModelReader reader = new PlainTextNaiveBayesModelReader(file);
    reader.checkModelType();
    AbstractModel abstractModel = reader.constructModel();
    assertNotNull(abstractModel);
  }
}