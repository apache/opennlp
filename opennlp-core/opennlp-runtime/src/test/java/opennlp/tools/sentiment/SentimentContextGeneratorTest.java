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
 * Tests for the {@link SentimentContextGenerator} class.
 */
public class SentimentContextGeneratorTest {

  @Test
  void testGetContextReturnsInput() {
    SentimentContextGenerator cg = new SentimentContextGenerator();
    String[] tokens = {"I", "love", "this"};
    Assertions.assertArrayEquals(tokens, cg.getContext(tokens));
  }

  @Test
  void testGetContextEmptyArray() {
    SentimentContextGenerator cg = new SentimentContextGenerator();
    String[] tokens = {};
    Assertions.assertArrayEquals(tokens, cg.getContext(tokens));
  }

  @Test
  void testGetContextWithIndexReturnsEmpty() {
    SentimentContextGenerator cg = new SentimentContextGenerator();
    String[] result = cg.getContext(0, new String[] {"a"}, new String[] {"b"}, null);
    Assertions.assertNotNull(result);
    Assertions.assertEquals(0, result.length);
  }

  @Test
  void testUpdateAdaptiveDataMismatchThrows() {
    SentimentContextGenerator cg = new SentimentContextGenerator();
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> cg.updateAdaptiveData(new String[] {"a", "b"}, new String[] {"x"}));
  }

  @Test
  void testUpdateAdaptiveDataMatchingLengths() {
    SentimentContextGenerator cg = new SentimentContextGenerator();
    Assertions.assertDoesNotThrow(
        () -> cg.updateAdaptiveData(new String[] {"a"}, new String[] {"x"}));
  }

  @Test
  void testUpdateAdaptiveDataWithNulls() {
    SentimentContextGenerator cg = new SentimentContextGenerator();
    Assertions.assertDoesNotThrow(() -> cg.updateAdaptiveData(null, null));
  }
}
