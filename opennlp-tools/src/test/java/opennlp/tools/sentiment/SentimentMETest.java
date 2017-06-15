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

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class SentimentMETest extends AbstractSentimentTest {

  @Test
  public void testSentimentModel() throws Exception {
    Sentiment sentiment = createEmptySentiment();
    SentimentSampleStream sampleStream = createSampleStream();

    sentiment.train(sampleStream);

    // "Angry"
    String[] tokens = tokenize(
        "Stupid , infantile , redundant , sloppy , over-the-top , and amateurish . Yep");
    Assert.assertEquals("angry", sentiment.predict(tokens));

    // "Sad"
    String[] tokens2 = tokenize(
        "Strong filmmaking requires a clear sense of purpose , and in that oh-so-important category , "
        + "The Four Feathers comes up short");
    Assert.assertEquals("sad", sentiment.predict(tokens2));

    // "Neutral"
    String[] tokens3 = tokenize(
        "to make its points about acceptance and growth");
    Assert.assertEquals("neutral", sentiment.predict(tokens3));

    // "Like"
    String[] tokens4 = tokenize("best performance");
    Assert.assertEquals("like", sentiment.predict(tokens4));

    // "Love"
    String[] tokens5 = tokenize("best short story writing");
    Assert.assertEquals("love", sentiment.predict(tokens5));
  }

  @Test(expected = NullPointerException.class)
  public void testEmptyModel() throws Exception {
    Sentiment sentiment = createEmptySentiment();
    String[] tokens = tokenize("best performance");
    sentiment.predict(tokens);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptySentiment() throws Exception {
    Sentiment sentiment = createEmptySentiment();
    SentimentSampleStream sampleStream = createSampleStream();

    sentiment.train(sampleStream);

    String[] tokens = new String[] {};
    sentiment.predict(tokens);
  }

  @Test
  public void testWorkingModel() throws Exception {
    File tempModel = saveTempModel();
    Sentiment sentiment = loadSentiment(tempModel);

    // "Angry"
    String[] tokens = tokenize(
        "Stupid , infantile , redundant , sloppy , over-the-top , and amateurish . Yep");
    Assert.assertEquals("angry", sentiment.predict(tokens));

    // "Sad"
    String[] tokens2 = tokenize(
        "Strong filmmaking requires a clear sense of purpose , and in that oh-so-important category , "
        + "The Four Feathers comes up short");
    Assert.assertEquals("sad", sentiment.predict(tokens2));

    // "Neutral"
    String[] tokens3 = tokenize(
        "to make its points about acceptance and growth");
    Assert.assertEquals("neutral", sentiment.predict(tokens3));

    // "Like"
    String[] tokens4 = tokenize("best performance");
    Assert.assertEquals("like", sentiment.predict(tokens4));

    // "Love"
    String[] tokens5 = tokenize("best short story writing");
    Assert.assertEquals("love", sentiment.predict(tokens5));
  }

}
