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

public class PosTaggerFeatureGeneratorTest {

  private List<String> features;
  static String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};
  static String[] testTags = new String[] {"DT", "VBZ", "DT", "NN", "NN"};

  @Before
  public void setUp() throws Exception {
    features = new ArrayList<>();
  }

  @Test
  public void testBegin() {

    final int testTokenIndex = 0;

    AdaptiveFeatureGenerator generator = new PosTaggerFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, testTags);

    Assert.assertEquals(0, features.size());
  }

  @Test
  public void testNext() {

    final int testTokenIndex = 1;

    AdaptiveFeatureGenerator generator = new PosTaggerFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, testTags);

    Assert.assertEquals(1, features.size());
    Assert.assertEquals("t=DT", features.get(0));
  }

  @Test
  public void testMiddle() {

    final int testTokenIndex = 3;

    AdaptiveFeatureGenerator generator = new PosTaggerFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, testTags);

    Assert.assertEquals(2, features.size());
    Assert.assertEquals("t=DT", features.get(0));
    Assert.assertEquals("t2=VBZ,DT", features.get(1));
  }
}
