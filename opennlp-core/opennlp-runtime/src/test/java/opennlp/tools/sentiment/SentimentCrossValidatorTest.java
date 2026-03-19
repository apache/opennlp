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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link SentimentCrossValidator} class.
 */
public class SentimentCrossValidatorTest {

  private SentimentSampleStream createSampleStream() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        SentimentCrossValidatorTest.class, "/opennlp/tools/sentiment/train.txt");

    return new SentimentSampleStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  @Test
  void testCrossValidation() throws IOException {
    SentimentCrossValidator cv = new SentimentCrossValidator("eng",
        TrainingParameters.defaultParams(), new SentimentFactory(),
        new SentimentEvaluationMonitor[] {});

    cv.evaluate(createSampleStream(), 2);

    Assertions.assertNotNull(cv.getFMeasure());
    Assertions.assertTrue(cv.getFMeasure().getRecallScore() >= 0);
    Assertions.assertTrue(cv.getFMeasure().getPrecisionScore() >= 0);
  }

  @Test
  void testCrossValidationWithMonitor() throws IOException {
    AtomicInteger correctCount = new AtomicInteger();
    AtomicInteger incorrectCount = new AtomicInteger();

    SentimentEvaluationMonitor monitor = new SentimentEvaluationMonitor() {
      @Override
      public void correctlyClassified(SentimentSample reference,
                                      SentimentSample prediction) {
        correctCount.incrementAndGet();
      }

      @Override
      public void misclassified(SentimentSample reference,
                                SentimentSample prediction) {
        incorrectCount.incrementAndGet();
      }
    };

    SentimentCrossValidator cv = new SentimentCrossValidator("eng",
        TrainingParameters.defaultParams(), new SentimentFactory(),
        new SentimentEvaluationMonitor[] {monitor});

    cv.evaluate(createSampleStream(), 2);

    Assertions.assertTrue(correctCount.get() + incorrectCount.get() > 0,
        "Monitor should have been called at least once");
    Assertions.assertNotNull(cv.getFMeasure());
  }
}
