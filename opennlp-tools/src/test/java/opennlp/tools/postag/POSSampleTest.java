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

import opennlp.tools.util.InvalidFormatException;

/**
 * Tests for the {@link POSSample} class.
 */
public class POSSampleTest {

  @Test
  public void testEquals() throws InvalidFormatException {
    Assert.assertFalse(createGoldSample() == createGoldSample());
    Assert.assertTrue(createGoldSample().equals(createGoldSample()));
    Assert.assertFalse(createPredSample().equals(createGoldSample()));
    Assert.assertFalse(createPredSample().equals(new Object()));
  }

  public static POSSample createGoldSample() throws InvalidFormatException {
    String sentence = "the_DT stories_NNS about_IN well-heeled_JJ "
        + "communities_NNS and_CC developers_NNS";
    return POSSample.parse(sentence);
  }

  public static POSSample createPredSample() throws InvalidFormatException {
    String sentence = "the_DT stories_NNS about_NNS well-heeled_JJ "
        + "communities_NNS and_CC developers_CC";
    return POSSample.parse(sentence);
  }

  /**
   * Tests if it can parse a valid token_tag sentence.
   *
   */
  @Test
  public void testParse() throws InvalidFormatException {
    String sentence = "the_DT stories_NNS about_IN well-heeled_JJ " +
        "communities_NNS and_CC developers_NNS";
    POSSample sample = POSSample.parse(sentence);
    Assert.assertEquals(sentence, sample.toString());
  }

  /**
   * Tests if it can parse an empty {@link String}.
   */
  @Test
  public void testParseEmptyString() throws InvalidFormatException {
    String sentence = "";

    POSSample sample = POSSample.parse(sentence);

    Assert.assertEquals(sample.getSentence().length, 0);
    Assert.assertEquals(sample.getTags().length, 0);
  }

  /**
   * Tests if it can parse an empty token.
   *
   */
  @Test
  public void testParseEmtpyToken() throws InvalidFormatException {
    String sentence = "the_DT _NNS";
    POSSample sample = POSSample.parse(sentence);
    Assert.assertEquals(sample.getSentence()[1], "");
  }

  /**
   * Tests if it can parse an empty tag.
   *
   */
  @Test
  public void testParseEmtpyTag() throws InvalidFormatException {
    String sentence = "the_DT stories_";
    POSSample sample = POSSample.parse(sentence);
    Assert.assertEquals(sample.getTags()[1], "");
  }

  /**
   * Tests if an exception is thrown if there is only a token/tag
   * in the sentence.
   */
  @Test
  public void testParseWithError() {
    String sentence = "the_DT stories";

    try {
      POSSample.parse(sentence);
    } catch (InvalidFormatException e) {
      return;
    }

    Assert.fail();
  }
}