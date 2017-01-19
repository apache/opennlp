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

package opennlp.tools.namefind;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.cmdline.namefind.NameEvaluationErrorListener;
import opennlp.tools.util.Span;

/**
 * This is the test class for {@link TokenNameFinderEvaluator}..
 */
public class TokenNameFinderEvaluatorTest {


  @Test
  public void testPositive() {
    OutputStream stream = new ByteArrayOutputStream();
    TokenNameFinderEvaluationMonitor listener = new NameEvaluationErrorListener(stream);

    Span[] pred = createSimpleNameSampleA().getNames();
    TokenNameFinderEvaluator eval =
        new TokenNameFinderEvaluator(new DummyNameFinder(pred), listener);

    eval.evaluateSample(createSimpleNameSampleA());

    Assert.assertEquals(1.0, eval.getFMeasure().getFMeasure(), 0.0);

    Assert.assertEquals(0, stream.toString().length());
  }

  @Test
  public void testNegative() {
    OutputStream stream = new ByteArrayOutputStream();
    TokenNameFinderEvaluationMonitor listener = new NameEvaluationErrorListener(stream);

    Span[] pred = createSimpleNameSampleB().getNames();
    TokenNameFinderEvaluator eval =
        new TokenNameFinderEvaluator(new DummyNameFinder(pred), listener);

    eval.evaluateSample(createSimpleNameSampleA());

    Assert.assertEquals(0.8, eval.getFMeasure().getFMeasure(), 0.0);

    Assert.assertNotSame(0, stream.toString().length());
  }



  private static String[] sentence = {"U", ".", "S", ".", "President", "Barack", "Obama", "is",
      "considering", "sending", "additional", "American", "forces",
      "to", "Afghanistan", "."};

  private static NameSample createSimpleNameSampleA() {

    Span[] names = { new Span(0, 4, "Location"), new Span(5, 7, "Person"),
        new Span(14, 15, "Location") };

    NameSample nameSample;
    nameSample = new NameSample(sentence, names, false);

    return nameSample;
  }

  private static NameSample createSimpleNameSampleB() {

    Span[] names = { new Span(0, 4, "Location"), new Span(14, 15, "Location") };

    NameSample nameSample;
    nameSample = new NameSample(sentence, names, false);

    return nameSample;
  }

  /** a dummy name finder that always return something expected */
  class DummyNameFinder implements TokenNameFinder {

    private Span[] ret;

    public DummyNameFinder(Span[] ret) {
      this.ret = ret;
    }

    public Span[] find(String[] tokens) {
      return ret;
    }

    public void clearAdaptiveData() {
    }

  }

}
