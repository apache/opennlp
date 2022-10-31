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

package opennlp.tools.doccat;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

public class BagOfWordsFeatureGeneratorTest {

  @Test
  public void testNull() {
    BagOfWordsFeatureGenerator generator = new BagOfWordsFeatureGenerator();
    try {
      generator.extractFeatures(null, Collections.emptyMap());
      Assert.fail("NullPointerException must be thrown");
    }
    catch (NullPointerException expected) {
    }
  }

  @Test
  public void testEmpty() {
    BagOfWordsFeatureGenerator generator = new BagOfWordsFeatureGenerator();

    Assert.assertEquals(0, generator.extractFeatures(new String[]{}, Collections.emptyMap()).size());
  }

  @Test
  public void testUseAllTokens() {
    BagOfWordsFeatureGenerator generator = new BagOfWordsFeatureGenerator();

    Assert.assertArrayEquals(new String[]{"bow=it", "bow=is", "bow=12.345", "bow=feet", "bow=long"},
        generator.extractFeatures(new String[]{"it", "is", "12.345", "feet", "long"},
            Collections.emptyMap()).toArray());
  }

  @Test
  public void testOnlyLetterTokens() {
    BagOfWordsFeatureGenerator generator = new BagOfWordsFeatureGenerator(true);

    Assert.assertArrayEquals(new String[]{"bow=it", "bow=is", "bow=feet", "bow=long"},
            generator.extractFeatures(new String[]{"it", "is", "12.345", "feet", "long"},
                    Collections.emptyMap()).toArray());
  }
}
