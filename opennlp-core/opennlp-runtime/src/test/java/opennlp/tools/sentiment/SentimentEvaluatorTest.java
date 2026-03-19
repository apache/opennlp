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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link SentimentEvaluator} class.
 */
public class SentimentEvaluatorTest {

  private static SentimentModel model;

  @BeforeAll
  static void trainModel() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        SentimentEvaluatorTest.class, "/opennlp/tools/sentiment/train.txt");

    SentimentSampleStream sampleStream = new SentimentSampleStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));

    model = SentimentME.train("eng", sampleStream,
        TrainingParameters.defaultParams(), new SentimentFactory());
  }

  @Test
  void testEvaluateSampleCorrectPrediction() {
    SentimentME me = new SentimentME(model);

    List<SentimentSample> references = new ArrayList<>();
    List<SentimentSample> predictions = new ArrayList<>();
    SentimentEvaluationMonitor monitor = new SentimentEvaluationMonitor() {
      @Override
      public void correctlyClassified(SentimentSample reference, SentimentSample prediction) {
        references.add(reference);
        predictions.add(prediction);
      }

      @Override
      public void misclassified(SentimentSample reference, SentimentSample prediction) {
        references.add(reference);
        predictions.add(prediction);
      }
    };

    SentimentEvaluator evaluator = new SentimentEvaluator(me, monitor);

    SentimentSample sample = new SentimentSample("positive",
        new String[] {"I", "love", "this", "great", "product"});

    evaluator.evaluateSample(sample);

    Assertions.assertEquals(1, references.size());
    Assertions.assertEquals(1, predictions.size());

    // Verify the reference sample is the original
    Assertions.assertEquals("positive", references.getFirst().getSentiment());
    Assertions.assertArrayEquals(sample.getSentence(), references.getFirst().getSentence());

    // Verify the predicted sample has a valid sentiment and the same sentence
    SentimentSample predicted = predictions.getFirst();
    Assertions.assertNotNull(predicted.getSentiment());
    Assertions.assertArrayEquals(sample.getSentence(), predicted.getSentence());

    Assertions.assertNotNull(evaluator.getFMeasure());
    Assertions.assertTrue(evaluator.getFMeasure().getRecallScore() > 0);
    Assertions.assertTrue(evaluator.getFMeasure().getPrecisionScore() > 0);
  }

  @Test
  void testGetFMeasureBeforeEvaluation() {
    SentimentME me = new SentimentME(model);
    SentimentEvaluator evaluator = new SentimentEvaluator(me);

    Assertions.assertNotNull(evaluator.getFMeasure());
    // FMeasure with no data should be -1 or 0
    Assertions.assertEquals(-1.0, evaluator.getFMeasure().getFMeasure());
  }

  @Test
  void testEvaluateMultipleSamples() {
    SentimentME me = new SentimentME(model);

    List<SentimentSample> predictions = new ArrayList<>();
    SentimentEvaluationMonitor monitor = new SentimentEvaluationMonitor() {
      @Override
      public void correctlyClassified(SentimentSample reference, SentimentSample prediction) {
        predictions.add(prediction);
      }

      @Override
      public void misclassified(SentimentSample reference, SentimentSample prediction) {
        predictions.add(prediction);
      }
    };

    SentimentEvaluator evaluator = new SentimentEvaluator(me, monitor);

    SentimentSample posSample = new SentimentSample("positive",
        new String[] {"wonderful", "amazing", "great"});
    SentimentSample negSample = new SentimentSample("negative",
        new String[] {"terrible", "horrible", "awful"});

    evaluator.evaluateSample(posSample);
    evaluator.evaluateSample(negSample);

    Assertions.assertEquals(2, predictions.size());

    // Each predicted sample should have a valid sentiment and the original sentence
    Assertions.assertNotNull(predictions.get(0).getSentiment());
    Assertions.assertArrayEquals(posSample.getSentence(), predictions.get(0).getSentence());

    Assertions.assertNotNull(predictions.get(1).getSentiment());
    Assertions.assertArrayEquals(negSample.getSentence(), predictions.get(1).getSentence());

    Assertions.assertNotNull(evaluator.getFMeasure());
    Assertions.assertTrue(evaluator.getFMeasure().getRecallScore() > 0);
    Assertions.assertTrue(evaluator.getFMeasure().getPrecisionScore() > 0);
  }

  @Test
  void testProcessSampleReturnsPrediction() {
    SentimentME me = new SentimentME(model);
    SentimentEvaluator evaluator = new SentimentEvaluator(me);

    SentimentSample reference = new SentimentSample("positive",
        new String[] {"I", "love", "this", "great", "product"});

    SentimentSample result = evaluator.processSample(reference);

    Assertions.assertNotNull(result);
    // The returned sample should contain the model's prediction as sentiment
    Assertions.assertNotNull(result.getSentiment());
    Assertions.assertTrue("positive".equals(result.getSentiment())
        || "negative".equals(result.getSentiment()));
    // The sentence should be preserved from the reference
    Assertions.assertArrayEquals(reference.getSentence(), result.getSentence());
  }

  @Test
  void testProcessSampleUpdatesScores() {
    SentimentME me = new SentimentME(model);
    SentimentEvaluator evaluator = new SentimentEvaluator(me);

    // FMeasure should have no data initially
    Assertions.assertEquals(-1.0, evaluator.getFMeasure().getFMeasure());

    evaluator.processSample(new SentimentSample("positive",
        new String[] {"wonderful", "amazing", "great"}));

    // After processing, FMeasure should have been updated
    Assertions.assertTrue(evaluator.getFMeasure().getRecallScore() > 0);
    Assertions.assertTrue(evaluator.getFMeasure().getPrecisionScore() > 0);
  }
}
