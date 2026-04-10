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
 * Test for the {@link CachedFeatureGenerator} class.
 * <p>
 * Per-token caching was removed for thread safety; tests cover delegation and deprecated statistics
 * accessors.
 */
public class CachedFeatureGeneratorTest {

  private final AdaptiveFeatureGenerator identityGenerator = new IdentityFeatureGenerator();

  private String[] testSentence1;
  private String[] testSentence2;
  private List<String> features;

  @BeforeEach
  void setUp() {
    testSentence1 = new String[] {"a1", "b1", "c1", "d1"};
    testSentence2 = new String[] {"a2", "b2", "c2", "d2"};
    features = new ArrayList<>();
  }

  /**
   * Tests that features are produced for two token indexes on the same sentence.
   */
  @Test
  void testDelegatesToUnderlyingGenerator() {
    CachedFeatureGenerator generator =
        new CachedFeatureGenerator(identityGenerator);

    generator.createFeatures(
        features, testSentence1, 0, null);
    Assertions.assertTrue(
        features.contains(testSentence1[0]));
    Assertions.assertEquals(1, features.size());

    features.clear();

    generator.createFeatures(
        features, testSentence1, 1, null);
    Assertions.assertTrue(
        features.contains(testSentence1[1]));
    Assertions.assertEquals(1, features.size());
  }

  /**
   * Tests that a different sentence still produces correct features at the same index.
   */
  @Test
  void testDifferentSentencesProduceCorrectFeatures() {
    CachedFeatureGenerator generator =
        new CachedFeatureGenerator(identityGenerator);

    generator.createFeatures(
        features, testSentence1, 0, null);
    Assertions.assertTrue(
        features.contains(testSentence1[0]));

    features.clear();

    generator.createFeatures(
        features, testSentence2, 0, null);
    Assertions.assertTrue(
        features.contains(testSentence2[0]));
    Assertions.assertEquals(1, features.size());
  }

  /**
   * Tests that deprecated cache hit and miss counters throw {@link UnsupportedOperationException}.
   */
  @Test
  void testDeprecatedCacheStatsThrowUnsupportedOperation() {
    CachedFeatureGenerator generator =
        new CachedFeatureGenerator(identityGenerator);

    generator.createFeatures(
        features, testSentence1, 0, null);

    Assertions.assertThrows(UnsupportedOperationException.class, generator::getNumberOfCacheHits);
    Assertions.assertThrows(UnsupportedOperationException.class, generator::getNumberOfCacheMisses);
  }
}
