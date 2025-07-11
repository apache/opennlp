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

package opennlp.tools.tokenize;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.tokenizer.TokenEvaluationErrorListener;
import opennlp.tools.util.Span;

public class TokenizerEvaluatorTest {

  static TokenSample createGoldSample() {
    return new TokenSample("A test.", new Span[] {
        new Span(0, 1), new Span(2, 6)});
  }

  static TokenSample createPredSample() {
    return new TokenSample("A test.", new Span[] {
        new Span(0, 3), new Span(2, 6)});
  }

  @Test
  void testPositive() {
    OutputStream stream = new ByteArrayOutputStream();
    TokenizerEvaluationMonitor listener = new TokenEvaluationErrorListener(stream);

    TokenizerEvaluator eval = new TokenizerEvaluator(
            new DummyTokenizer(createGoldSample()), listener);

    eval.evaluateSample(createGoldSample());

    Assertions.assertEquals(1.0, eval.getFMeasure().getFMeasure(), 0.0);
    Assertions.assertEquals(0, stream.toString().length());
  }

  @Test
  void testNegative() {
    OutputStream stream = new ByteArrayOutputStream();
    TokenizerEvaluationMonitor listener = new TokenEvaluationErrorListener(
        stream);

    TokenizerEvaluator eval = new TokenizerEvaluator(
            new DummyTokenizer(createGoldSample()), listener);

    eval.evaluateSample(createPredSample());

    Assertions.assertEquals(.5d, eval.getFMeasure().getFMeasure(), .1d);
    Assertions.assertNotSame(0, stream.toString().length());
  }

  /**
   * a dummy tokenizer that always return something expected
   */
  static class DummyTokenizer implements Tokenizer {

    private final TokenSample sample;

    public DummyTokenizer(TokenSample sample) {
      this.sample = sample;
    }

    @Override
    public String[] tokenize(String s) {
      return null;
    }

    @Override
    public Span[] tokenizePos(String s) {
      return this.sample.getTokenSpans();
    }

  }

}
