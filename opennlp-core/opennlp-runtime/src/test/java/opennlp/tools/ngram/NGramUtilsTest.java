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

package opennlp.tools.ngram;

import java.util.Collection;
import java.util.LinkedList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.StringList;

/**
 * Tests for {@link NGramUtils}
 */
public class NGramUtilsTest {

  @Test
  void testBigramMLProbability() {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("<s>", "I", "am", "Sam", "</s>"));
    set.add(new StringList("<s>", "Sam", "I", "am", "</s>"));
    set.add(new StringList("<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>"));
    set.add(new StringList(""));
    Double d = NGramUtils.calculateBigramMLProbability("<s>", "I", set);
    Assertions.assertEquals(Double.valueOf(0.6666666666666666d), d);
    d = NGramUtils.calculateBigramMLProbability("Sam", "</s>", set);
    Assertions.assertEquals(Double.valueOf(0.5d), d);
    d = NGramUtils.calculateBigramMLProbability("<s>", "Sam", set);
    Assertions.assertEquals(Double.valueOf(0.3333333333333333d), d);
  }

  @Test
  void testTrigramMLProbability() {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("<s>", "I", "am", "Sam", "</s>"));
    set.add(new StringList("<s>", "Sam", "I", "am", "</s>"));
    set.add(new StringList("<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>"));
    set.add(new StringList(""));
    Double d = NGramUtils.calculateTrigramMLProbability("I", "am", "Sam", set);
    Assertions.assertEquals(Double.valueOf(0.5), d);
    d = NGramUtils.calculateTrigramMLProbability("Sam", "I", "am", set);
    Assertions.assertEquals(Double.valueOf(1d), d);
  }

  @Test
  void testNgramMLProbability() {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("<s>", "I", "am", "Sam", "</s>"));
    set.add(new StringList("<s>", "Sam", "I", "am", "</s>"));
    set.add(new StringList("<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>"));
    set.add(new StringList(""));
    Double d = NGramUtils.calculateNgramMLProbability(new StringList("I", "am", "Sam"), set);
    Assertions.assertEquals(Double.valueOf(0.5), d);
    d = NGramUtils.calculateNgramMLProbability(new StringList("Sam", "I", "am"), set);
    Assertions.assertEquals(Double.valueOf(1d), d);
  }

  @Test
  void testLinearInterpolation() {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("the", "green", "book", "STOP"));
    set.add(new StringList("my", "blue", "book", "STOP"));
    set.add(new StringList("his", "green", "house", "STOP"));
    set.add(new StringList("book", "STOP"));
    Double lambda = 1d / 3d;
    Double d = NGramUtils.calculateTrigramLinearInterpolationProbability("the", "green",
        "book", set, lambda, lambda, lambda);
    Assertions.assertNotNull(d);
    Assertions.assertEquals(Double.valueOf(0.5714285714285714d), d, "wrong result");
  }

  @Test
  void testLinearInterpolation2() {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("D", "N", "V", "STOP"));
    set.add(new StringList("D", "N", "V", "STOP"));
    Double lambda = 1d / 3d;
    Double d = NGramUtils.calculateTrigramLinearInterpolationProbability("N", "V",
        "STOP", set, lambda, lambda, lambda);
    Assertions.assertNotNull(d);
    Assertions.assertEquals(Double.valueOf(0.75d), d, "wrong result");
  }

  @Test
  void testGetNGrams() {
    Collection<StringList> nGrams = NGramUtils.getNGrams(new StringList("I",
        "saw", "brown", "fox"), 2);
    Assertions.assertEquals(3, nGrams.size());
    nGrams = NGramUtils.getNGrams(new StringList("I", "saw", "brown", "fox"), 3);
    Assertions.assertEquals(2, nGrams.size());
  }
}
