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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;

public class BrownBigramFeatureGeneratorTest {

  private AdaptiveFeatureGenerator generator;

  @BeforeEach
  void setup() throws IOException {

    ResourceAsStreamFactory stream = new ResourceAsStreamFactory(
        getClass(), "/opennlp/tools/formats/brown-cluster.txt");

    BrownCluster brownCluster = new BrownCluster(stream.createInputStream());

    generator = new BrownBigramFeatureGenerator(brownCluster);

  }

  @Test
  void createFeaturesTest() {

    String[] tokens = new String[] {"he", "went", "with", "you"};

    List<String> features = new ArrayList<>();
    generator.createFeatures(features, tokens, 3, null);

    Assertions.assertEquals(2, features.size());
    Assertions.assertTrue(features.contains("pbrowncluster,browncluster=0101,0010"));
    Assertions.assertTrue(features.contains("pbrowncluster,browncluster=01010,00101"));

  }

  @Test
  void createFeaturesSuccessiveTokensTest() {

    final String[] testSentence = new String[] {"he", "went", "with", "you", "in", "town"};

    List<String> features = new ArrayList<>();
    generator.createFeatures(features, testSentence, 3, null);

    Assertions.assertEquals(3, features.size());
    Assertions.assertTrue(features.contains("pbrowncluster,browncluster=0101,0010"));
    Assertions.assertTrue(features.contains("pbrowncluster,browncluster=01010,00101"));
    Assertions.assertTrue(features.contains("browncluster,nbrowncluster=0010,0000"));

  }

  @Test
  void noFeaturesTest() {

    final String[] testSentence = new String[] {"he", "went", "with", "you"};

    List<String> features = new ArrayList<>();
    generator.createFeatures(features, testSentence, 0, null);

    Assertions.assertEquals(0, features.size());

  }

}
