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
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;
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

  @ParameterizedTest
  @CsvSource({
      "'CF1001-1 p=2', 1001, 2",
      "'1001 p=2', 1001, 2",
      "'CF-1001 par=7 p=12 x', 1001, 12",
      // the text id is the first digit run, whatever follows it
      "'CF1001-1 par=7 p=1', 1001, 1",
      "'1001x p=2', 1001, 2",
      // p= without digits is skipped, a later p= with digits counts
      "'1001 p=x p=3', 1001, 3",
      "'1001 p= p=4', 1001, 4",
      "'1001 pp=5', 1001, 5",
      "'1001p=6', 1001, 6",
      "'1001 p=007', 1001, 7"})
  void testParseTextAndParagraph(String meta, int text, int para) {
    Assertions.assertArrayEquals(new int[] {text, para},
        ADSentenceSampleStream.parseTextAndParagraph(meta));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "AX", "CF- p=1", "1001", "1001 p=", "1001 p=x", "1001 P=2",
      "1001 p =2", "1001 p= 2",
      // the text id must be ASCII digits, and must directly follow the letters and hyphens
      "\u0661 p=2", "CF_1001 p=2", "CF 1001 p=2"})
  void testParseTextAndParagraphRejects(String meta) {
    Assertions.assertNull(ADSentenceSampleStream.parseTextAndParagraph(meta));
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
    Iterator<String> iterator = lines.iterator();
    ObjectStream<String> lineStream = new ObjectStream<>() {
      @Override
      public String read() {
        return iterator.hasNext() ? iterator.next() : null;
      }
    };
    try (ADSentenceSampleStream stream = new ADSentenceSampleStream(lineStream, true)) {
      Assertions.assertThrows(RuntimeException.class, stream::read);
    }
  }

}
