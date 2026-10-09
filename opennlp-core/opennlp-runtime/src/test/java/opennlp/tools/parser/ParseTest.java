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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.Span;

/**
 * Tests for the {@link Parse} class.
 */
public class ParseTest {

  public static final String PARSE_STRING = "(TOP  (S (S (NP-SBJ (PRP She)  )(VP (VBD was)  "
      + "(ADVP (RB just)  )(NP-PRD (NP (DT another)  (NN freighter)  )(PP (IN from)  (NP (DT the)  "
      + "(NNPS States)  )))))(, ,)  (CC and) (S (NP-SBJ (PRP she)  )(VP (VBD seemed)  "
      + "(ADJP-PRD (ADJP (RB as)  (JJ commonplace)  )(PP (IN as)  (NP (PRP$ her)  " +
      "(NN name)  )))))(. .)  ))";

  @Test
  void testToHashCode() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    p1.hashCode();
  }

  @Test
  void testToString() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    p1.toString();
  }

  @Test
  void testEquals() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    Assertions.assertEquals(p1, p1);
  }

  @Test
  void testParseClone() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    Parse p2 = (Parse) p1.clone();
    Assertions.assertEquals(p1, p2);
    Assertions.assertEquals(p2, p1);
  }

  @Test
  void testGetText() {
    Parse p = Parse.parseParse(PARSE_STRING);

    // TODO: Why does parse attaches a space to the end of the text ???
    String expectedText = "She was just another freighter from the States , " +
        "and she seemed as commonplace as her name . ";

    Assertions.assertEquals(expectedText, p.getText());
  }

  @Test
  void testShow() {
    Parse p1 = Parse.parseParse(PARSE_STRING);

    StringBuffer parseString = new StringBuffer();
    p1.show(parseString);
    Parse p2 = Parse.parseParse(parseString.toString());
    Assertions.assertEquals(p1, p2);
  }

  @Test
  void testTokenReplacement() {
    Parse p1 = Parse.parseParse("(TOP  (S-CLF (NP-SBJ (PRP It)  )(VP (VBD was) " +
        " (NP-PRD (NP (DT the)  (NN trial)  )(PP (IN of) " +
        " (NP (NP (NN oleomargarine)  (NN heir)  )(NP (NNP Minot) " +
        " (PRN (-LRB- -LRB-) (NNP Mickey) " +
        " (-RRB- -RRB-) )(NNP Jelke)  )))(PP (IN for) " +
        " (NP (JJ compulsory)  (NN prostitution) " +
        " ))(PP-LOC (IN in)  (NP (NNP New)  (NNP York) " +
        " )))(SBAR (WHNP-1 (WDT that)  )(S (VP (VBD put) " +
        " (NP (DT the)  (NN spotlight)  )(PP (IN on)  (NP (DT the) " +
        " (JJ international)  (NN play-girl)  ))))))(. .)  ))");

    StringBuffer parseString = new StringBuffer();
    p1.show(parseString);

    Parse p2 = Parse.parseParse(parseString.toString());
    Assertions.assertEquals(p1, p2);
  }

  @Test
  void testGetTagNodes() {
    Parse p = Parse.parseParse(PARSE_STRING);

    Parse[] tags = p.getTagNodes();

    for (Parse node : tags) {
      Assertions.assertTrue(node.isPosTag());
    }

    Assertions.assertEquals("PRP", tags[0].getType());
    Assertions.assertEquals("VBD", tags[1].getType());
    Assertions.assertEquals("RB", tags[2].getType());
    Assertions.assertEquals("DT", tags[3].getType());
    Assertions.assertEquals("NN", tags[4].getType());
    Assertions.assertEquals("IN", tags[5].getType());
    Assertions.assertEquals("DT", tags[6].getType());
    Assertions.assertEquals("NNPS", tags[7].getType());
    Assertions.assertEquals(",", tags[8].getType());
    Assertions.assertEquals("CC", tags[9].getType());
    Assertions.assertEquals("PRP", tags[10].getType());
    Assertions.assertEquals("VBD", tags[11].getType());
    Assertions.assertEquals("RB", tags[12].getType());
    Assertions.assertEquals("JJ", tags[13].getType());
    Assertions.assertEquals("IN", tags[14].getType());
    Assertions.assertEquals("PRP$", tags[15].getType());
    Assertions.assertEquals("NN", tags[16].getType());
    Assertions.assertEquals(".", tags[17].getType());
  }

  @Test
  void testCreateFromTokens() {
    String[] tokens = {"The", "cat", "sat", "on", "the", "mat"};
    Parse p = Parse.createFromTokens(tokens);

    // Verify text is space-joined
    Assertions.assertEquals("The cat sat on the mat", p.getText());

    // Verify root span covers full text
    Assertions.assertEquals(new Span(0, 22), p.getSpan());

    // Verify root type is INC
    Assertions.assertEquals(Parser.INC_NODE, p.getType());

    // Verify token children
    Parse[] children = p.getChildren();
    Assertions.assertEquals(tokens.length, children.length);

    int start = 0;
    for (int i = 0; i < tokens.length; i++) {
      Assertions.assertEquals(Parser.TOK_NODE, children[i].getType());
      Assertions.assertEquals(new Span(start, start + tokens[i].length()), children[i].getSpan());
      Assertions.assertEquals(tokens[i], children[i].getCoveredText());
      start += tokens[i].length() + 1;
    }
  }

  @Test
  void testCreateFromTokensNullThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> Parse.createFromTokens(null));
  }

  @Test
  void testCreateFromTokensEmptyThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> Parse.createFromTokens(new String[0]));
  }

  @Test
  void testCreateFromTokensSingleToken() {
    String[] tokens = {"Hello"};
    Parse p = Parse.createFromTokens(tokens);

    Assertions.assertEquals("Hello", p.getText());
    Assertions.assertEquals(1, p.getChildren().length);
    Assertions.assertEquals(new Span(0, 5), p.getChildren()[0].getSpan());
  }

  /**
   * Reference expressions for the constituent label, the function tag and the token of a
   * tree-bank constituent before 3.0.0; the constituent tests below assert that they agree
   * with the current parser.
   */
  private static final Pattern TYPE_ORACLE = Pattern.compile("^([^ =-]+)");
  private static final Pattern FUNCTION_TAG_ORACLE = Pattern.compile("^[^ =-]+-([^ =-]+)");
  private static final Pattern TOKEN_ORACLE = Pattern.compile("^[^ ()]+ ([^ ()]+)\\s*\\)");

  private static final Map<String, String> BRACKETS = Map.of("-LRB-", "(", "-RRB-", ")",
      "-LCB-", "{", "-RCB-", "}", "-LSB-", "[", "-RSB-", "]");

  /**
   * Supplies constituents with a valid label: the input after the opening parenthesis, the
   * expected label without and with function tags, and the expected token.
   *
   * @return The constituents and their expected label and token.
   */
  private static Stream<Arguments> constituentLabels() {
    return Stream.of(
        Arguments.of("NN word)", "NN", "NN", "word"),
        Arguments.of("NP-SBJ word)", "NP", "NP-SBJ", "word"),
        Arguments.of("NP-SBJ=2 word)", "NP", "NP-SBJ", "word"),
        Arguments.of("NP-SBJ-1 word)", "NP", "NP-SBJ", "word"),
        Arguments.of("NP-SBJ-TMP=3 word)", "NP", "NP-SBJ", "word"),
        Arguments.of("NP=2 word)", "NP", "NP", "word"),
        Arguments.of("NP--X word)", "NP", "NP", "word"),
        Arguments.of("NP- word)", "NP", "NP", "word"),
        Arguments.of("PRP$ her)", "PRP$", "PRP$", "her"),
        Arguments.of("-NONE- *T*-1)", "-NONE-", "-NONE-", "*T*-1"),
        Arguments.of("-LRB- -LRB-)", "-LRB-", "-LRB-", "("),
        Arguments.of("-RRB- -RRB-)", "-RRB-", "-RRB-", ")"),
        Arguments.of("-LCB- -LCB-)", "-LCB-", "-LCB-", "{"),
        Arguments.of("-RSB- -RSB-)", "-RSB-", "-RSB-", "]"),
        Arguments.of(". .)", ".", ".", "."),
        Arguments.of(", ,)", ",", ",", ","),
        Arguments.of("NN a=b-c)", "NN", "NN", "a=b-c"));
  }

  /**
   * Checks the label and token of a valid constituent, without and with function tags.
   *
   * @param rest The constituent text after the opening parenthesis.
   * @param type The expected label without function tags.
   * @param typeWithFunctionTag The expected label with function tags.
   * @param token The expected token.
   */
  @ParameterizedTest
  @MethodSource("constituentLabels")
  void testConstituentLabel(String rest, String type, String typeWithFunctionTag, String token) {
    Assertions.assertEquals(type, oracleType(rest, false));
    Assertions.assertEquals(typeWithFunctionTag, oracleType(rest, true));

    assertConstituent(rest, false, type, token);
    assertConstituent(rest, true, typeWithFunctionTag, token);
  }

  /**
   * Supplies constituents whose token is followed by whitespace or contains non-ASCII
   * characters: the input after the opening parenthesis and the expected token. Only ASCII
   * whitespace between the token and the closing parenthesis is skipped.
   *
   * @return The constituents and their expected token.
   */
  private static Stream<Arguments> constituentTokens() {
    return Stream.of(
        Arguments.of("NN word )", "word"),
        Arguments.of("NN word   )", "word"),
        Arguments.of("NN word \t)", "word"),
        Arguments.of("NN word \u000B\f\r\n)", "word"),
        Arguments.of("NN word\t)", "word\t"),
        Arguments.of("NN word )", "word "),
        Arguments.of("NN été)", "été"),
        Arguments.of("NN 😀)", "😀"));
  }

  /**
   * Checks the token of a constituent whose token ends in whitespace or contains non-ASCII
   * characters.
   *
   * @param rest The constituent text after the opening parenthesis.
   * @param token The expected token.
   */
  @ParameterizedTest
  @MethodSource("constituentTokens")
  void testConstituentToken(String rest, String token) {
    Assertions.assertEquals(token, oracleToken(rest));

    assertConstituent(rest, false, "NN", token);
    assertConstituent(rest, true, "NN", token);
  }

  /**
   * Checks that an invalid constituent yields no node, without and with function tags.
   *
   * @param rest The constituent text after the opening parenthesis.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "NN word  )", "NN word \u001C)", "NN word x)", "NN  word)", "NN)", "NN word",
      "NN word ", "NN ", "NN"})
  void testInvalidConstituentYieldsNoNode(String rest) {
    Assertions.assertNull(oracleToken(rest));

    for (boolean functionTags : new boolean[] {false, true}) {
      Parse.useFunctionTags(functionTags);
      try {
        Assertions.assertEquals(0, Parse.parseParse("(" + rest).getChildren().length, rest);
      } finally {
        Parse.useFunctionTags(false);
      }
    }
  }

  /**
   * Parses a single constituent and checks that it yields one node with the given label and
   * token.
   *
   * @param rest The constituent text after the opening parenthesis.
   * @param functionTags Whether function tags are appended to the label.
   * @param type The expected label.
   * @param token The expected token.
   */
  private static void assertConstituent(String rest, boolean functionTags, String type,
                                        String token) {
    Parse.useFunctionTags(functionTags);
    try {
      Parse p = Parse.parseParse("(" + rest);
      Assertions.assertEquals(1, p.getChildren().length, rest);
      Assertions.assertEquals(type, p.getChildren()[0].getType(), rest);
      Assertions.assertEquals(token, p.getChildren()[0].getCoveredText(), rest);
    } finally {
      Parse.useFunctionTags(false);
    }
  }

  /**
   * Computes the expected label of a constituent with the reference expressions.
   *
   * @param rest The constituent text after the opening parenthesis.
   * @param functionTags Whether function tags are appended to the label.
   * @return The expected label, or {@code null} if there is none.
   */
  private static String oracleType(String rest, boolean functionTags) {
    for (String bracket : new String[] {"-LCB-", "-RCB-", "-LRB-", "-RRB-", "-RSB-", "-LSB-",
        "-NONE-"}) {
      if (rest.startsWith(bracket)) {
        return bracket;
      }
    }
    Matcher type = TYPE_ORACLE.matcher(rest);
    if (!type.find()) {
      return null;
    }
    Matcher functionTag = FUNCTION_TAG_ORACLE.matcher(rest);
    if (functionTags && functionTag.find()) {
      return type.group(1) + "-" + functionTag.group(1);
    }
    return type.group(1);
  }

  /**
   * Computes the expected token of a constituent with the reference expression.
   *
   * @param rest The constituent text after the opening parenthesis.
   * @return The expected decoded token, or {@code null} if there is none.
   */
  private static String oracleToken(String rest) {
    Matcher token = TOKEN_ORACLE.matcher(rest);
    return token.find() ? BRACKETS.getOrDefault(token.group(1), token.group(1)) : null;
  }
}
