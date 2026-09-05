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

package opennlp.tools.tokenize.lattice;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.ResourceLimits;
import opennlp.tools.util.Span;

/**
 * Tests the lattice segmenter against a project-authored miniature dictionary; no
 * external dictionary data is involved.
 *
 * <p>Source strings are written as Unicode escapes to keep this file ASCII-only; the
 * class works over the same miniature Japanese dictionary as the sibling usage
 * example, whose javadoc spells out each fixture word.</p>
 */
public class LatticeTokenizerTest {

  private static final String LEXICON_CSV = "lexicon.csv";
  private static final String MATRIX_DEF = "matrix.def";
  private static final String CHAR_DEF = "char.def";
  private static final String UNK_DEF = "unk.def";

  /** UTF-8 lead byte with the required continuation byte omitted. */
  private static final byte TRUNCATED_UTF8_LEAD_BYTE = (byte) 0xC3;

  /** A one by one connection matrix charging cost zero, for single-context fixtures. */
  private static final String UNIT_MATRIX = "1 1\n0 0 0\n";

  /**
   * The {@code char.def} line defining the DEFAULT category: it does not invoke
   * unknown-word handling beside a lexicon match, it groups a whole run into one
   * candidate, and it offers no fixed-length candidates.
   */
  private static final String DEFAULT_CATEGORY_LINE = "DEFAULT 0 1 0";

  /** The {@code unk.def} template line for the DEFAULT category. */
  private static final String DEFAULT_UNKNOWN_TEMPLATE = "DEFAULT,0,0,10000,symbol,unknown";

  @TempDir
  static Path directory;

  private static LatticeTokenizer tokenizer;

