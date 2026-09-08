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

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.formats.ad.ADSentenceStream.Sentence;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Leaf;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.TreeElement;

public class ADSentenceStreamTest {

  private static Stream<Arguments> nodeLines() {
    return Stream.of(
        Arguments.of("STA:fcl", 1, "STA:fcl"),
        Arguments.of("=PIV:pp", 2, "PIV:pp"),
        Arguments.of("==P<:np", 3, "P<:np"),
        Arguments.of("===>N:adjp", 4, ">N:adjp"),
        // an optional part in parentheses and tag groups may follow the tag
        Arguments.of("=X:y(z)", 2, "X:y"),
        Arguments.of("=X:y (<a>)", 2, "X:y"),
        Arguments.of("=X:y(z) (<a>)(<b>) ", 2, "X:y"),
        Arguments.of("==P<:np(<x>)", 3, "P<:np"),
        // a leaf line followed by a tag group, or without a lexeme, is a node line
        Arguments.of("=H:n(\"casa\" M S) (<x>)", 2, "H:n"),
        Arguments.of("=H:n(\"casa\" M S)", 2, "H:n"),
        Arguments.of("=H:n(\"casa\" M S) ", 2, "H:n"),
        // hyphens count as level, but the last one joins the tag when a colon follows it
        Arguments.of("=-X:y", 3, "X:y"),
        Arguments.of("--:y", 2, "-:y"),
        Arguments.of("-:y", 1, "-:y"));
  }

  @ParameterizedTest
  @MethodSource("nodeLines")
  void testNodeLines(String line, int level, String syntacticTag) {
    TreeElement element = new SentenceParser().getElement(line);
    Assertions.assertNotNull(element);
    Assertions.assertFalse(element.isLeaf());
    Assertions.assertEquals(level, element.getLevel());
    Assertions.assertEquals(syntacticTag, element.getSyntacticTag());
  }

  private static Stream<Arguments> leafLines() {
    return Stream.of(
        Arguments.of("=P:v-fin(\"iniciar\" <fmc> <mv> PR 3S IND VFIN)\tInicia",
            2, "P", "v-fin", "iniciar", "<fmc> <mv>", "PR 3S IND VFIN", "Inicia"),
        Arguments.of("===H:n(\"av.\" <np-idf> <cjt-acc> <right> M S)\tAv.",
            4, "H", "n", "av.", "<np-idf> <cjt-acc> <right>", "M S", "Av."),
        Arguments.of("===N<:num(\"6\" <NER:date> <card> <np-close> M P)\t6",
            4, "N<", "num", "6", "<NER:date> <card> <np-close>", "M P", "6"),
        // no morphological tag
        Arguments.of("==H:prp(\"em\" <sam-> <right>)\tem",
            3, "H", "prp", "em", "<sam-> <right>", null, "em"),
        Arguments.of("SUB:conj-s(\"que\" <clb-fs>)\tque",
            1, "SUB", "conj-s", "que", "<clb-fs>", null, "que"),
        // no secondary tags
        Arguments.of("=P:v-fin(\"iniciar\" PR 3S IND VFIN)\tInicia",
            2, "P", "v-fin", "iniciar", "", "PR 3S IND VFIN", "Inicia"),
        Arguments.of("=H:n('casa')  casa", 2, "H", "n", "casa", "", null, "casa"),
        // quotes inside lemma and lexeme
        Arguments.of("=H:n(\"d'água\" <x> M S)\td'água",
            2, "H", "n", "d'água", "<x>", "M S", "d'água"),
        // the lemma extends to the last quote after which the rest of the line still parses
        Arguments.of("=H:n(\"a\" <b> \"c\")\tw", 2, "H", "n", "a\" <b> \"c", "", null, "w"),
        Arguments.of("=H:n(\"x\" M S)\ta') b", 2, "H", "n", "x\" M S)\ta", "", null, "b"),
        // the secondary tags extend to the last closing angle bracket
        Arguments.of("=H:n(\"casa\" <a>b<c> M S)\tcasa",
            2, "H", "n", "casa", "<a>b<c>", "M S", "casa"),
        Arguments.of("=H:n(\"casa\" <a) b> M S)\tcasa",
            2, "H", "n", "casa", "<a) b>", "M S", "casa"),
        // the last of trailing whitespace characters is the lexeme
        Arguments.of("=H:n(\"a)\" M S)  ", 2, "H", "n", "a)", "", "M S", " "));
  }

  @ParameterizedTest
  @MethodSource("leafLines")
  void testLeafLines(String line, int level, String syntacticTag, String functionalTag,
                     String lemma, String secondaryTag, String morphologicalTag, String lexeme) {
    TreeElement element = new SentenceParser().getElement(line);
    Assertions.assertNotNull(element);
    Assertions.assertTrue(element.isLeaf());
    Leaf leaf = (Leaf) element;
    Assertions.assertEquals(level, leaf.getLevel());
    Assertions.assertEquals(syntacticTag, leaf.getSyntacticTag());
    Assertions.assertEquals(functionalTag, leaf.getFunctionalTag());
    Assertions.assertEquals(lemma, leaf.getLemma());
    Assertions.assertEquals(secondaryTag, leaf.getSecondaryTag());
    Assertions.assertEquals(morphologicalTag, leaf.getMorphologicalTag());
    Assertions.assertEquals(lexeme, leaf.getLexeme());
  }

