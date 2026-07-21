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

package opennlp.tools.stemmer.hunspell;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.stemmer.Stemmer;

/**
 * Tests the affix engine against a project-authored miniature dictionary; no external
 * dictionary data is involved.
 */
public class HunspellStemmerTest {

  private static final String AFFIX = String.join("\n",
      "# project-authored test fixture",
      "SET UTF-8",
      "",
      "PFX U Y 1",
      "PFX U 0 un .",
      "",
      "SFX S Y 3",
      "SFX S 0 s [^sxy]",
      "SFX S y ies y",
      "SFX S 0 es [sx]",
      "",
      "SFX G Y 2",
      "SFX G 0 ing [^e]",
      "SFX G e ing e",
      "");

  private static final String WORDS = String.join("\n",
      "6",
      "lock/USG",
      "pony/S",
      "make/G",
      "cat/S",
      "box/S",
      "fish",
      "");

  private static HunspellStemmer stemmer;

  @BeforeAll
  static void loadDictionary() throws IOException {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(AFFIX.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(WORDS.getBytes(StandardCharsets.UTF_8)));
    stemmer = new HunspellStemmer(dictionary);
  }

  @Test
  void testSuffixRules() {
    Assertions.assertEquals("cat", stemmer.stem("cats").toString());
    Assertions.assertEquals("pony", stemmer.stem("ponies").toString());
    Assertions.assertEquals("box", stemmer.stem("boxes").toString());
    Assertions.assertEquals("make", stemmer.stem("making").toString());
    Assertions.assertEquals("lock", stemmer.stem("locking").toString());
  }

  @Test
  void testPrefixAndCrossProduct() {
    Assertions.assertEquals("lock", stemmer.stem("unlock").toString());
    Assertions.assertEquals("lock", stemmer.stem("unlocks").toString());
    Assertions.assertEquals("lock", stemmer.stem("unlocking").toString());
  }

  @Test
  void testConditionsBlockWrongAnalyses() {
    // the s rule requires a stem not ending in s, x, or y
    Assertions.assertEquals("boxs", stemmer.stem("boxs").toString());
    // cat carries no G flag, so no ing analysis exists
    Assertions.assertEquals("cating", stemmer.stem("cating").toString());
    // fish carries no flags at all
    Assertions.assertEquals("fishs", stemmer.stem("fishs").toString());
  }

  @Test
  void testDirectLookupAndCase() {
    Assertions.assertEquals("fish", stemmer.stem("fish").toString());
    Assertions.assertEquals("cat", stemmer.stem("Cats").toString());
    Assertions.assertEquals("lock", stemmer.stem("Unlocks").toString());
  }

  @Test
  void testUnknownWordsPassThroughUnchanged() {
    Assertions.assertEquals("zebras", stemmer.stem("zebras").toString());
    Assertions.assertEquals(1, stemmer.stemAll("zebras").size());
  }

  @Test
  void testStemAllReportsEveryAnalysis() {
    Assertions.assertEquals(1, stemmer.stemAll("unlocks").size());
    Assertions.assertEquals("lock", stemmer.stemAll("unlocks").get(0).toString());
    // the surface form itself is an entry AND an analysis target
    Assertions.assertEquals("lock", stemmer.stemAll("lock").get(0).toString());
  }

  @Test
  void testTwofoldSuffixesThroughContinuationClasses() throws IOException {
    final String affix = String.join("\n",
        "SET UTF-8",
        "SFX A Y 1",
        "SFX A 0 er/B .",
        "SFX B Y 1",
        "SFX B 0 s .",
        "");
    final String words = String.join("\n", "1", "kind/A", "");
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(affix.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(words.getBytes(StandardCharsets.UTF_8)));
    final HunspellStemmer twofold = new HunspellStemmer(dictionary);

    Assertions.assertEquals("kind", twofold.stem("kinder").toString());
    Assertions.assertEquals("kind", twofold.stem("kinders").toString());
    // B alone never applies: no entry carries it directly
    Assertions.assertEquals("kinds", twofold.stem("kinds").toString());
  }

  @Test
  void testNumericFlagMode() throws IOException {
    final String affix = String.join("\n",
        "SET UTF-8",
        "FLAG num",
        "SFX 100 Y 1",
        "SFX 100 0 s .",
        "");
    final String words = String.join("\n", "1", "walk/100,7", "");
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(affix.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(words.getBytes(StandardCharsets.UTF_8)));
    Assertions.assertEquals("walk",
        new HunspellStemmer(dictionary).stem("walks").toString());
  }

  @Test
  void testLongFlagMode() throws IOException {
    final String affix = String.join("\n",
        "SET UTF-8",
        "FLAG long",
        "SFX Aa Y 1",
        "SFX Aa 0 s .",
        "");
    final String words = String.join("\n", "1", "walk/AaBb", "");
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(affix.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(words.getBytes(StandardCharsets.UTF_8)));
    Assertions.assertEquals("walk",
        new HunspellStemmer(dictionary).stem("walks").toString());
  }

  @Test
  void testFactoryHandsOutWorkingStemmers() throws IOException {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(AFFIX.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(WORDS.getBytes(StandardCharsets.UTF_8)));
    final Stemmer fresh = new HunspellStemmerFactory(dictionary).newStemmer();
    Assertions.assertEquals("pony", fresh.stem("ponies").toString());
  }

