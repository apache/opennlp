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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import opennlp.tools.cmdline.sentiment.SentimentEvaluationErrorListener;
import opennlp.tools.util.InvalidFormatException;

public class SentimentEvaluatorTest extends AbstractSentimentTest {

  private static final String LIKE_SENTENCE = "benefits from serendipity also reminds us of our "
      + "own responsibility to question what is told as the truth";

  private static final String ANGRY_SENTENCE = "Stupid , infantile , redundant , sloppy , "
      + "over-the-top , and amateurish . Yep";

  private static final String NEUTRAL = "stripped almost entirely of such tools as nudity , "
      + "profanity and violence";

  private Sentiment sentiment;

  @Before
  public void setup() throws IOException {
    sentiment = createEmptySentiment();
    SentimentSampleStream sampleStream = createSampleStream();
    sentiment.train(sampleStream);
  }

  @Test
  public void testPositive() throws InvalidFormatException, IOException {
    String[] tokens = tokenize(LIKE_SENTENCE);
    SentimentSample sample = new SentimentSample("like", tokens, false);
    OutputStream stream = new ByteArrayOutputStream();
    SentimentEvaluationMonitor listener = new SentimentEvaluationErrorListener(
        stream);
    SentimentEvaluator eval = new SentimentEvaluator(sentiment, listener);

    eval.evaluateSample(sample);

    Assert.assertEquals(1.0, eval.getFMeasure().getFMeasure(), 0.0);

    Assert.assertEquals(0, stream.toString().length());

    tokens = tokenize(ANGRY_SENTENCE);
    sample = new SentimentSample("angry", tokens, false);

    Assert.assertEquals(1.0, eval.getFMeasure().getFMeasure(), 0.0);
    Assert.assertEquals(0, stream.toString().length());
  }

  @Test
  public void testMissclassified() throws InvalidFormatException, IOException {
    OutputStream stream = new ByteArrayOutputStream();
    SentimentEvaluationMonitor listener = new SentimentEvaluationErrorListener(
        stream);

    String[] tokens = tokenize(NEUTRAL);
    SentimentSample sample = new SentimentSample("like", tokens, false);
    SentimentEvaluator eval = new SentimentEvaluator(sentiment, listener);

    eval.evaluateSample(sample);

    Assert.assertEquals(-1.0, eval.getFMeasure().getFMeasure(), 0.0);
    Assert.assertNotEquals(0, stream.toString().length());
  }

}
