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

package opennlp.tools.formats.conllu;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;

public class ConlluStreamTest {

  @Test
  public void testParseTwoSentences() throws IOException {

    InputStreamFactory streamFactory =
        new ResourceAsStreamFactory(ConlluStreamTest.class, "de-ud-train-sample.conllu");

    try (ObjectStream<ConlluSentence> stream = new ConlluStream(streamFactory)) {
      ConlluSentence sent1 = stream.read();

      Assert.assertEquals("train-s21", sent1.getSentenceIdComment());
      Assert.assertEquals("Fachlich kompetent, sehr gute Beratung und ein freundliches Team.",
          sent1.getTextComment());
      Assert.assertEquals(11, sent1.getWordLines().size());

      ConlluSentence sent2 = stream.read();

      Assert.assertEquals("train-s22", sent2.getSentenceIdComment());
      Assert.assertEquals(
          "Beiden Zahnärzten verdanke ich einen neuen Biss und dadurch endlich keine Rückenschmerzen mehr.",
          sent2.getTextComment());
      Assert.assertEquals(14, sent2.getWordLines().size());

      Assert.assertNull("Stream must be exhausted", stream.read());
    }
  }

  @Test
  public void testOptionalComments() throws IOException {
    InputStreamFactory streamFactory =
            new ResourceAsStreamFactory(ConlluStreamTest.class, "full-sample.conllu");

    try (ObjectStream<ConlluSentence> stream = new ConlluStream(streamFactory)) {
      ConlluSentence sent1 = stream.read();

      Assert.assertEquals("1", sent1.getSentenceIdComment());
      Assert.assertEquals("They buy and sell books.",
              sent1.getTextComment());
      Assert.assertTrue(sent1.isNewDocument());
      Assert.assertTrue(sent1.isNewParagraph());
      Assert.assertEquals(6, sent1.getWordLines().size());

      ConlluSentence sent2 = stream.read();

      Assert.assertEquals("2", sent2.getSentenceIdComment());
      Assert.assertEquals(
              "I have no clue.",
              sent2.getTextComment());
      Assert.assertTrue(sent2.isNewDocument());
      Assert.assertEquals(5, sent2.getWordLines().size());

      ConlluSentence sent3 = stream.read();

      Assert.assertEquals("panc0.s4", sent3.getSentenceIdComment());
      Assert.assertEquals(Optional.of("tat yathānuśrūyate."), sent3.getTranslit());
      Assert.assertEquals("तत् यथानुश्रूयते।", sent3.getTextComment());
      Assert.assertEquals(3, sent3.getWordLines().size());
      Assert.assertTrue(sent3.isNewParagraph());
      Map<Object, Object> textLang3 = new HashMap<>();
      textLang3.put(new Locale("fr"), "Voilà ce qui nous est parvenu par la tradition orale.");
      textLang3.put(new Locale("en"), "This is what is heard.");
      Assert.assertEquals(Optional.of(textLang3)
              , sent3.getTextLang());

      ConlluSentence sent4 = stream.read();

      Assert.assertEquals("mf920901-001-p1s1A", sent4.getSentenceIdComment());
      Assert.assertEquals(
              "Slovenská ústava: pro i proti",
              sent4.getTextComment());
      Assert.assertEquals(6, sent4.getWordLines().size());
      Assert.assertTrue(sent4.isNewDocument());
      Assert.assertTrue(sent4.isNewParagraph());
      Assert.assertEquals(Optional.of("mf920901-001"), sent4.getDocumentId());
      Assert.assertEquals(Optional.of("mf920901-001-p1"), sent4.getParagraphId());
      Assert.assertEquals(Optional.of(Collections.singletonMap(new Locale("en"),
                      "Slovak constitution: pros and cons"))
              , sent4.getTextLang());

      Assert.assertNull("Stream must be exhausted", stream.read());
    }
  }
}
