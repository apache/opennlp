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
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for the {@link CachedFeatureGenerator} class.
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

  @Test
  void testCachingOfRealWorldSentence() {
    CachedFeatureGenerator generator = new CachedFeatureGenerator(identityGenerator);
    final String[] sentence = "He belongs to Apache \n Software Foundation .".split(" ");
    int testIndex = 0;

    // after this call features are cached for testIndex
    generator.createFeatures(features, sentence, testIndex, null);
    Assertions.assertEquals(1, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(0, generator.getNumberOfCacheHits());

    generator.createFeatures(features, sentence, testIndex, null);
    Assertions.assertEquals(1, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(1, generator.getNumberOfCacheHits());

    generator.createFeatures(features, sentence, testIndex + 1, null);
    Assertions.assertEquals(2, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(1, generator.getNumberOfCacheHits());

    generator.createFeatures(features, sentence, testIndex + 1, null);
    Assertions.assertEquals(2, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(2, generator.getNumberOfCacheHits());

    generator.clearCache();

    Assertions.assertEquals(0, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(0, generator.getNumberOfCacheHits());

    generator.createFeatures(features, sentence, testIndex + 1, null);
    Assertions.assertEquals(1, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(0, generator.getNumberOfCacheHits());

  }

  /**
   * Tests if cache works for one sentence and two different token indexes.
   */
  @Test
  void testCachingOfSentence() {
    CachedFeatureGenerator generator = new CachedFeatureGenerator(identityGenerator);

    int testIndex = 0;

    // after this call features are cached for testIndex
    generator.createFeatures(features, testSentence1, testIndex, null);

    Assertions.assertEquals(1, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(0, generator.getNumberOfCacheHits());

    Assertions.assertTrue(features.contains(testSentence1[testIndex]));

    features.clear();

    // check if features are really cached

    final String expectedToken = testSentence1[testIndex];

    generator.createFeatures(features, Arrays.copyOf(testSentence1, testSentence1.length), testIndex, null);

    Assertions.assertEquals(1, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(1, generator.getNumberOfCacheHits());

    Assertions.assertTrue(features.contains(expectedToken));
    Assertions.assertEquals(1, features.size());

    features.clear();

    // try caching with an other index

    int testIndex2 = testIndex + 1;

    generator.createFeatures(features, Arrays.copyOf(testSentence1, testSentence1.length), testIndex2, null);

    Assertions.assertEquals(2, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(1, generator.getNumberOfCacheHits());
    Assertions.assertTrue(features.contains(testSentence1[testIndex2]));

    features.clear();

    // now check if cache still contains feature for testIndex

    generator.createFeatures(features, testSentence1, testIndex, null);

    Assertions.assertTrue(features.contains(expectedToken));
  }

  /**
   * Tests if the cache was cleared after the sentence changed.
   */
  @Test
  void testCacheClearAfterSentenceChange() {
    CachedFeatureGenerator generator = new CachedFeatureGenerator(identityGenerator);

    int testIndex = 0;

    // use generator with sentence 1
    generator.createFeatures(features, testSentence1, testIndex, null);

    features.clear();

    // use another sentence but same index
    generator.createFeatures(features, Arrays.copyOf(testSentence2, testSentence2.length), testIndex, null);

    Assertions.assertEquals(2, generator.getNumberOfCacheMisses());
    Assertions.assertEquals(0, generator.getNumberOfCacheHits());

    Assertions.assertTrue(features.contains(testSentence2[testIndex]));
    Assertions.assertEquals(1, features.size());

    features.clear();

    // check if features are really cached
    final String expectedToken = testSentence2[testIndex];
    generator.createFeatures(features, Arrays.copyOf(testSentence2, testSentence2.length), testIndex, null);

    Assertions.assertTrue(features.contains(expectedToken));
    Assertions.assertEquals(1, features.size());
  }
}
