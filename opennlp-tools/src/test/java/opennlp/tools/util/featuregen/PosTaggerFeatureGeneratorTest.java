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

public class PosTaggerFeatureGeneratorTest {

  private List<String> features;
  static String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};
  static String[] testTags = new String[] {"DT", "VBZ", "DT", "NN", "NN"};

  @BeforeEach
  void setUp()  {
    features = new ArrayList<>();
  }

  @Test
  void testBegin() {

    final int testTokenIndex = 0;

    AdaptiveFeatureGenerator generator = new PosTaggerFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, testTags);

    Assertions.assertEquals(0, features.size());
  }

  @Test
  void testNext() {

    final int testTokenIndex = 1;

    AdaptiveFeatureGenerator generator = new PosTaggerFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, testTags);

    Assertions.assertEquals(1, features.size());
    Assertions.assertEquals("t=DT", features.get(0));
  }

  @Test
  void testMiddle() {

    final int testTokenIndex = 3;

    AdaptiveFeatureGenerator generator = new PosTaggerFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, testTags);

    Assertions.assertEquals(2, features.size());
    Assertions.assertEquals("t=DT", features.get(0));
    Assertions.assertEquals("t2=VBZ,DT", features.get(1));
  }
}
