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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import opennlp.tools.parser.Parse;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests the head rules shipped in {@code opennlp-tools/lang/es/parser/es-head-rules}
 * with {@link AncoraSpanishHeadRules}.
 */
public class AncoraSpanishHeadRulesBundledTest {

  private static final String BUNDLED_RULES = "opennlp-tools/lang/es/parser/es-head-rules";

  private static final String FILLER = "FILLER";

  private static AncoraSpanishHeadRules headRules;

  @BeforeAll
  static void loadBundledRules() throws IOException {
    Path rules = locateBundledRules();
    assertNotNull(rules, "Bundled rules not found: " + BUNDLED_RULES);
    try (Reader in = Files.newBufferedReader(rules, StandardCharsets.UTF_8)) {
      headRules = new AncoraSpanishHeadRules(in);
    }
  }

  private static Path locateBundledRules() {
    for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
      Path candidate = dir.resolve(BUNDLED_RULES);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private static Parse leaf(String type, int index) {
    return new Parse("w" + index, new Span(index * 2, index * 2 + 1), type, 1.0, index);
  }

  /**
   * Builds the children so that the rule's fallback (first child for left-to-right rules,
   * last child otherwise) is the filler, which is therefore only selected when no rule
   * tag matches {@code childType}.
   */
  private static Parse[] children(Parse candidate, Parse filler, boolean leftToRight) {
    return leftToRight ? new Parse[] {filler, candidate} : new Parse[] {candidate, filler};
  }

  /**
   * Each escaped tag of the bundled rules must match its literal constituent type, so that
   * the constituent is chosen as head instead of the rule's fallback.
   *
   * @param ruleType The constituent type whose head rule is applied.
   * @param leftToRight Whether the rule searches left to right.
   * @param childType A constituent type that one of the rule's escaped tags must match.
   */
  @ParameterizedTest(name = "{0}: {2}")
  @CsvSource({
      "S, false, GRUP.VERB",
      "S, false, GRUP.NOM",
      "SENTENCE, false, GRUP.VERB",
      "SENTENCE, false, GRUP.NOM",
      "SA, false, $",
      "SA, false, GRUP.ADV",
      "SA, false, S.A",
      "SA, false, GRUP.A",
      "SA, false, GRUP.NOM",
      "S.A, false, S.A",
      "GRUP.A, true, GRUP.A",
      "GRUP.ADV, false, GRUP.ADV",
      "SADV, true, GRUP.ADV",
      "SADV, true, S.A",
      "GRUP.VERB, false, GRUP.VERB",
      "MORFEMA.PRONOMINAL, false, GRUP.NOM",
      "MORFEMA.PRONOMINAL, false, GRUP.VERB",
      "MORFEMA.VERBAL, false, GRUP.VERB",
      "INC, false, S.A",
      "RELATIU, false, GRUP.VERB"
  })
  void testEscapedTagMatchesLiteralType(String ruleType, boolean leftToRight, String childType) {
    Parse candidate = leaf(childType, 0);
    Parse filler = leaf(FILLER, 1);

    assertSame(candidate, headRules.getHead(children(candidate, filler, leftToRight), ruleType));
  }

  /**
   * An escaped dot must match only a literal dot, so types that differ from the intended
   * type in that position must not be chosen as head.
   *
   * @param ruleType The constituent type whose head rule is applied.
   * @param leftToRight Whether the rule searches left to right.
   * @param childType A constituent type that no tag of the rule may match.
   */
  @ParameterizedTest(name = "{0}: {2}")
  @CsvSource({
      "S, false, GRUPXVERB",
      "S, false, GRUPXNOM",
      "SA, false, GRUPXADV",
      "SA, false, SXA",
      "SA, false, GRUPXA",
      "GRUP.VERB, false, GRUPXVERB",
      "MORFEMA.VERBAL, false, GRUPXVERB"
  })
  void testEscapedTagRejectsNonLiteralType(String ruleType, boolean leftToRight, String childType) {
    Parse candidate = leaf(childType, 0);
    Parse filler = leaf(FILLER, 1);

    assertSame(filler, headRules.getHead(children(candidate, filler, leftToRight), ruleType));
  }

  /**
   * In a GRUP.VERB constituent, a nested GRUP.VERB precedes SA in the rule's tag order and
   * must therefore be chosen as head over an SA sibling.
   */
  @Test
  void testGrupVerbHeadPrefersNestedGrupVerbOverSa() {
    Parse verb = leaf("GRUP.VERB", 0);
    Parse adjective = leaf("SA", 1);

    assertSame(verb, headRules.getHead(new Parse[] {verb, adjective}, "GRUP.VERB"));
  }
}
