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

public class BigramNameFeatureGeneratorTest {

  private List<String> features;
  static String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};

  @BeforeEach
  void setUp() {
    features = new ArrayList<>();
  }

  @Test
  void testBegin() {

    final int testTokenIndex = 0;

    AdaptiveFeatureGenerator generator = new BigramNameFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);

    Assertions.assertEquals(2, features.size());
    Assertions.assertEquals("w,nw=This,is", features.get(0));
    Assertions.assertEquals("wc,nc=ic,lc", features.get(1));
  }

  @Test
  void testMiddle() {

    final int testTokenIndex = 2;

    AdaptiveFeatureGenerator generator = new BigramNameFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);

    Assertions.assertEquals(4, features.size());
    Assertions.assertEquals("pw,w=is,an", features.get(0));
    Assertions.assertEquals("pwc,wc=lc,lc", features.get(1));
    Assertions.assertEquals("w,nw=an,example", features.get(2));
    Assertions.assertEquals("wc,nc=lc,lc", features.get(3));
  }

  @Test
  void testEnd() {

    final int testTokenIndex = 4;

    AdaptiveFeatureGenerator generator = new BigramNameFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);

    Assertions.assertEquals(2, features.size());
    Assertions.assertEquals("pw,w=example,sentence", features.get(0));
    Assertions.assertEquals("pwc,wc=lc,lc", features.get(1));
  }

  @Test
  void testShort() {

    String[] shortSentence = new String[] {"word"};

    final int testTokenIndex = 0;

    AdaptiveFeatureGenerator generator = new BigramNameFeatureGenerator();

    generator.createFeatures(features, shortSentence, testTokenIndex, null);

    Assertions.assertEquals(0, features.size());
  }
}
