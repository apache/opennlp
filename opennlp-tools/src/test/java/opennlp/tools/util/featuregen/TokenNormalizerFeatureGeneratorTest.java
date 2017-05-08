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


import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TokenNormalizerFeatureGeneratorTest {

  /**
   * Tests if the {@link TokenNormalizerFeatureGenerator} works as specified with the default
   * normalizer
   */
  @Test
  public void testDefaultNormalizerNone() {

    String[] testSentence = new String[] {"a", "b", "c", "d",
        "e", "f", "g", "h"};

    TokenNormalizerFeatureGenerator featureGenerator =
        new TokenNormalizerFeatureGenerator(new IdentityFeatureGenerator());

    List<String> features = new ArrayList<>();
    for (int i = 0; i < testSentence.length; i++) {
      featureGenerator.createFeatures(features, testSentence, i, null);
      Assert.assertEquals(features.get(i), testSentence[i]);
    }

  }

  @Test
  public void testDefaultNormalizerTwice() {

    String[] testSentence = new String[] {"a", "b", "c", "d",
        "e", "f", "g", "h"};

    TokenNormalizerFeatureGenerator featureGenerator =
        new TokenNormalizerFeatureGenerator(new IdentityFeatureGenerator());

    List<String> features = new ArrayList<>();
    for (int i = 0; i < testSentence.length; i++) {
      featureGenerator.createFeatures(features, testSentence, i, null);
      Assert.assertEquals(features.get(i), testSentence[i]);
    }

    testSentence = new String[] {"i", "j", "l", "m",
        "n", "o", "p", "q"};

    features = new ArrayList<>();
    for (int i = 0; i < testSentence.length; i++) {
      featureGenerator.createFeatures(features, testSentence, i, null);
      Assert.assertEquals(features.get(i), testSentence[i]);
    }
  }

  @Test
  public void testDefaultNormalizerNumbers() {

    String[] testSentence = new String[] {"a", "8937", "c", "1",
        "0", "f", "g56kj", "8"};

    String[] testResult = new String[] {"a", "9999", "c", "9",
        "9", "f", "g99kj", "9"};

    TokenNormalizerFeatureGenerator featureGenerator =
        new TokenNormalizerFeatureGenerator(new IdentityFeatureGenerator());

    List<String> features = new ArrayList<>();
    for (int i = 0; i < testSentence.length; i++) {
      featureGenerator.createFeatures(features, testSentence, i, null);
      Assert.assertEquals(features.get(i), testResult[i]);
    }

  }

  @Test
  public void testDefaultNormalizerEmailURL() {

    String[] testSentence = new String[] {"a", "abc0io@example.com", "c", "d",
        "e", "f", "https://example.com", "h"};

    String[] testResult = new String[] {"a", "$EMAIL$", "c", "d",
        "e", "f", "$URL$", "h"};

    TokenNormalizerFeatureGenerator featureGenerator =
        new TokenNormalizerFeatureGenerator(new IdentityFeatureGenerator());

    List<String> features = new ArrayList<>();
    for (int i = 0; i < testSentence.length; i++) {
      featureGenerator.createFeatures(features, testSentence, i, null);
      Assert.assertEquals(features.get(i), testResult[i]);
    }

  }

  @Test
  public void testCreationFromXML() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TokenNormalizerFeatureGeneratorTest.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AggregatedFeatureGenerator aggregatedGenerator =
        (AggregatedFeatureGenerator) GeneratorFactory.create(generatorDescriptorIn, null);

    Assert.assertEquals(1, aggregatedGenerator.getGenerators().size());
    Assert.assertEquals(TokenNormalizerFeatureGenerator.class.getName(),
        aggregatedGenerator.getGenerators().iterator().next().getClass().getName());

  }

  public void testCreationFromXMLCustom() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TokenNormalizerFeatureGeneratorCustomTest.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AggregatedFeatureGenerator aggregatedGenerator =
        (AggregatedFeatureGenerator) GeneratorFactory.create(generatorDescriptorIn, null);

    Assert.assertEquals(1, aggregatedGenerator.getGenerators().size());
    Assert.assertEquals(TokenNormalizerFeatureGenerator.class.getName(),
        aggregatedGenerator.getGenerators().iterator().next().getClass().getName());

    TokenNormalizerFeatureGenerator fg =
        (TokenNormalizerFeatureGenerator) aggregatedGenerator.getGenerators().iterator().next();

    Assert.assertEquals(CustomNormalizer.class.getName(),
        fg.getTokenNormalizer().getClass().getName());

  }

  public static class CustomNormalizer implements TokenNormalizer {

    @Override
    public String normalize(String token) {
      return "$dummy$";
    }
  }
}