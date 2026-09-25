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


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

public class MascPennTagParserTest {

  private static MascPennTagParser parse(String xml) throws Exception {
    return MascParserTestUtil.parse(xml, new MascPennTagParser());
  }

  @Test
  void testTokenIdsLoseTheirPrefix() throws Exception {
    MascPennTagParser parser = parse("<graph>"
        + "<node xml:id=\"penn-n10\"><link targets=\"seg-r0 seg-r1\"/></node>"
        + "<a ref=\"penn-n10\"><fs>"
        + "<f name=\"msd\" value=\"NN\"/><f name=\"base\" value=\"test\"/>"
        + "</fs></a>"
        + "</graph>");
    Assertions.assertArrayEquals(new int[] {0, 1}, parser.getTokenToQuarks().get(10));
    Assertions.assertEquals("NN", parser.getTags().get(10));
    Assertions.assertEquals("test", parser.getBases().get(10));
  }

  @ParameterizedTest
  // a tab or a line break written directly into the attribute is a space after XML
  // attribute-value normalization
  @ValueSource(strings = {"seg-r0 seg-r1", "  seg-r0   seg-r1  ", "seg-r0\tseg-r1", "seg-r0\nseg-r1",
      "seg-r0&#9;seg-r1", "seg-r0&#10;seg-r1", "seg-r0&#13;seg-r1"})
  void testLinkTargetsUseXmlWhitespace(String targets) throws Exception {
    MascPennTagParser parser = parse("<graph>"
        + "<node xml:id=\"penn-n10\"><link targets=\"" + targets + "\"/></node>"
        + "</graph>");
    Assertions.assertArrayEquals(new int[] {0, 1}, parser.getTokenToQuarks().get(10));
  }

  @ParameterizedTest
  // these characters are not XML whitespace, even when introduced through a reference
  @ValueSource(strings = {"seg-r0&#xA0;seg-r1", "seg-r0&#x3000;seg-r1", "seg-r0&#x85;seg-r1"})
  void testLinkTargetsSeparatedByOtherWhitespaceAreRejected(String targets) {
    Assertions.assertThrows(SAXException.class, () -> parse("<graph>"
        + "<node xml:id=\"penn-n10\"><link targets=\"" + targets + "\"/></node>"
        + "</graph>"));
  }

  @ParameterizedTest
  // doubled prefix, missing prefix, other prefix, no digits, trailing text
  @ValueSource(strings = {"penn-npenn-n2", "2", "ne-n2", "penn-n", "penn-n2x"})
  void testMalformedTokenIdsAreRejected(String id) {
    Assertions.assertThrows(SAXException.class, () -> parse(
        "<graph><node xml:id=\"" + id + "\"><link targets=\"seg-r0\"/></node></graph>"));
    Assertions.assertThrows(SAXException.class, () -> parse(
        "<graph><a ref=\"" + id + "\"><fs><f name=\"msd\" value=\"NN\"/></fs></a></graph>"));
  }

  @ParameterizedTest
  // empty list, one malformed entry, other prefix, comma separated
  @ValueSource(strings = {"", " ", "seg-r0 seg-r", "seg-r0 penn-n1", "seg-r0,seg-r1"})
  void testMalformedLinkTargetsAreRejected(String targets) {
    Assertions.assertThrows(SAXException.class, () -> parse(
        "<graph><node xml:id=\"penn-n2\"><link targets=\"" + targets + "\"/></node></graph>"));
  }
}
