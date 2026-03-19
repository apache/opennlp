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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link SentimentSample} class.
 */
public class SentimentSampleTest {

  @Test
  void testCreation() {
    SentimentSample sample = new SentimentSample("positive",
        new String[] {"I", "love", "this"});

    Assertions.assertEquals("positive", sample.getSentiment());
    Assertions.assertArrayEquals(new String[] {"I", "love", "this"}, sample.getSentence());
    Assertions.assertTrue(sample.isClearAdaptiveDataSet());
  }

  @Test
  void testCreationWithClearAdaptiveDataFalse() {
    SentimentSample sample = new SentimentSample("negative",
        new String[] {"I", "hate", "this"}, false);

    Assertions.assertEquals("negative", sample.getSentiment());
    Assertions.assertArrayEquals(new String[] {"I", "hate", "this"}, sample.getSentence());
    Assertions.assertFalse(sample.isClearAdaptiveDataSet());
  }

  @Test
  void testNullSentimentThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SentimentSample(null, new String[] {"test"}));
  }

  @Test
  void testNullSentenceThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SentimentSample("positive", null));
  }

  @Test
  void testSentenceIsDefensiveCopy() {
    String[] original = {"a", "b", "c"};
    SentimentSample sample = new SentimentSample("positive", original);
    String[] returned = sample.getSentence();
    returned[0] = "modified";
    Assertions.assertEquals("a", sample.getSentence()[0]);
  }

  @Test
  void testEmptySentence() {
    SentimentSample sample = new SentimentSample("neutral", new String[] {});
    Assertions.assertEquals(0, sample.getSentence().length);
  }

  @Test
  void testToString() {
    SentimentSample sample = new SentimentSample("positive",
        new String[] {"I", "love", "this"});
    Assertions.assertEquals("positive I love this", sample.toString());
  }

  @Test
  void testEquals() {
    SentimentSample a = new SentimentSample("positive", new String[] {"a", "b"});
    SentimentSample b = new SentimentSample("positive", new String[] {"a", "b"});
    SentimentSample c = new SentimentSample("negative", new String[] {"a", "b"});

    Assertions.assertEquals(a, b);
    Assertions.assertNotEquals(a, c);
    Assertions.assertNotEquals(null, a);
  }

  @Test
  void testHashCode() {
    SentimentSample a = new SentimentSample("positive", new String[] {"a", "b"});
    SentimentSample b = new SentimentSample("positive", new String[] {"a", "b"});
    Assertions.assertEquals(a.hashCode(), b.hashCode());
  }
}
