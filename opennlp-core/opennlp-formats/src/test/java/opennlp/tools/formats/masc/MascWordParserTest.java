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
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import opennlp.tools.util.XmlUtil;

public class MascWordParserTest {

  private static MascWordParser parse(String xml) throws Exception {
    MascWordParser handler = new MascWordParser();
    XmlUtil.createSaxParser().parse(
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);
    return handler;
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

  @Test
  void testOnlyTheFirstPrefixOccurrenceIsRemoved() {
    Assertions.assertThrows(SAXException.class, () -> parse(
        "<graph><region xml:id=\"seg-rseg-r3\" anchors=\"0 4\"/></graph>"));
  }
}
