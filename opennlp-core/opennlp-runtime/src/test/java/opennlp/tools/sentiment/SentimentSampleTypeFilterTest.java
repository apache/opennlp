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
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStreamUtils;

/**
 * Tests for the {@link SentimentSampleTypeFilter} class.
 */
public class SentimentSampleTypeFilterTest {

  @Test
  void testFilterMatchingType() throws IOException {
    SentimentSample pos = new SentimentSample("positive", new String[] {"great"});
    SentimentSample neg = new SentimentSample("negative", new String[] {"bad"});

    SentimentSampleTypeFilter filter = new SentimentSampleTypeFilter(
        new String[] {"positive"},
        ObjectStreamUtils.createObjectStream(pos, neg));

    SentimentSample result = filter.read();
    Assertions.assertNotNull(result);
    Assertions.assertEquals("positive", result.getSentiment());

    Assertions.assertNull(filter.read());
  }

  @Test
  void testFilterMultipleTypes() throws IOException {
    SentimentSample pos = new SentimentSample("positive", new String[] {"great"});
    SentimentSample neu = new SentimentSample("neutral", new String[] {"ok"});
    SentimentSample neg = new SentimentSample("negative", new String[] {"bad"});

    SentimentSampleTypeFilter filter = new SentimentSampleTypeFilter(
        Set.of("positive", "negative"),
        ObjectStreamUtils.createObjectStream(pos, neu, neg));

    SentimentSample first = filter.read();
    Assertions.assertEquals("positive", first.getSentiment());

    SentimentSample second = filter.read();
    Assertions.assertEquals("negative", second.getSentiment());

    Assertions.assertNull(filter.read());
  }

  @Test
  void testFilterNoMatch() throws IOException {
    SentimentSample neg = new SentimentSample("negative", new String[] {"bad"});

    SentimentSampleTypeFilter filter = new SentimentSampleTypeFilter(
        new String[] {"positive"}, ObjectStreamUtils.createObjectStream(neg));

    Assertions.assertNull(filter.read());
  }

  @Test
  void testEmptyStreamReturnsNull() throws IOException {
    SentimentSampleTypeFilter filter = new SentimentSampleTypeFilter(
        new String[] {"positive"}, ObjectStreamUtils.createObjectStream());

    Assertions.assertNull(filter.read());
  }
}
