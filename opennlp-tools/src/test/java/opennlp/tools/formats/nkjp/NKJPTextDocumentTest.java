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

package opennlp.tools.formats.nkjp;

import java.io.InputStream;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NKJPTextDocumentTest {
  @Test
  public void testParsingSimpleDoc() throws Exception {
    try (InputStream nkjpTextXmlIn =
           NKJPTextDocumentTest.class.getResourceAsStream("text_structure.xml")) {

      NKJPTextDocument doc = NKJPTextDocument.parse(nkjpTextXmlIn);

      assertEquals(1, doc.getDivtypes().size());
      assertEquals("article", doc.getDivtypes().get("div-1"));

      assertEquals(1, doc.getTexts().size());
      assertEquals(1, doc.getTexts().get("text-1").size());
      assertEquals(2, doc.getTexts().get("text-1").get("div-1").size());

      String exp = "To krótki tekst w formacie NKJP. Zawiera dwa zdania.";
      assertEquals(exp, doc.getTexts().get("text-1").get("div-1").get("p-1"));
    }
  }

  @Test
  public void testGetParagraphs() throws Exception {
    try (InputStream nkjpTextXmlIn =
           NKJPTextDocumentTest.class.getResourceAsStream("text_structure.xml")) {

      NKJPTextDocument doc = NKJPTextDocument.parse(nkjpTextXmlIn);
      Map<String, String> paras = doc.getParagraphs();
      assertEquals("To krótkie zdanie w drugim akapicie.", paras.get("ab-1"));
    }
  }
}
