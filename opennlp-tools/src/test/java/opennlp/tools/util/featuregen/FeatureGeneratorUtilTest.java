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

import org.junit.Assert;
import org.junit.Test;

public class FeatureGeneratorUtilTest {

  @Test
  public void test() {
    // digits
    Assert.assertEquals("2d", FeatureGeneratorUtil.tokenFeature("12"));
    Assert.assertEquals("4d", FeatureGeneratorUtil.tokenFeature("1234"));
    Assert.assertEquals("an", FeatureGeneratorUtil.tokenFeature("abcd234"));
    Assert.assertEquals("dd", FeatureGeneratorUtil.tokenFeature("1234-56"));
    Assert.assertEquals("ds", FeatureGeneratorUtil.tokenFeature("4/6/2017"));
    Assert.assertEquals("dc", FeatureGeneratorUtil.tokenFeature("1,234,567"));
    Assert.assertEquals("dp", FeatureGeneratorUtil.tokenFeature("12.34567"));
    Assert.assertEquals("num", FeatureGeneratorUtil.tokenFeature("123(456)7890"));

    // letters
    Assert.assertEquals("lc", FeatureGeneratorUtil.tokenFeature("opennlp"));
    Assert.assertEquals("sc", FeatureGeneratorUtil.tokenFeature("O"));
    Assert.assertEquals("ac", FeatureGeneratorUtil.tokenFeature("OPENNLP"));
    Assert.assertEquals("cp", FeatureGeneratorUtil.tokenFeature("A."));
    Assert.assertEquals("ic", FeatureGeneratorUtil.tokenFeature("Mike"));
    Assert.assertEquals("other", FeatureGeneratorUtil.tokenFeature("somethingStupid"));
  }
}
