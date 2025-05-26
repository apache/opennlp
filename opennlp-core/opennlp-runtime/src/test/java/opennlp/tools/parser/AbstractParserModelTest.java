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

package opennlp.tools.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;

/**
 * Common test class for {@link ParserModel}-driven test cases.
 */
public abstract class AbstractParserModelTest {

  /**
   * @return Retrieves a valid {@link ParserModel}, either trained or loaded.
   */
  protected abstract ParserModel getModel();

  /**
   * Verifies that serialization of {@link ParserModel} equals trained state.
   * <p>
   * Tests {@link ParserModel#equals(Object)}.
   */
  @Test
  void testModelSerializationAndEquality() throws IOException {
    Assertions.assertNotNull(getModel());
    Assertions.assertFalse(getModel().isLoadedFromSerialized());

    // Test serializing and de-serializing model
    ByteArrayOutputStream outArray = new ByteArrayOutputStream();
    getModel().serialize(outArray);
    outArray.close();

    // TEST: de-serialization and equality
    ParserModel outputModel = new ParserModel(new ByteArrayInputStream(outArray.toByteArray()));
    Assertions.assertNotNull(outputModel);
    Assertions.assertTrue(outputModel.isLoadedFromSerialized());
    Assertions.assertEquals(getModel(), outputModel);
  }

  /**
   * Verifies that parsing with a {@link ParserModel} does not cause problems at runtime.
   */
  @ParameterizedTest(name = "Parse example {index}.")
  @MethodSource("provideParsePairs")
  void testParsing(String input, String reference) {
    // prepare
    Assertions.assertNotNull(getModel());
    Parse p = Parse.parseParse(input);
    Assertions.assertNotNull(p);
    Assertions.assertTrue(p.complete());
    Assertions.assertEquals(reference, p.getText());
    opennlp.tools.parser.Parser parser = ParserFactory.create(getModel());
    Assertions.assertNotNull(parser);

    // TEST: parsing
    Parse parsedViaParser = parser.parse(p);
    Assertions.assertNotNull(parsedViaParser);
    Assertions.assertTrue(parsedViaParser.complete());
    Assertions.assertEquals(reference, p.getText());
    Span s = parsedViaParser.getSpan();
    Assertions.assertNotNull(s);
  }

  /*
   * Verifies / addresses OPENNLP-509
   * See: https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-509
   */
  @Test
  void testParsingCheckParentReferencesArePopulated() {
    // fixtures
    final String sent = "Martin is testing.";
    // prepare
    List<String> tokens = Arrays.asList(WhitespaceTokenizer.INSTANCE.tokenize(sent));
    String text = String.join(" ", tokens);

    Parse sentP = new Parse(text, new Span(0, text.length()),
            AbstractBottomUpParser.INC_NODE, 0, null);
    int start = 0;
    int i = 0;
    for (Iterator<String> ti = tokens.iterator(); ti.hasNext(); i++) {
      String tok = ti.next();
      sentP.insert(new Parse(text, new Span(start, start + tok.length()),
              AbstractBottomUpParser.TOK_NODE, 0, i));
      start += tok.length() + 1;
    }

    Parser parser = ParserFactory.create(getModel());
    Assertions.assertNotNull(parser);

    // Verifies parents of top-k parses (k=2)
    Parse[] parses = parser.parse(sentP, 2);
    Assertions.assertNotNull(parses);
    for (Parse parent : parses) {
      checkParentsEqual(parent);
    }
  }

  /*
   * Recursively traverses the parse tree and verifies parent references are populated.
   */
  private void checkParentsEqual(Parse parent) {
    for (Parse child : parent.getChildren()) {
      Parse cParent = child.getParent();
      // System.out.println(cParent.toStringPennTreebank() " --- type: " cParent.getType());
      if (AbstractBottomUpParser.TOK_NODE.equals(child.getType())) {
        return; // found a leaf node: stopping recursion
      }
      Assertions.assertEquals(parent, cParent);
      if (cParent.getChildren() != null) {
        checkParentsEqual(child);
      }
    }
  }

