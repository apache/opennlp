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

package opennlp.tools.sentiment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link SentimentME} class.
 */
public class SentimentMETest {

  private static SentimentModel model;

  // SUT
  private SentimentME sentiment;

  @BeforeAll
  static void trainModel() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        SentimentMETest.class, "/opennlp/tools/sentiment/train.txt");

    SentimentSampleStream sampleStream = new SentimentSampleStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));

    model = SentimentME.train("eng", sampleStream,
        TrainingParameters.defaultParams(), new SentimentFactory());

    Assertions.assertNotNull(model);
  }

  @BeforeEach
  void setup() {
    sentiment = new SentimentME(model);
  }

  @Test
  void testPredictWithTokens() {

    String prediction = sentiment.predict(new String[] {"I", "love", "this", "product"});
    Assertions.assertNotNull(prediction);
    Assertions.assertEquals("positive", prediction);
  }

  @Test
  void testPredictWithString() {
    String prediction = sentiment.predict("I love this product");
    Assertions.assertNotNull(prediction);
  }

  @Test
  void testProbabilities() {
    double[] probs = sentiment.probabilities(new String[] {"great", "amazing", "wonderful"});
    Assertions.assertNotNull(probs);
    Assertions.assertTrue(probs.length > 0);

    double sum = 0;
    for (double p : probs) {
      Assertions.assertTrue(p >= 0 && p <= 1);
      sum += p;
    }
    Assertions.assertEquals(1.0, sum, 0.01);
  }

  @Test
  void testGetBestSentiment() {
    double[] probs = sentiment.probabilities(new String[] {"terrible", "awful", "bad"});
    String best = sentiment.getBestSentiment(probs);
    Assertions.assertNotNull(best);
  }

  @Test
  void testModelSerialization() throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      model.serialize(out);

      byte[] bytes = out.toByteArray();
      Assertions.assertTrue(bytes.length > 0);

      final SentimentModel deserialized = new SentimentModel(new ByteArrayInputStream(bytes));
      Assertions.assertNotNull(deserialized);
      Assertions.assertEquals("eng", deserialized.getLanguage());
      Assertions.assertNotNull(deserialized.getMaxentModel());
      Assertions.assertNotNull(deserialized.getFactory());

      SentimentME me = new SentimentME(deserialized);
      String prediction = me.predict(new String[] {"love", "great", "happy"});
      Assertions.assertNotNull(prediction);
      Assertions.assertEquals("positive", prediction);
    }
  }

  @Test
  void testNullModelThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new SentimentME(null));
  }

  @Test
  void testModelLanguage() {
    Assertions.assertEquals("eng", model.getLanguage());
  }

  @Test
  void testModelFactory() {
    Assertions.assertNotNull(model.getFactory());
    Assertions.assertInstanceOf(SentimentFactory.class, model.getFactory());
  }

  @Test
  void testModelMaxentModel() {
    Assertions.assertNotNull(model.getMaxentModel());
  }

  @Test
  void testPredictPositiveSentence() {
    String prediction = sentiment.predict("I love this wonderful amazing great product");
    Assertions.assertEquals("positive", prediction);
  }

  @Test
  void testPredictReturnsValidLabel() {
    String prediction = sentiment.predict("I hate this terrible awful horrible product");
    Assertions.assertTrue("positive".equals(prediction) || "negative".equals(prediction),
        "Prediction should be a valid sentiment label but was: " + prediction);
  }

  @Test
  void testModelFileSerializationRoundTrip() throws IOException {
    File tempFile = File.createTempFile("sentiment-model", ".bin");
    tempFile.deleteOnExit();

    model.serialize(tempFile);
    Assertions.assertTrue(tempFile.length() > 0);

    final SentimentModel deserialized = new SentimentModel(tempFile);
    Assertions.assertNotNull(deserialized);
    Assertions.assertEquals("eng", deserialized.getLanguage());
    Assertions.assertNotNull(deserialized.getMaxentModel());
    Assertions.assertNotNull(deserialized.getFactory());
    Assertions.assertTrue(deserialized.isLoadedFromSerialized());

    SentimentME me = new SentimentME(deserialized);
    String prediction = me.predict(new String[] {"love", "great", "happy"});
    Assertions.assertNotNull(prediction);
  }

  @Test
  void testModelPathSerializationRoundTrip() throws IOException {
    Path tempPath = File.createTempFile("sentiment-model", ".bin").toPath();
    tempPath.toFile().deleteOnExit();

    model.serialize(tempPath);
    Assertions.assertTrue(tempPath.toFile().length() > 0);

    SentimentModel deserialized = new SentimentModel(tempPath.toUri().toURL());
    Assertions.assertNotNull(deserialized);
    Assertions.assertEquals("eng", deserialized.getLanguage());
    Assertions.assertNotNull(deserialized.getMaxentModel());
    Assertions.assertNotNull(deserialized.getFactory());

    SentimentME me = new SentimentME(deserialized);
    String prediction = me.predict(new String[] {"terrible", "bad"});
    Assertions.assertNotNull(prediction);
  }

  @Test
  void testModelNotLoadedFromSerialized() {
    Assertions.assertFalse(model.isLoadedFromSerialized());
  }

  @Test
  void testModelIsLoadedFromSerialized() throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      model.serialize(out);

      final SentimentModel deserialized = new SentimentModel(
          new ByteArrayInputStream(out.toByteArray()));
      Assertions.assertTrue(deserialized.isLoadedFromSerialized());
    }
  }
}
