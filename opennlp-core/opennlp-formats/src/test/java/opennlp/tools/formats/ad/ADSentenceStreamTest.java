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

package opennlp.tools.formats.ad;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.formats.ad.ADSentenceStream.Sentence;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Leaf;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.TreeElement;
import opennlp.tools.util.ObjectStreamUtils;

public class ADSentenceStreamTest {

  private static final String SOURCE = "SOURCE: ref=\"1\" source=\"SELVA 1\"";
  private static final String FIRST_TEXT = "1001 Inicia a poesia.";
  private static final String[] FIRST_TREE = {"A1", "STA:fcl",
      "=P:v-fin(\"iniciar\" PR 3S IND VFIN)\tInicia", "=SUBJ:np", "==>N:art(\"o\" DET F S)\ta",
      "==H:n(\"poesia\" F S)\tpoesia", "=PU:pu(\".\" PU)\t."};
  private static final String SECOND_TEXT = "1002 Toma porto.";
  private static final String[] SECOND_TREE = {"A1", "STA:fcl",
      "=P:v-fin(\"tomar\" PR 3S IND VFIN)\tToma", "=ACC:n(\"porto\" M S)\tporto",
      "=PU:pu(\".\" PU)\t."};

  private static String[] lines(String... groups) {
    return String.join("\n", groups).split("\n", -1);
  }

  private static String join(String[] tree) {
    return String.join("\n", tree);
  }

  private static ADSentenceStream stream(String... lines) {
    return new ADSentenceStream(ObjectStreamUtils.createObjectStream(lines));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\n", "<p>\n</p>", "<ext id=\"1\">\n</ext>"})
  void testInputWithoutSentenceGivesNoSentence(String input) throws IOException {
    try (ADSentenceStream stream = stream(input.split("\n", -1))) {
      Assertions.assertNull(stream.read());
    }
  }

  /**
   * A file that ends after an opening sentence tag has no sentence to return; the stream must
   * report the end of the input rather than read the end of file again and again.
   */
  @ParameterizedTest
  @ValueSource(strings = {"<s>", "<s id=\"1\">", "<p>\n<s id=\"1\">", "<s>\n</s>\n<s>"})
  @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void testOpenSentenceAtEndOfFileGivesNoSentence(String input) throws IOException {
    try (ADSentenceStream stream = stream(input.split("\n", -1))) {
      Assertions.assertNull(stream.read());
      Assertions.assertNull(stream.read());
    }
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void testOpenSentenceAfterCompleteSentenceEndsTheStream() throws IOException {
    try (ADSentenceStream stream = stream(lines("<s id=\"1\">", SOURCE, FIRST_TEXT,
        join(FIRST_TREE), "</s>", "<s id=\"2\">"))) {
      ADSentenceStream.Sentence first = stream.read();
      Assertions.assertNotNull(first);
      Assertions.assertEquals("Inicia a poesia.", first.text());
      Assertions.assertNull(stream.read());
    }
  }

  /** A sentence cut off before its closing tag is returned with the content read so far. */
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void testTruncatedSentenceWithContentIsReturned() throws IOException {
    try (ADSentenceStream stream = stream(lines("<s id=\"1\">", SOURCE, FIRST_TEXT,
        join(FIRST_TREE)))) {
      ADSentenceStream.Sentence sentence = stream.read();
      Assertions.assertNotNull(sentence);
      Assertions.assertEquals("Inicia a poesia.", sentence.text());
      Assertions.assertEquals(3, elementsOfClause(sentence));
      Assertions.assertNull(stream.read());
    }
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void testTwoSentencesAreRead() throws IOException {
    try (ADSentenceStream stream = stream(lines("<s id=\"1\">", SOURCE, FIRST_TEXT,
        join(FIRST_TREE), "</s>", "<s id=\"2\">", SOURCE, SECOND_TEXT, join(SECOND_TREE),
        "</s>"))) {
      ADSentenceStream.Sentence first = stream.read();
      ADSentenceStream.Sentence second = stream.read();
      Assertions.assertNotNull(first);
      Assertions.assertNotNull(second);
      Assertions.assertEquals("Inicia a poesia.", first.text());
      Assertions.assertEquals("Toma porto.", second.text());
      Assertions.assertEquals(3, elementsOfClause(second));
      Assertions.assertNull(stream.read());
    }
  }

  /** The number of elements of the clause node under the root. */
  private static int elementsOfClause(ADSentenceStream.Sentence sentence) {
    ADSentenceStream.SentenceParser.TreeElement[] top = sentence.root().getElements();
    Assertions.assertEquals(1, top.length);
    return ((ADSentenceStream.SentenceParser.Node) top[0]).getElements().length;
  }


  @Test
  void testPunctuationLeaf() {
    SentenceParser parser = new SentenceParser();

    Leaf leaf = (Leaf) parser.getElement("==,");
    Assertions.assertEquals(3, leaf.getLevel());
    Assertions.assertEquals(",", leaf.getLexeme());

    leaf = (Leaf) parser.getElement(".");
    Assertions.assertEquals(1, leaf.getLevel());
    Assertions.assertEquals(".", leaf.getLexeme());

    // a line of only equals signs matches, with the last one as lexeme
    leaf = (Leaf) parser.getElement("===");
    Assertions.assertEquals(3, leaf.getLevel());
    Assertions.assertEquals("=", leaf.getLexeme());

    // non-word characters other than equals make up the lexeme
    leaf = (Leaf) parser.getElement("=!?");
    Assertions.assertEquals(2, leaf.getLevel());
    Assertions.assertEquals("!?", leaf.getLexeme());

    // a word character excludes the punctuation parse, the line is treated
    // as a bizarre leaf instead
    TreeElement element = parser.getElement("=ab");
    Assertions.assertTrue(element.isLeaf());
    leaf = (Leaf) element;
    Assertions.assertEquals(2, leaf.getLevel());
    Assertions.assertEquals("b", leaf.getLexeme());
  }

  @Test
  void testFixPunctuation() {
    SentenceParser parser = new SentenceParser();

    Sentence sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá mundo » .\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá mundo ».", sentence.text());

    sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá » , tudo bem » .\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá », tudo bem ».", sentence.text());

    // without whitespace between » and the punctuation nothing is replaced
    sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá mundo ».\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá mundo ».", sentence.text());
  }
}
