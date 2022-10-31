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

public class TrigramNameFeatureGeneratorTest {

  private List<String> features;
  static String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};

  @Before
  public void setUp() throws Exception {
    features = new ArrayList<>();
  }

  @Test
  public void testBegin() {

    final int testTokenIndex = 0;

    AdaptiveFeatureGenerator generator = new TrigramNameFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);

    Assert.assertEquals(2, features.size());
    Assert.assertEquals("w,nw,nnw=This,is,an", features.get(0));
    Assert.assertEquals("wc,nwc,nnwc=ic,lc,lc", features.get(1));
  }

  @Test
  public void testNextOfBegin() {

    final int testTokenIndex = 1;

    AdaptiveFeatureGenerator generator = new TrigramNameFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);

    Assert.assertEquals(2, features.size());
    Assert.assertEquals("w,nw,nnw=is,an,example", features.get(0));
    Assert.assertEquals("wc,nwc,nnwc=lc,lc,lc", features.get(1));
  }

  @Test
  public void testMiddle() {

    final int testTokenIndex = 2;

    AdaptiveFeatureGenerator generator = new TrigramNameFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);

    Assert.assertEquals(4, features.size());
    Assert.assertEquals("ppw,pw,w=This,is,an", features.get(0));
    Assert.assertEquals("ppwc,pwc,wc=ic,lc,lc", features.get(1));
    Assert.assertEquals("w,nw,nnw=an,example,sentence", features.get(2));
    Assert.assertEquals("wc,nwc,nnwc=lc,lc,lc", features.get(3));
  }

  @Test
  public void testEnd() {

    final int testTokenIndex = 4;

    AdaptiveFeatureGenerator generator = new TrigramNameFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);

    Assert.assertEquals(2, features.size());
    Assert.assertEquals("ppw,pw,w=an,example,sentence", features.get(0));
    Assert.assertEquals("ppwc,pwc,wc=lc,lc,lc", features.get(1));
  }

  @Test
  public void testShort() {

    String[] shortSentence = new String[] {"I", "know", "it"};

    final int testTokenIndex = 1;

    AdaptiveFeatureGenerator generator = new TrigramNameFeatureGenerator();

    generator.createFeatures(features, shortSentence, testTokenIndex, null);

    Assert.assertEquals(0, features.size());
  }
}
