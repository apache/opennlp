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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParserToolTest {

  /*
   * Expected values replicate the original two sequential replaceAll passes:
   * the first pass inserts a space before every paren that follows a non-space
   * character, the second pass inserts a space after every paren that precedes
   * a non-space character. Each pass resumes scanning after its last match, so
   * the pair overlapping a match is only reconsidered by the second pass.
   */
  @Test
  void testSpaceUntokenizedParens() {
    assertEquals("a ( b ) c", ParserTool.spaceUntokenizedParens("a(b)c"));
    assertEquals("a ( b ) c", ParserTool.spaceUntokenizedParens("a (b) c"));
    assertEquals("foo ( bar ) {baz }", ParserTool.spaceUntokenizedParens("foo(bar){baz}"));
    assertEquals("( ( a ) (b ) )", ParserTool.spaceUntokenizedParens("((a)(b))"));
    assertEquals("a ( b ( c ) d ) e", ParserTool.spaceUntokenizedParens("a(b(c)d)e"));
    assertEquals("x ( (", ParserTool.spaceUntokenizedParens("x(("));
    assertEquals("( ( x", ParserTool.spaceUntokenizedParens("((x"));
    assertEquals("( )", ParserTool.spaceUntokenizedParens("()"));
    assertEquals("(", ParserTool.spaceUntokenizedParens("("));
    assertEquals("", ParserTool.spaceUntokenizedParens(""));
    assertEquals("no parens here", ParserTool.spaceUntokenizedParens("no parens here"));
    assertEquals("«quoted»", ParserTool.spaceUntokenizedParens("«quoted»"));
  }

  @Test
  void testSpaceUntokenizedParensOnlySplitsOnSpace() {
    // the patterns use [^ ], so tabs do not act as separators
    assertEquals("tab\there (", ParserTool.spaceUntokenizedParens("tab\there("));
    assertEquals("a  ( b", ParserTool.spaceUntokenizedParens("a  (b"));
  }
}
