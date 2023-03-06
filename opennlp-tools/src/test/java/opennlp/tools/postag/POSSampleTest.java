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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.InvalidFormatException;

/**
 * Tests for the {@link POSSample} class.
 */
public class POSSampleTest {

  @Test
  void testEquals() throws InvalidFormatException {
    Assertions.assertNotSame(createGoldSample(), createGoldSample());
    Assertions.assertEquals(createGoldSample(), createGoldSample());
    Assertions.assertNotEquals(createPredSample(), createGoldSample());
    Assertions.assertNotEquals(createPredSample(), new Object());
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

  @Test
  void testPOSSampleSerDe() throws IOException {
    POSSample posSample = createGoldSample();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    ObjectOutput out = new ObjectOutputStream(byteArrayOutputStream);
    out.writeObject(posSample);
    out.flush();
    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
    ObjectInput objectInput = new ObjectInputStream(byteArrayInputStream);

    POSSample deSerializedPOSSample = null;
    try {
      deSerializedPOSSample = (POSSample) objectInput.readObject();
    } catch (ClassNotFoundException e) {
      // do nothing
    }

    Assertions.assertNotNull(deSerializedPOSSample);
    Assertions.assertArrayEquals(posSample.getAdditionalContext(),
        deSerializedPOSSample.getAdditionalContext());
    Assertions.assertArrayEquals(posSample.getSentence(), deSerializedPOSSample.getSentence());
    Assertions.assertArrayEquals(posSample.getTags(), deSerializedPOSSample.getTags());
  }

  /**
   * Tests if it can parse a valid token_tag sentence.
   */
  @Test
  void testParse() throws InvalidFormatException {
    String sentence = "the_DT stories_NNS about_IN well-heeled_JJ " +
        "communities_NNS and_CC developers_NNS";
    POSSample sample = POSSample.parse(sentence);
    Assertions.assertEquals(sentence, sample.toString());
  }

  /**
   * Tests if it can parse an empty {@link String}.
   */
  @Test
  void testParseEmptyString() throws InvalidFormatException {
    String sentence = "";

    POSSample sample = POSSample.parse(sentence);

    Assertions.assertEquals(sample.getSentence().length, 0);
    Assertions.assertEquals(sample.getTags().length, 0);
  }

  /**
   * Tests if it can parse an empty token.
   */
  @Test
  void testParseEmtpyToken() throws InvalidFormatException {
    String sentence = "the_DT _NNS";
    POSSample sample = POSSample.parse(sentence);
    Assertions.assertEquals(sample.getSentence()[1], "");
  }

  /**
   * Tests if it can parse an empty tag.
   */
  @Test
  void testParseEmtpyTag() throws InvalidFormatException {
    String sentence = "the_DT stories_";
    POSSample sample = POSSample.parse(sentence);
    Assertions.assertEquals(sample.getTags()[1], "");
  }

  /**
   * Tests if an exception is thrown if there is only a token/tag
   * in the sentence.
   */
  @Test
  void testParseWithError() {
    String sentence = "the_DT stories";

    try {
      POSSample.parse(sentence);
    } catch (InvalidFormatException e) {
      return;
    }

    Assertions.fail();
  }
}
