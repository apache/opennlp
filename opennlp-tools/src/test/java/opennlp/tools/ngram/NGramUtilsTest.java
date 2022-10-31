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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.StringList;

/**
 * Tests for {@link NGramUtils}
 */
public class NGramUtilsTest {

  @Test
  public void testBigramMLProbability() {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("<s>", "I", "am", "Sam", "</s>"));
    set.add(new StringList("<s>", "Sam", "I", "am", "</s>"));
    set.add(new StringList("<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>"));
    set.add(new StringList(""));
    Double d = NGramUtils.calculateBigramMLProbability("<s>", "I", set);
    Assert.assertEquals(Double.valueOf(0.6666666666666666d), d);
    d = NGramUtils.calculateBigramMLProbability("Sam", "</s>", set);
    Assert.assertEquals(Double.valueOf(0.5d), d);
    d = NGramUtils.calculateBigramMLProbability("<s>", "Sam", set);
    Assert.assertEquals(Double.valueOf(0.3333333333333333d), d);
  }

  @Test
  public void testTrigramMLProbability() {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("<s>", "I", "am", "Sam", "</s>"));
    set.add(new StringList("<s>", "Sam", "I", "am", "</s>"));
    set.add(new StringList("<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>"));
    set.add(new StringList(""));
    Double d = NGramUtils.calculateTrigramMLProbability("I", "am", "Sam", set);
    Assert.assertEquals(Double.valueOf(0.5), d);
    d = NGramUtils.calculateTrigramMLProbability("Sam", "I", "am", set);
    Assert.assertEquals(Double.valueOf(1d), d);
  }

  @Test
  public void testNgramMLProbability() {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("<s>", "I", "am", "Sam", "</s>"));
    set.add(new StringList("<s>", "Sam", "I", "am", "</s>"));
    set.add(new StringList("<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>"));
    set.add(new StringList(""));
    Double d = NGramUtils.calculateNgramMLProbability(new StringList("I", "am", "Sam"), set);
    Assert.assertEquals(Double.valueOf(0.5), d);
    d = NGramUtils.calculateNgramMLProbability(new StringList("Sam", "I", "am"), set);
    Assert.assertEquals(Double.valueOf(1d), d);
  }

  @Test
  public void testLinearInterpolation() throws Exception {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("the", "green", "book", "STOP"));
    set.add(new StringList("my", "blue", "book", "STOP"));
    set.add(new StringList("his", "green", "house", "STOP"));
    set.add(new StringList("book", "STOP"));
    Double lambda = 1d / 3d;
    Double d = NGramUtils.calculateTrigramLinearInterpolationProbability("the", "green",
        "book", set, lambda, lambda, lambda);
    Assert.assertNotNull(d);
    Assert.assertEquals("wrong result", Double.valueOf(0.5714285714285714d), d);
  }

  @Test
  public void testLinearInterpolation2() throws Exception {
    Collection<StringList> set = new LinkedList<>();
    set.add(new StringList("D", "N", "V", "STOP"));
    set.add(new StringList("D", "N", "V", "STOP"));
    Double lambda = 1d / 3d;
    Double d = NGramUtils.calculateTrigramLinearInterpolationProbability("N", "V",
        "STOP", set, lambda, lambda, lambda);
    Assert.assertNotNull(d);
    Assert.assertEquals("wrong result", Double.valueOf(0.75d), d);
  }

  @Test
  public void testGetNGrams() throws Exception {
    Collection<StringList> nGrams = NGramUtils.getNGrams(new StringList("I",
        "saw", "brown", "fox"), 2);
    Assert.assertEquals(3, nGrams.size());
    nGrams = NGramUtils.getNGrams(new StringList("I", "saw", "brown", "fox"), 3);
    Assert.assertEquals(2, nGrams.size());
  }
}