  @BeforeAll
  static void loadDictionary() throws IOException {
    write(LEXICON_CSV, String.join("\n",
        "\u6771\u4EAC,0,0,3000,noun,proper",
        "\u4EAC\u90FD,0,0,3000,noun,proper",
        "\u6771,0,0,6000,noun,common",
        "\u90FD,0,0,4000,noun,suffix",
        "\u306B,0,0,1000,particle,case",
        "\u884C\u304F,0,0,3000,verb,base",
        ""));
    write(MATRIX_DEF, UNIT_MATRIX);
    write(CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "KANJI 0 0 2",
        "HIRAGANA 0 1 0",
        "LATIN 1 1 0",
        "",
        "0x3041..0x3096 HIRAGANA",
        "0x4E00..0x9FFF KANJI",
        "0x0041..0x005A LATIN",
        "0x0061..0x007A LATIN",
        ""));
    write(UNK_DEF, String.join("\n",
        DEFAULT_UNKNOWN_TEMPLATE,
        "LATIN,0,0,4000,noun,foreign",
        "KANJI,0,0,8000,noun,unknown",
        "HIRAGANA,0,0,9000,particle,unknown",
        ""));
    tokenizer = new LatticeTokenizer(MecabDictionary.load(directory));
  }

  /** Writes one UTF-8 dictionary file into the shared dictionary directory. */
  private static void write(String name, String content) throws IOException {
    write(directory, name, content);
  }

  /** Writes one UTF-8 dictionary file into a test-supplied directory. */
  private static void write(Path target, String name, String content) throws IOException {
    Files.write(target.resolve(name), content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void testLatticePrefersTheCheaperSegmentation() {
    // Tokyo plus the metropolis suffix must beat the competing reading east plus Kyoto.
    final String text = "\u6771\u4EAC\u90FD\u306B\u884C\u304F";
    Assertions.assertArrayEquals(
        new String[] {"\u6771\u4EAC", "\u90FD", "\u306B", "\u884C\u304F"},
        tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 2), new Span(2, 3), new Span(3, 4), new Span(4, 6)},
        tokenizer.tokenizePos(text));
  }

  @Test
  void testMorphemesCarryDictionaryFeatures() {
    final List<Morpheme> morphemes =
        tokenizer.analyze("\u6771\u4EAC\u90FD\u306B\u884C\u304F");
    Assertions.assertEquals(4, morphemes.size());
    Assertions.assertEquals(List.of("noun", "proper"), morphemes.get(0).features());
    Assertions.assertEquals(List.of("particle", "case"), morphemes.get(2).features());
    Assertions.assertFalse(morphemes.get(0).unknown());
  }

  @Test
  void testUnknownLatinRunGroupsIntoOneMorpheme() {
    final List<Morpheme> morphemes = tokenizer.analyze("ABC\u306B\u884C\u304F");
    Assertions.assertEquals(3, morphemes.size());
    Assertions.assertEquals("ABC", morphemes.get(0).surface());
    Assertions.assertTrue(morphemes.get(0).unknown());
    Assertions.assertEquals(List.of("noun", "foreign"), morphemes.get(0).features());
  }

  @Test
  void testUnknownKanjiPreferOneMorphemeOverTwo() {
    final List<Morpheme> morphemes = tokenizer.analyze("\u5CE0\u9053\u306B\u884C\u304F");
    Assertions.assertEquals(3, morphemes.size());
    Assertions.assertEquals("\u5CE0\u9053", morphemes.get(0).surface());
    Assertions.assertTrue(morphemes.get(0).unknown());
  }

  /**
   * Verifies that an unknown-word candidate never spans a character category boundary.
   * An unlisted kanji directly followed by a Latin letter must be analyzed as two
   * morphemes of their own categories, never as one KANJI morpheme whose surface glues
   * the kanji to the letter.
   */
  @Test
  void testUnknownCandidatesNeverSpanCategoryBoundaries() {
    final String text = "\u5CE0a";
    Assertions.assertArrayEquals(new String[] {"\u5CE0", "a"}, tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {new Span(0, 1), new Span(1, 2)},
        tokenizer.tokenizePos(text));
    final List<Morpheme> morphemes = tokenizer.analyze(text);
    Assertions.assertEquals(List.of("noun", "unknown"), morphemes.get(0).features());
    Assertions.assertEquals(List.of("noun", "foreign"), morphemes.get(1).features());
  }

  /**
   * Verifies that bounding unknown-word candidates by the category run does not under
   * generate inside the run: a two-kanji unlisted run followed by a Latin letter still
   * offers the length-two KANJI candidate, which wins over two single-kanji morphemes.
   */
  @Test
  void testUnknownRunStillOffersWithinCategoryLengths() {
    final String text = "\u5CE0\u9053a";
    Assertions.assertArrayEquals(new String[] {"\u5CE0\u9053", "a"},
        tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {new Span(0, 2), new Span(2, 3)},
        tokenizer.tokenizePos(text));
  }

  @Test
  void testWhitespaceSeparatesAndIsNeverAMorpheme() {
    final String text = "\u6771\u4EAC \u306B \u884C\u304F";
    Assertions.assertArrayEquals(
        new String[] {"\u6771\u4EAC", "\u306B", "\u884C\u304F"},
        tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 2), new Span(3, 4), new Span(5, 7)},
        tokenizer.tokenizePos(text));
    Assertions.assertEquals(0, tokenizer.analyze("   ").size());
    Assertions.assertEquals(0, tokenizer.analyze("").size());
  }

  /**
   * Verifies that empty input yields empty results from every view of the tokenizer.
   */
  @Test
  void testEmptyInputYieldsEmptyResults() {
    Assertions.assertArrayEquals(new String[0], tokenizer.tokenize(""));
    Assertions.assertArrayEquals(new Span[0], tokenizer.tokenizePos(""));
  }

  /**
   * Verifies single-character input for a listed surface and for an unlisted kanji:
   * both come back as exactly one morpheme covering {@code [0, 1)}, and only the
   * unlisted one is marked unknown.
   */
  @Test
  void testSingleCharacterInput() {
    Assertions.assertArrayEquals(new String[] {"\u306B"}, tokenizer.tokenize("\u306B"));
    Assertions.assertArrayEquals(new Span[] {new Span(0, 1)}, tokenizer.tokenizePos("\u306B"));
    Assertions.assertFalse(tokenizer.analyze("\u306B").get(0).unknown());

    final List<Morpheme> unknown = tokenizer.analyze("\u5CE0");
    Assertions.assertEquals(1, unknown.size());
    Assertions.assertEquals("\u5CE0", unknown.get(0).surface());
    Assertions.assertEquals(new Span(0, 1), unknown.get(0).span());
    Assertions.assertTrue(unknown.get(0).unknown());
  }

  /**
   * Verifies input made entirely of characters absent from both the lexicon and the
   * {@code char.def} mappings: they fall into the DEFAULT category, whose grouping
   * setting joins the whole same-category run into one unknown morpheme carrying the
   * DEFAULT template's features.
   */
  @Test
  void testEntirelyUnknownInputGroupsIntoOneDefaultMorpheme() {
    final List<Morpheme> morphemes = tokenizer.analyze("\u2460\u2461\u2462");
    Assertions.assertEquals(1, morphemes.size());
    Assertions.assertEquals("\u2460\u2461\u2462", morphemes.get(0).surface());
    Assertions.assertEquals(new Span(0, 3), morphemes.get(0).span());
    Assertions.assertTrue(morphemes.get(0).unknown());
    Assertions.assertEquals(List.of("symbol", "unknown"), morphemes.get(0).features());
  }

  /**
   * Verifies a mixed run of known and unknown text: the lexicon words around an
   * unmapped character are kept intact, the unmapped character becomes its own
   * unknown morpheme, and every span stays in original text coordinates.
   */
  @Test
  void testMixedKnownAndUnknownRuns() {
    final String text = "\u6771\u4EAC\u2460\u306B\u884C\u304F";
    Assertions.assertArrayEquals(
        new String[] {"\u6771\u4EAC", "\u2460", "\u306B", "\u884C\u304F"},
        tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 2), new Span(2, 3), new Span(3, 4), new Span(4, 6)},
        tokenizer.tokenizePos(text));
    final List<Morpheme> morphemes = tokenizer.analyze(text);
    Assertions.assertFalse(morphemes.get(0).unknown());
    Assertions.assertTrue(morphemes.get(1).unknown());
    Assertions.assertFalse(morphemes.get(2).unknown());
  }

  /**
   * Verifies that spans keep original text coordinates when the interesting content
   * does not start at position zero because of leading whitespace.
   */
  @Test
  void testSpansStayOriginalAfterLeadingWhitespace() {
    final String text = "  \u6771\u4EAC\u90FD\u306B\u884C\u304F";
    Assertions.assertArrayEquals(
        new String[] {"\u6771\u4EAC", "\u90FD", "\u306B", "\u884C\u304F"},
        tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(2, 4), new Span(4, 5), new Span(5, 6), new Span(6, 8)},
        tokenizer.tokenizePos(text));
  }

  /**
   * Verifies that a lexicon row with fewer than the four mandatory columns is
   * rejected at load time.
   */
  @Test
  void testShortLexiconRowFailsLoud(@TempDir Path broken) throws IOException {
    // The rest of the dictionary is well formed, so the short row is what load rejects.
    writeUnitMatrixDictionary(broken);
    write(broken, LEXICON_CSV, "\u6771,0,0\n");
    Assertions.assertThrows(IOException.class, () -> MecabDictionary.load(broken));
  }

  /**
   * Verifies that a non-numeric cost column in a lexicon row is rejected at load
   * time.
   */
  @Test
  void testNonNumericLexiconCostFailsLoud(@TempDir Path broken) throws IOException {
    // The rest of the dictionary is well formed, so the cost column is what load rejects.
    writeUnitMatrixDictionary(broken);
    write(broken, LEXICON_CSV, "\u6771,0,0,abc,noun\n");
    Assertions.assertThrows(IOException.class, () -> MecabDictionary.load(broken));
  }

  /**
   * Verifies that a {@code matrix.def} data line with the wrong number of fields is
   * rejected at load time.
   */
  @Test
  void testMalformedMatrixLineFailsLoud(@TempDir Path broken) throws IOException {
    write(broken, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    write(broken, MATRIX_DEF, "1 1\n0 0\n");
    Assertions.assertThrows(IOException.class, () -> MecabDictionary.load(broken));
  }

  /**
   * Verifies that a {@code char.def} code point mapping without a category name is
   * rejected at load time.
   */
  @Test
  void testCharDefMappingWithoutCategoryFailsLoud(@TempDir Path broken)
      throws IOException {
    write(broken, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    write(broken, MATRIX_DEF, UNIT_MATRIX);
    write(broken, CHAR_DEF, DEFAULT_CATEGORY_LINE + "\n0x4E00..0x9FFF\n");
    Assertions.assertThrows(IOException.class, () -> MecabDictionary.load(broken));
  }

  /**
   * Verifies the fail-loud path when a loadable dictionary cannot cover the input: the
   * {@code unk.def} has no DEFAULT template, so a character with neither a lexicon
   * entry nor a category template stops segmentation with an exception instead of
   * being dropped silently.
   */
  @Test
  void testMissingDefaultTemplateFailsLoudAtTokenizeTime(@TempDir Path partial)
      throws IOException {
    write(partial, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    write(partial, MATRIX_DEF, UNIT_MATRIX);
    write(partial, CHAR_DEF, DEFAULT_CATEGORY_LINE + "\nKANJI 0 0 2\n0x4E00..0x9FFF KANJI\n");
    write(partial, UNK_DEF, "KANJI,0,0,8000,noun\n");
    final LatticeTokenizer limited =
        new LatticeTokenizer(MecabDictionary.load(partial));
    Assertions.assertThrows(IllegalStateException.class, () -> limited.analyze("\u2460"));
  }

  /**
   * Verifies that a directory holding a lexicon but none of the definition files is
   * rejected at load time, naming the first file that is missing.
   */
  @Test
  void testMissingDefinitionFileFailsLoud(@TempDir Path broken) throws IOException {
    write(broken, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(broken));
    Assertions.assertEquals("required dictionary file is missing: "
        + broken.resolve(MATRIX_DEF), e.getMessage());
  }

  /**
   * Verifies that a {@code char.def} without the mandatory DEFAULT category is rejected
   * at load time rather than leaving unmapped code points without a fallback.
   */
  @Test
  void testCharDefWithoutDefaultCategoryFailsLoud(@TempDir Path broken) throws IOException {
    write(broken, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    write(broken, MATRIX_DEF, UNIT_MATRIX);
    write(broken, CHAR_DEF, "KANJI 0 0 2\n0x4E00..0x9FFF KANJI\n");
    write(broken, UNK_DEF, "KANJI,0,0,8000,noun\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(broken));
    Assertions.assertEquals("char.def defines no DEFAULT category: "
        + broken.resolve(CHAR_DEF), e.getMessage());
  }

  /**
   * Verifies that a directory with the definition files but no lexicon entry at all is
   * rejected at load time, since no text could be segmented against it.
   */
  @Test
  void testDictionaryWithoutLexiconEntriesFailsLoud(@TempDir Path empty) throws IOException {
    writeUnitMatrixDictionary(empty);
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(empty));
    Assertions.assertEquals("no lexicon entries found under " + empty, e.getMessage());
  }

  /**
   * Verifies that an empty {@code matrix.def} is reported as such instead of as a
   * malformed header with nothing to show.
   */
  @Test
  void testEmptyMatrixDefFailsLoud(@TempDir Path broken) throws IOException {
    write(broken, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    write(broken, MATRIX_DEF, "");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(broken));
    Assertions.assertEquals("empty matrix.def under " + broken, e.getMessage());
  }

  /**
   * Verifies the {@code char.def} fail-loud paths that a malformed line can take: a
   * descending code point range, a code point outside the Unicode range, a code point
   * field that is not hexadecimal, and a category line missing its length column.
   *
   * @param charDef The {@code char.def} content under test.
   * @param broken The directory the fixture dictionary is written into.
   * @throws IOException Thrown if writing the fixture fails.
   */
  @ParameterizedTest(name = "[{index}] char.def {0}")
  @ValueSource(strings = {
      DEFAULT_CATEGORY_LINE + "\n0x0110..0x0100 LATIN\n",
      DEFAULT_CATEGORY_LINE + "\n0x110000 LATIN\n",
      DEFAULT_CATEGORY_LINE + "\n0xZZ LATIN\n",
      "DEFAULT 0 1\n",
      "DEFAULT 2 1 0\n",
      "DEFAULT 0 true 0\n",
      "DEFAULT 0 1 -1\n"})
  void testMalformedCharDefFailsLoud(String charDef, @TempDir Path broken)
      throws IOException {
    write(broken, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    write(broken, MATRIX_DEF, UNIT_MATRIX);
    write(broken, CHAR_DEF, charDef);
    write(broken, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");
    Assertions.assertThrows(IOException.class, () -> MecabDictionary.load(broken));
  }

  /**
   * Verifies that a MeCab-style quoted CSV field may contain a comma, with {@code ""}
   * escaping a literal quote, and that the loaded features keep both intact.
   */
  @Test
  void testQuotedCsvFieldWithCommaLoads(@TempDir Path quoted) throws IOException {
    write(quoted, LEXICON_CSV,
        "\u6771,0,0,3000,\"noun,common\",\"say \"\"hi\"\"\"\n");
    write(quoted, MATRIX_DEF, UNIT_MATRIX);
    write(quoted, CHAR_DEF, DEFAULT_CATEGORY_LINE + "\n");
    write(quoted, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");

    final List<Morpheme> morphemes =
        new LatticeTokenizer(MecabDictionary.load(quoted)).analyze("\u6771");
    Assertions.assertEquals(1, morphemes.size());
    Assertions.assertEquals(List.of("noun,common", "say \"hi\""),
        morphemes.get(0).features());
  }

  /**
   * Verifies that an {@code unk.def} template naming a category {@code char.def} never
   * defined fails at load with {@link IOException}.
   */
  @Test
  void testUnkDefUndefinedCategoryFailsLoud(@TempDir Path ghost) throws IOException {
    write(ghost, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    write(ghost, MATRIX_DEF, UNIT_MATRIX);
    write(ghost, CHAR_DEF, DEFAULT_CATEGORY_LINE + "\n");
    write(ghost, UNK_DEF, "GHOST,0,0,8000,noun\n");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(ghost));
    Assertions.assertEquals("unk.def names the undefined category GHOST: "
        + ghost.resolve(UNK_DEF), e.getMessage());
  }

  /**
   * Writes a miniature dictionary whose {@code char.def} maps a supplementary plane
   * range, the shape a UniDic-style distribution uses for the CJK extension blocks.
   *
   * @param target The directory to write the dictionary files into. Must not be
   *               {@code null} and must exist.
   * @throws IOException Thrown if writing any of the files fails.
   */
  private static void writeSupplementaryDictionary(Path target) throws IOException {
    write(target, LEXICON_CSV, "\u6771,0,0,6000,noun,common\n");
    write(target, MATRIX_DEF, UNIT_MATRIX);
    write(target, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "KANJI 0 0 2",
        "LATIN 1 1 0",
        "",
        "0x4E00..0x9FFF KANJI",
        "0x20000..0x2A6DF KANJI",
        "0x0061..0x007A LATIN",
        ""));
    write(target, UNK_DEF, String.join("\n",
        DEFAULT_UNKNOWN_TEMPLATE,
        "KANJI,0,0,8000,noun,unknown",
        "LATIN,0,0,4000,noun,foreign",
        ""));
  }

  /**
   * Verifies that a {@code char.def} range above U+FFFF is honored rather than
   * discarded: a supplementary plane ideograph inside the mapped range takes the
   * category the range names, while a supplementary code point outside every mapped
   * range still falls back to DEFAULT.
   */
  @Test
  void testSupplementaryCharDefRangeIsHonored(@TempDir Path supplementary)
      throws IOException {
    writeSupplementaryDictionary(supplementary);
    final MecabDictionary dictionary = MecabDictionary.load(supplementary);
    // U+20BB7 is a CJK extension B ideograph inside the mapped range.
    Assertions.assertEquals("KANJI", dictionary.categoryOf(0x20BB7).name());
    Assertions.assertEquals("KANJI", dictionary.categoryOf(0x6771).name());
    Assertions.assertEquals("DEFAULT", dictionary.categoryOf(0x2460).name());
    Assertions.assertEquals("DEFAULT", dictionary.categoryOf(0x2A6E0).name());
    Assertions.assertEquals("LATIN", dictionary.categoryOf('a').name());
  }

  /**
   * Verifies that a supplementary plane ideograph is analyzed as the single character
   * it is: one morpheme whose span covers both code units and which carries the
   * features of the category its {@code char.def} range names, never one morpheme per
   * surrogate. The second case shows the category's length templates count characters,
   * not code units, so a run of two supplementary ideographs is still reachable by the
   * length-two template.
   */
  @Test
  void testSupplementaryIdeographIsOneMorpheme(@TempDir Path supplementary)
      throws IOException {
    writeSupplementaryDictionary(supplementary);
    final LatticeTokenizer supplementaryTokenizer =
        new LatticeTokenizer(MecabDictionary.load(supplementary));
    // U+20BB7 written as its surrogate pair, per this file's ASCII-only convention.
    final String text = "\uD842\uDFB7";
    final List<Morpheme> morphemes = supplementaryTokenizer.analyze(text);
    Assertions.assertEquals(1, morphemes.size());
    Assertions.assertEquals(text, morphemes.get(0).surface());
    Assertions.assertEquals(new Span(0, 2), morphemes.get(0).span());
    Assertions.assertEquals(List.of("noun", "unknown"), morphemes.get(0).features());

    final List<Morpheme> pair = supplementaryTokenizer.analyze(text + text);
    Assertions.assertEquals(1, pair.size());
    Assertions.assertEquals(new Span(0, 4), pair.get(0).span());
    Assertions.assertEquals(List.of("noun", "unknown"), pair.get(0).features());
  }

  /**
   * Verifies that a supplementary plane ideograph does not absorb neighbouring text of
   * another category: the ideograph and an unmapped symbol beside it stay two
   * morphemes, each span covering whole characters.
   */
  @Test
  void testSupplementaryIdeographDoesNotAbsorbItsNeighbour(@TempDir Path supplementary)
      throws IOException {
    writeSupplementaryDictionary(supplementary);
    final LatticeTokenizer supplementaryTokenizer =
        new LatticeTokenizer(MecabDictionary.load(supplementary));
    final String text = "\uD842\uDFB7\u2460";
    Assertions.assertArrayEquals(new String[] {"\uD842\uDFB7", "\u2460"},
        supplementaryTokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {new Span(0, 2), new Span(2, 3)},
        supplementaryTokenizer.tokenizePos(text));
  }

  /**
   * Writes every dictionary file except the lexicon, so a test can supply a lexicon of
   * its own against a one by one connection matrix.
   *
   * @param target The directory to write the dictionary files into. Must not be
   *               {@code null} and must exist.
   * @throws IOException Thrown if writing any of the files fails.
   */
  private static void writeUnitMatrixDictionary(Path target) throws IOException {
    write(target, MATRIX_DEF, UNIT_MATRIX);
    write(target, CHAR_DEF, DEFAULT_CATEGORY_LINE + "\n");
    write(target, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");
  }

  /**
   * Verifies that a lexicon row whose right context id is outside the
   * {@code matrix.def} dimensions is rejected at load time, naming the file, the line,
   * and the offending id, rather than reaching the cost matrix with an out of range
   * index during segmentation.
   */
  @Test
  void testRightContextIdBeyondMatrixFailsLoudAtLoad(@TempDir Path mismatched)
      throws IOException {
    writeUnitMatrixDictionary(mismatched);
    write(mismatched, LEXICON_CSV, "\u6771,0,5,3000,noun\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(mismatched));
    Assertions.assertEquals("malformed entry at " + mismatched.resolve(LEXICON_CSV)
        + " line 1: right context id 5 is outside the matrix.def dimensions 1 1",
        e.getMessage());
  }

  /**
   * Verifies that a lexicon row whose left context id is outside the {@code matrix.def}
   * dimensions is rejected at load time, naming the file, the line, and the offending
   * id.
   */
  @Test
  void testLeftContextIdBeyondMatrixFailsLoudAtLoad(@TempDir Path mismatched)
      throws IOException {
    writeUnitMatrixDictionary(mismatched);
    write(mismatched, LEXICON_CSV, "\u6771,0,0,3000,noun\n\u90FD,7,0,3000,noun\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(mismatched));
    Assertions.assertEquals("malformed entry at " + mismatched.resolve(LEXICON_CSV)
        + " line 2: left context id 7 is outside the matrix.def dimensions 1 1",
        e.getMessage());
  }

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new LatticeTokenizer(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionary.load(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionary.load(null, StandardCharsets.UTF_8));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionary.load(directory, null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> tokenizer.analyze(null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> tokenizer.tokenize(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> tokenizer.tokenizePos(null));
  }

  /**
   * Verifies the {@link Morpheme} contract every segmentation result is built from: a
   * {@code null} span, a {@code null} or empty surface, and {@code null} features are
   * all rejected, and the feature list is copied so a later change to the caller's list
   * cannot be seen through the morpheme.
   */
  @Test
  void testMorphemeRejectsInvalidArguments() {
    final Span span = new Span(0, 1);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new Morpheme(null, "\u6771", List.of("noun"), false));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new Morpheme(span, null, List.of("noun"), false));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new Morpheme(span, "", List.of("noun"), false));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new Morpheme(span, "\u6771", null, false));

    final List<String> features = new ArrayList<>(List.of("noun"));
    final Morpheme morpheme = new Morpheme(span, "\u6771", features, false);
    features.add("proper");
    Assertions.assertEquals(List.of("noun"), morpheme.features());
  }

  /**
   * Verifies the supplementary range table's interval cutting and precedence: a later
   * {@code char.def} mapping strictly inside an earlier one wins exactly on its own
   * stretch, and the earlier category resumes after it, so the cut produces three
   * intervals from two overlapping ranges.
   */
  @Test
  void testLaterSupplementaryMappingWinsInsideAnEarlierRange(@TempDir Path overlapped)
      throws IOException {
    write(overlapped, LEXICON_CSV, "\u6771,0,0,6000,noun\n");
    write(overlapped, MATRIX_DEF, UNIT_MATRIX);
    write(overlapped, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "KANJI 0 0 2",
        "LATIN 1 1 0",
        "",
        "0x20000..0x2FFFF KANJI",
        "0x24000..0x25000 LATIN",
        ""));
    write(overlapped, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");

    final MecabDictionary dictionary = MecabDictionary.load(overlapped);
    Assertions.assertEquals("KANJI", dictionary.categoryOf(0x20000).name());
    Assertions.assertEquals("KANJI", dictionary.categoryOf(0x23FFF).name());
    Assertions.assertEquals("LATIN", dictionary.categoryOf(0x24000).name());
    Assertions.assertEquals("LATIN", dictionary.categoryOf(0x25000).name());
    Assertions.assertEquals("KANJI", dictionary.categoryOf(0x25001).name());
    Assertions.assertEquals("KANJI", dictionary.categoryOf(0x2FFFF).name());
    Assertions.assertEquals("DEFAULT", dictionary.categoryOf(0x30000).name());
  }

  /**
   * Verifies a {@code char.def} range straddling the BMP boundary: the part up to
   * U+FFFF lands in the directly indexed table and the rest in the range table, and
   * both halves answer the same category with no gap at the seam.
   */
  @Test
  void testCharDefRangeStraddlingTheBmpBoundary(@TempDir Path straddling)
      throws IOException {
    write(straddling, LEXICON_CSV, "\u6771,0,0,6000,noun\n");
    write(straddling, MATRIX_DEF, UNIT_MATRIX);
    write(straddling, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "LATIN 1 1 0",
        "",
        "0xFF00..0x10040 LATIN",
        ""));
    write(straddling, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");

    final MecabDictionary dictionary = MecabDictionary.load(straddling);
    Assertions.assertEquals("LATIN", dictionary.categoryOf(0xFF00).name());
    Assertions.assertEquals("LATIN", dictionary.categoryOf(0xFFFF).name());
    Assertions.assertEquals("LATIN", dictionary.categoryOf(0x10000).name());
    Assertions.assertEquals("LATIN", dictionary.categoryOf(0x10040).name());
    Assertions.assertEquals("DEFAULT", dictionary.categoryOf(0x10041).name());
    Assertions.assertEquals("DEFAULT", dictionary.categoryOf(0xFEFF).name());
  }

  /**
   * Verifies that a {@code char.def} mapping to a category its category section never
   * defined fails at load, naming the code point and the ghost category, instead of
   * silently falling back to DEFAULT at lookup time.
   */
  @Test
  void testMappingToUndefinedCategoryFailsLoud(@TempDir Path ghost) throws IOException {
    write(ghost, LEXICON_CSV, "\u6771,0,0,6000,noun\n");
    write(ghost, MATRIX_DEF, UNIT_MATRIX);
    write(ghost, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "",
        "0x0100..0x0110 GHOST",
        ""));
    write(ghost, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(ghost));
    Assertions.assertEquals("char.def declaration at U+0100 names the undefined"
        + " category GHOST", e.getMessage());
  }

  /**
   * Verifies that a connection cost outside the 16-bit range the binary matrix format
   * defines is rejected at load instead of being truncated by the narrowing cast into
   * a silently different cost.
   */
  @Test
  void testMatrixCostOutsideShortRangeFailsLoud(@TempDir Path broken) throws IOException {
    writeUnitMatrixDictionary(broken);
    write(broken, MATRIX_DEF, "1 1\n0 0 40000\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(broken));
    Assertions.assertEquals("malformed matrix.def line 2: connection cost 40000 is"
        + " outside the 16-bit range the format defines", e.getMessage());
  }

  /**
   * Verifies that {@code matrix.def} dimensions whose product exceeds the addressable
   * array size fail loud at the header instead of overflowing the int multiplication
   * into a negative or wrapped allocation size.
   */
  @Test
  void testMatrixDimensionProductBeyondIntRangeFailsLoud(@TempDir Path broken)
      throws IOException {
    writeUnitMatrixDictionary(broken);
    write(broken, MATRIX_DEF, "70000 70000\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(broken));
    Assertions.assertEquals("matrix.def dimensions 70000 x 70000 overflow the"
        + " addressable connection matrix", e.getMessage());
  }

  /**
   * Verifies that a single matrix dimension above {@link ResourceLimits#MAX_ENTRIES}
   * is rejected before the connection-cost array is allocated.
   */
  @Test
  void testMatrixDimensionAboveMaxEntriesFailsLoud(@TempDir Path broken) throws IOException {
    writeUnitMatrixDictionary(broken);
    final int over = ResourceLimits.MAX_ENTRIES + 1;
    write(broken, MATRIX_DEF, over + " 1\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(broken));
    Assertions.assertTrue(e.getMessage().contains("exceed safe limit of "
        + ResourceLimits.MAX_ENTRIES), e.getMessage());
  }

  /**
   * Verifies that a matrix whose cell count is above
   * {@link ResourceLimits#MAX_MATRIX_CELLS} but still below {@link Integer#MAX_VALUE}
   * is rejected. Without that bound, a header such as {@code 46340 46340} would
   * allocate about 4 GiB of shorts.
   */
  @Test
  void testMatrixCellCountAboveMaxCellsFailsLoud(@TempDir Path broken) throws IOException {
    writeUnitMatrixDictionary(broken);
    // 11600 x 11600 = 134_560_000 cells, above the default MAX_MATRIX_CELLS of 2^27.
    write(broken, MATRIX_DEF, "11600 11600\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(broken));
    Assertions.assertEquals("matrix.def dimensions 11600 x 11600 exceed safe limit of "
        + ResourceLimits.MAX_MATRIX_CELLS, e.getMessage());
  }

  /**
   * Verifies that the dimensions of a real published distribution pass the header
   * bound. mecab-ko-dic 2.1.1 declares {@code 3822 2693}, which is 10,292,646 cells:
   * above {@link ResourceLimits#MAX_ENTRIES} but a legitimate 20 MB cost matrix, so
   * the cell bound must be sized to cells rather than reusing the entry bound. The
   * load still fails on the truncated body, but with the incomplete-matrix message,
   * not the safe-limit one.
   */
  @Test
  void testKoDicSizedMatrixDimensionsPassTheHeaderBound(@TempDir Path koDic)
      throws IOException {
    writeUnitMatrixDictionary(koDic);
    write(koDic, MATRIX_DEF, "3822 2693\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(koDic));
    Assertions.assertEquals("matrix.def declares 3822 x 2693 connection costs but only 0"
        + " pairs are listed", e.getMessage());
  }

  /**
   * Verifies that a truncated {@code matrix.def} fails loud. Unlisted pairs must not
   * keep the short-array default of cost zero, the cheapest connection.
   */
  @Test
  void testIncompleteMatrixFailsLoud(@TempDir Path broken) throws IOException {
    writeUnitMatrixDictionary(broken);
    write(broken, MATRIX_DEF, "2 2\n0 0 1\n0 1 2\n1 0 3\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(broken));
    Assertions.assertEquals("matrix.def declares 2 x 2 connection costs but only 3"
        + " pairs are listed", e.getMessage());
  }

  /**
   * Verifies that a {@code matrix.def} data row naming context ids outside the
   * declared dimensions is rejected at load with the offending line and ids.
   */
  @Test
  void testMatrixRowContextIdsOutsideDimensionsFailLoud(@TempDir Path broken)
      throws IOException {
    writeUnitMatrixDictionary(broken);
    write(broken, MATRIX_DEF, "1 1\n2 0 5\n");
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(broken));
    Assertions.assertEquals("malformed matrix.def line 2: context ids 2 0 are outside"
        + " the declared dimensions 1 1", e.getMessage());
  }

  @Test
  void testZeroLengthCategoryUsesItsUnknownTemplate(@TempDir Path dictionary)
      throws IOException {
    write(dictionary, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    write(dictionary, MATRIX_DEF, UNIT_MATRIX);
    write(dictionary, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "SINGLE 1 0 0",
        "",
        "0x2460 SINGLE",
        ""));
    write(dictionary, UNK_DEF, String.join("\n",
        DEFAULT_UNKNOWN_TEMPLATE,
        "SINGLE,0,0,1000,symbol,single",
        ""));

    final Morpheme morpheme = new LatticeTokenizer(MecabDictionary.load(dictionary))
        .analyze("\u2460").get(0);

    Assertions.assertEquals(List.of("symbol", "single"), morpheme.features());
  }

  @Test
  void testRejectsEmptyLexiconSurface(@TempDir Path dictionary) throws IOException {
    writeUnitMatrixDictionary(dictionary);
    write(dictionary, LEXICON_CSV, String.join("\n",
        "\u6771,0,0,3000,noun",
        ",0,0,3000,noun",
        ""));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(dictionary));

    Assertions.assertEquals("malformed entry at " + dictionary.resolve(LEXICON_CSV)
        + " line 2: surface must not be empty", e.getMessage());
  }

  @ParameterizedTest(name = "word cost {0}")
  @ValueSource(ints = {-32769, 32768})
  void testRejectsLexiconCostOutsideShortRange(int cost, @TempDir Path dictionary)
      throws IOException {
    writeUnitMatrixDictionary(dictionary);
    write(dictionary, LEXICON_CSV, "\u6771,0,0," + cost + ",noun\n");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(dictionary));

    Assertions.assertEquals("malformed entry at " + dictionary.resolve(LEXICON_CSV)
        + " line 1: word cost " + cost
        + " is outside the 16-bit range the format defines", e.getMessage());
  }

  @Test
  void testRejectsDuplicateCharacterCategory(@TempDir Path dictionary)
      throws IOException {
    write(dictionary, LEXICON_CSV, "\u6771,0,0,3000,noun\n");
    write(dictionary, MATRIX_DEF, UNIT_MATRIX);
    write(dictionary, CHAR_DEF, DEFAULT_CATEGORY_LINE + "\nDEFAULT 1 0 2\n");
    write(dictionary, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(dictionary));

    Assertions.assertEquals("duplicate char.def category DEFAULT at line 2",
        e.getMessage());
  }

  @Test
  void testRejectsDuplicateMatrixEntry(@TempDir Path dictionary) throws IOException {
    writeUnitMatrixDictionary(dictionary);
    write(dictionary, MATRIX_DEF, "1 1\n0 0 1\n0 0 2\n");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(dictionary));

    Assertions.assertEquals("duplicate matrix.def entry 0 0 at line 3", e.getMessage());
  }

  @Test
  void testRejectsMalformedDictionaryEncoding(@TempDir Path dictionary)
      throws IOException {
    writeUnitMatrixDictionary(dictionary);
    Files.write(dictionary.resolve(LEXICON_CSV),
        new byte[] {TRUNCATED_UTF8_LEAD_BYTE, ',', '0', ',', '0', ',', '1', '\n'});

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(dictionary));

    Assertions.assertInstanceOf(MalformedInputException.class, e);
    Assertions.assertEquals("Input length = 1", e.getMessage());
  }

  @Test
  void testLongLexiconSurfaceLoads(@TempDir Path dictionary) throws IOException {
    writeUnitMatrixDictionary(dictionary);
    final String surface = "a".repeat(20_000);
    write(dictionary, LEXICON_CSV, surface + ",0,0,3000,fixture\n");

    final LatticeTokenizer longSurfaceTokenizer =
        new LatticeTokenizer(MecabDictionary.load(dictionary));

    Assertions.assertArrayEquals(new String[] {surface},
        longSurfaceTokenizer.tokenize(surface));
  }

  @Test
  void testSecondaryCharacterCategoryExtendsUnknownRun(@TempDir Path dictionary)
      throws IOException {
    write(dictionary, LEXICON_CSV, "\u6771,0,0,6000,noun\n");
    write(dictionary, MATRIX_DEF, UNIT_MATRIX);
    write(dictionary, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "KANJI 1 0 1",
        "KANJINUMERIC 1 1 0",
        "",
        "0x4E00 KANJINUMERIC KANJI",
        "0x5C71 KANJI",
        ""));
    write(dictionary, UNK_DEF, String.join("\n",
        DEFAULT_UNKNOWN_TEMPLATE,
        "KANJI,0,0,5000,noun,unknown",
        "KANJINUMERIC,0,0,1000,number,unknown",
        ""));

    final List<Morpheme> morphemes = new LatticeTokenizer(
        MecabDictionary.load(dictionary)).analyze("\u4E00\u5C71");

    Assertions.assertEquals(1, morphemes.size());
    Assertions.assertEquals("\u4E00\u5C71", morphemes.get(0).surface());
    Assertions.assertEquals(List.of("number", "unknown"), morphemes.get(0).features());
  }

  /**
   * Verifies MeCab's category-chain grouping. Character A has X and Y, B has X, and C
   * has Y. A and B intersect, while B and C do not, so the initial unknown word covers
   * {@code ab}.
   */
  @Test
  void testMultipleCategoriesUsePairwiseGrouping(@TempDir Path dictionary)
      throws IOException {
    write(dictionary, LEXICON_CSV, "z,0,0,6000,fixture\n");
    write(dictionary, MATRIX_DEF, UNIT_MATRIX);
    write(dictionary, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "X 1 1 0",
        "Y 1 1 0",
        "",
        "0x0061 X Y",
        "0x0062 X",
        "0x0063 Y",
        ""));
    write(dictionary, UNK_DEF, String.join("\n",
        DEFAULT_UNKNOWN_TEMPLATE,
        "X,0,0,1000,x,unknown",
        "Y,0,0,1000,y,unknown",
        ""));

    final LatticeTokenizer groupingTokenizer =
        new LatticeTokenizer(MecabDictionary.load(dictionary));

    Assertions.assertArrayEquals(new String[] {"ab", "c"},
        groupingTokenizer.tokenize("abc"));
  }

  /**
   * Verifies that an intermediate multi-category character can connect a run.
   * Character A has X, B has X and Y, and C has Y. MeCab advances the active
   * assignment at each position, allowing B to connect both portions.
   */
  @Test
  void testCategoryOverlapCanConnectRun(@TempDir Path dictionary)
      throws IOException {
    write(dictionary, LEXICON_CSV, "z,0,0,6000,fixture\n");
    write(dictionary, MATRIX_DEF, UNIT_MATRIX);
    write(dictionary, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "X 1 1 0",
        "Y 1 1 0",
        "",
        "0x0061 X",
        "0x0062 X Y",
        "0x0063 Y",
        ""));
    write(dictionary, UNK_DEF, String.join("\n",
        DEFAULT_UNKNOWN_TEMPLATE,
        "X,0,0,1000,x,unknown",
        "Y,0,0,1000,y,unknown",
        ""));

    final LatticeTokenizer groupingTokenizer =
        new LatticeTokenizer(MecabDictionary.load(dictionary));

    Assertions.assertArrayEquals(new String[] {"abc"},
        groupingTokenizer.tokenize("abc"));
  }

  @Test
  void testRejectsTooManyCharacterCategories(@TempDir Path dictionary)
      throws IOException {
    write(dictionary, LEXICON_CSV, "z,0,0,6000,fixture\n");
    write(dictionary, MATRIX_DEF, UNIT_MATRIX);
    final StringBuilder charDef = new StringBuilder(DEFAULT_CATEGORY_LINE).append('\n');
    for (int i = 1; i < 18; i++) {
      charDef.append('C').append(i).append(" 0 0 1\n");
    }
    write(dictionary, CHAR_DEF, charDef.toString());
    write(dictionary, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(dictionary));

    Assertions.assertEquals("char.def defines 18 categories; MeCab supports at most 17",
        e.getMessage());
  }

  @Test
  void testRejectsCharacterCategoryLengthAboveMecabLimit(@TempDir Path dictionary)
      throws IOException {
    write(dictionary, LEXICON_CSV, "z,0,0,6000,fixture\n");
    write(dictionary, MATRIX_DEF, UNIT_MATRIX);
    write(dictionary, CHAR_DEF, "DEFAULT 0 1 16\n");
    write(dictionary, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(dictionary));

    Assertions.assertEquals("category LENGTH must be between 0 and 15 at "
        + dictionary.resolve(CHAR_DEF) + " line 1", e.getMessage());
  }

  @Test
  void testRejectsUndefinedSecondaryCharacterCategory(@TempDir Path dictionary)
      throws IOException {
    write(dictionary, LEXICON_CSV, "\u6771,0,0,6000,noun\n");
    write(dictionary, MATRIX_DEF, UNIT_MATRIX);
    write(dictionary, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "KANJINUMERIC 1 1 0",
        "",
        "0x4E00 KANJINUMERIC GHOST",
        ""));
    write(dictionary, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(dictionary));

    Assertions.assertEquals("char.def declaration at U+4E00 names the undefined"
        + " category GHOST", e.getMessage());
  }

  @Test
  void testRejectsUndefinedCategoryOnShadowedMapping(@TempDir Path dictionary)
      throws IOException {
    write(dictionary, LEXICON_CSV, "z,0,0,6000,fixture\n");
    write(dictionary, MATRIX_DEF, UNIT_MATRIX);
    write(dictionary, CHAR_DEF, String.join("\n",
        DEFAULT_CATEGORY_LINE,
        "LATIN 1 1 0",
        "",
        "0x0061 GHOST",
        "0x0061 LATIN",
        ""));
    write(dictionary, UNK_DEF, DEFAULT_UNKNOWN_TEMPLATE + "\n");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionary.load(dictionary));

    Assertions.assertEquals("char.def declaration at U+0061 names the undefined"
        + " category GHOST", e.getMessage());
  }
}
