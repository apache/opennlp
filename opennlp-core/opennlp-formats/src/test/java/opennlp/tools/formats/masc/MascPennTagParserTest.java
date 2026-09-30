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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static opennlp.tools.formats.masc.MascParserTestUtil.assertRejected;
import static opennlp.tools.formats.masc.MascParserTestUtil.parse;

public class MascPennTagParserTest {

  /**
   * Builds a token node linked to segmentation regions.
   *
   * @param id The token identifier.
   * @param targets The region identifiers.
   * @return The annotation XML.
   */
  private static String tokenWithTargets(String id, String targets) {
    return "<graph><node xml:id=\"" + id + "\"><link targets=\"" + targets + "\"/></node></graph>";
  }

  @Test
  void testTokenIdsLoseTheirPrefix() throws Exception {
    MascPennTagParser parser = parse("<graph>"
        + "<node xml:id=\"penn-n10\"><link targets=\"seg-r0 seg-r1\"/></node>"
        + "<a ref=\"penn-n10\"><fs>"
        + "<f name=\"msd\" value=\"NN\"/><f name=\"base\" value=\"test\"/>"
        + "</fs></a>"
        + "</graph>", new MascPennTagParser());
    Assertions.assertArrayEquals(new int[] {0, 1}, parser.getTokenToQuarks().get(10));
    Assertions.assertEquals("NN", parser.getTags().get(10));
    Assertions.assertEquals("test", parser.getBases().get(10));
  }

  @ParameterizedTest
  // a tab or a line break written directly into the attribute is a space after XML
  // attribute-value normalization, a character reference keeps the character
  @MethodSource("opennlp.tools.formats.masc.MascParserTestUtil#xmlWhitespaceSeparators")
  void testLinkTargetsUseXmlWhitespace(String separator) throws Exception {
    MascPennTagParser parser = parse(tokenWithTargets("penn-n10", " seg-r0" + separator + "seg-r1 "),
        new MascPennTagParser());
    Assertions.assertArrayEquals(new int[] {0, 1}, parser.getTokenToQuarks().get(10));
  }

  @ParameterizedTest
  // these characters are not XML whitespace, even when introduced through a reference
  @ValueSource(strings = {"seg-r0&#xA0;seg-r1", "seg-r0&#x3000;seg-r1", "seg-r0&#x85;seg-r1"})
  void testLinkTargetsSeparatedByOtherWhitespaceAreRejected(String targets) {
    assertRejected(tokenWithTargets("penn-n10", targets), new MascPennTagParser());
  }

  @ParameterizedTest
  // doubled prefix, missing prefix, other prefix, no digits, trailing text
  @ValueSource(strings = {"penn-npenn-n2", "2", "ne-n2", "penn-n", "penn-n2x"})
  void testMalformedTokenNodeIdsAreRejected(String id) {
    assertRejected(tokenWithTargets(id, "seg-r0"), new MascPennTagParser());
  }

  @ParameterizedTest
  @ValueSource(strings = {"penn-npenn-n2", "2", "ne-n2", "penn-n", "penn-n2x"})
  void testMalformedTokenRefsAreRejected(String ref) {
    assertRejected("<graph><a ref=\"" + ref + "\"><fs><f name=\"msd\" value=\"NN\"/></fs></a></graph>",
        new MascPennTagParser());
  }

  @ParameterizedTest
  // empty list, one malformed entry, other prefix, comma separated
  @ValueSource(strings = {"", " ", "seg-r0 seg-r", "seg-r0 penn-n1", "seg-r0,seg-r1"})
  void testMalformedLinkTargetsAreRejected(String targets) {
    assertRejected(tokenWithTargets("penn-n2", targets), new MascPennTagParser());
  }
}
