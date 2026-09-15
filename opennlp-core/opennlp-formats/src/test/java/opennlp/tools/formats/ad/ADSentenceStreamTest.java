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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.formats.ad.ADSentenceStream.Sentence;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Leaf;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Node;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.TreeElement;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.PlainTextByLineStream;

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
        // a parenthesis inside the tag group content does not end the node tail
        Arguments.of("=X:y(z) (<a(b>)", 2, "X:y"),
        // tag groups may follow the tag with only whitespace between
        Arguments.of("=X:y (<a>)(<b>)", 2, "X:y"),
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
        // any whitespace separates the closing parenthesis from the lexeme
        Arguments.of("=H:n(\"casa\" M S)\u00A0casa", 2, "H", "n", "casa", "", "M S", "casa"),
        Arguments.of("=H:n(\"casa\" M S)\u3000 casa", 2, "H", "n", "casa", "", "M S", "casa"),
        // a line separator inside the line is an ordinary character
        Arguments.of("=H:n(\"a\u2028b\" <x\u2028y> M S)\tc\u2028d",
            2, "H", "n", "a\u2028b", "<x\u2028y>", "M S", "c\u2028d"),
        // supplementary-plane characters in lemma and lexeme
        Arguments.of("=H:n(\"\uD83D\uDE00\" M S)\t\uD83D\uDE00",
            2, "H", "n", "\uD83D\uDE00", "", "M S", "\uD83D\uDE00"));
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
        Arguments.of("=-=x=y(a) b", 4, "x=y", null, "a", "b"),
        // any whitespace separates the closing parenthesis from the lexeme
        Arguments.of("=x=y(a)\u00A0b", 2, "x=y", null, "a", "b"));
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
      "===x|4|''",
      // whitespace only after the closing parenthesis is no lexeme
      "=H:n(\"a)\" M S)  |2|:n(\"a)\" M S)  ",
      "=x=y(a)  |4|(a)  ",
      "=x=y(\"q\")\t|4|(\"q\")\t"
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
  @ValueSource(strings = {"_", "<lixo>", "pause", "=ab.", "=xa<b", "=x1>y", "=a_b.c",
      // the word may start with a letter or digit of any script
      "=ção.", "=Ünïcode<x>", "=١٢.", "=ab\u2028."})
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

  private static Stream<Arguments> punctuationLines() {
    return Stream.of(
        Arguments.of(".", 1, "."),
        Arguments.of("==,", 3, ","),
        Arguments.of("=!?", 2, "!?"),
        Arguments.of("===", 3, "="),
        Arguments.of("=", 1, "="),
        Arguments.of("=,=", 2, ",="),
        // guillemets, an ellipsis, an em dash, and a vulgar fraction are all punctuation
        Arguments.of("=\u00AB", 2, "\u00AB"),
        Arguments.of("=\u00BB", 2, "\u00BB"),
        Arguments.of("=\u2026", 2, "\u2026"),
        Arguments.of("=\u2014", 2, "\u2014"),
        Arguments.of("=\u00BD", 2, "\u00BD"),
        // whitespace is not a word character either
        Arguments.of("= .", 2, " ."),
        Arguments.of("=\uD83D\uDE00", 2, "\uD83D\uDE00"));
  }

  @ParameterizedTest
  @MethodSource("punctuationLines")
  void testParsePunctuationLine(String line, int level, String lexeme) {
    String[] parsed = SentenceParser.parsePunctuationLine(line);
    Assertions.assertNotNull(parsed);
    Assertions.assertEquals(String.valueOf(level), parsed[0]);
    Assertions.assertEquals(lexeme, parsed[1]);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "=a", "=A", "=1", "=_", "=.a", "a.",
      // letters and digits from any script are word characters, not punctuation
      "=\u00E9", "=\u00C9", "=\u0661", "=\u65E5", "=\uD801\uDC12", "=\uD835\uDFCE"})
  void testParsePunctuationLineRejectsWords(String line) {
    Assertions.assertNull(SentenceParser.parsePunctuationLine(line));
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

    // tabs and longer runs are ASCII whitespace too, and collapse the same way
    sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá mundo »\t.\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá mundo ».", sentence.text());

    sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá mundo »   ,\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá mundo »,", sentence.text());

    // a no-break space between » and the punctuation is whitespace too
    sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá mundo »\u00A0.\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá mundo ».", sentence.text());

    // other punctuation after » is left alone, and so is a » at the end
    sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá mundo » ! Fim »\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá mundo » ! Fim »", sentence.text());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
      "<s>|s|true",
      "<s id=\"63955\" ref=\"1001.porto-poesia-2\" source=\"SELVA 1001\">|s|true",
      "<s\tid=\"1\">|s|true",
      "<s\u00A0id=\"1\">|s|true",
      "<ext id=\"1001.porto-poesia\">|ext|true",
      "<caixa>|caixa|true",
      "<p par=\"1\">|p|true",
      "<t>|t|true",
      "<s|s|false",
      "<s>>|s|false",
      "<s> |s|false",
      " <s>|s|false",
      // the name must be followed by whitespace or the closing bracket
      "<sx>|s|false",
      "<sid=\"1\">|s|false",
      "<ss>|s|false",
      // a bracket inside a quoted attribute closes the tag
      "<s x=\">\">|s|false",
      "<s>x</s>|s|false",
      "</s>|s|false",
      "<p>|s|false",
      "<caixa>|s|false",
      "<>|s|false",
      "''|s|false",
      "<ext>|t|false",
      "<t>|ext|false",
      "< s>|s|false",
      // a name that another name starts with is its own tag
      "<text>|t|false",
      "<text id=\"1\">|t|false",
      "<tt>|t|false",
      "<t>|text|false",
      "<t\u2003id=\"1\">|t|true",
      "<t\u3000>|t|true",
      // an opening bracket inside the attributes is ordinary text
      "<s id=\"<\">|s|true",
      "<s<|s|false",
      "<<s>>|s|false",
      "<s/>|s|false"
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
      "</ s>|s|false",
      "</text>|t|false",
      "</t>|text|false",
      "</t >|t|false"
  })
  void testIsClosingTag(String line, String name, boolean expected) {
    Assertions.assertEquals(expected, ADSentenceStream.isClosingTag(line, name));
  }

  @Test
  void testTrailingCarriageReturnIsNotATag() {
    Assertions.assertFalse(ADSentenceStream.isOpeningTag("<s>\r", "s"));
    Assertions.assertFalse(ADSentenceStream.isClosingTag("</s>\r", "s"));
  }

  private static Stream<Arguments> leafLinesWithMarkupInside() {
    return Stream.of(
        // nested and doubled angle brackets extend the secondary tags to the last bracket
        Arguments.of("=H:n(\"a\" <x<y>> M S)\ta", "a", "<x<y>>", "M S", "a"),
        Arguments.of("=H:n(\"a\" <<x>> M S)\ta", "a", "<<x>>", "M S", "a"),
        // an unbalanced bracket is part of the morphological tag
        Arguments.of("=H:n(\"a\" <x> <y M S)\ta", "a", "<x>", "<y M S", "a"),
        Arguments.of("=H:n(\"a\" x> M S)\ta", "a", "", "x> M S", "a"),
        // angle brackets in the lemma and the lexeme are ordinary characters
        Arguments.of("=H:n(\"a<b\" M S)\ta<b", "a<b", "", "M S", "a<b"),
        Arguments.of("=H:n(\"a\" M S)\t<a>b", "a", "", "M S", "<a>b"),
        // UTF-8 bytes read as ISO-8859-1, and ISO-8859-1 bytes read as UTF-8
        Arguments.of("=H:n(\"\u00C3\u00A7\u00C3\u00A3o\" M S)\t\u00C3\u00A7\u00C3\u00A3o",
            "\u00C3\u00A7\u00C3\u00A3o", "", "M S", "\u00C3\u00A7\u00C3\u00A3o"),
        Arguments.of("=H:n(\"\uFFFD\uFFFDo\" M S)\t\uFFFD\uFFFDo",
            "\uFFFD\uFFFDo", "", "M S", "\uFFFD\uFFFDo"),
        // tabs and other whitespace separate the parts inside the parentheses
        Arguments.of("=H:n(\"a\"\t<x>\t<y>\tM S)\ta", "a", "<x>\t<y>", "M S", "a"),
        Arguments.of("=H:n(\"a\"\u00A0<x>\u00A0M S)\u00A0a", "a", "<x>", "M S", "a"),
        Arguments.of("=H:n(\"a\" <x> M\tS)\ta", "a", "<x>", "M\tS", "a"),
        Arguments.of("=H:n(\"a\" <x>\u3000)\ta", "a", "<x>", null, "a"));
  }

  @ParameterizedTest
  @MethodSource("leafLinesWithMarkupInside")
  void testLeafLinesWithMarkupInside(String line, String lemma, String secondaryTag,
                                     String morphologicalTag, String lexeme) {
    TreeElement element = new SentenceParser().getElement(line);
    Assertions.assertNotNull(element);
    Assertions.assertTrue(element.isLeaf());
    Leaf leaf = (Leaf) element;
    Assertions.assertEquals(2, leaf.getLevel());
    Assertions.assertEquals("H", leaf.getSyntacticTag());
    Assertions.assertEquals("n", leaf.getFunctionalTag());
    Assertions.assertEquals(lemma, leaf.getLemma());
    Assertions.assertEquals(secondaryTag, leaf.getSecondaryTag());
    Assertions.assertEquals(morphologicalTag, leaf.getMorphologicalTag());
    Assertions.assertEquals(lexeme, leaf.getLexeme());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
      "=X:y\u00A0(<a>)|2|X:y",
      "=X:y\t(<a>)\t|2|X:y",
      "=X:y (<a<b>>)|2|X:y",
      "=X:y(z)\u3000(<a>)|2|X:y"
  })
  void testNodeLinesWithWhitespaceAndNestedMarkup(String line, int level, String syntacticTag) {
    TreeElement element = new SentenceParser().getElement(line);
    Assertions.assertNotNull(element);
    Assertions.assertFalse(element.isLeaf());
    Assertions.assertEquals(level, element.getLevel());
    Assertions.assertEquals(syntacticTag, element.getSyntacticTag());
  }

  @ParameterizedTest
  @ValueSource(strings = {"=a<b<c>>", "=a<<", "=a>>", "=a<", "=a>", "=a<b>c", "=1<", "=_>",
      "=a<b.c>", "==ab>", "=ab\t<c>",
      // a word in either mojibake form is a word
      "=\u00C3\u00A7\u00C3\u00A3o.", "=\u00C3\u00A7<x>"})
  void testWordsWithUnbalancedOrNestedMarkupAreIgnored(String line) {
    Assertions.assertNull(new SentenceParser().getElement(line));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
      "=a|2|a",
      "=a-b|2|a-b",
      "=a b|2|a b",
      "=1x|2|1x",
      "=_x|2|_x",
      "=ab,|2|ab,",
      "==a|3|a",
      "=\u00C3\u00A7\u00C3\u00A3o|2|\u00C3\u00A7\u00C3\u00A3o",
      "=\uFFFDx|2|\uFFFDx"
  })
  void testFallbackLeafKeepsTheWholeWord(String line, int level, String lexeme) {
    TreeElement element = new SentenceParser().getElement(line);
    Assertions.assertNotNull(element);
    Assertions.assertTrue(element.isLeaf());
    Leaf leaf = (Leaf) element;
    Assertions.assertEquals(level, leaf.getLevel());
    Assertions.assertEquals("", leaf.getSyntacticTag());
    Assertions.assertEquals(lexeme, leaf.getLexeme());
  }

  private static final List<String> CORPUS_LINES = List.of(
      "<ext id=\"1001\" cat=\"x\">",
      "<p par=\"1\">",
      "<s id=\"1\">",
      "SOURCE: src",
      "CF1001-1 Olá mundo .",
      "STA:fcl",
      "=P:v-fin(\"olá\" PR 3S IND VFIN)\tOlá",
      "=H:n(\"mundo\" M S)\tmundo",
      "=.",
      "</s>",
      "<s id=\"2\">",
      "SOURCE: src",
      "CF1001-2 Adeus .",
      "STA:fcl",
      "=P:v-fin(\"adeus\" IMP)\tAdeus",
      "=.",
      "</s>",
      "</p>",
      "</ext>");

  private static List<Sentence> readSentences(byte[] corpus, Charset charset) throws IOException {
    List<Sentence> sentences = new ArrayList<>();
    try (ObjectStream<String> lines = new PlainTextByLineStream(
        () -> new ByteArrayInputStream(corpus), charset);
         ADSentenceStream stream = new ADSentenceStream(lines)) {
      Sentence sentence;
      while ((sentence = stream.read()) != null) {
        sentences.add(sentence);
      }
    }
    return sentences;
  }

  private static List<Sentence> readSentences(String corpus) throws IOException {
    return readSentences(corpus.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
  }

  private static List<String> lexemes(Sentence sentence) {
    List<String> lexemes = new ArrayList<>();
    for (TreeElement element : sentence.root().getElements()) {
      for (TreeElement child : ((Node) element).getElements()) {
        lexemes.add(((Leaf) child).getLexeme());
      }
    }
    return lexemes;
  }

  private static void assertCorpusSentences(List<Sentence> sentences) {
    Assertions.assertEquals(2, sentences.size());
    Assertions.assertEquals("Olá mundo .", sentences.get(0).text());
    Assertions.assertEquals("CF1001-1 p=1 src", sentences.get(0).metadata());
    Assertions.assertEquals(List.of("Olá", "mundo", "."), lexemes(sentences.get(0)));
    Assertions.assertEquals("Adeus .", sentences.get(1).text());
    Assertions.assertEquals("CF1001-2 p=1 src", sentences.get(1).metadata());
    Assertions.assertEquals(List.of("Adeus", "."), lexemes(sentences.get(1)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", "\r"})
  void testLineEndingsGiveTheSameSentences(String lineEnding) throws IOException {
    List<Sentence> sentences = readSentences(String.join(lineEnding, CORPUS_LINES) + lineEnding);
    assertCorpusSentences(sentences);
    List<Sentence> withLineFeeds = readSentences(String.join("\n", CORPUS_LINES) + "\n");
    for (int i = 0; i < sentences.size(); i++) {
      Assertions.assertEquals(withLineFeeds.get(i).root().toString(),
          sentences.get(i).root().toString());
    }
  }

  @Test
  void testEmptyLinesBetweenSentencesAreSkipped() throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("");
    boolean inSentence = false;
    for (String line : CORPUS_LINES) {
      if (line.startsWith("<s ")) {
        inSentence = true;
      }
      lines.add(line);
      if (line.equals("</s>")) {
        inSentence = false;
      }
      if (!inSentence) {
        lines.add("");
      }
    }
    assertCorpusSentences(readSentences(String.join("\n", lines)));
  }

  @Test
  void testSentenceAtEndOfInputWithoutClosingTag() throws IOException {
    List<String> lines = CORPUS_LINES.subList(0, CORPUS_LINES.indexOf("</s>"));
    List<Sentence> sentences = readSentences(String.join("\n", lines));
    Assertions.assertEquals(1, sentences.size());
    Assertions.assertEquals("Olá mundo .", sentences.get(0).text());
    Assertions.assertEquals(List.of("Olá", "mundo", "."), lexemes(sentences.get(0)));
  }

  @Test
  void testLatin1BytesReadAsUtf8() throws IOException {
    byte[] corpus = (String.join("\n", CORPUS_LINES) + "\n").getBytes(StandardCharsets.ISO_8859_1);
    List<Sentence> sentences = readSentences(corpus, StandardCharsets.UTF_8);
    String ola = new String("Olá".getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    Assertions.assertTrue(ola.contains("\uFFFD"));
    Assertions.assertEquals(2, sentences.size());
    Assertions.assertEquals(ola + " mundo .", sentences.get(0).text());
    Assertions.assertEquals(List.of(ola, "mundo", "."), lexemes(sentences.get(0)));
    Assertions.assertEquals(List.of("Adeus", "."), lexemes(sentences.get(1)));
  }

  @Test
  void testUtf8BytesReadAsLatin1() throws IOException {
    byte[] corpus = (String.join("\n", CORPUS_LINES) + "\n").getBytes(StandardCharsets.UTF_8);
    List<Sentence> sentences = readSentences(corpus, StandardCharsets.ISO_8859_1);
    String ola = new String("Olá".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    Assertions.assertEquals("Ol\u00C3¡", ola);
    Assertions.assertEquals(2, sentences.size());
    Assertions.assertEquals(ola + " mundo .", sentences.get(0).text());
    Assertions.assertEquals(List.of(ola, "mundo", "."), lexemes(sentences.get(0)));
  }
}
