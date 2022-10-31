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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FeatureGeneratorUtilTest {

  @Test
  void test() {
    // digits
    Assertions.assertEquals("2d", FeatureGeneratorUtil.tokenFeature("12"));
    Assertions.assertEquals("4d", FeatureGeneratorUtil.tokenFeature("1234"));
    Assertions.assertEquals("an", FeatureGeneratorUtil.tokenFeature("abcd234"));
    Assertions.assertEquals("dd", FeatureGeneratorUtil.tokenFeature("1234-56"));
    Assertions.assertEquals("ds", FeatureGeneratorUtil.tokenFeature("4/6/2017"));
    Assertions.assertEquals("dc", FeatureGeneratorUtil.tokenFeature("1,234,567"));
    Assertions.assertEquals("dp", FeatureGeneratorUtil.tokenFeature("12.34567"));
    Assertions.assertEquals("num", FeatureGeneratorUtil.tokenFeature("123(456)7890"));

    // letters
    Assertions.assertEquals("lc", FeatureGeneratorUtil.tokenFeature("opennlp"));
    Assertions.assertEquals("sc", FeatureGeneratorUtil.tokenFeature("O"));
    Assertions.assertEquals("ac", FeatureGeneratorUtil.tokenFeature("OPENNLP"));
    Assertions.assertEquals("cp", FeatureGeneratorUtil.tokenFeature("A."));
    Assertions.assertEquals("ic", FeatureGeneratorUtil.tokenFeature("Mike"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("somethingStupid"));

    // symbols
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature(","));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("."));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("?"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("!"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("#"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("%"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("&"));
  }

  @Test
  void testJapanese() {
    // Hiragana
    Assertions.assertEquals("jah", FeatureGeneratorUtil.tokenFeature("そういえば"));
    Assertions.assertEquals("jah", FeatureGeneratorUtil.tokenFeature("おーぷん・そ〜す・そふとうぇあ"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("あぱっち・そふとうぇあ財団"));

    // Katakana
    Assertions.assertEquals("jak", FeatureGeneratorUtil.tokenFeature("ジャパン"));
    Assertions.assertEquals("jak", FeatureGeneratorUtil.tokenFeature("オープン・ソ〜ス・ソフトウェア"));
    Assertions.assertEquals("other", FeatureGeneratorUtil.tokenFeature("アパッチ・ソフトウェア財団"));
  }
}
