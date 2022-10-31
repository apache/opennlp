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

public class TokenPatternFeatureGeneratorTest {

  private List<String> features;

  @Before
  public void setUp() throws Exception {
    features = new ArrayList<>();
  }

  @Test
  public void testSingleToken() {

    String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};
    final int testTokenIndex = 3;

    AdaptiveFeatureGenerator generator = new TokenPatternFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);
    Assert.assertEquals(1, features.size());
    Assert.assertEquals("st=example", features.get(0));
  }

  @Test
  public void testSentence() {

    String[] testSentence = new String[] {"This is an example sentence"};
    final int testTokenIndex = 0;

    AdaptiveFeatureGenerator generator = new TokenPatternFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);
    Assert.assertEquals(14, features.size());
    Assert.assertEquals("stn=5", features.get(0));
    Assert.assertEquals("pt2=iclc", features.get(1));
    Assert.assertEquals("pt3=iclclc", features.get(2));
    Assert.assertEquals("st=this", features.get(3));
    Assert.assertEquals("pt2=lclc", features.get(4));
    Assert.assertEquals("pt3=lclclc", features.get(5));
    Assert.assertEquals("st=is", features.get(6));
    Assert.assertEquals("pt2=lclc", features.get(7));
    Assert.assertEquals("pt3=lclclc", features.get(8));
    Assert.assertEquals("st=an", features.get(9));
    Assert.assertEquals("pt2=lclc", features.get(10));
    Assert.assertEquals("st=example", features.get(11));
    Assert.assertEquals("st=sentence", features.get(12));
    Assert.assertEquals("pta=iclclclclc", features.get(13));
  }
}
