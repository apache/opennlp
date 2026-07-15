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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.Span;

/**
 * Tests the lattice segmenter against a project-authored miniature dictionary; no
 * external dictionary data is involved.
 */
public class LatticeTokenizerTest {

  @TempDir
  static Path directory;

  private static LatticeTokenizer tokenizer;

  @BeforeAll
  static void loadDictionary() throws IOException {
    write("lexicon.csv", String.join("\n",
        "東京,0,0,3000,noun,proper",
        "京都,0,0,3000,noun,proper",
        "東,0,0,6000,noun,common",
        "都,0,0,4000,noun,suffix",
        "に,0,0,1000,particle,case",
        "行く,0,0,3000,verb,base",
        ""));
    write("matrix.def", "1 1\n0 0 0\n");
    write("char.def", String.join("\n",
        "DEFAULT 0 1 0",
        "KANJI 0 0 2",
        "HIRAGANA 0 1 0",
        "LATIN 1 1 0",
        "",
        "0x3041..0x3096 HIRAGANA",
        "0x4E00..0x9FFF KANJI",
        "0x0041..0x005A LATIN",
        "0x0061..0x007A LATIN",
        ""));
    write("unk.def", String.join("\n",
        "DEFAULT,0,0,10000,symbol,unknown",
        "LATIN,0,0,4000,noun,foreign",
        "KANJI,0,0,8000,noun,unknown",
        "HIRAGANA,0,0,9000,particle,unknown",
        ""));
    tokenizer = new LatticeTokenizer(MecabDictionary.load(directory));
  }

  private static void write(String name, String content) throws IOException {
    Files.write(directory.resolve(name), content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void testLatticePrefersTheCheaperSegmentation() {
    // the famous case: Tokyo+Metro must win over East+Kyoto
    final String text = "東京都に行く";
    Assertions.assertArrayEquals(
        new String[] {"東京", "都", "に", "行く"},
        tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 2), new Span(2, 3), new Span(3, 4), new Span(4, 6)},
        tokenizer.tokenizePos(text));
  }

  @Test
  void testMorphemesCarryDictionaryFeatures() {
    final List<Morpheme> morphemes =
        tokenizer.analyze("東京都に行く");
    Assertions.assertEquals(4, morphemes.size());
    Assertions.assertEquals(List.of("noun", "proper"), morphemes.get(0).features());
    Assertions.assertEquals(List.of("particle", "case"), morphemes.get(2).features());
    Assertions.assertEquals(false, morphemes.get(0).unknown());
  }

  @Test
  void testUnknownLatinRunGroupsIntoOneMorpheme() {
    final List<Morpheme> morphemes = tokenizer.analyze("ABCに行く");
    Assertions.assertEquals(3, morphemes.size());
    Assertions.assertEquals("ABC", morphemes.get(0).surface());
    Assertions.assertEquals(true, morphemes.get(0).unknown());
    Assertions.assertEquals(List.of("noun", "foreign"), morphemes.get(0).features());
  }

  @Test
  void testUnknownKanjiPreferOneMorphemeOverTwo() {
    final List<Morpheme> morphemes = tokenizer.analyze("峠道に行く");
    Assertions.assertEquals(3, morphemes.size());
    Assertions.assertEquals("峠道", morphemes.get(0).surface());
    Assertions.assertEquals(true, morphemes.get(0).unknown());
  }

  @Test
  void testWhitespaceSeparatesAndIsNeverAMorpheme() {
    final String text = "東京 に 行く";
    Assertions.assertArrayEquals(
        new String[] {"東京", "に", "行く"},
        tokenizer.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 2), new Span(3, 4), new Span(5, 7)},
        tokenizer.tokenizePos(text));
    Assertions.assertEquals(0, tokenizer.analyze("   ").size());
    Assertions.assertEquals(0, tokenizer.analyze("").size());
  }

  @Test
  void testMalformedDictionariesFailLoud(@TempDir Path broken) throws IOException {
    Files.write(broken.resolve("lexicon.csv"),
        "東,0,0,3000,noun\n".getBytes(StandardCharsets.UTF_8));
    Assertions.assertThrows(IOException.class, () -> MecabDictionary.load(broken));

    Files.write(broken.resolve("matrix.def"), "1 1\n0 0 0\n".getBytes(StandardCharsets.UTF_8));
    Files.write(broken.resolve("char.def"),
        "KANJI 0 0 2\n0x4E00..0x9FFF KANJI\n".getBytes(StandardCharsets.UTF_8));
    Files.write(broken.resolve("unk.def"),
        "KANJI,0,0,8000,noun\n".getBytes(StandardCharsets.UTF_8));
    Assertions.assertThrows(IOException.class, () -> MecabDictionary.load(broken));
  }

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new LatticeTokenizer(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionary.load(null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> tokenizer.analyze(null));
  }
}
