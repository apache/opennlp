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

package opennlp.tools.sentdetect;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.Span;

/**
 * Tests for the {@link SentenceSample} class.
 */
public class SentenceSampleTest {

  @Test
  public void testRetrievingContent() {

    SentenceSample sample = new SentenceSample("1. 2.",
        new Span(0, 2), new Span(3, 5));

    Assert.assertEquals("1. 2.", sample.getDocument());
    Assert.assertEquals(new Span(0, 2), sample.getSentences()[0]);
    Assert.assertEquals(new Span(3, 5), sample.getSentences()[1]);
  }

  @Test
  public void testEquals() {
    Assert.assertFalse(createGoldSample() == createGoldSample());
    Assert.assertTrue(createGoldSample().equals(createGoldSample()));
    Assert.assertFalse(createPredSample().equals(createGoldSample()));
    Assert.assertFalse(createPredSample().equals(new Object()));
  }

  public static SentenceSample createGoldSample() {
    return new SentenceSample("1. 2.", new Span(0, 2), new Span(3, 5));
  }

  public static SentenceSample createPredSample() {
    return new SentenceSample("1. 2.", new Span(0, 1), new Span(4, 5));
  }
}
