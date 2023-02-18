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

package opennlp.tools.tokenize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

public class TokenSampleTest {

  public static TokenSample createGoldSample() {
    return new TokenSample("A test.", new Span[] {new Span(0, 1),
        new Span(2, 6)});
  }

  public static TokenSample createPredSample() {
    return new TokenSample("A test.", new Span[] {new Span(0, 3),
        new Span(2, 6)});
  }

  public static TokenSample createPredSilverSample() {
    return new TokenSample("A t st.", new Span[] {new Span(0, 1),
        new Span(2, 6)});
  }

  @Test
  void testRetrievingContent() {

    String sentence = "A test";

    TokenSample sample = new TokenSample(sentence, new Span[] {new Span(0, 1),
        new Span(2, 6)});

    Assertions.assertEquals("A test", sample.getText());

    Assertions.assertEquals(new Span(0, 1), sample.getTokenSpans()[0]);
    Assertions.assertEquals(new Span(2, 6), sample.getTokenSpans()[1]);
  }

  @Test
  void testTokenSampleSerDe() throws IOException {
    TokenSample tokenSample = createGoldSample();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    ObjectOutput out = new ObjectOutputStream(byteArrayOutputStream);
    out.writeObject(tokenSample);
    out.flush();
    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
    ObjectInput objectInput = new ObjectInputStream(byteArrayInputStream);

    TokenSample deSerializedTokenSample = null;
    try {
      deSerializedTokenSample = (TokenSample) objectInput.readObject();
    } catch (ClassNotFoundException e) {
      // do nothing
    }

    Assertions.assertNotNull(deSerializedTokenSample);
    Assertions.assertEquals(tokenSample.getText(), deSerializedTokenSample.getText());
    Assertions.assertArrayEquals(tokenSample.getTokenSpans(), deSerializedTokenSample.getTokenSpans());
  }

  @Test
  void testCreationWithDetokenizer() throws IOException {

    Detokenizer detokenizer = DictionaryDetokenizerTest.createLatinDetokenizer();

    String[] tokens = new String[] {
        "start",
        "(", // move right
        ")", // move left
        "end",
        ".", // move left
        "hyphen",
        "-", // move both
        "string",
        "."
    };

    TokenSample a = new TokenSample(detokenizer, tokens);

    Assertions.assertEquals("start () end. hyphen-string.", a.getText());
    Assertions.assertEquals("start (" + TokenSample.DEFAULT_SEPARATOR_CHARS + ") end"
        + TokenSample.DEFAULT_SEPARATOR_CHARS + "."
        + " hyphen" + TokenSample.DEFAULT_SEPARATOR_CHARS + "-" + TokenSample.DEFAULT_SEPARATOR_CHARS
        + "string" + TokenSample.DEFAULT_SEPARATOR_CHARS + ".", a.toString());

    Assertions.assertEquals(9, a.getTokenSpans().length);

    Assertions.assertEquals(new Span(0, 5), a.getTokenSpans()[0]);
    Assertions.assertEquals(new Span(6, 7), a.getTokenSpans()[1]);
    Assertions.assertEquals(new Span(7, 8), a.getTokenSpans()[2]);
    Assertions.assertEquals(new Span(9, 12), a.getTokenSpans()[3]);
    Assertions.assertEquals(new Span(12, 13), a.getTokenSpans()[4]);

    Assertions.assertEquals(new Span(14, 20), a.getTokenSpans()[5]);
    Assertions.assertEquals(new Span(20, 21), a.getTokenSpans()[6]);
    Assertions.assertEquals(new Span(21, 27), a.getTokenSpans()[7]);
    Assertions.assertEquals(new Span(27, 28), a.getTokenSpans()[8]);
  }

  @Test
  void testEquals() {
    Assertions.assertNotSame(createGoldSample(), createGoldSample());
    Assertions.assertEquals(createGoldSample(), createGoldSample());
    Assertions.assertNotEquals(createPredSample(), createGoldSample());
    Assertions.assertNotEquals(createPredSample(), new Object());
  }
}
