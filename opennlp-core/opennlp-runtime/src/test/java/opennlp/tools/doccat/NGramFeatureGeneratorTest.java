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

import opennlp.tools.util.InvalidFormatException;

public class NGramFeatureGeneratorTest {

  static final String[] TOKENS = new String[] {"a", "b", "c", "d", "e", "f", "g"};

  @Test
  void testNull() throws Exception {
    NGramFeatureGenerator generator = new NGramFeatureGenerator();
    try {
      generator.extractFeatures(null, Collections.emptyMap());
      Assertions.fail("NullPointerException must be thrown");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  void testEmpty() throws Exception {
    NGramFeatureGenerator generator = new NGramFeatureGenerator();

    Assertions.assertEquals(0, generator.extractFeatures(new String[] {}, Collections.emptyMap()).size());
  }

  @Test
  void testInvalidGramSize1() {
    try {
      new NGramFeatureGenerator(0, 1);
      Assertions.fail("InvalidFormatException must be thrown");
    } catch (InvalidFormatException expected) {
    }
  }

  @Test
  void testInvalidGramSize2() {
    try {
      new NGramFeatureGenerator(2, 1);
      Assertions.fail("InvalidFormatException must be thrown");
    } catch (InvalidFormatException expected) {
    }
  }

  @Test
  void testUnigram() throws Exception {
    NGramFeatureGenerator generator = new NGramFeatureGenerator(1, 1);

    Assertions.assertArrayEquals(
        new String[] {"ng=:a", "ng=:b", "ng=:c", "ng=:d", "ng=:e", "ng=:f", "ng=:g"},
        generator.extractFeatures(TOKENS, Collections.emptyMap()).toArray());
  }

  @Test
  void testBigram() throws Exception {
    NGramFeatureGenerator generator = new NGramFeatureGenerator(2, 2);

    Assertions.assertArrayEquals(
        new String[] {"ng=:a:b", "ng=:b:c", "ng=:c:d", "ng=:d:e", "ng=:e:f", "ng=:f:g"},
        generator.extractFeatures(TOKENS, Collections.emptyMap()).toArray());
  }

  @Test
  void testTrigram() throws Exception {
    NGramFeatureGenerator generator = new NGramFeatureGenerator(3, 3);

    Assertions.assertArrayEquals(
        new String[] {"ng=:a:b:c", "ng=:b:c:d", "ng=:c:d:e", "ng=:d:e:f", "ng=:e:f:g"},
        generator.extractFeatures(TOKENS, Collections.emptyMap()).toArray());
  }

  @Test
  void test12gram() throws Exception {
    NGramFeatureGenerator generator = new NGramFeatureGenerator(1, 2);

    Assertions.assertArrayEquals(
        new String[] {
            "ng=:a", "ng=:a:b",
            "ng=:b", "ng=:b:c",
            "ng=:c", "ng=:c:d",
            "ng=:d", "ng=:d:e",
            "ng=:e", "ng=:e:f",
            "ng=:f", "ng=:f:g",
            "ng=:g"
        },
        generator.extractFeatures(TOKENS, Collections.emptyMap()).toArray());
  }

  @Test
  void test13gram() throws Exception {
    NGramFeatureGenerator generator = new NGramFeatureGenerator(1, 3);

    Assertions.assertArrayEquals(
        new String[] {
            "ng=:a", "ng=:a:b", "ng=:a:b:c",
            "ng=:b", "ng=:b:c", "ng=:b:c:d",
            "ng=:c", "ng=:c:d", "ng=:c:d:e",
            "ng=:d", "ng=:d:e", "ng=:d:e:f",
            "ng=:e", "ng=:e:f", "ng=:e:f:g",
            "ng=:f", "ng=:f:g",
            "ng=:g"
        },
        generator.extractFeatures(TOKENS, Collections.emptyMap()).toArray());
  }
}
