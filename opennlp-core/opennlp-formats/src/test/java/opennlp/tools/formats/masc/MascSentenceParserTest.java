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
package opennlp.tools.formats.masc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

import opennlp.tools.util.Span;

class MascSentenceParserTest {

  @ParameterizedTest
  @ValueSource(strings = {"0 4", " 0  4 ", "0\t4", "0\n4", "0&#9;4", "0&#10;4", "0&#13;4"})
  void testSentenceAnchorsUseXmlWhitespace(String anchors) throws Exception {
    MascSentenceParser parser = MascParserTestUtil.parse(
        "<graph><region anchors=\"" + anchors + "\"/></graph>", new MascSentenceParser());
    Assertions.assertEquals(List.of(new Span(0, 4)), parser.getAnchors());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0 4 5", "0&#xA0;4", "0&#x85;4", "0 x", "-1 4",
      "4 0", "0 2147483648", "", " "})
  void testMalformedSentenceAnchorsPreserveTheCause(String anchors) {
    SAXException error = Assertions.assertThrows(SAXException.class,
        () -> MascParserTestUtil.parse("<graph><region anchors=\"" + anchors + "\"/></graph>",
            new MascSentenceParser()));
    Assertions.assertNotNull(error.getCause());
    Assertions.assertTrue(error.getMessage().contains("anchors"), error.getMessage());
  }

  @Test
  void testMissingAnchorsAreRejected() {
    SAXException error = Assertions.assertThrows(SAXException.class,
        () -> MascParserTestUtil.parse("<graph><region/></graph>", new MascSentenceParser()));
    Assertions.assertNotNull(error.getCause());
  }

  @Test
  void testDocumentPreservesUnicodeTextAndOffsets() throws IOException {
    String text = "\uD83D\uDE00 cafe\u0301 中文";
    String words = "<graph><region xml:id=\"seg-r0\" anchors=\"0 2\"/>"
        + "<region xml:id=\"seg-r1\" anchors=\"3 8\"/>"
        + "<region xml:id=\"seg-r2\" anchors=\"9 11\"/></graph>";
    String penn = "<graph>"
        + "<node xml:id=\"penn-n0\"><link targets=\"seg-r0\"/></node>"
        + "<node xml:id=\"penn-n1\"><link targets=\"seg-r1\"/></node>"
        + "<node xml:id=\"penn-n2\"><link targets=\"seg-r2\"/></node>"
        + "<a ref=\"penn-n0\"><fs><f name=\"msd\" value=\"SYM\"/></fs></a>"
        + "<a ref=\"penn-n1\"><fs><f name=\"msd\" value=\"NN\"/></fs></a>"
        + "<a ref=\"penn-n2\"><fs><f name=\"msd\" value=\"NN\"/></fs></a></graph>";
    MascDocument document = MascDocument.parseDocument("unicode", input(text), input(words),
        input(penn), input("<graph><region anchors=\"0 11\"/></graph>"), null);
    MascSentence sentence = document.read();
    Assertions.assertEquals(text, sentence.getSentDetectText());
    Assertions.assertEquals(List.of("\uD83D\uDE00", "cafe\u0301", "中文"), sentence.getTokenStrings());
    Assertions.assertEquals(List.of(new Span(0, 2), new Span(3, 8), new Span(9, 11)),
        sentence.getTokensSpans());
    Assertions.assertNull(document.read());
  }

  @Test
  void testDocumentKeepsAnnotationFailureCause() {
    IOException error = Assertions.assertThrows(IOException.class,
        () -> MascDocument.parseDocument("broken", input("text"),
            input("<graph><region xml:id=\"seg-r0\" anchors=\"0 4 8\"/></graph>"),
            null, input("<graph><region anchors=\"0 4\"/></graph>"), null));
    Assertions.assertInstanceOf(SAXException.class, error.getCause());
    Assertions.assertNotNull(error.getCause().getCause());
  }

  private static ByteArrayInputStream input(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }
}
