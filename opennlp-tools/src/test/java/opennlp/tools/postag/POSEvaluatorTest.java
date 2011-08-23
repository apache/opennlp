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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.cmdline.postag.POSEvaluationErrorListener;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Sequence;

import org.junit.Test;


public class POSEvaluatorTest {
  

  @Test
  public void testPositive() throws InvalidFormatException {
    OutputStream stream = new ByteArrayOutputStream();
    POSTaggerEvaluationMonitor listener = new POSEvaluationErrorListener(stream);
    
    POSEvaluator eval = new POSEvaluator(new DummyPOSTagger(
        POSSampleTest.createGoldSample()), listener);
    
    eval.evaluateSample(POSSampleTest.createGoldSample());
    
    assertEquals(1.0, eval.getWordAccuracy());
    
    assertEquals(0, stream.toString().length());
  }
  
  @Test
  public void testNegative() throws InvalidFormatException {
    OutputStream stream = new ByteArrayOutputStream();
    POSTaggerEvaluationMonitor listener = new POSEvaluationErrorListener(stream);
    
    POSEvaluator eval = new POSEvaluator(new DummyPOSTagger(POSSampleTest.createGoldSample()), listener);
    
    eval.evaluateSample(POSSampleTest.createPredSample());
    
    assertEquals(.7, eval.getWordAccuracy(), .1d);
    
    assertNotSame(0, stream.toString().length());
  }
  
  class DummyPOSTagger implements POSTagger {
    
    private POSSample sample;
    
    public DummyPOSTagger(POSSample sample) {
      this.sample = sample;
    }

    public List<String> tag(List<String> sentence) {
      return Arrays.asList(sample.getTags());
    }

    public String[] tag(String[] sentence) {
      return sample.getTags();
    }

    public String tag(String sentence) {
      return null;
    }

    public Sequence[] topKSequences(List<String> sentence) {
      return null;
    }

    public Sequence[] topKSequences(String[] sentence) {
      return null;
    }
    
  }

}
