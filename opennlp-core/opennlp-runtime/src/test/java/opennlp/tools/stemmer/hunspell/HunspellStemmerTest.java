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
