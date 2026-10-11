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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.parser.Parse;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AncoraSpanishHeadRulesTest {

  private static final String HEADER = "# opennlp-ancora-head-rules 2\n";

  @Test
  void testVersionTwoGlobMatchesLiteralWildcardAndChoice() throws IOException {
    AncoraSpanishHeadRules rules = rules("6 GRUP.VERB 0 GRUP.VERB V[MAS]* NC*S* $\n");
    Parse falseDotMatch = constituent("GRUPxVERB", 0);
    Parse wildcard = constituent("NCMS000", 1);
    Parse choice = constituent("VAIP3S0", 2);
    Parse exact = constituent("GRUP.VERB", 3);

    assertSame(exact, rules.getHead(new Parse[] {falseDotMatch, wildcard, choice, exact},
        "GRUP.VERB"));
    assertSame(choice, rules("3 TEST 0 V[MAS]*\n")
        .getHead(new Parse[] {constituent("OTHER", 0), choice}, "TEST"));
    assertSame(wildcard, rules("3 TEST 0 NC*S*\n")
        .getHead(new Parse[] {constituent("OTHER", 0), wildcard}, "TEST"));
  }

  @Test
  void testRuleAndConstituentPriorityRemainStable() throws IOException {
    Parse firstNoun = constituent("NCMS", 0);
    Parse adjective = constituent("AQA0", 1);
    Parse lastNoun = constituent("NCFP", 2);
    Parse[] constituents = {firstNoun, adjective, lastNoun};

    assertSame(firstNoun, rules("4 TEST 1 NC* AQA*\n").getHead(constituents, "TEST"));
    assertSame(lastNoun, rules("4 TEST 0 NC* AQA*\n").getHead(constituents, "TEST"));
    assertSame(adjective, rules("4 TEST 0 AQA* NC*\n").getHead(constituents, "TEST"));
  }

  @Test
  void testChoiceDoesNotMatchNul() throws IOException {
    Parse invalid = constituent("V\u0000", 0);
    Parse fallback = constituent("OTHER", 1);
    assertSame(fallback, rules("3 TEST 0 V[MAS]\n")
        .getHead(new Parse[] {invalid, fallback}, "TEST"));
  }

  @Test
  void testKnownLegacyLiteralTagSpelling() throws IOException {
    AncoraSpanishHeadRules rules = new AncoraSpanishHeadRules(
        new StringReader("3 TEST 1 GRUP.ADV\n"));
    Parse invalid = constituent("GRUPxADV", 0);
    Parse literal = constituent("GRUP.ADV", 1);
    assertSame(literal, rules.getHead(new Parse[] {invalid, literal}, "TEST"));
  }

  /**
   * Reads the tags of earlier releases with the dot and dollar escaped once (the bundled rules
   * before 3.0 were read this way after GH-1460) or twice (the released rule files).
   *
   * @param escape The escape before a literal dot or dollar sign.
   */
  @ParameterizedTest
  @ValueSource(strings = {"\\", "\\\\"})
  void testLegacyEscapesAreImportedAsLiterals(String escape) throws IOException {
    String legacy = "5 TEST 0 GRUP" + escape + ".NOM " + escape + "$ SP[CS].*\n";
    AncoraSpanishHeadRules rules = new AncoraSpanishHeadRules(new StringReader(legacy));
    Parse falseDotMatch = constituent("GRUPxNOM", 0);
    Parse literalDollar = constituent("$", 1);
    Parse choice = constituent("SPS00", 2);
    Parse literalDot = constituent("GRUP.NOM", 3);

    assertSame(literalDot,
        rules.getHead(new Parse[] {falseDotMatch, literalDollar, choice, literalDot}, "TEST"));
    assertSame(literalDollar,
        new AncoraSpanishHeadRules(new StringReader("3 TEST 0 " + escape + "$\n"))
            .getHead(new Parse[] {constituent("OTHER", 0), literalDollar}, "TEST"));
    assertSame(choice,
        new AncoraSpanishHeadRules(new StringReader("3 TEST 0 SP[CS].*\n"))
            .getHead(new Parse[] {constituent("OTHER", 0), choice}, "TEST"));
  }

  @Test
  void testSnRulesUseDomainMatchers() throws IOException {
    AncoraSpanishHeadRules rules = rules("");
    Parse falseDotMatch = constituent("GRUPxA", 0);
    Parse adjective = constituent("AQAC0", 1);
    Parse literalDot = constituent("GRUP.A", 2);

    assertSame(literalDot,
        rules.getHead(new Parse[] {falseDotMatch, literalDot, adjective}, "SN"));
  }

  @Test
  void testSerializationWritesVersionTwoAndRoundTrips() throws IOException {
    AncoraSpanishHeadRules original = rules("4 TEST 0 GRUP.NOM NC*S*\n");
    StringWriter serialized = new StringWriter();

    original.serialize(serialized);

    assertTrue(serialized.toString().startsWith(HEADER));
    assertEquals(original,
        new AncoraSpanishHeadRules(new StringReader(serialized.toString())));
  }

  @Test
  void testLargestSupportedPatternAndZeroLengthWildcard() throws IOException {
    Parse exact = constituent("A".repeat(62), 0);
    Parse fallback = constituent("OTHER", 1);
    assertSame(exact, rules("3 TEST 0 " + "A".repeat(62) + "\n")
        .getHead(new Parse[] {exact, fallback}, "TEST"));
    Parse noGap = constituent("AB", 0);
    assertSame(noGap, rules("3 TEST 0 A*B\n")
        .getHead(new Parse[] {noGap, fallback}, "TEST"));
    assertThrows(IOException.class, () -> rules("3 TEST 0 A**B\n"));
  }

  @Test
  void testLongNearMissUsesBoundedMatcher() throws IOException {
    AncoraSpanishHeadRules rules = rules("3 TEST 0 A*B*C*Z\n");
    Parse nearMiss = constituent("A" + "BC".repeat(50_000) + "Y", 0);
    Parse fallback = constituent("OTHER", 1);

    assertSame(fallback, rules.getHead(new Parse[] {nearMiss, fallback}, "TEST"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "3 TEST 0 A+", "3 TEST 0 A|B", "3 TEST 0 (AB)", "3 TEST 0 A?",
      "3 TEST 0 [A-Z]", "3 TEST 0 []", "3 TEST 0 [XYZ]", "3 TEST 0 A\\B"
  })
  void testVersionTwoRejectsUnsupportedPatterns(String rule) {
    IOException error = assertThrows(IOException.class, () -> rules(rule + "\n"));
    assertTrue(error.getMessage().contains("line 2"));
    assertTrue(error.getMessage().contains("TEST"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "3 TEST 0 A+", "3 TEST 0 A|B", "3 TEST 0 (AB)", "3 TEST 0 [A-Z]",
      "3 TEST 0 A\\d", "3 TEST 0 A.B", "3 TEST 0 A$"
  })
  void testLegacyImportRejectsUnsupportedRegex(String rule) {
    IOException error = assertThrows(IOException.class,
        () -> new AncoraSpanishHeadRules(new StringReader(rule + "\n")));
    assertTrue(error.getMessage().contains("line 1"));
    assertTrue(error.getMessage().contains("TEST"));
  }

  @Test
  void testRejectsUnknownFormatHeader() {
    IOException error = assertThrows(IOException.class,
        () -> new AncoraSpanishHeadRules(
            new StringReader("# opennlp-ancora-head-rules 3\n")));
    assertTrue(error.getMessage().contains("header"));
    assertTrue(error.getMessage().contains("line 1"));
  }

  @Test
  void testRejectsPatternBeyondStateBound() {
    IOException error = assertThrows(IOException.class,
        () -> rules("3 TEST 0 " + "A".repeat(63) + "\n"));
    assertTrue(error.getMessage().contains("62 states"));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "3 TEST 2 A*|line 2 for rule TEST: direction must be 0 or 1",
      "4 TEST 0 A*|line 2: declared field count does not match the rule",
      "2 TEST 0 A*|line 2: declared field count does not match the rule",
      "3 TEST 0 A*\\n3 TEST 1 B*|line 3 for rule TEST: duplicate constituent rule",
      "not-a-count TEST 0 A*|line 2: invalid field count 'not-a-count'",
      "3 TEST 0 |line 2: declared field count does not match the rule",
      "3 TEST 0 A*\\n\\n3 OTHER 0 B*|line 3: blank lines are not supported"
  })
  void testRejectsMalformedRules(String rule, String expected) {
    IOException error = assertThrows(IOException.class,
        () -> rules(rule.replace("\\n", "\n") + "\n"));
    assertTrue(error.getMessage().contains(expected), error.getMessage());
  }

  @Test
  void testRejectsNullPublicArguments() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> new AncoraSpanishHeadRules(null));

    AncoraSpanishHeadRules rules = rules("");
    Parse valid = constituent("A", 0);
    assertThrows(IllegalArgumentException.class, () -> rules.getHead(null, "TEST"));
    assertThrows(IllegalArgumentException.class, () -> rules.getHead(new Parse[0], "TEST"));
    assertThrows(IllegalArgumentException.class, () -> rules.getHead(new Parse[] {null}, "TEST"));
    assertThrows(IllegalArgumentException.class, () -> rules.getHead(new Parse[] {valid}, null));
    assertThrows(IllegalArgumentException.class, () -> rules.serialize(null));

    AncoraSpanishHeadRules.HeadRulesSerializer serializer =
        new AncoraSpanishHeadRules.HeadRulesSerializer();
    assertThrows(IllegalArgumentException.class, () -> serializer.create(null));
    assertThrows(IllegalArgumentException.class,
        () -> serializer.serialize(null, new ByteArrayOutputStream()));
    assertThrows(IllegalArgumentException.class, () -> serializer.serialize(rules, null));
  }

  private static AncoraSpanishHeadRules rules(String body) throws IOException {
    return new AncoraSpanishHeadRules(new StringReader(HEADER + body));
  }

  /**
   * {@return a constituent of {@code type}; head selection reads only the type}
   *
   * @param type The constituent type.
   * @param index The index of the constituent among its siblings.
   */
  static Parse constituent(String type, int index) {
    return new Parse("text", new Span(0, 4), type, 1.0, index);
  }
}
