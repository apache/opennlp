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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for the {@link CachedFeatureGenerator} class.
 */
public class CachedFeatureGeneratorTest {

  private AdaptiveFeatureGenerator identityGenerator[] = new AdaptiveFeatureGenerator[] {
      new IdentityFeatureGenerator()};

  private String testSentence1[];

  private String testSentence2[];

  private List<String> features;

  @Before
  public void setUp() throws Exception {

    testSentence1 = new String[] {"a1", "b1", "c1", "d1"};

    testSentence2 = new String[] {"a2", "b2", "c2", "d2"};

    features = new ArrayList<>();
  }

  /**
   * Tests if cache works for one sentence and two different token indexes.
   */
  @Test
  public void testCachingOfSentence() {
    CachedFeatureGenerator generator = new CachedFeatureGenerator(identityGenerator);

    int testIndex = 0;

    // after this call features are cached for testIndex
    generator.createFeatures(features, testSentence1, testIndex, null);

    Assert.assertEquals(1, generator.getNumberOfCacheMisses());
    Assert.assertEquals(0, generator.getNumberOfCacheHits());

    Assert.assertTrue(features.contains(testSentence1[testIndex]));

    features.clear();

    // check if features are really cached

    final String expectedToken = testSentence1[testIndex];

    testSentence1[testIndex] = null;

    generator.createFeatures(features, testSentence1, testIndex, null);

    Assert.assertEquals(1, generator.getNumberOfCacheMisses());
    Assert.assertEquals(1, generator.getNumberOfCacheHits());

    Assert.assertTrue(features.contains(expectedToken));
    Assert.assertEquals(1, features.size());

    features.clear();

    // try caching with an other index

    int testIndex2 = testIndex + 1;

    generator.createFeatures(features, testSentence1, testIndex2, null);

    Assert.assertEquals(2, generator.getNumberOfCacheMisses());
    Assert.assertEquals(1, generator.getNumberOfCacheHits());
    Assert.assertTrue(features.contains(testSentence1[testIndex2]));

    features.clear();

    // now check if cache still contains feature for testIndex

    generator.createFeatures(features, testSentence1, testIndex, null);

    Assert.assertTrue(features.contains(expectedToken));
  }

  /**
   * Tests if the cache was cleared after the sentence changed.
   */
  @Test
  public void testCacheClearAfterSentenceChange() {
    CachedFeatureGenerator generator = new CachedFeatureGenerator(identityGenerator);

    int testIndex = 0;

    // use generator with sentence 1
    generator.createFeatures(features, testSentence1, testIndex, null);

    features.clear();

    // use another sentence but same index
    generator.createFeatures(features, testSentence2, testIndex, null);

    Assert.assertEquals(2, generator.getNumberOfCacheMisses());
    Assert.assertEquals(0, generator.getNumberOfCacheHits());

    Assert.assertTrue(features.contains(testSentence2[testIndex]));
    Assert.assertEquals(1, features.size());

    features.clear();

    // check if features are really cached
    final String expectedToken = testSentence2[testIndex];

    testSentence2[testIndex] = null;

    generator.createFeatures(features, testSentence2, testIndex, null);

    Assert.assertTrue(features.contains(expectedToken));
    Assert.assertEquals(1, features.size());
  }
}