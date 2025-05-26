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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;

public class ConlluStreamTest extends AbstractConlluSampleStreamTest<SentenceSample> {

  @Test
  void testParseTwoSentences() throws IOException {
    try (ObjectStream<ConlluSentence> stream = getStream("de-ud-train-sample.conllu")) {
      ConlluSentence sent1 = stream.read();

      Assertions.assertEquals("train-s21", sent1.getSentenceIdComment());
      Assertions.assertEquals("Fachlich kompetent, sehr gute Beratung und ein freundliches Team.",
          sent1.getTextComment());
      Assertions.assertEquals(11, sent1.getWordLines().size());

      ConlluSentence sent2 = stream.read();

      Assertions.assertEquals("train-s22", sent2.getSentenceIdComment());
      Assertions.assertEquals(
          "Beiden Zahnärzten verdanke ich einen neuen Biss und dadurch endlich keine Rückenschmerzen mehr.",
          sent2.getTextComment());
      Assertions.assertEquals(14, sent2.getWordLines().size());

      Assertions.assertNull(stream.read(), "Stream must be exhausted");
    }
  }

  @Test
  void testOptionalComments() throws IOException {
    try (ObjectStream<ConlluSentence> stream = getStream("full-sample.conllu")) {
      ConlluSentence sent1 = stream.read();

      Assertions.assertEquals("1", sent1.getSentenceIdComment());
      Assertions.assertEquals("They buy and sell books.",
          sent1.getTextComment());
      Assertions.assertTrue(sent1.isNewDocument());
      Assertions.assertTrue(sent1.isNewParagraph());
      Assertions.assertEquals(6, sent1.getWordLines().size());

      ConlluSentence sent2 = stream.read();

      Assertions.assertEquals("2", sent2.getSentenceIdComment());
      Assertions.assertEquals(
          "I have no clue.",
          sent2.getTextComment());
      Assertions.assertTrue(sent2.isNewDocument());
      Assertions.assertEquals(5, sent2.getWordLines().size());

      ConlluSentence sent3 = stream.read();

      Assertions.assertEquals("panc0.s4", sent3.getSentenceIdComment());
      Assertions.assertEquals(Optional.of("tat yathānuśrūyate."), sent3.getTranslit());
      Assertions.assertEquals("तत् यथानुश्रूयते।", sent3.getTextComment());
      Assertions.assertEquals(3, sent3.getWordLines().size());
      Assertions.assertTrue(sent3.isNewParagraph());
      Map<Object, Object> textLang3 = new HashMap<>();
      textLang3.put(new Locale("fr"), "Voilà ce qui nous est parvenu par la tradition orale.");
      textLang3.put(new Locale("en"), "This is what is heard.");
      Assertions.assertEquals(Optional.of(textLang3)
          , sent3.getTextLang());

      ConlluSentence sent4 = stream.read();

      Assertions.assertEquals("mf920901-001-p1s1A", sent4.getSentenceIdComment());
      Assertions.assertEquals(
          "Slovenská ústava: pro i proti",
          sent4.getTextComment());
      Assertions.assertEquals(6, sent4.getWordLines().size());
      Assertions.assertTrue(sent4.isNewDocument());
      Assertions.assertTrue(sent4.isNewParagraph());
      Assertions.assertEquals(Optional.of("mf920901-001"), sent4.getDocumentId());
      Assertions.assertEquals(Optional.of("mf920901-001-p1"), sent4.getParagraphId());
      Assertions.assertEquals(Optional.of(Collections.singletonMap(new Locale("en"),
              "Slovak constitution: pros and cons"))
          , sent4.getTextLang());

      Assertions.assertNull(stream.read(), "Stream must be exhausted");
    }
  }
}
