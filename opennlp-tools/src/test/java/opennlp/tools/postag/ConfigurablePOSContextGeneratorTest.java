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

package opennlp.tools.postag;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.TokenFeatureGenerator;

public class ConfigurablePOSContextGeneratorTest {

  private void testContextGeneration(int cacheSize) {
    AdaptiveFeatureGenerator fg = new TokenFeatureGenerator();
    ConfigurablePOSContextGenerator cg = new ConfigurablePOSContextGenerator(cacheSize, fg);

    String[] tokens = new String[] {"a", "b", "c", "d", "e"};
    String[] tags = new String[] {"t_a", "t_b", "t_c", "t_d", "t_e"};

    cg.getContext(0, tokens, tags, null);

    Assert.assertEquals(1, cg.getContext(0, tokens, tags, null).length);
    Assert.assertEquals("w=a", cg.getContext(0, tokens, tags, null)[0]);
    Assert.assertEquals("w=b", cg.getContext(1, tokens, tags, null)[0]);
    Assert.assertEquals("w=c", cg.getContext(2, tokens, tags, null)[0]);
    Assert.assertEquals("w=d", cg.getContext(3, tokens, tags, null)[0]);
    Assert.assertEquals("w=e", cg.getContext(4, tokens, tags, null)[0]);
  }

  @Test
  public void testWithoutCache() {
    testContextGeneration(0);
  }

  @Test
  public void testWithCache() {
    testContextGeneration(3);
  }

}
