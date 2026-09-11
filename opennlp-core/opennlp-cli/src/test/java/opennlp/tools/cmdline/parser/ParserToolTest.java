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

package opennlp.tools.cmdline.parser;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.tokenize.WhitespaceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParserToolTest {

  /*
   * The first pass puts a space before a bracket that follows a non-space character, the
   * second after a bracket that precedes one. Each pass resumes after the pair it spaced, so
   * "((" gets its space from the second pass only, and only a space, not a tab, separates.
   */
  private static Stream<Arguments> parenLines() {
    return Stream.of(
        Arguments.of("a(b)c", "a ( b ) c"),
        Arguments.of("a (b) c", "a ( b ) c"),
        Arguments.of("foo(bar){baz}", "foo ( bar ) {baz }"),
        Arguments.of("((a)(b))", "( ( a ) (b ) )"),
        Arguments.of("a(b(c)d)e", "a ( b ( c ) d ) e"),
        Arguments.of("x((", "x ( ("),
        Arguments.of("((x", "( ( x"),
        Arguments.of("()", "( )"),
        Arguments.of("(", "("),
        Arguments.of("", ""),
        Arguments.of("no parens here", "no parens here"),
        Arguments.of("«quoted»", "«quoted»"),
        Arguments.of("tab\there(", "tab\there ("),
        Arguments.of("a  (b", "a  ( b"));
  }

  @ParameterizedTest
  @MethodSource("parenLines")
  void testSpaceUntokenizedParens(String line, String expected) {
    assertEquals(expected, ParserTool.spaceUntokenizedParens(line));
  }

  @Test
  void testParseLineSeparatesParensBeforeTokenizing() {
    Parser echo = new Parser() {
      @Override
      public Parse[] parse(Parse tokens, int numParses) {
        return new Parse[] {tokens};
      }

      @Override
      public Parse parse(Parse tokens) {
        return tokens;
      }
    };
    Parse[] parses = ParserTool.parseLine("f(x)+g({y})", echo, WhitespaceTokenizer.INSTANCE, 1);
    assertEquals(1, parses.length);
    String[] tokens = Stream.of(parses[0].getChildren()).map(Parse::getCoveredText)
        .toArray(String[]::new);
    // "{y" stays joined: the second pass consumed "{" while spacing "( {"
    assertArrayEquals(new String[] {"f", "(", "x", ")", "+g", "(", "{y", "}", ")"}, tokens);
  }
}
