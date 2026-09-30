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

import opennlp.tools.util.Span;

import static opennlp.tools.formats.masc.MascParserTestUtil.assertRejected;
import static opennlp.tools.formats.masc.MascParserTestUtil.parse;

public class MascSentenceParserTest {

  /**
   * Builds a sentence region with the given offsets.
   *
   * @param anchors The region offsets.
   * @return The annotation XML.
   */
  private static String region(String anchors) {
    return "<graph><region anchors=\"" + anchors + "\"/></graph>";
  }

  @ParameterizedTest
  @MethodSource("opennlp.tools.formats.masc.MascParserTestUtil#xmlWhitespaceSeparators")
  void testSentenceAnchorsUseXmlWhitespace(String separator) throws Exception {
    Assertions.assertEquals(List.of(new Span(0, 4)),
        parse(region(" 0" + separator + "4 "), new MascSentenceParser()).getAnchors());
  }

  @ParameterizedTest
  // wrong arity, non-XML whitespace, text, negative, signed, digits of another script,
  // reversed, overflowing, or missing
  @ValueSource(strings = {"0", "0 4 5", "0&#xA0;4", "0&#x85;4", "0 x",
      "-1 4", "+0 4", "0 +4", "0 \u0664", "0 \uFF14", "4 0", "0 2147483648", "", " "})
  void testMalformedSentenceAnchorsPreserveTheCause(String anchors) {
    SAXException error = assertRejected(region(anchors), new MascSentenceParser());
    Assertions.assertTrue(error.getMessage().contains("anchors"), error.getMessage());
  }


  @Test
  void testMissingAnchorsAreRejected() {
    assertRejected("<graph><region/></graph>", new MascSentenceParser());
  }
}
