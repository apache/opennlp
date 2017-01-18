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
import org.junit.Test;

/**
 * Test for the {@link PreviousMapFeatureGenerator} class.
 */
public class PreviousMapFeatureGeneratorTest {

  @Test
  public void testFeatureGeneration() {

    AdaptiveFeatureGenerator fg = new PreviousMapFeatureGenerator();

    String sentence[] = new String[] {"a", "b", "c"};

    List<String> features = new ArrayList<>();

    // this should generate the pd=null feature
    fg.createFeatures(features, sentence, 0, null);
    Assert.assertEquals(1, features.size());
    Assert.assertEquals("pd=null", features.get(0));

    features.clear();

    // this should generate the pd=1 feature
    fg.updateAdaptiveData(sentence, new String[] {"1", "2", "3"});
    fg.createFeatures(features, sentence, 0, null);
    Assert.assertEquals(1, features.size());
    Assert.assertEquals("pd=1", features.get(0));

    features.clear();

    // this should generate the pd=null feature again after
    // the adaptive data was cleared
    fg.clearAdaptiveData();
    fg.createFeatures(features, sentence, 0, null);
    Assert.assertEquals(1, features.size());
    Assert.assertEquals("pd=null", features.get(0));
  }
}
