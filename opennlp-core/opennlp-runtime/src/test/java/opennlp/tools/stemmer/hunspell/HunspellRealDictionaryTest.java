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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Gated checks against published dictionaries, which are never bundled: the tests run
 * only when {@code -Dopennlp.hunspell.dict.dir} names a directory holding
 * {@code <name>.aff}/{@code <name>.dic} pairs, and each test additionally skips when
 * its dictionary pair is absent. The download helper in {@code dev/} fetches the pairs
 * together with their license files; see {@code dev/README-hunspell-dictionaries.md}.
 *
 * <p>The assertions are limited to morphology stable across dictionary revisions:
 * everyday inflections, and for German the decomposability of ordinary compounds.</p>
 */
public class HunspellRealDictionaryTest {

  private static final String DICT_DIR_PROPERTY = "opennlp.hunspell.dict.dir";

  /**
   * Loads one dictionary pair from the gated directory, skipping the test when the
   * gate or the pair is absent.
   *
   * @param name The dictionary base name, such as {@code en_US}.
   * @return A stemmer over the loaded pair. Never {@code null}.
   * @throws IOException Thrown if a present pair fails to load, which is a failure,
   *         not a skip.
   */
  private static HunspellStemmer loadOrSkip(String name) throws IOException {
    final String dir = System.getProperty(DICT_DIR_PROPERTY);
    Assumptions.assumeTrue(dir != null && !dir.isBlank(),
        "no " + DICT_DIR_PROPERTY + " given");
    final Path affix = Path.of(dir, name + ".aff");
    final Path words = Path.of(dir, name + ".dic");
    Assumptions.assumeTrue(Files.isReadable(affix) && Files.isReadable(words),
        name + " pair not present under " + dir);
    return new HunspellStemmer(HunspellDictionary.load(affix, words));
  }

  @Test
  void testEnglishInflections() throws IOException {
    final HunspellStemmer stemmer = loadOrSkip("en_US");
    Assertions.assertEquals("worker", stemmer.stem("workers").toString());
    Assertions.assertEquals("cat", stemmer.stem("cats").toString());
    Assertions.assertEquals("unhappy", stemmer.stem("unhappiest").toString());
    Assertions.assertEquals("quick", stemmer.stem("quickly").toString());
    Assertions.assertEquals("look", stemmer.stem("looked").toString());
    // unknown vocabulary degrades to identity
    Assertions.assertEquals("zyzzyvax", stemmer.stem("zyzzyvax").toString());
  }

  @Test
  void testGermanInflections() throws IOException {
    final HunspellStemmer stemmer = loadOrSkip("de_DE_frami");
    Assertions.assertEquals("Kind", stemmer.stem("Kinder").toString());
    // Haeuser, written with a-umlaut, stems to Haus
    Assertions.assertEquals("Haus", stemmer.stem("H\u00E4user").toString());
    Assertions.assertEquals("schnell", stemmer.stem("schnellsten").toString());
  }

  @Test
  void testGermanCompoundsDecompose() throws IOException {
    final HunspellStemmer stemmer = loadOrSkip("de_DE_frami");
    // the exact part spellings follow the dictionary's own entries and may shift
    // between revisions; that ordinary compounds decompose at all must not
    // Haustuer, written with u-umlaut, is Haus + Tuer
    Assertions.assertTrue(stemmer.stemAll("Haust\u00FCr").size() >= 2);
    Assertions.assertTrue(stemmer.stemAll("Kinderzimmer").size() >= 2);
    Assertions.assertTrue(stemmer.stemAll("Abbildungsverzeichnis").size() >= 2);
  }

  @Test
  void testHungarianInflections() throws IOException {
    final HunspellStemmer stemmer = loadOrSkip("hu_HU");
    // kutyak, written with a-acute, is the plural of kutya
    Assertions.assertEquals("kutya", stemmer.stem("kuty\u00E1k").toString());
    Assertions.assertEquals("asztal", stemmer.stem("asztalon").toString());
    // konyveket, written with o-umlaut, is an inflected form of konyv
    Assertions.assertEquals("k\u00F6nyv", stemmer.stem("k\u00F6nyveket").toString());
  }
}
