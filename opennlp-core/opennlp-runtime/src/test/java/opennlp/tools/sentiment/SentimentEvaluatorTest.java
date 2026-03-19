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
  void testEvaluateSample() {
    SentimentME me = new SentimentME(model);
    SentimentEvaluator evaluator = new SentimentEvaluator(me);

    SentimentSample sample = new SentimentSample("positive",
        new String[] {"I", "love", "this", "great", "product"});

    evaluator.evaluateSample(sample);

    Assertions.assertNotNull(evaluator.getFMeasure());
  }

  @Test
  void testGetFMeasureBeforeEvaluation() {
    SentimentME me = new SentimentME(model);
    SentimentEvaluator evaluator = new SentimentEvaluator(me);

    Assertions.assertNotNull(evaluator.getFMeasure());
    // FMeasure with no data should be -1 or 0
    Assertions.assertTrue(evaluator.getFMeasure().getFMeasure() <= 0);
  }

  @Test
  void testEvaluateMultipleSamples() {
    SentimentME me = new SentimentME(model);
    SentimentEvaluator evaluator = new SentimentEvaluator(me);

    evaluator.evaluateSample(new SentimentSample("positive",
        new String[] {"wonderful", "amazing", "great"}));
    evaluator.evaluateSample(new SentimentSample("negative",
        new String[] {"terrible", "horrible", "awful"}));

    Assertions.assertNotNull(evaluator.getFMeasure());
  }
}
