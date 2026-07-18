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

package opennlp.tools.parser.lang.es;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.parser.Parse;
import opennlp.tools.util.Span;

public class AncoraSpanishHeadRulesTest {

  /**
   * Positive: well-formed head rules line with a small tag count loads without error.
   */
  @Test
  void testValidTagCountLoads() {
    String rules = "5 NP 1 NN NNS NNP\n";
    Assertions.assertDoesNotThrow(() -> new AncoraSpanishHeadRules(new StringReader(rules)));
  }

  /**
   * Negative: oversized tag count must throw IOException, not cause OOM.
   */
  @Test
  void testOversizedTagCountThrows() {
    String rules = "2147483647 NP 1\n";
    Assertions.assertThrows(IOException.class,
        () -> new AncoraSpanishHeadRules(new StringReader(rules)));
  }

  /**
   * Negative: tag count producing negative array size must throw IOException.
   */
  @Test
  void testNegativeTagCountThrows() {
    String rules = "1 NP 1\n";  // 1 - 2 = -1
    Assertions.assertThrows(IOException.class,
        () -> new AncoraSpanishHeadRules(new StringReader(rules)));
  }

  /**
   * Boundary: value just above MAX_TAGS_PER_RULE (1003 → numTags = 1001) must throw IOException.
   */
  @Test
  void testJustAboveLimitThrows() {
    // 1003 tokens declared; 1003 - 2 = 1001 tags, which exceeds MAX_TAGS_PER_RULE (1000)
    String rules = "1003 NP 1\n";
    Assertions.assertThrows(IOException.class,
        () -> new AncoraSpanishHeadRules(new StringReader(rules)));
  }

  /**
   * Negative: non-numeric token count must throw IOException, not NumberFormatException.
   */
  @Test
  void testNonNumericTagCountThrows() {
    String rules = "NaN NP 1\n";
    Assertions.assertThrows(IOException.class,
        () -> new AncoraSpanishHeadRules(new StringReader(rules)));
  }

  /**
   * getHead() for a rule loaded from a head-rules file must still honour regex tag
   * patterns (e.g. "AQA.*") after switching from String.matches() to precompiled
   * java.util.regex.Pattern - a literal tag like "AQA.*" must NOT be required to
   * appear verbatim, only to match the pattern.
   */
  @Test
  void testGetHeadMatchesRegexTagFromRuleFile() throws IOException {
    // Rule for type "SA", right-to-left (dir=0), single tag pattern "AQA.*"
    String rules = "3 SA 0 AQA.*\n";
    AncoraSpanishHeadRules headRules = new AncoraSpanishHeadRules(new StringReader(rules));

    Parse other = new Parse("text", new Span(0, 4), "OTHER", 1.0, 0);
    // "AQA0" matches the regex "AQA.*" but is not equal to it - proves matching is
    // still regex-based, not literal/equals-based, after the fix.
    Parse matching = new Parse("text", new Span(5, 9), "AQA0", 1.0, 0);

    Parse[] constituents = {other, matching};
    Parse head = headRules.getHead(constituents, "SA");

    Assertions.assertSame(matching, head);
  }

  /**
   * getHead() for the hardcoded SN/GRUP.NOM branch must still honour its inline regex
   * tag patterns (e.g. "GRUP\\.A") after they were hoisted into precompiled, static
   * Pattern arrays.
   */
  @Test
  void testGetHeadMatchesRegexTagInSnBranch() throws IOException {
    AncoraSpanishHeadRules headRules = new AncoraSpanishHeadRules(new StringReader(""));

    Parse other = new Parse("text", new Span(0, 4), "OTHER", 1.0, 0);
    // "GRUP.A" matches the escaped-dot regex "GRUP\\.A" used in the SN branch's tags1.
    Parse matching = new Parse("text", new Span(5, 11), "GRUP.A", 1.0, 0);

    Parse[] constituents = {other, matching};
    Parse head = headRules.getHead(constituents, "SN");

    Assertions.assertSame(matching, head);
  }
}
