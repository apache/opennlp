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

import opennlp.tools.util.ObjectStreamUtils;

/**
 * Tests for the {@link SentimentSampleStream} class.
 */
public class SentimentSampleStreamTest {

  @Test
  void testReadSample() throws IOException {
    SentimentSampleStream stream = new SentimentSampleStream(
        ObjectStreamUtils.createObjectStream("positive I love this"));

    SentimentSample sample = stream.read();
    Assertions.assertNotNull(sample);
    Assertions.assertEquals("positive", sample.getSentiment());
    Assertions.assertArrayEquals(new String[] {"I", "love", "this"}, sample.getSentence());
  }

  @Test
  void testReadMultipleSamples() throws IOException {
    SentimentSampleStream stream = new SentimentSampleStream(
        ObjectStreamUtils.createObjectStream(
            "positive I love this",
            "negative I hate this"));

    SentimentSample first = stream.read();
    Assertions.assertNotNull(first);
    Assertions.assertEquals("positive", first.getSentiment());

    SentimentSample second = stream.read();
    Assertions.assertNotNull(second);
    Assertions.assertEquals("negative", second.getSentiment());

    Assertions.assertNull(stream.read());
  }

  @Test
  void testReadEmptyStreamReturnsNull() throws IOException {
    SentimentSampleStream stream = new SentimentSampleStream(
        ObjectStreamUtils.createObjectStream());

    Assertions.assertNull(stream.read());
  }

  @Test
  void testSingleTokenLineThrows() {
    SentimentSampleStream stream = new SentimentSampleStream(
        ObjectStreamUtils.createObjectStream("onlysentiment"));

    Assertions.assertThrows(IOException.class, stream::read);
  }
}
