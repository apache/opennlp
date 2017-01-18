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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

/**
 * Tests for the {@link TokSpanEventStream} class.
 */
public class TokSpanEventStreamTest {

  /**
   * Tests the event stream for correctly generated outcomes.
   */
  @Test
  public void testEventOutcomes() throws IOException {

    ObjectStream<String> sentenceStream =
        ObjectStreamUtils.createObjectStream("\"<SPLIT>out<SPLIT>.<SPLIT>\"");

    ObjectStream<TokenSample> tokenSampleStream = new TokenSampleStream(sentenceStream);

    ObjectStream<Event> eventStream = new TokSpanEventStream(tokenSampleStream, false);

    Assert.assertEquals(TokenizerME.SPLIT, eventStream.read().getOutcome());
    Assert.assertEquals(TokenizerME.NO_SPLIT, eventStream.read().getOutcome());
    Assert.assertEquals(TokenizerME.NO_SPLIT, eventStream.read().getOutcome());
    Assert.assertEquals(TokenizerME.SPLIT, eventStream.read().getOutcome());
    Assert.assertEquals(TokenizerME.SPLIT, eventStream.read().getOutcome());

    Assert.assertNull(eventStream.read());
    Assert.assertNull(eventStream.read());
  }
}
