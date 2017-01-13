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

package opennlp.tools.formats.letsmt;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class LetsmtDocumentTest {

  @Test
  public void testParsingSimpleDoc() throws IOException {
    try (InputStream letsmtXmlIn = LetsmtDocumentTest.class.getResourceAsStream("letsmt-with-words.xml");) {

      LetsmtDocument doc = LetsmtDocument.parse(letsmtXmlIn);

      List<LetsmtDocument.LetsmtSentence> sents = doc.getSentences();

      Assert.assertEquals(2, sents.size());

      LetsmtDocument.LetsmtSentence sent1 = sents.get(0);
      Assert.assertNull(sent1.getNonTokenizedText());

      Assert.assertArrayEquals(new String[]{
          "The",
          "Apache",
          "Software",
          "Foundation",
          "uses",
          "various",
          "licenses",
          "to",
          "distribute",
          "software",
          "and",
          "documentation",
          ",",
          "to",
          "accept",
          "regular",
          "contributions",
          "from",
          "individuals",
          "and",
          "corporations",
          ",",
          "and",
          "to",
          "accept",
          "larger",
          "grants",
          "of",
          "existing",
          "software",
          "products",
          "."
          }, sent1.getTokens());

      LetsmtDocument.LetsmtSentence sent2 = sents.get(1);
      Assert.assertNull(sent2.getNonTokenizedText());

      Assert.assertArrayEquals(new String[]{
          "All",
          "software",
          "produced",
          "by",
          "The",
          "Apache",
          "Software",
          "Foundation",
          "or",
          "any",
          "of",
          "its",
          "projects",
          "or",
          "subjects",
          "is",
          "licensed",
          "according",
          "to",
          "the",
          "terms",
          "of",
          "the",
          "documents",
          "listed",
          "below",
          "."
          }, sent2.getTokens());
    }
  }
}