  private static Stream<Arguments> bizarreLeafLines() {
    return Stream.of(
        Arguments.of("=x=y(\"q\" a) b", 2, "x=y", "q", "a", "b"),
        Arguments.of("=x=y('q')\tb", 2, "x=y", "q", null, "b"),
        Arguments.of("=x=y(a b) c", 2, "x=y", null, "a b", "c"),
        Arguments.of("=x=y() b", 2, "x=y", null, null, "b"),
        // a quoted part without a closing quote is the morphological tag
        Arguments.of("=x=y(\"q) b", 2, "x=y", null, "\"q", "b"),
        // the level prefix gives up hyphens so that the tag can start
        Arguments.of("==-=x(a) b", 3, "-=x", null, "a", "b"),
        Arguments.of("=-=x=y(a) b", 4, "x=y", null, "a", "b"));
  }

  @ParameterizedTest
  @MethodSource("bizarreLeafLines")
  void testBizarreLeafLines(String line, int level, String syntacticTag, String lemma,
                            String morphologicalTag, String lexeme) {
    TreeElement element = new SentenceParser().getElement(line);
    Assertions.assertNotNull(element);
    Assertions.assertTrue(element.isLeaf());
    Leaf leaf = (Leaf) element;
    Assertions.assertEquals(level, leaf.getLevel());
    Assertions.assertEquals(syntacticTag, leaf.getSyntacticTag());
    Assertions.assertNull(leaf.getFunctionalTag());
    Assertions.assertEquals(lemma, leaf.getLemma());
    Assertions.assertNull(leaf.getSecondaryTag());
    Assertions.assertEquals(morphologicalTag, leaf.getMorphologicalTag());
    Assertions.assertEquals(lexeme, leaf.getLexeme());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
      // no whitespace between the closing parenthesis and the lexeme: not a leaf line
      "=H:n(\"casa\" M S)casa|2|:n(\"casa\" M S)casa",
      // an empty lemma
      "=H:n(\"\" M S) casa|2|:n(\"\" M S) casa",
      "=ab|2|b",
      "=a.b|2|.b",
      "===x|4|''"
  })
  void testFallbackLeafLines(String line, int level, String lexeme) {
    TreeElement element = new SentenceParser().getElement(line);
    Assertions.assertNotNull(element);
    Assertions.assertTrue(element.isLeaf());
    Leaf leaf = (Leaf) element;
    Assertions.assertEquals(level, leaf.getLevel());
    Assertions.assertEquals("", leaf.getSyntacticTag());
    Assertions.assertEquals("", leaf.getFunctionalTag());
    Assertions.assertEquals("", leaf.getMorphologicalTag());
    Assertions.assertNull(leaf.getLemma());
    Assertions.assertEquals(lexeme, leaf.getLexeme());
  }

  @ParameterizedTest
  @ValueSource(strings = {"_", "<lixo>", "pause", "=ab.", "=xa<b", "=x1>y", "=a_b.c"})
  void testIgnoredLines(String line) {
    Assertions.assertNull(new SentenceParser().getElement(line));
  }

  @Test
  void testUnparsableLineIsLexeme() {
    TreeElement element = new SentenceParser().getElement("random text");
    Assertions.assertTrue(element.isLeaf());
    Leaf leaf = (Leaf) element;
    Assertions.assertEquals(1, leaf.getLevel());
    Assertions.assertEquals("", leaf.getSyntacticTag());
    Assertions.assertEquals("random text", leaf.getLexeme());
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

  @ParameterizedTest
  @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
      "<s>|s|true",
      "<s id=\"63955\" ref=\"1001.porto-poesia-2\" source=\"SELVA 1001\">|s|true",
      "<sx>|s|true",
      "<ext id=\"1001.porto-poesia\">|ext|true",
      "<caixa>|caixa|true",
      "<p par=\"1\">|p|true",
      "<t>|t|true",
      "<s|s|false",
      "<s>>|s|false",
      "<s> |s|false",
      " <s>|s|false",
      "<s>x</s>|s|false",
      "</s>|s|false",
      "<p>|s|false",
      "<caixa>|s|false",
      "<>|s|false",
      "''|s|false",
      "<ext>|t|false",
      "<t>|ext|false",
      "< s>|s|false"
  })
  void testIsOpeningTag(String line, String name, boolean expected) {
    Assertions.assertEquals(expected, ADSentenceStream.isOpeningTag(line, name));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
      "</s>|s|true",
      "</ext>|ext|true",
      "</t>|t|true",
      "</caixa>|caixa|true",
      "</s> |s|false",
      " </s>|s|false",
      "</s|s|false",
      "</sx>|s|false",
      "</t>|s|false",
      "<s>|s|false",
      "</s>>|s|false",
      "</ext>|t|false",
      "''|s|false",
      "</ s>|s|false"
  })
  void testIsClosingTag(String line, String name, boolean expected) {
    Assertions.assertEquals(expected, ADSentenceStream.isClosingTag(line, name));
  }
}
