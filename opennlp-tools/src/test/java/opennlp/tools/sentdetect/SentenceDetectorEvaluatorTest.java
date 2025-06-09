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

package opennlp.tools.sentdetect;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.sentdetect.SentenceEvaluationErrorListener;
import opennlp.tools.util.Span;

public class SentenceDetectorEvaluatorTest {

  public static SentenceSample createGoldSample() {
    return new SentenceSample("1. 2.", new Span(0, 2), new Span(3, 5));
  }

  public static SentenceSample createPredSample() {
    return new SentenceSample("1. 2.", new Span(0, 1), new Span(4, 5));
  }

  @Test
  void testPositive() {
    OutputStream stream = new ByteArrayOutputStream();
    SentenceDetectorEvaluationMonitor listener = new SentenceEvaluationErrorListener(stream);

    SentenceDetectorEvaluator eval = new SentenceDetectorEvaluator(
            new DummySD(createGoldSample()), listener);

    eval.evaluateSample(createGoldSample());

    Assertions.assertEquals(1.0, eval.getFMeasure().getFMeasure());
    Assertions.assertEquals(0, stream.toString().length());
  }

  @Test
  void testNegative() {
    OutputStream stream = new ByteArrayOutputStream();
    SentenceDetectorEvaluationMonitor listener = new SentenceEvaluationErrorListener(stream);

    SentenceDetectorEvaluator eval = new SentenceDetectorEvaluator(
            new DummySD(createGoldSample()), listener);

    eval.evaluateSample(createPredSample());

    Assertions.assertEquals(-1.0, eval.getFMeasure().getFMeasure(), .1d);
    Assertions.assertNotSame(0, stream.toString().length());
  }


  /**
   * A dummy sentence detector that always return something expected
   */
  public static class DummySD implements SentenceDetector {

    private final SentenceSample sample;

    public DummySD(SentenceSample sample) {
      this.sample = sample;
    }

    @Override
    public String[] sentDetect(CharSequence s) {
      return null;
    }

    @Override
    public Span[] sentPosDetect(CharSequence s) {
      return sample.getSentences();
    }

  }
}
