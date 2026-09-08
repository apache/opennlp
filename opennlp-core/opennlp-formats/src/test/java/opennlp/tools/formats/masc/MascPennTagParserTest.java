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
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import opennlp.tools.util.XmlUtil;

public class MascPennTagParserTest {

  private static MascPennTagParser parse(String xml) throws Exception {
    MascPennTagParser handler = new MascPennTagParser();
    XmlUtil.createSaxParser().parse(
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);
    return handler;
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

  @Test
  void testOnlyTheFirstPrefixOccurrenceIsRemoved() {
    Assertions.assertThrows(SAXException.class, () -> parse(
        "<graph><node xml:id=\"penn-npenn-n2\"><link targets=\"seg-r0\"/></node></graph>"));
    Assertions.assertThrows(SAXException.class, () -> parse(
        "<graph><a ref=\"penn-npenn-n2\"><fs><f name=\"msd\" value=\"NN\"/></fs></a></graph>"));
  }
}
