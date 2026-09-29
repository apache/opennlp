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

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

public class MascWordParserTest {

  /**
   * Parses an annotation fixture.
   *
   * @param xml The annotation XML.
   * @return The parser containing the annotations.
   * @throws Exception Thrown if parsing fails.
   */
  private static MascWordParser parse(String xml) throws Exception {
    return MascParserTestUtil.parse(xml, new MascWordParser());
  }

  /**
   * Builds a segmentation region with the given offsets.
   *
   * @param id The region identifier.
   * @param anchors The region offsets.
   * @return The annotation XML.
   */
  private static String region(String id, String anchors) {
    return "<graph><region xml:id=\"" + id + "\" anchors=\"" + anchors + "\"/></graph>";
  }

  /**
   * Checks that malformed annotations retain their validation cause.
   *
   * @param xml The malformed annotation XML.
   * @return The parsing exception.
   */
  private static SAXException assertRejected(String xml) {
    SAXException e = Assertions.assertThrows(SAXException.class, () -> parse(xml));
    Assertions.assertInstanceOf(IllegalArgumentException.class, e.getCause());
    return e;
  }

  @Test
  void testRegionIdsLoseTheirPrefix() throws Exception {
    List<MascWord> words = parse("<graph>"
        + "<region xml:id=\"seg-r0\" anchors=\"0 4\"/>"
        + "<region xml:id=\"seg-r11\" anchors=\"5 7\"/>"
        + "</graph>").getAnchors();
    Assertions.assertEquals(2, words.size());
    Assertions.assertEquals(0, words.get(0).getId());
    Assertions.assertEquals(0, words.get(0).getStart());
    Assertions.assertEquals(4, words.get(0).getEnd());
    Assertions.assertEquals(11, words.get(1).getId());
    Assertions.assertEquals(5, words.get(1).getStart());
    Assertions.assertEquals(7, words.get(1).getEnd());
  }

  @ParameterizedTest
  // doubled prefix, missing prefix, other prefix, no digits, trailing text
  @ValueSource(strings = {"seg-rseg-r3", "3", "penn-n3", "seg-r", "seg-r3x"})
  void testMalformedRegionIdsAreRejected(String id) {
    assertRejected(region(id, "0 4"));
  }

  @ParameterizedTest
  // XML whitespace written literally or supplied through character references
  @MethodSource("opennlp.tools.formats.masc.MascParserTestUtil#xmlWhitespaceSeparators")
  void testAnchorsUseXmlWhitespace(String separator) throws Exception {
    List<MascWord> words = parse(region("seg-r0", " 0" + separator + "4 ")).getAnchors();
    Assertions.assertEquals(0, words.get(0).getStart());
    Assertions.assertEquals(4, words.get(0).getEnd());
  }

  @ParameterizedTest
  // wrong arity, non-XML whitespace, text, negative, reversed,
  // overflowing, or missing
  @ValueSource(strings = {"0", "0 4 5", "0&#xA0;4", "0 x", "-1 4",
      "4 0", "0 2147483648", "", " "})
  void testMalformedAnchorsAreRejectedWithTheReason(String anchors) {
    SAXException e = assertRejected(region("seg-r0", anchors));
    Assertions.assertTrue(e.getMessage().startsWith("Could not parse the word segmentation"), e.getMessage());
  }

  @ParameterizedTest
  @MethodSource("opennlp.tools.formats.masc.MascParserTestUtil#equivalentAnchors")
  void testWordAnchorsAcceptEquivalentIntegerForms(String anchors) throws Exception {
    List<MascWord> words = parse(region("seg-r0", anchors)).getAnchors();
    Assertions.assertEquals(1, words.size());
    Assertions.assertEquals(0, words.get(0).getStart());
    Assertions.assertEquals(4, words.get(0).getEnd());
  }
}
