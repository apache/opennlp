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

public class MascNamedEntityParserTest {

  private static MascNamedEntityParser parse(String xml) throws Exception {
    MascNamedEntityParser handler = new MascNamedEntityParser();
    XmlUtil.createSaxParser().parse(
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);
    return handler;
  }

  @Test
  void testEntityAndTokenIdsLoseTheirPrefix() throws Exception {
    MascNamedEntityParser parser = parse("<graph>"
        + "<a ref=\"ne-n3\" label=\"person\"/>"
        + "<edge from=\"ne-n3\" to=\"penn-n4\"/>"
        + "<edge from=\"ne-n3\" to=\"penn-n15\"/>"
        + "</graph>");
    Assertions.assertEquals("person", parser.getEntityIDtoEntityType().get(3));
    Assertions.assertEquals(List.of(4, 15), parser.getEntityIDsToTokens().get(3));
  }

  @Test
  void testOnlyTheFirstPrefixOccurrenceIsRemoved() {
    Assertions.assertThrows(SAXException.class, () -> parse(
        "<graph><a ref=\"ne-nne-n3\" label=\"person\"/></graph>"));
    Assertions.assertThrows(SAXException.class, () -> parse(
        "<graph><a ref=\"ne-n3\" label=\"person\"/>"
        + "<edge from=\"ne-n3\" to=\"penn-npenn-n4\"/></graph>"));
  }
}
