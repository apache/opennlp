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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TokenPatternFeatureGeneratorTest {

  private List<String> features;

  @BeforeEach
  void setUp()  {
    features = new ArrayList<>();
  }

  @Test
  void testSingleToken() {

    String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};
    final int testTokenIndex = 3;

    AdaptiveFeatureGenerator generator = new TokenPatternFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);
    Assertions.assertEquals(1, features.size());
    Assertions.assertEquals("st=example", features.get(0));
  }

  @Test
  void testSentence() {

    String[] testSentence = new String[] {"This is an example sentence"};
    final int testTokenIndex = 0;

    AdaptiveFeatureGenerator generator = new TokenPatternFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);
    Assertions.assertEquals(14, features.size());
    Assertions.assertEquals("stn=5", features.get(0));
    Assertions.assertEquals("pt2=iclc", features.get(1));
    Assertions.assertEquals("pt3=iclclc", features.get(2));
    Assertions.assertEquals("st=this", features.get(3));
    Assertions.assertEquals("pt2=lclc", features.get(4));
    Assertions.assertEquals("pt3=lclclc", features.get(5));
    Assertions.assertEquals("st=is", features.get(6));
    Assertions.assertEquals("pt2=lclc", features.get(7));
    Assertions.assertEquals("pt3=lclclc", features.get(8));
    Assertions.assertEquals("st=an", features.get(9));
    Assertions.assertEquals("pt2=lclc", features.get(10));
    Assertions.assertEquals("st=example", features.get(11));
    Assertions.assertEquals("st=sentence", features.get(12));
    Assertions.assertEquals("pta=iclclclclc", features.get(13));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "well-known|st=well;st=known|st=-",
      "caf\u00E9-bar|st=bar|st=caf\u00E9;st=-",
      "abc-123|st=abc|st=123;st=-"})
  void testSubTokenFeaturesNeedAsciiLetters(String token, String present, String absent) {
    AdaptiveFeatureGenerator generator = new TokenPatternFeatureGenerator();
    generator.createFeatures(features, new String[] {token}, 0, null);
    for (String feature : present.split(";")) {
      Assertions.assertTrue(features.contains(feature), feature + " of " + token);
    }
    for (String feature : absent.split(";")) {
      Assertions.assertFalse(features.contains(feature), feature + " of " + token);
    }
  }
}
