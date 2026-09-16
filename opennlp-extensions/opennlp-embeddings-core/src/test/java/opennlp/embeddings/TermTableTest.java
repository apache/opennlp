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
package opennlp.embeddings;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The term table's normalization contract, its validation of stored terms, and the greedy
 * longest-first matching over word runs.
 */
class TermTableTest {

  private static final String SOURCE = "terms.txt";

  private static TermTable table(String... terms) throws InvalidFormatException {
    return TermTable.of(List.of(terms), 10, SOURCE);
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
      "habeas corpus;habeas corpus",
      "Habeas Corpus;habeas corpus",
      "habeas-corpus!;habeas corpus",
      "'  writ   OF  Habeas ';writ of habeas",
      "res judicata.;res judicata",
      "42 USC 1983;42 usc 1983"
  })
  void testNormalizeTermFoldsAndJoinsWordRuns(String raw, String expected) {
    assertEquals(expected, TermTable.normalizeTerm(raw));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "&!.", "--"})
  void testNormalizeTermOfTextWithoutWordsIsEmpty(String raw) {
    assertEquals("", TermTable.normalizeTerm(raw));
  }

  @Test
  void testNormalizeTermFoldsSupplementaryPlaneLetters() {
    // DESERET CAPITAL LETTER LONG I (U+10400) is a cased letter outside the BMP; its lower-case
    // form is U+10428, one code point, so the word run survives the fold intact.
    final String capital = new String(Character.toChars(0x10400));
    final String small = new String(Character.toChars(0x10428));
    assertEquals(small + "x", TermTable.normalizeTerm(capital + "x"));
  }

  @Test
  void testRejectsATermThatIsNotNormalized() {
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> table("HABEAS CORPUS"));
    assertTrue(e.getMessage().contains("HABEAS CORPUS"), e.getMessage());
    assertTrue(e.getMessage().contains(SOURCE), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "habeas  corpus", " habeas", "habeas "})
  void testRejectsMalformedTermForms(String term) {
    assertThrows(InvalidFormatException.class, () -> table(term));
  }

  @Test
  void testRejectsADuplicateTerm() {
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> table("habeas corpus", "habeas corpus"));
    assertTrue(e.getMessage().contains("more than once"), e.getMessage());
  }

  @Test
  void testRejectsNullArguments() {
    assertThrows(IllegalArgumentException.class, () -> TermTable.of(null, 0, SOURCE));
    assertThrows(IllegalArgumentException.class, () -> TermTable.normalizeTerm(null));
  }

  @Test
  void testTermsOwnRowsFromTheFirstRowOnward() throws InvalidFormatException {
    final TermTable table = table("habeas corpus", "replevin");
    assertEquals(2, table.size());
    assertEquals("habeas corpus", table.term(10));
    assertEquals("replevin", table.term(11));
    assertThrows(IllegalArgumentException.class, () -> table.term(9));
    assertThrows(IllegalArgumentException.class, () -> table.term(12));
  }

  @Test
  void testMatchesFoldCaseAndSpanPunctuation() throws InvalidFormatException {
    final TermTable table = table("habeas corpus");
    final List<TermTable.Match> matches = table.matches("The writ of Habeas-Corpus, granted.");
    assertEquals(1, matches.size());
    assertEquals(10, matches.get(0).row());
    assertEquals("Habeas-Corpus", "The writ of Habeas-Corpus, granted."
        .substring(matches.get(0).start(), matches.get(0).end()));
  }

  @Test
  void testTheLongestTermWinsAndConsumesItsWords() throws InvalidFormatException {
    final TermTable table = table("habeas corpus", "writ of habeas corpus", "corpus");
    final List<TermTable.Match> matches = table.matches("a writ of habeas corpus indeed");
    // The four-word term wins over both shorter terms, and its words are consumed: the inner
    // "habeas corpus" and "corpus" do not match again.
    assertEquals(1, matches.size());
    assertEquals(11, matches.get(0).row());
  }

  @Test
  void testMatchingContinuesAfterAConsumedTerm() throws InvalidFormatException {
    final TermTable table = table("habeas corpus", "replevin");
    final List<TermTable.Match> matches = table.matches("habeas corpus then replevin");
    assertEquals(2, matches.size());
    assertEquals(10, matches.get(0).row());
    assertEquals(11, matches.get(1).row());
    assertTrue(matches.get(0).end() <= matches.get(1).start());
  }

  @Test
  void testWordsSeparatedByOtherWordsDoNotMatchAPhrase() throws InvalidFormatException {
    final TermTable table = table("habeas corpus");
    assertTrue(table.matches("habeas late corpus").isEmpty());
  }

  @Test
  void testAnEmptyTableMatchesNothing() throws InvalidFormatException {
    assertTrue(table().matches("habeas corpus").isEmpty());
  }

  @Test
  void testMatchesRejectsNullText() throws InvalidFormatException {
    final TermTable table = table("habeas corpus");
    assertThrows(IllegalArgumentException.class, () -> table.matches(null));
  }
}
