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

public class CharacterNgramFeatureGeneratorTest {

  private List<String> features;
  static String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};

  @Before
  public void setUp() throws Exception {
    features = new ArrayList<>();
  }

  @Test
  public void testDefault() {

    final int testTokenIndex = 3;

    AdaptiveFeatureGenerator generator = new CharacterNgramFeatureGenerator();

    generator.createFeatures(features, testSentence, testTokenIndex, null);

    assertContainsNg(features,
            "ex", "exa", "exam", "examp",
            "xa", "xam", "xamp", "xampl",
            "am", "amp", "ampl", "ample",
            "mp", "mpl", "mple",
            "pl", "ple",
            "le");
  }

  private static void assertContainsNg(List<String> features, String... elements) {
    Assert.assertEquals(elements.length, features.size());
    for (String e: elements) {
      Assert.assertTrue(features.contains("ng=" + e));
    }
  }
}
