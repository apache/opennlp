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

/**
 * Tests for the {@link SentenceSample} class.
 */

public class SentenceSampleTest {

  @Test
  void testRetrievingContent() {
    SentenceSample sample = new SentenceSample("1. 2.",
        new Span(0, 2), new Span(3, 5));

    Assertions.assertEquals("1. 2.", sample.getDocument());
    Assertions.assertEquals(new Span(0, 2), sample.getSentences()[0]);
    Assertions.assertEquals(new Span(3, 5), sample.getSentences()[1]);
  }

  @Test
  void testSentenceSampleSerDe() throws IOException {
    SentenceSample sentenceSample = createGoldSample();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    ObjectOutput out = new ObjectOutputStream(byteArrayOutputStream);
    out.writeObject(sentenceSample);
    out.flush();
    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
    ObjectInput objectInput = new ObjectInputStream(byteArrayInputStream);

    SentenceSample deSerializedSentenceSample = null;
    try {
      deSerializedSentenceSample = (SentenceSample) objectInput.readObject();
    } catch (ClassNotFoundException e) {
      // do nothing
    }

    Assertions.assertNotNull(deSerializedSentenceSample);
    Assertions.assertEquals(sentenceSample.getDocument(), deSerializedSentenceSample.getDocument());
    Assertions.assertArrayEquals(sentenceSample.getSentences(), deSerializedSentenceSample.getSentences());
  }

  @Test
  void testInvalidSpansFailFast() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      SentenceSample sample = new SentenceSample("1. 2.",
          new Span(0, 2), new Span(5, 7));
    });
  }

  @Test
  void testEquals() {
    Assertions.assertNotSame(createGoldSample(), createGoldSample());
    Assertions.assertEquals(createGoldSample(), createGoldSample());
    Assertions.assertNotEquals(createPredSample(), createGoldSample());
    Assertions.assertNotEquals(createPredSample(), new Object());
  }

  public static SentenceSample createGoldSample() {
    return new SentenceSample("1. 2.", new Span(0, 2), new Span(3, 5));
  }

  public static SentenceSample createPredSample() {
    return new SentenceSample("1. 2.", new Span(0, 1), new Span(4, 5));
  }
}
