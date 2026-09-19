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

package opennlp.tools.formats.ad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class ADSentenceSampleStreamTest extends AbstractADSampleStreamTest<SentenceSample> {

  @BeforeEach
  void setup() throws IOException {
    super.setup();

    try (ADSentenceSampleStream stream = new ADSentenceSampleStream(
            new PlainTextByLineStream(in, StandardCharsets.UTF_8), true)) {

      SentenceSample sample;
      while ((sample = stream.read()) != null) {
        samples.add(sample);
      }
      Assertions.assertFalse(samples.isEmpty());
    }
  }

  @Test
  void testSimpleCount() {
    Assertions.assertEquals(5, samples.size());
  }

  @Test
  void testSentences() {

    Assertions.assertNotNull(samples.get(0).getDocument());
    Assertions.assertEquals(3, samples.get(0).getSentences().length);
    Assertions.assertEquals(new Span(0, 119), samples.get(0).getSentences()[0]);
    Assertions.assertEquals(new Span(120, 180), samples.get(0).getSentences()[1]);
  }

  @Test
  void testInvalidMetadataIsRejected() throws IOException {
    // the second sentence id "AX" has no digits, so its metadata cannot be parsed
    List<String> lines = List.of(
        "<s>",
        "SOURCE: src",
        "1001 Hello world .",
        "</s>",
        "<s>",
        "SOURCE: src",
        "AX Hi there .",
        "</s>");
    try (ADSentenceSampleStream stream = new ADSentenceSampleStream(
        ObjectStreamUtils.createObjectStream(lines), true)) {
      RuntimeException e = Assertions.assertThrows(RuntimeException.class, stream::read);
      Assertions.assertEquals("Invalid metadata: AX p=0 src", e.getMessage());
    }
  }

  @Test
  void testNextLineCharacterInTheSourceIsMetadata() throws IOException {
    List<String> lines = List.of(
        "<s>",
        "SOURCE: ref=\"a\u0085b\"",
        "1001 Hello world .",
        "STA:fcl",
        "=H:n(\"world\" M S)\tworld",
        "</s>");
    try (ADSentenceSampleStream stream = new ADSentenceSampleStream(
        ObjectStreamUtils.createObjectStream(lines), true)) {
      SentenceSample sample = stream.read();
      Assertions.assertNotNull(sample);
      Assertions.assertEquals("Hello world .", sample.getDocument());
      Assertions.assertNull(stream.read());
    }
  }

  @Test
  void testIdsThatDoNotFitAnIntAreInvalidMetadata() throws IOException {
    List<String> lines = List.of("<s>", "SOURCE: src", "2147483648 Hello .", "</s>");
    try (ADSentenceSampleStream stream = new ADSentenceSampleStream(
        ObjectStreamUtils.createObjectStream(lines), true)) {
      RuntimeException e = Assertions.assertThrows(RuntimeException.class, stream::read);
      Assertions.assertEquals("Invalid metadata: 2147483648 p=0 src", e.getMessage());
    }
  }
}
