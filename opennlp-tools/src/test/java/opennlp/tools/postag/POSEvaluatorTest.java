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

package opennlp.tools.postag;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.postag.POSEvaluationErrorListener;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Sequence;

public class POSEvaluatorTest {

  static POSSample createGoldSample() throws InvalidFormatException {
    String sentence = "the_DT stories_NNS about_IN well-heeled_JJ "
            + "communities_NNS and_CC developers_NNS";
    return POSSample.parse(sentence);
  }

  static POSSample createPredSample() throws InvalidFormatException {
    String sentence = "the_DT stories_NNS about_NNS well-heeled_JJ "
            + "communities_NNS and_CC developers_CC";
    return POSSample.parse(sentence);
  }

  @Test
  void testPositive() throws InvalidFormatException {
    OutputStream stream = new ByteArrayOutputStream();
    POSTaggerEvaluationMonitor listener = new POSEvaluationErrorListener(stream);

    POSEvaluator eval = new POSEvaluator(
            new DummyPOSTagger(createGoldSample()), listener);

    eval.evaluateSample(createGoldSample());
    Assertions.assertEquals(1.0, eval.getWordAccuracy(), 0.0);
    Assertions.assertEquals(0, stream.toString().length());
  }

  @Test
  void testNegative() throws InvalidFormatException {
    OutputStream stream = new ByteArrayOutputStream();
    POSTaggerEvaluationMonitor listener = new POSEvaluationErrorListener(stream);

    POSEvaluator eval = new POSEvaluator(
            new DummyPOSTagger(createGoldSample()), listener);

    eval.evaluateSample(createPredSample());
    Assertions.assertEquals(.7, eval.getWordAccuracy(), .1d);
    Assertions.assertNotSame(0, stream.toString().length());
  }

  static class DummyPOSTagger implements POSTagger {

    private final POSSample sample;

    public DummyPOSTagger(POSSample sample) {
      this.sample = sample;
    }

    public List<String> tag(List<String> sentence) {
      return Arrays.asList(sample.getTags());
    }

    @Override
    public String[] tag(String[] sentence) {
      return sample.getTags();
    }

    public String tag(String sentence) {
      return null;
    }

    public Sequence[] topKSequences(List<String> sentence) {
      return null;
    }

    @Override
    public Sequence[] topKSequences(String[] sentence) {
      return null;
    }

    @Override
    public String[] tag(String[] sentence, Object[] additionalContext) {
      return tag(sentence);
    }

    @Override
    public Sequence[] topKSequences(String[] sentence, Object[] additionalContext) {
      return topKSequences(sentence);
    }

  }

}
