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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
        () -> HunspellDictionary.load((java.io.InputStream) null,
            (java.io.InputStream) null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HunspellStemmer(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HunspellStemmerFactory(null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> stemmer.stemAll(null));
  }
}