  /*
   * Verifies changes in OPENNLP-1330 and addresses follow-up OPENNLP-1333
   * See: https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-1333
   *
   * Uses test data from PR 392 (https://github.com/apache/opennlp/pull/392).
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3})
  void testParsingTopParses(int k) {
    // fixtures
    final String sent = "Eric is testing.";
    final String refParseTopChunking =
            "(TOP (S (NP (NNP Eric)) (VP (VBZ is) (NP (DT testing.)))))";
    final String refParseTopTreeInsert =
            "(TOP (S (NP (NNP Eric)) (VP (VBZ is) (NN testing.))))";

    // prepare
    List<String> tokens = Arrays.asList(WhitespaceTokenizer.INSTANCE.tokenize(sent));
    String text = String.join(" ", tokens);

    Parse sentP = new Parse(text, new Span(0, text.length()),
            AbstractBottomUpParser.INC_NODE, 0, 0);
    int start = 0;
    int i = 0;
    for (Iterator<String> ti = tokens.iterator(); ti.hasNext(); i++) {
      String tok = ti.next();
      sentP.insert(new Parse(text, new Span(start, start + tok.length()),
              AbstractBottomUpParser.TOK_NODE, 0, i));
      start += tok.length() + 1;
    }

    opennlp.tools.parser.Parser parser = ParserFactory.create(getModel());
    Assertions.assertNotNull(parser);

    // TEST: parsing
    Parse[] parses = parser.parse(sentP, k);
    Assertions.assertNotNull(parses);
    Assertions.assertEquals(k, parses.length);
    double previousProb = 0; // initial ref value
    for (int j = 0; j < parses.length; j++) {
      Assertions.assertTrue(parses[j].getProb() < previousProb);
      String asPennTreebankStyle = parses[j].toStringPennTreebank();
      // System.out.println(parses[j].getProb() + " - " + asPennTreebankStyle);
      if (j == 0) {
        if (ParserType.CHUNKING.equals(getModel().getParserType())) {
          Assertions.assertEquals(refParseTopChunking, asPennTreebankStyle);
        } else if (ParserType.TREEINSERT.equals(getModel().getParserType())) {
          Assertions.assertEquals(refParseTopTreeInsert, asPennTreebankStyle);
        }
      }
    }
  }

  /*
   * Produces a stream of <parse|text> pairs for parameterized unit tests.
   */
  private static Stream<Arguments> provideParsePairs() {
    return Stream.of(
            // Example 1: with eos character
            Arguments.of("(TOP  "
                        + "(S (S (NP-SBJ (PRP She)  )(VP (VBD was)  "
                        + "(ADVP (RB just)  )(NP-PRD (NP (DT another)  (NN freighter)  )"
                        + "(PP (IN from)  (NP (DT the)  (NNPS States)  )))))(, ,)  "
                        + "(CC and) "
                        + "(S (NP-SBJ (PRP she)  )(VP (VBD seemed)  "
                        + "(ADJP-PRD (ADJP (RB as)  (JJ commonplace)  )(PP (IN as)  (NP (PRP$ her)  "
                        + "(NN name)  )))))(. .)  ))",
                        "She was just another freighter from the States , " +
                        "and she seemed as commonplace as her name . "),
            // Example 2: without eos character
            Arguments.of("(S  "
                        + "(PP (IN On) (NP (NNP June) (CD 16))) "
                        + "(NP (PRP he))"
                        + "(VP (VBD was) (VP (VBN born) "
                        + "(PP in (NP Germany)))))",
                        "On June 16 he was born Germany ")
    ) ;
  }

  /**
   * Verifies that parsing with a {@link ParserModel} picks up top k.
   */
  @ParameterizedTest(name = "Parse example {index}.")
  @MethodSource("provideParsePairsForTopKEquals2")
  void testParsingForTopKEquals2(String input, String reference) {
    // prepare
    Assertions.assertNotNull(getModel());
    Parse p = Parse.parseParse(input);
    Assertions.assertNotNull(p);
    Assertions.assertTrue(p.complete());
    Assertions.assertEquals(reference , p.getText());

    opennlp.tools.parser.Parser parser = ParserFactory.create(getModel());
    Assertions.assertNotNull(parser);

    // TEST: parsing with numParses = 2
    Parse[] pArr = parser.parse(p , 2);
    Assertions.assertNotNull(pArr);
    Assertions.assertEquals(2 , pArr.length);
    Assertions.assertEquals(reference , p.getText());
  }

  /*
   * Produces a stream of <parse|text> pairs for parameterized unit tests.
   */
  private static Stream<Arguments> provideParsePairsForTopKEquals2() {
    return Stream.of(
        // Example 1:
        Arguments.of("(TOP " +
                "(VP (VBG Testing) " +
                "(PP (IN for) " +
                "(NP (DT the) " +
                "(NNP AbstractBottomUpParser))) " +
                "(S (VP (TO to) (VP (VB return) " +
                "(NP (JJ top) (JJ first) (NN k)) (, ,) " +
                "(PP (RB instead) (IN of) (NP (DT the) (NN bottom) (NN k)))))) (. parses.)))" ,
            "Testing for the AbstractBottomUpParser to return top first k , " +
                "instead of the bottom k parses. ")
    );
  }
}