  /**
   * Loads a dictionary from in-memory affix and word-list content, both encoded as
   * UTF-8, through the stream-based entry point.
   *
   * @param affix The {@code .aff} content. Must not be {@code null}.
   * @param words The {@code .dic} content. Must not be {@code null}.
   * @return The loaded dictionary. Never {@code null}.
   * @throws IOException Thrown if the content is malformed.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  private static HunspellDictionary load(String affix, String words) throws IOException {
    if (affix == null || words == null) {
      throw new IllegalArgumentException("affix and words must not be null");
    }
    return HunspellDictionary.load(
        new ByteArrayInputStream(affix.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(words.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Verifies that cross-product combination of a prefix with a suffix only happens
   * when both rules declare the cross-product marker {@code Y}. Removing just the one
   * affix whose rule exists keeps working; the combined form must not be analyzed.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCrossProductRequiresBothRulesOptIn() throws IOException {
    // the prefix rule declares N, so it never combines with the suffix
    final HunspellStemmer prefixOptedOut = new HunspellStemmer(load(String.join("\n",
        "PFX U N 1",
        "PFX U 0 un .",
        "SFX S Y 1",
        "SFX S 0 s .",
        ""), "1\nlock/US\n"));
    Assertions.assertEquals("lock", prefixOptedOut.stem("unlock").toString());
    Assertions.assertEquals("lock", prefixOptedOut.stem("locks").toString());
    Assertions.assertEquals("unlocks", prefixOptedOut.stem("unlocks").toString());

    // the suffix rule declares N, so the combined form is likewise not analyzed
    final HunspellStemmer suffixOptedOut = new HunspellStemmer(load(String.join("\n",
        "PFX U Y 1",
        "PFX U 0 un .",
        "SFX S N 1",
        "SFX S 0 s .",
        ""), "1\nlock/US\n"));
    Assertions.assertEquals("lock", suffixOptedOut.stem("unlock").toString());
    Assertions.assertEquals("lock", suffixOptedOut.stem("locks").toString());
    Assertions.assertEquals("unlocks", suffixOptedOut.stem("unlocks").toString());
  }

  /**
   * Verifies that a non-negated character class rejects a candidate stem: the
   * {@code es} rule requires a stem ending in {@code s} or {@code x}, so removing
   * {@code es} from {@code cates} produces {@code cat}, which the class rejects, and
   * the surface form falls through unchanged.
   */
  @Test
  void testPositiveCharacterClassRejectsCandidate() {
    Assertions.assertEquals("cates", stemmer.stem("cates").toString());
    Assertions.assertEquals(1, stemmer.stemAll("cates").size());
  }

