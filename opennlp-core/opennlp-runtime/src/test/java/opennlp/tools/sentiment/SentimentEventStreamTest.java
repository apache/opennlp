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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

/**
 * Tests for the {@link SentimentEventStream} class.
 */
public class SentimentEventStreamTest {

  @Test
  void testEventOutcome() throws IOException {
    SentimentSample sample = new SentimentSample("positive",
        new String[] {"I", "love", "this"});

    ObjectStream<Event> eventStream = new SentimentEventStream(
        ObjectStreamUtils.createObjectStream(sample),
        new SentimentContextGenerator());

    Event event = eventStream.read();
    Assertions.assertNotNull(event);
    Assertions.assertEquals("positive", event.getOutcome());
    Assertions.assertArrayEquals(new String[] {"I", "love", "this"}, event.getContext());
  }

  @Test
  void testMultipleEvents() throws IOException {
    SentimentSample pos = new SentimentSample("positive",
        new String[] {"great", "product"});
    SentimentSample neg = new SentimentSample("negative",
        new String[] {"terrible", "service"});

    ObjectStream<Event> eventStream = new SentimentEventStream(
        ObjectStreamUtils.createObjectStream(pos, neg),
        new SentimentContextGenerator());

    Event first = eventStream.read();
    Assertions.assertEquals("positive", first.getOutcome());

    Event second = eventStream.read();
    Assertions.assertEquals("negative", second.getOutcome());

    Assertions.assertNull(eventStream.read());
  }

  @Test
  void testOneEventPerSample() throws IOException {
    SentimentSample sample = new SentimentSample("positive",
        new String[] {"a", "b", "c", "d", "e"});

    ObjectStream<Event> eventStream = new SentimentEventStream(
        ObjectStreamUtils.createObjectStream(sample),
        new SentimentContextGenerator());

    Assertions.assertNotNull(eventStream.read());
    Assertions.assertNull(eventStream.read());
  }
}
