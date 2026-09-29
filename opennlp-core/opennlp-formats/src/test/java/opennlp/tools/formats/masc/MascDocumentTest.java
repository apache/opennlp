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

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import opennlp.tools.util.Span;

public class MascDocumentTest {

  private static final String ONE_SENTENCE = "<graph><region anchors=\"0 11\"/></graph>";

  private static final String ONE_WORD =
      "<graph><region xml:id=\"seg-r0\" anchors=\"0 4\"/></graph>";

  @Test
  void testDocumentKeepsOffsetsOfSupplementaryAndCombiningText() throws IOException {
    // guards the offsets only: a surrogate pair and a combining mark count as two code units
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
    MascDocument document = MascDocument.parseDocument("unicode", MascParserTestUtil.input(text),
        MascParserTestUtil.input(words), MascParserTestUtil.input(penn),
        MascParserTestUtil.input(ONE_SENTENCE), null);
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
        () -> MascDocument.parseDocument("broken", MascParserTestUtil.input("text"),
            MascParserTestUtil.input("<graph><region xml:id=\"seg-r0\" anchors=\"0 4 8\"/></graph>"),
            null, MascParserTestUtil.input(ONE_SENTENCE), null));
    Assertions.assertInstanceOf(SAXException.class, error.getCause());
    Assertions.assertNotNull(error.getCause().getCause());
  }

  @Test
  void testDocumentKeepsPennAttachFailureCause() {
    // the token also links a quark that no region declares
    String penn = "<graph><node xml:id=\"penn-n0\"><link targets=\"seg-r0 seg-r9\"/></node></graph>";
    IOException error = Assertions.assertThrows(IOException.class,
        () -> MascDocument.parseDocument("broken", MascParserTestUtil.input("text"),
            MascParserTestUtil.input(ONE_WORD), MascParserTestUtil.input(penn),
            MascParserTestUtil.input(ONE_SENTENCE), null));
    Assertions.assertEquals("Could not attach POS tags to words", error.getMessage());
    Assertions.assertInstanceOf(IOException.class, error.getCause());
  }
}
