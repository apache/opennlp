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

public class AuxiliaryInfoAwareDelegateFeatureGeneratorTest {

  private final String[] testSentence = "w1/pos1 w2/pos2 w3/pos3 w4/pos4".split("\\s+");

  private List<String> features;

  @Before
  public void setUp() throws Exception {
    features = new ArrayList<>();
  }

  @Test
  public void testWord() throws Exception {
    AdaptiveFeatureGenerator featureGenerator = new AuxiliaryInfoAwareDelegateFeatureGenerator(
        new IdentityFeatureGenerator(), false);

    featureGenerator.createFeatures(features, testSentence, 2, null);
    Assert.assertEquals(1, features.size());
    Assert.assertEquals("w3", features.get(0));
  }

  @Test
  public void testAuxInfo() throws Exception {
    AdaptiveFeatureGenerator featureGenerator = new AuxiliaryInfoAwareDelegateFeatureGenerator(
        new IdentityFeatureGenerator(), true);

    featureGenerator.createFeatures(features, testSentence, 3, null);
    Assert.assertEquals(1, features.size());
    Assert.assertEquals("pos4", features.get(0));
  }
}
