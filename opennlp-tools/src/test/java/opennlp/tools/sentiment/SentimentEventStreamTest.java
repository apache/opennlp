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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

public class SentimentEventStreamTest {

  private static final String[] SENTENCE = { "benefits", "from", "serendipity",
      "but", "also", "reminds", "us", "of", "our", "own", "responsibility",
      "to", "question", "what", "is", "told", "as", "the", "truth" };
  private static final String SENTIMENT = "like";
  private static final SentimentContextGenerator CG = new SentimentContextGenerator();

  @Test
  public void testSentEventStream() throws Exception {
    SentimentSample sample = new SentimentSample(SENTIMENT, SENTENCE, false);
    ObjectStream<Event> eventStream = new SentimentEventStream(
        ObjectStreamUtils.createObjectStream(sample), CG);

    Assert.assertEquals(SENTIMENT, eventStream.read().getOutcome());

    eventStream.close();
  }

}