  /**
   * Verifies that the {@code SET} declaration selects the charset both files are
   * decoded with: a word list holding the byte {@code 0xE9} only maps to the word
   * caf\u00E9 (e with acute accent) when decoded as ISO-8859-1, as the affix file declares.
   *
   * @throws IOException Thrown if the fixture fails to load.
   */
  @Test
  void testSetDeclarationSelectsEncoding() throws IOException {
    final Charset latin1 = StandardCharsets.ISO_8859_1;
    final String affix = String.join("\n",
        "SET ISO8859-1",
        "SFX S Y 1",
        "SFX S 0 s .",
        "");
    final String words = "1\ncaf\u00E9/S\n";
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(affix.getBytes(latin1)),
        new ByteArrayInputStream(words.getBytes(latin1)));
    final HunspellStemmer latin1Stemmer = new HunspellStemmer(dictionary);
    Assertions.assertEquals("caf\u00E9", latin1Stemmer.stem("caf\u00E9s").toString());
    Assertions.assertEquals("caf\u00E9", latin1Stemmer.stem("caf\u00E9").toString());
  }

  /**
   * Verifies that continuation classes also work in {@code FLAG long} mode, where a
   * flag is a two-character run: the plural {@code Bb} stacks on the agentive
   * {@code Aa} to analyze a twofold suffix chain.
   *
   * @throws IOException Thrown if the fixture fails to load.
   */
  @Test
  void testLongFlagContinuation() throws IOException {
    final HunspellStemmer longFlags = new HunspellStemmer(load(String.join("\n",
        "FLAG long",
        "SFX Aa Y 1",
        "SFX Aa 0 er/Bb .",
        "SFX Bb Y 1",
        "SFX Bb 0 s .",
        ""), "1\nkind/Aa\n"));
    Assertions.assertEquals("kind", longFlags.stem("kinder").toString());
    Assertions.assertEquals("kind", longFlags.stem("kinders").toString());
  }

  /**
   * Verifies that cross-product prefix and suffix combination also works in
   * {@code FLAG num} mode, where flags are comma-separated decimal numbers.
   *
   * @throws IOException Thrown if the fixture fails to load.
   */
  @Test
  void testNumericFlagCrossProduct() throws IOException {
    final HunspellStemmer numericFlags = new HunspellStemmer(load(String.join("\n",
        "FLAG num",
        "PFX 1 Y 1",
        "PFX 1 0 un .",
        "SFX 2 Y 1",
        "SFX 2 0 s .",
        ""), "1\nlock/1,2\n"));
    Assertions.assertEquals("lock", numericFlags.stem("unlock").toString());
    Assertions.assertEquals("lock", numericFlags.stem("locks").toString());
    Assertions.assertEquals("lock", numericFlags.stem("unlocks").toString());
  }

  /**
   * Verifies the exact exception and message for each malformed {@code FLAG}
   * declaration the parser detects: a missing mode and an unrecognized mode name.
   */
  @Test
  void testMalformedFlagDeclarationMessages() {
    IOException e = Assertions.assertThrows(IOException.class,
        () -> load("FLAG\n", "0\n"));
    Assertions.assertEquals("FLAG line without a mode at line 1", e.getMessage());

    e = Assertions.assertThrows(IOException.class, () -> load("FLAG short\n", "0\n"));
    Assertions.assertEquals("unsupported FLAG mode 'short' at line 1", e.getMessage());
  }

  /**
   * Verifies the exact exception and message for each malformed affix block the
   * parser detects: a header with too few fields, a non-numeric rule count, a block
   * with fewer rule lines than its count announces, a rule line whose type tag does
   * not match its header, and an unterminated character class in a condition.
   */
  @Test
  void testMalformedAffixBlockMessages() {
    IOException e = Assertions.assertThrows(IOException.class,
        () -> load("PFX U Y\n", "0\n"));
    Assertions.assertEquals("malformed affix header at line 1", e.getMessage());

    e = Assertions.assertThrows(IOException.class,
        () -> load("SFX S Y many\nSFX S 0 s .\n", "0\n"));
    Assertions.assertEquals("malformed affix rule count at line 1", e.getMessage());

    e = Assertions.assertThrows(IOException.class,
        () -> load("SFX S Y 2\nSFX S 0 s .", "0\n"));
    Assertions.assertEquals("affix block truncated at line 3", e.getMessage());

    e = Assertions.assertThrows(IOException.class,
        () -> load("SFX S Y 1\nPFX S 0 s .\n", "0\n"));
    Assertions.assertEquals("malformed affix rule at line 2", e.getMessage());

    e = Assertions.assertThrows(IOException.class,
        () -> load("SFX S Y 1\nSFX S 0 s [ab\n", "0\n"));
    Assertions.assertEquals("unterminated character class at line 2", e.getMessage());
  }

  /**
   * Verifies the exact exception and message for each malformed flag value the parser
   * detects: an odd-length flag run in {@code FLAG long} mode, a non-numeric flag in
   * {@code FLAG num} mode, an affix header naming more than one flag, and a
   * {@code SET} declaration naming an unknown encoding.
   */
  @Test
  void testMalformedFlagValueMessages() {
    IOException e = Assertions.assertThrows(IOException.class,
        () -> load("FLAG long\n", "1\nwalk/AaB\n"));
    Assertions.assertEquals("odd long-flag run at line 2", e.getMessage());

    e = Assertions.assertThrows(IOException.class,
        () -> load("FLAG num\n", "1\nwalk/12,x\n"));
    Assertions.assertEquals("malformed numeric flag at line 2", e.getMessage());

    e = Assertions.assertThrows(IOException.class,
        () -> load("FLAG long\nSFX AaBb Y 1\nSFX AaBb 0 s .\n", "0\n"));
    Assertions.assertEquals("expected exactly one flag at line 2", e.getMessage());

    e = Assertions.assertThrows(IOException.class,
        () -> load("SET NO-SUCH-ENCODING\n", "0\n"));
    Assertions.assertEquals("unsupported SET encoding: NO-SUCH-ENCODING", e.getMessage());
  }

  /**
   * Verifies that a morphological field is cut off the entry before the flag separator
   * is looked for, so a slash inside a morphological field is not mistaken for the
   * separator: the entry {@code walk po:verb/noun} registers the word {@code walk}
   * with no flags in every flag mode, and its morphology is ignored.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testMorphologicalFieldsAreCutBeforeTheFlagSeparator() throws IOException {
    final HunspellDictionary chars = load("SFX G Y 1\nSFX G 0 ing .\n",
        "1\nwalk po:verb/noun\n");
    Assertions.assertNotNull(chars.lookup("walk"));
    Assertions.assertEquals(0, chars.lookup("walk").get(0).length);
    Assertions.assertNull(chars.lookup("walk po:verb"));

    final HunspellDictionary numbers = load("FLAG num\nSFX 1 Y 1\nSFX 1 0 ing .\n",
        "1\nwalk po:verb/noun\n");
    Assertions.assertNotNull(numbers.lookup("walk"));
    Assertions.assertEquals(0, numbers.lookup("walk").get(0).length);

    // the tabulator is the older morphological field separator
    final HunspellDictionary tabbed = load("SFX G Y 1\nSFX G 0 ing .\n",
        "1\nwalk\tpo:verb/noun\n");
    Assertions.assertNotNull(tabbed.lookup("walk"));
    Assertions.assertEquals(0, tabbed.lookup("walk").get(0).length);
  }

  /**
   * Verifies that an entry keeps its flags when it carries both a flag run and a
   * morphological field holding a slash, in every flag mode.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testFlaggedEntriesKeepTheirFlagsBesideMorphology() throws IOException {
    final HunspellDictionary chars = load("SFX A Y 1\nSFX A 0 ing .\n",
        "1\nwalk/AB po:verb/noun\n");
    Assertions.assertArrayEquals(new int[] {'A', 'B'}, chars.lookup("walk").get(0));
    Assertions.assertEquals("walk",
        new HunspellStemmer(chars).stem("walking").toString());

    final HunspellDictionary numbers = load("FLAG num\nSFX 1 Y 1\nSFX 1 0 ing .\n",
        "1\nwalk/1,2 po:verb/noun\n");
    Assertions.assertArrayEquals(new int[] {1, 2}, numbers.lookup("walk").get(0));
    Assertions.assertEquals("walk",
        new HunspellStemmer(numbers).stem("walking").toString());
  }

  /**
   * Verifies that a multi-word entry keeps both its spaces and its flags: the word of
   * a word-list entry runs up to its morphological fields, not up to its first space.
   *
   * @throws IOException Thrown if the fixture fails to load.
   */
  @Test
  void testMultiWordEntriesKeepTheirSpacesAndFlags() throws IOException {
    final HunspellDictionary dictionary = load("FLAG num\nSFX 39 Y 1\nSFX 39 0 s .\n",
        "1\nall right/39\n");
    Assertions.assertArrayEquals(new int[] {39}, dictionary.lookup("all right").get(0));
    Assertions.assertNull(dictionary.lookup("all"));
  }

  /**
   * Verifies that the parser trims word-list entries with the same whitespace
   * definition it uses to find their fields: an entry led by a no-break space is
   * registered under its real word, both with and without a flag run.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testEntriesLedByNoBreakSpaceAreTrimmed() throws IOException {
    // \u00A0 is the no-break space, which StringUtil.isWhitespace treats as whitespace
    final HunspellDictionary dictionary = load("SFX S Y 1\nSFX S 0 s .\n",
        "2\n\u00A0fish\n\u00A0cat/S\n");
    Assertions.assertNotNull(dictionary.lookup("fish"));
    Assertions.assertNotNull(dictionary.lookup("cat"));
    Assertions.assertNull(dictionary.lookup(""));
    Assertions.assertEquals("cat", new HunspellStemmer(dictionary).stem("cats").toString());
  }

  /**
   * Verifies that {@code FLAG UTF-8}, which declares single-character flags, is
   * accepted and read exactly like the default single-character mode, including a flag
   * outside ASCII.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testUtf8FlagModeDeclaresSingleCharacterFlags() throws IOException {
    final HunspellStemmer plain = new HunspellStemmer(load(String.join("\n",
        "FLAG UTF-8",
        "SFX S Y 1",
        "SFX S 0 s .",
        ""), "1\nwalk/S\n"));
    Assertions.assertEquals("walk", plain.stem("walks").toString());

    // \u00E9 is e with an acute accent, a single-character flag outside ASCII
    final HunspellStemmer accented = new HunspellStemmer(load(String.join("\n",
        "FLAG UTF-8",
        "SFX \u00E9 Y 1",
        "SFX \u00E9 0 s .",
        ""), "1\nwalk/\u00E9\n"));
    Assertions.assertEquals("walk", accented.stem("walks").toString());
  }

  /**
   * Verifies that a strip-only rule, whose affix material is empty and which therefore
   * only removes stem material, is undone: the suffix rule turns the entry
   * {@code bake} into the surface form {@code bak}, and the prefix rule turns
   * {@code apple} into {@code pple}.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testStripOnlyAffixRulesAreUndone() throws IOException {
    final HunspellStemmer suffixStripping = new HunspellStemmer(load(String.join("\n",
        "SFX A Y 1",
        "SFX A e 0 e",
        ""), "1\nbake/A\n"));
    Assertions.assertEquals("bake", suffixStripping.stem("bak").toString());

    final HunspellStemmer prefixStripping = new HunspellStemmer(load(String.join("\n",
        "PFX B Y 1",
        "PFX B a 0 a",
        ""), "1\napple/B\n"));
    Assertions.assertEquals("apple", prefixStripping.stem("pple").toString());
  }

  /**
   * Verifies that an entry written with an empty flag run loads and carries no flags in
   * every flag mode, rather than failing the load in {@code FLAG num} mode alone.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testEmptyFlagRunYieldsNoFlagsInEveryMode() throws IOException {
    Assertions.assertEquals(0, load("", "1\nword/\n").lookup("word").get(0).length);
    Assertions.assertEquals(0,
        load("FLAG long\n", "1\nword/\n").lookup("word").get(0).length);
    Assertions.assertEquals(0,
        load("FLAG num\n", "1\nword/\n").lookup("word").get(0).length);
  }

  @Test
  void testMalformedInputFailsLoud() {
    Assertions.assertThrows(IOException.class, () -> HunspellDictionary.load(
        new ByteArrayInputStream("SFX S Y 2\nSFX S 0 s .\n".getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream("1\ncat/S\n".getBytes(StandardCharsets.UTF_8))));
    Assertions.assertThrows(IOException.class, () -> HunspellDictionary.load(
        new ByteArrayInputStream("SET NO-SUCH-ENCODING\n".getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream("0\n".getBytes(StandardCharsets.UTF_8))));
    Assertions.assertThrows(IOException.class, () -> HunspellDictionary.load(
        new ByteArrayInputStream("SFX S 0 s [a\n".getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream("0\n".getBytes(StandardCharsets.UTF_8))));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> HunspellDictionary.load((InputStream) null, (InputStream) null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HunspellStemmer(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HunspellStemmerFactory(null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> stemmer.stemAll(null));
  }

  /**
   * Verifies hunspell's tolerance for trailing text after a numeric or long flag run:
   * the flag run ends at the first space, and whatever follows is a morphological
   * field even without a two-letter tag, so such an entry loads instead of aborting
   * the whole dictionary.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testTrailingTextAfterNumericFlagRunIsMorphologyNotAnError() throws IOException {
    final HunspellDictionary numbers = load("FLAG num\n",
        "2\nwalk/39 blah\nrun/7,9 xyz abc\n");
    Assertions.assertNotNull(numbers.lookup("walk"));
    Assertions.assertTrue(HunspellDictionary.hasFlag(numbers.lookup("walk"), 39));
    Assertions.assertTrue(HunspellDictionary.hasFlag(numbers.lookup("run"), 7));
    Assertions.assertTrue(HunspellDictionary.hasFlag(numbers.lookup("run"), 9));

    final HunspellDictionary longs = load("FLAG long\n", "1\nwalk/AB cd\n");
    Assertions.assertTrue(HunspellDictionary.hasFlag(longs.lookup("walk"),
        ('A' << 16) | 'B'));
  }

  /**
   * Verifies that stemming the empty word answers the empty word: a zero-length
   * surface has no morphology, and a strip-only rule must not restore its strip
   * string onto nothing and answer a non-empty stem.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testEmptyWordStemsToItself() throws IOException {
    final HunspellStemmer stripOnly = new HunspellStemmer(load(
        "PFX P Y 1\nPFX P xy 0 .\n",
        "1\nxy/P\n"));
    Assertions.assertEquals("", stripOnly.stem("").toString());
    Assertions.assertEquals(List.of(""), stripOnly.stemAll(""));
  }

  /**
   * Verifies the escaped-slash feature: {@code \/} belongs to the word, so an entry
   * naming a slashed term keeps its slash while the first unescaped slash still
   * separates the flag run.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testEscapedSlashBelongsToTheWord() throws IOException {
    final HunspellDictionary slashed = load("FLAG num\n",
        "2\nTCP\\/IP/39\nAC\\/DC\n");
    Assertions.assertNotNull(slashed.lookup("TCP/IP"));
    Assertions.assertTrue(HunspellDictionary.hasFlag(slashed.lookup("TCP/IP"), 39));
    Assertions.assertNotNull(slashed.lookup("AC/DC"));
    Assertions.assertNull(slashed.lookup("TCP"));
  }

  /**
   * Verifies the sharpest combination of the morphology cut: an entry that is both a
   * multi-word term and carries trailing tag morphology keeps the whole multi-word
   * surface and its flags, and the tags stay out of the word.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testMultiWordEntryWithTrailingTagMorphology() throws IOException {
    final HunspellDictionary phrases = load("FLAG num\n",
        "1\nall right/39 po:phrase st:allright\n");
    Assertions.assertNotNull(phrases.lookup("all right"));
    Assertions.assertTrue(HunspellDictionary.hasFlag(phrases.lookup("all right"), 39));
    Assertions.assertNull(phrases.lookup("all right po:phrase st:allright"));
  }

  /**
   * Pins FLAG UTF-8 for a supplementary flag character: a flag is one code point, so
   * a character above U+FFFF is one flag carrying its code point value, never two
   * surrogate-unit flags. The Spanish dictionary of the LibreOffice collection names
   * affix rules with such characters, so an affix keyed by a supplementary flag must
   * connect to the entries that carry it.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testSupplementaryFlagCharacterIsOneCodePointFlag() throws IOException {
    // U+1F600 as a flag, written as its surrogate pair
    final HunspellDictionary emoji = load("FLAG UTF-8\n",
        "1\nwalk/\uD83D\uDE00\n");
    Assertions.assertTrue(HunspellDictionary.hasFlag(emoji.lookup("walk"), 0x1F600));
    Assertions.assertFalse(HunspellDictionary.hasFlag(emoji.lookup("walk"), 0xD83D));

    final HunspellStemmer supplementaryFlags = new HunspellStemmer(load(
        "FLAG UTF-8\nSFX \uD83D\uDE00 Y 1\nSFX \uD83D\uDE00 0 s .\n",
        "1\nwalk/\uD83D\uDE00\n"));
    Assertions.assertEquals("walk", supplementaryFlags.stem("walks").toString());
  }

  /**
   * Pins the variation-selector rule the Spanish dictionary of the LibreOffice
   * collection relies on: a variation selector after a flag character selects its
   * presentation and is no flag of its own, so an affix rule named with the emoji
   * form of a character connects to entries flagged with either spelling.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testVariationSelectorIsDroppedFromFlagIdentity() throws IOException {
    // U+260E BLACK TELEPHONE followed by U+FE0F VARIATION SELECTOR-16, the exact
    // shape of a prefix flag in the published es_ES affix file
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "FLAG UTF-8\nPFX \u260E\uFE0F Y 1\nPFX \u260E\uFE0F 0 tele .\n",
        "1\nfono/\u260E\n"));
    Assertions.assertEquals("fono", stemmer.stem("telefono").toString());
  }

  /**
   * Pins the documented rejection of rules that neither add nor remove material: a
   * suffix rule with strip {@code 0} and affix {@code 0} loads without error and
   * never fires, so stemming a flagged dictionary word answers that word exactly
   * once.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testRuleThatNeitherAddsNorRemovesLoadsAndNeverFires() throws IOException {
    final HunspellStemmer identity = new HunspellStemmer(load(
        "SFX X Y 1\nSFX X 0 0 .\n",
        "1\nwalk/X\n"));
    Assertions.assertEquals(List.of("walk"), identity.stemAll("walk"));
  }

  /**
   * Verifies the AF flag alias table: the first AF line declares the count, every
   * further AF line is one flag run, and a purely numeric flag field in the word
   * list is a 1-based reference into that table, the layout the published Hungarian
   * dictionary uses for all of its ninety-seven thousand entries. Alias lines may
   * carry trailing comments, which the field split already discards.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testNumericDictionaryFlagsResolveThroughTheAliasTable() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        String.join("\n",
            "AF 2",
            "AF S # 1",
            "AF SP # 2",
            "SFX S Y 1",
            "SFX S 0 s .",
            "PFX P Y 1",
            "PFX P 0 re .",
            ""),
        "2\nwalk/1\nplay/2\n"));
    Assertions.assertEquals("walk", stemmer.stem("walks").toString());
    Assertions.assertEquals("play", stemmer.stem("plays").toString());
    Assertions.assertEquals("play", stemmer.stem("replay").toString());
    // walk carries alias 1, the suffix-only run, so the prefix must not apply
    Assertions.assertEquals("rewalk", stemmer.stem("rewalk").toString());
  }

  /**
   * Verifies that an alias reference outside the AF table fails loud with the line
   * and the table size, instead of silently flagging the entry with nothing, and that
   * a digit run too large for an alias number fails loud as well.
   */
  @Test
  void testAliasReferenceOutsideTheTableFailsLoud() {
    IOException e = Assertions.assertThrows(IOException.class, () -> load(
        "AF 1\nAF S # 1\nSFX S Y 1\nSFX S 0 s .\n",
        "1\nwalk/2\n"));
    Assertions.assertEquals(
        "flag alias 2 at line 2 is outside the AF table of 1 aliases",
        e.getMessage());

    e = Assertions.assertThrows(IOException.class, () -> load(
        "AF 1\nAF S # 1\nSFX S Y 1\nSFX S 0 s .\n",
        "1\nwalk/99999999999999999999\n"));
    Assertions.assertEquals(
        "malformed flag alias '99999999999999999999' at line 2",
        e.getMessage());
  }

  /**
   * Verifies that numeric flag fields stay ordinary flags when no AF table exists:
   * under FLAG num a digit run is a flag value, not an alias reference.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testNumericFlagsWithoutAliasTableStayFlags() throws IOException {
    final HunspellDictionary numbers = load("FLAG num\n", "1\nwalk/39\n");
    Assertions.assertTrue(HunspellDictionary.hasFlag(numbers.lookup("walk"), 39));
  }

  /**
   * Verifies two-part compound decomposition under the general compounding flag: a
   * word the affix analysis cannot explain splits into two listed parts that both
   * carry the flag, reported left to right, while a part without the flag blocks the
   * split and the word stays unanalyzed.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCompoundFlagDecomposesUnanalyzedWords() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDMIN 3\n",
        "3\ndog/Z\nhouse/Z\ncat\n"));
    Assertions.assertEquals(List.of("dog", "house"), stemmer.stemAll("doghouse"));
    // cat is listed without the compounding flag, so no split may use it
    Assertions.assertEquals(List.of("cathouse"), stemmer.stemAll("cathouse"));
    // a listed word never decomposes; it is its own analysis
    Assertions.assertEquals(List.of("dog"), stemmer.stemAll("dog"));
  }

  /**
   * Verifies the positional compound flags: the begin flag only opens and the end
   * flag only closes, so the parts compose in one order and refuse the other.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCompoundBeginAndEndFlagsArePositional() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "COMPOUNDBEGIN B\nCOMPOUNDEND E\nCOMPOUNDMIN 3\n",
        "2\ndog/B\nhouse/E\n"));
    Assertions.assertEquals(List.of("dog", "house"), stemmer.stemAll("doghouse"));
    Assertions.assertEquals(List.of("housedog"), stemmer.stemAll("housedog"));
  }

  /**
   * Verifies the minimum part length: a split leaving a side shorter than
   * COMPOUNDMIN is never taken, although both sides are listed and flagged.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCompoundMinBoundsThePartLength() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDMIN 4\n",
        "2\ndog/Z\nhouse/Z\n"));
    // the left side would be three characters, below the declared minimum of four
    Assertions.assertEquals(List.of("doghouse"), stemmer.stemAll("doghouse"));
  }

  /**
   * Verifies the NEEDAFFIX flag on entries: a virtual stem exists only to be affixed,
   * the linking forms of the published German dictionary being the model, so its bare
   * form is no analysis of itself while its affixed forms still reduce to it.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testNeedAffixEntryIsNoStandaloneAnalysis() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "NEEDAFFIX h\nSFX S Y 1\nSFX S 0 s .\nSFX K Y 1\nSFX K 0 k .\n",
        "2\nlink/hS\nlin/K\n"));
    // the virtual entry no longer explains the bare form; only the k analysis remains
    Assertions.assertEquals(List.of("lin"), stemmer.stemAll("link"));
    // affixed, the virtual stem is exactly what the s removal lands on
    Assertions.assertEquals(List.of("link"), stemmer.stemAll("links"));
  }

  /**
   * Verifies NEEDAFFIX against homonyms: the flag blocks one entry's flag set, not
   * the word, so a second listing without the flag keeps the bare form valid.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testNeedAffixHomonymKeepsTheBareWord() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "NEEDAFFIX h\nSFX S Y 1\nSFX S 0 s .\n",
        "2\nlink/hS\nlink\n"));
    Assertions.assertEquals(List.of("link"), stemmer.stemAll("link"));
  }

  /**
   * Verifies the historical PSEUDOROOT alias, the directive's name before hunspell
   * renamed it to NEEDAFFIX; older dictionaries still declare it.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testPseudoRootIsNeedAffixByItsOldName() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "PSEUDOROOT h\nSFX S Y 1\nSFX S 0 s .\nSFX K Y 1\nSFX K 0 k .\n",
        "2\nlink/hS\nlin/K\n"));
    Assertions.assertEquals(List.of("lin"), stemmer.stemAll("link"));
  }

  /**
   * Verifies the NEEDAFFIX flag on affix rules: a rule carrying the flag among its
   * continuation classes makes a form that still needs another affix, so its
   * single-removal analysis is suppressed while a twofold removal, whose inner affix
   * is the further one required, still reports the stem.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testNeedAffixOnAnAffixRequiresAnotherAffix() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        String.join("\n",
            "NEEDAFFIX h",
            "SFX A Y 1",
            "SFX A 0 er/hB .",
            "SFX B Y 1",
            "SFX B 0 s .",
            ""),
        "1\nwork/A\n"));
    // work + er alone is virtual, so worker has no analysis and passes through
    Assertions.assertEquals(List.of("worker"), stemmer.stemAll("worker"));
    // work + er + s is complete; the twofold removal reaches the listed stem
    Assertions.assertEquals(List.of("work"), stemmer.stemAll("workers"));
  }

  /**
   * Verifies that a cross-product analysis satisfies an affix's NEEDAFFIX marker:
   * the prefix is the further affix the marked suffix requires, mirroring how
   * hunspell accepts a prefix plus a needs-affix suffix together.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCrossProductSatisfiesNeedAffixOnTheSuffix() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        String.join("\n",
            "NEEDAFFIX h",
            "PFX P Y 1",
            "PFX P 0 un .",
            "SFX A Y 1",
            "SFX A 0 er/h .",
            ""),
        "1\nwork/AP\n"));
    Assertions.assertEquals(List.of("worker"), stemmer.stemAll("worker"));
    Assertions.assertEquals(List.of("work"), stemmer.stemAll("unworker"));
  }

  /**
   * Verifies the ONLYINCOMPOUND flag: an entry carrying it appears only inside
   * compounds, the ordinal parts of the published US English dictionary being the
   * model, so neither its bare form nor its affixed forms are standalone analyses,
   * while compound decomposition may still use it.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testOnlyInCompoundEntrySupportsNoStandaloneAnalyses() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "ONLYINCOMPOUND c\nCOMPOUNDFLAG Z\nCOMPOUNDMIN 3\nSFX S Y 1\nSFX S 0 s .\n",
        "3\npart/cSZ\nhouse/Z\nwalk/S\n"));
    // the affix analysis is suppressed because part's only flag set is compound-only
    Assertions.assertEquals(List.of("parts"), stemmer.stemAll("parts"));
    Assertions.assertEquals(List.of("part"), stemmer.stemAll("part"));
    // inside a compound the entry serves exactly its declared purpose
    Assertions.assertEquals(List.of("part", "house"), stemmer.stemAll("parthouse"));
    Assertions.assertEquals(List.of("walk"), stemmer.stemAll("walks"));
  }

  /**
   * Verifies the FORBIDDENWORD flag: an entry carrying it is listed to be blocked,
   * so it supports no analysis and no compound part.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testForbiddenWordSupportsNothing() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "FORBIDDENWORD w\nCOMPOUNDFLAG Z\nCOMPOUNDMIN 3\nSFX S Y 1\nSFX S 0 s .\n",
        "3\nfoo/wSZ\nhouse/Z\nbar/S\n"));
    Assertions.assertEquals(List.of("foo"), stemmer.stemAll("foo"));
    Assertions.assertEquals(List.of("foos"), stemmer.stemAll("foos"));
    Assertions.assertEquals(List.of("foohouse"), stemmer.stemAll("foohouse"));
    Assertions.assertEquals(List.of("bar"), stemmer.stemAll("bars"));
  }

  /** The circumfix fixture: the German {@code ge...t} participle in miniature. */
  private static final String CIRCUMFIX_AFFIX = String.join("\n",
      "CIRCUMFIX f",
      "PFX G Y 1",
      "PFX G 0 ge/f .",
      "SFX T Y 1",
      "SFX T en et/f en",
      "PFX U Y 1",
      "PFX U 0 un .",
      "SFX S Y 1",
      "SFX S 0 s .",
      "");

  /**
   * Verifies the CIRCUMFIX flag: two marked halves analyze together and neither
   * analyzes alone, so the participle reduces to its verb while the half-applied
   * forms stay unexplained.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCircumfixHalvesOnlyAnalyzeTogether() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        CIRCUMFIX_AFFIX, "1\narbeiten/GT\n"));
    Assertions.assertEquals(List.of("arbeiten"), stemmer.stemAll("gearbeitet"));
    // the suffix half alone is no word, although the stem carries its flag
    Assertions.assertEquals(List.of("arbeitet"), stemmer.stemAll("arbeitet"));
    // the prefix half alone is no word either
    Assertions.assertEquals(List.of("gearbeiten"), stemmer.stemAll("gearbeiten"));
  }

  /**
   * Verifies that circumfixing rejects mixed pairs: a marked half never combines
   * with an unmarked affix of the other kind, in either direction, while a fully
   * unmarked cross-product in the same dictionary still analyzes.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCircumfixRejectsMixedPairs() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        CIRCUMFIX_AFFIX, "2\narbeiten/GTUS\nlauf/US\n"));
    // unmarked prefix with the marked suffix half
    Assertions.assertEquals(List.of("unarbeitet"), stemmer.stemAll("unarbeitet"));
    // the marked prefix half with an unmarked suffix
    Assertions.assertEquals(List.of("gearbeitens"), stemmer.stemAll("gearbeitens"));
    // both halves marked still analyze beside the rejected mixtures
    Assertions.assertEquals(List.of("arbeiten"), stemmer.stemAll("gearbeitet"));
    // a fully unmarked cross-product is untouched by the circumfix declaration
    Assertions.assertEquals(List.of("lauf"), stemmer.stemAll("unlaufs"));
  }

  /**
   * Verifies decomposition beyond two parts: the positional flags admit a begin, a
   * middle, and an end part, a part fit only for the middle neither opens nor closes,
   * and repeated middles fold into the reported set.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCompoundMiddleAdmitsInnerParts() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "COMPOUNDBEGIN B\nCOMPOUNDMIDDLE M\nCOMPOUNDEND E\nCOMPOUNDMIN 3\n",
        "3\ndog/B\ncat/M\nhouse/E\n"));
    Assertions.assertEquals(List.of("dog", "cat", "house"),
        stemmer.stemAll("dogcathouse"));
    Assertions.assertEquals(List.of("dog", "cat", "house"),
        stemmer.stemAll("dogcatcathouse"));
    Assertions.assertEquals(List.of("dog", "house"), stemmer.stemAll("doghouse"));
    // cat holds only the middle flag, so it neither opens nor closes
    Assertions.assertEquals(List.of("cathouse"), stemmer.stemAll("cathouse"));
    Assertions.assertEquals(List.of("dogcat"), stemmer.stemAll("dogcat"));
  }

  /**
   * Verifies COMPOUNDWORDMAX: a decomposition needing more parts than declared is
   * rejected while one within the bound still analyzes.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCompoundWordMaxBoundsThePartCount() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "COMPOUNDBEGIN B\nCOMPOUNDMIDDLE M\nCOMPOUNDEND E\nCOMPOUNDMIN 3\n"
            + "COMPOUNDWORDMAX 2\n",
        "3\ndog/B\ncat/M\nhouse/E\n"));
    Assertions.assertEquals(List.of("dog", "house"), stemmer.stemAll("doghouse"));
    Assertions.assertEquals(List.of("dogcathouse"), stemmer.stemAll("dogcathouse"));
  }

  /**
   * Verifies affixed compound parts, the German linking form being the model: a part
   * is its entry plus one suffix whose continuation classes position the derived form
   * and permit it at the internal boundary, and the reported analysis is the entry,
   * not the linking form. The lowercase interior spelling of a capitalized entry is
   * found through the part's uppercased retry.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testLinkingSuffixJoinsCompoundParts() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        String.join("\n",
            "COMPOUNDBEGIN x",
            "COMPOUNDEND z",
            "COMPOUNDPERMITFLAG c",
            "COMPOUNDMIN 2",
            "SFX j Y 1",
            "SFX j 0 s/xc .",
            ""),
        "2\nAbbildung/j\nVerzeichnis/z\n"));
    Assertions.assertEquals(List.of("Abbildung", "Verzeichnis"),
        stemmer.stemAll("Abbildungsverzeichnis"));
    // without the linking s the first part has no admitting reading
    Assertions.assertEquals(List.of("Abbildungverzeichnis"),
        stemmer.stemAll("Abbildungverzeichnis"));
  }

  /**
   * Verifies zero-suffix part positioning, the pattern the published German
   * dictionary uses: a virtual stem enters compounds through a rule that adds no
   * material but whose continuation classes carry the positional and permit flags.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testZeroSuffixPositionsAVirtualStemInCompounds() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        String.join("\n",
            "NEEDAFFIX h",
            "COMPOUNDBEGIN x",
            "COMPOUNDEND z",
            "COMPOUNDPERMITFLAG c",
            "COMPOUNDMIN 3",
            "SFX j Y 1",
            "SFX j 0 0/xc .",
            ""),
        "2\nfugen/hj\nwerk/z\n"));
    Assertions.assertEquals(List.of("fugen", "werk"), stemmer.stemAll("fugenwerk"));
    // the virtual stem alone is still no word
    Assertions.assertEquals(List.of("fugen"), stemmer.stemAll("fugen"));
  }

  /**
   * Verifies COMPOUNDFORBIDFLAG: an affixed form whose rule carries the flag stays
   * out of compounds although its positioning otherwise admits it.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCompoundForbidFlagBarsAnAffixedPart() throws IOException {
    final String words = "2\ndog/ZS\nhouse/Z\n";
    final HunspellStemmer barred = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDPERMITFLAG c\nCOMPOUNDFORBIDFLAG F\nCOMPOUNDMIN 3\n"
            + "SFX S Y 1\nSFX S 0 s/cF .\n",
        words));
    Assertions.assertEquals(List.of("dogshouse"), barred.stemAll("dogshouse"));
    final HunspellStemmer allowed = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDPERMITFLAG c\nCOMPOUNDMIN 3\n"
            + "SFX S Y 1\nSFX S 0 s/c .\n",
        words));
    Assertions.assertEquals(List.of("dog", "house"), allowed.stemAll("dogshouse"));
  }

  /**
   * Verifies that an affix without the permit flag keeps off internal boundaries: a
   * suffixed reading fits the last part but not an earlier one.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testAffixWithoutPermitFlagStaysAtTheEdge() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDMIN 3\nSFX S Y 1\nSFX S 0 s .\n",
        "2\ndog/ZS\nhouse/ZS\n"));
    // the suffix closes the word, so the last part may carry it
    Assertions.assertEquals(List.of("dog", "house"), stemmer.stemAll("doghouses"));
    // an internal suffix without the permit flag blocks the split
    Assertions.assertEquals(List.of("dogshouse"), stemmer.stemAll("dogshouse"));
  }

  /**
   * Verifies CHECKCOMPOUNDDUP: a part must not repeat its left neighbor, while the
   * same dictionary without the declaration accepts the repetition.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCheckCompoundDupForbidsRepeatedParts() throws IOException {
    final String words = "2\ndog/Z\nhouse/Z\n";
    final HunspellStemmer checked = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDMIN 3\nCHECKCOMPOUNDDUP\n", words));
    Assertions.assertEquals(List.of("dogdoghouse"), checked.stemAll("dogdoghouse"));
    final HunspellStemmer unchecked = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDMIN 3\n", words));
    Assertions.assertEquals(List.of("dog", "house"), unchecked.stemAll("dogdoghouse"));
  }

  /**
   * Verifies CHECKCOMPOUNDCASE: an uppercase character on either side of a junction
   * forbids the split, while the same dictionary without the declaration accepts it.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCheckCompoundCaseForbidsUppercaseJunctions() throws IOException {
    final String words = "2\ndog/Z\nHouse/Z\n";
    final HunspellStemmer checked = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDMIN 3\nCHECKCOMPOUNDCASE\n", words));
    Assertions.assertEquals(List.of("dogHouse"), checked.stemAll("dogHouse"));
    final HunspellStemmer unchecked = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDMIN 3\n", words));
    Assertions.assertEquals(List.of("dog", "House"), unchecked.stemAll("dogHouse"));
  }

  /**
   * Verifies CHECKCOMPOUNDTRIPLE: the same character three times in a row across a
   * junction forbids the split, while the same dictionary without the declaration
   * accepts it.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testCheckCompoundTripleForbidsTripleLetters() throws IOException {
    final String words = "2\nbell/Z\nlow/Z\n";
    final HunspellStemmer checked = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDMIN 3\nCHECKCOMPOUNDTRIPLE\n", words));
    Assertions.assertEquals(List.of("belllow"), checked.stemAll("belllow"));
    final HunspellStemmer unchecked = new HunspellStemmer(load(
        "COMPOUNDFLAG Z\nCOMPOUNDMIN 3\n", words));
    Assertions.assertEquals(List.of("bell", "low"), unchecked.stemAll("belllow"));
  }

  /**
   * Verifies that a listed forbidden word never decomposes: the dictionary blocks
   * one specific ill-formed compound while its parts stay productive elsewhere.
   *
   * @throws IOException Thrown if a fixture fails to load.
   */
  @Test
  void testForbiddenEntryBlocksItsDecomposition() throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(load(
        "FORBIDDENWORD w\nCOMPOUNDFLAG Z\nCOMPOUNDMIN 3\n",
        "4\ndog/Z\nhouse/Z\ncat/Z\ndoghouse/w\n"));
    Assertions.assertEquals(List.of("doghouse"), stemmer.stemAll("doghouse"));
    Assertions.assertEquals(List.of("cat", "house"), stemmer.stemAll("cathouse"));
  }
}
