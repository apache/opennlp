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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BagOfWordsFeatureGeneratorTest {

  @Test
  void testNull() {
    BagOfWordsFeatureGenerator generator = new BagOfWordsFeatureGenerator();
    try {
      generator.extractFeatures(null, Collections.emptyMap());
      Assertions.fail("NullPointerException must be thrown");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  void testEmpty() {
    BagOfWordsFeatureGenerator generator = new BagOfWordsFeatureGenerator();

    Assertions.assertEquals(0, generator.extractFeatures(new String[] {}, Collections.emptyMap()).size());
  }

  @Test
  void testUseAllTokens() {
    BagOfWordsFeatureGenerator generator = new BagOfWordsFeatureGenerator();

    Assertions.assertArrayEquals(new String[] {"bow=it", "bow=is", "bow=12.345", "bow=feet", "bow=long"},
        generator.extractFeatures(new String[] {"it", "is", "12.345", "feet", "long"},
            Collections.emptyMap()).toArray());
  }

  @Test
  void testOnlyLetterTokens() {
    BagOfWordsFeatureGenerator generator = new BagOfWordsFeatureGenerator(true);

    Assertions.assertArrayEquals(new String[] {"bow=it", "bow=is", "bow=feet", "bow=long"},
        generator.extractFeatures(new String[] {"it", "is", "12.345", "feet", "long"},
            Collections.emptyMap()).toArray());
  }
}
