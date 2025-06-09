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

package opennlp.tools.util.featuregen;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for the {@link WindowFeatureGenerator} class.
 */
public class WindowFeatureGeneratorTest {

  private final String[] testSentence = new String[] {"a", "b", "c", "d",
      "e", "f", "g", "h"};

  private List<String> features;

  @BeforeEach
  void setUp()  {
    features = new ArrayList<>();
  }

  /**
   * Tests if the {@link WindowFeatureGenerator} works as specified, with a previous
   * and next window size of zero.
   */
  @Test
  void testWithoutWindow() {

    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
        new IdentityFeatureGenerator(), 0, 0);

    int testTokenIndex = 2;

    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);

    Assertions.assertEquals(1, features.size());

    Assertions.assertEquals("c", features.get(0));
  }

  @Test
  void testWindowSizeOne() {
    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
        new IdentityFeatureGenerator(), 1, 1);

    int testTokenIndex = 2;

    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);

    Assertions.assertEquals(3, features.size());

    Assertions.assertEquals("c", features.get(0));
    Assertions.assertEquals("p1b", features.get(1));
    Assertions.assertEquals("n1d", features.get(2));
  }

  @Test
  void testWindowAtBeginOfSentence() {
    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
        new IdentityFeatureGenerator(), 1, 0);

    int testTokenIndex = 0;
    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);
    Assertions.assertEquals(1, features.size());
    Assertions.assertEquals("a", features.get(0));
  }

  @Test
  void testWindowAtEndOfSentence() {
    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
        new IdentityFeatureGenerator(), 0, 1);

    int testTokenIndex = testSentence.length - 1;
    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);
    Assertions.assertEquals(1, features.size());
    Assertions.assertEquals("h", features.get(0));
  }

  /**
   * Tests for a window size of previous and next 2 if the features are correct.
   */
  @Test
  void testForCorrectFeatures() {
    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
        new IdentityFeatureGenerator(), 2, 2);

    int testTokenIndex = 3;
    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);
    Assertions.assertEquals(5, features.size());

    Assertions.assertEquals("d", features.get(0));
    Assertions.assertEquals("p1c", features.get(1));
    Assertions.assertEquals("p2b", features.get(2));
    Assertions.assertEquals("n1e", features.get(3));
    Assertions.assertEquals("n2f", features.get(4));
  }
}
