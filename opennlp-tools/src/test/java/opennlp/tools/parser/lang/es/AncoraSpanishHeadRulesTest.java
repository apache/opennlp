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
   * A tag of a rule loaded from a head rules file is matched as a regular expression,
   * not as a literal.
   */
  @Test
  void testGetHeadMatchesRegexTagFromRuleFile() throws IOException {
    // Rule for type "SA", right-to-left (dir=0), single tag pattern "AQA.*"
    String rules = "3 SA 0 AQA.*\n";
    AncoraSpanishHeadRules headRules = new AncoraSpanishHeadRules(new StringReader(rules));

    Parse other = new Parse("text", new Span(0, 4), "OTHER", 1.0, 0);
    // "AQA0" matches the regular expression "AQA.*" but is not equal to it.
    Parse matching = new Parse("text", new Span(5, 9), "AQA0", 1.0, 0);

    Parse[] constituents = {other, matching};
    Parse head = headRules.getHead(constituents, "SA");

    Assertions.assertSame(matching, head);
  }

  /**
   * The built-in SN and GRUP.NOM rules match their tags as regular expressions.
   */
  @Test
  void testGetHeadMatchesRegexTagInSnBranch() throws IOException {
    AncoraSpanishHeadRules headRules = new AncoraSpanishHeadRules(new StringReader(""));

    Parse other = new Parse("text", new Span(0, 4), "OTHER", 1.0, 0);
    // "GRUP.A" matches the tag "GRUP\\.A" of the SN rule.
    Parse matching = new Parse("text", new Span(5, 11), "GRUP.A", 1.0, 0);

    Parse[] constituents = {other, matching};
    Parse head = headRules.getHead(constituents, "SN");

    Assertions.assertSame(matching, head);
  }
}
