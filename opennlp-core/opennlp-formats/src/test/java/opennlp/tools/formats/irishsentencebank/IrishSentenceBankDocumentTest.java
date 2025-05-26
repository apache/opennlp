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

package opennlp.tools.formats.irishsentencebank;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.AbstractFormatTest;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.Span;

public class IrishSentenceBankDocumentTest extends AbstractFormatTest {

  @Test
  void testParsingSimpleDoc() throws IOException {
    try (InputStream irishSBXmlIn = getResourceStream("irishsentencebank/irishsentencebank-sample.xml")) {

      IrishSentenceBankDocument doc = IrishSentenceBankDocument.parse(irishSBXmlIn);

      List<IrishSentenceBankDocument.IrishSentenceBankSentence> sents = doc.getSentences();

      Assertions.assertEquals(2, sents.size());

      IrishSentenceBankDocument.IrishSentenceBankSentence sent1 = sents.get(0);
      IrishSentenceBankDocument.IrishSentenceBankSentence sent2 = sents.get(1);

      Assertions.assertEquals("A Dhia, tá mé ag iompar clainne!", sent1.getOriginal());

      IrishSentenceBankDocument.IrishSentenceBankFlex[] flex = sent1.getFlex();
      Assertions.assertEquals(7, flex.length);
      Assertions.assertEquals("A", flex[0].getSurface());
      Assertions.assertArrayEquals(new String[] {"a"}, flex[0].getFlex());

      IrishSentenceBankDocument.IrishSentenceBankFlex[] flex2 = sent2.getFlex();
      Assertions.assertEquals("ón", flex2[4].getSurface());
      Assertions.assertArrayEquals(new String[] {"ó", "an"}, flex2[4].getFlex());

      Assertions.assertEquals("Excuse me, are you from the stone age?", sent2.getTranslation());

      TokenSample ts = sent1.getTokenSample();
      Span[] spans = ts.getTokenSpans();
      Assertions.assertEquals(9, spans.length);
      Assertions.assertEquals(24, spans[7].getStart());
      Assertions.assertEquals(31, spans[7].getEnd());
      Assertions.assertEquals("clainne", ts.getText().substring(spans[7].getStart(), spans[7].getEnd()));
    }
  }
}
