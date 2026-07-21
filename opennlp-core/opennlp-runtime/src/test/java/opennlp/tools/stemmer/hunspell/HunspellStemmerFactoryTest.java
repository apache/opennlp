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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.stemmer.Stemmer;

/**
 * Demonstrates the intended end-to-end usage of the Hunspell stemming classes: a user
 * writes (or ships) a {@code .aff}/{@code .dic} file pair, loads it once into a
 * {@link HunspellDictionary}, wraps the dictionary in a {@link HunspellStemmerFactory},
 * and obtains {@link Stemmer} instances from the factory wherever stemming is needed.
 * The fixture dictionary is authored inside this test class, so no external dictionary
 * data is involved.
 */
public class HunspellStemmerFactoryTest {

  /**
   * The affix fixture: the prefix {@code re-}, the suffix {@code -er} whose continuation
   * class {@code S} lets the plural {@code -s} stack on top of it, and the plural
   * {@code -s} itself, restricted to stems not ending in {@code s}, {@code x}, or
   * {@code y}. All three rules opt into cross-product combination.
   */
  private static final String AFFIX = String.join("\n",
      "# project-authored test fixture",
      "SET UTF-8",
      "",
      "PFX R Y 1",
      "PFX R 0 re .",
      "",
      "SFX E Y 1",
      "SFX E 0 er/S .",
      "",
      "SFX S Y 1",
      "SFX S 0 s [^sxy]",
      "");

  /**
   * The word-list fixture: {@code work} accepts the prefix and both suffixes,
   * {@code paint} accepts only the agentive {@code -er}.
   */
  private static final String WORDS = String.join("\n",
      "2",
      "work/RES",
      "paint/E",
      "");

  /**
   * Writes the fixture dictionary pair into a directory and loads it through the
   * file-based {@link HunspellDictionary#load(Path, Path)} entry point.
   *
   * @param directory The directory to write into. Must not be {@code null} and must
   *                  denote an existing directory.
   * @return The loaded dictionary. Never {@code null}.
   * @throws IOException Thrown if writing or loading fails.
   * @throws IllegalArgumentException Thrown if {@code directory} is unusable.
   */
  private static HunspellDictionary writeAndLoadFixture(Path directory) throws IOException {
    if (directory == null || !Files.isDirectory(directory)) {
      throw new IllegalArgumentException("directory must be an existing directory");
    }
    final Path affixFile = directory.resolve("fixture.aff");
    final Path dictionaryFile = directory.resolve("fixture.dic");
    Files.write(affixFile, AFFIX.getBytes(StandardCharsets.UTF_8));
    Files.write(dictionaryFile, WORDS.getBytes(StandardCharsets.UTF_8));
    return HunspellDictionary.load(affixFile, dictionaryFile);
  }

  /**
   * Walks the whole intended flow on a single thread: files on disk, one dictionary,
   * one factory, one stemmer, and exact stems for a prefixed form, a suffixed form, a
   * twofold suffix chain, a cross-product form, an in-dictionary word, and an unknown
   * word.
   *
   * @param tempDir A scratch directory managed by the test framework.
   * @throws IOException Thrown if the fixture cannot be written or loaded.
   */
  @Test
  void testEndToEndUsageFromFiles(@TempDir Path tempDir) throws IOException {
    final HunspellDictionary dictionary = writeAndLoadFixture(tempDir);
    final HunspellStemmerFactory factory = new HunspellStemmerFactory(dictionary);
    final Stemmer stemmer = factory.newStemmer();

    // one suffix removed
    Assertions.assertEquals("work", stemmer.stem("worker").toString());
    Assertions.assertEquals("paint", stemmer.stem("painter").toString());
    // twofold suffixes: -s stacks on -er through the continuation class S
    Assertions.assertEquals("work", stemmer.stem("workers").toString());
    // one prefix removed
    Assertions.assertEquals("work", stemmer.stem("rework").toString());
    // cross product: the prefix re- and the suffix -s on the same stem
    Assertions.assertEquals("work", stemmer.stem("reworks").toString());
    // a word that is itself listed stems to itself
    Assertions.assertEquals("work", stemmer.stem("work").toString());
    // unknown vocabulary passes through unchanged
    Assertions.assertEquals("table", stemmer.stem("table").toString());
  }

  /**
   * Shares one factory between two threads: each thread obtains its own stemmer
   * instance from the factory and stems the same inputs. The test asserts that the two
   * instances are distinct objects and that their results are identical to each other
   * and to the expected stems.
   *
   * @param tempDir A scratch directory managed by the test framework.
   * @throws Exception Thrown if the fixture cannot be loaded or a worker fails.
   */
  @Test
  void testFactorySharedAcrossThreads(@TempDir Path tempDir) throws Exception {
    final HunspellStemmerFactory factory =
        new HunspellStemmerFactory(writeAndLoadFixture(tempDir));
    final List<String> inputs = List.of("workers", "reworks", "painter", "table");
    final List<String> expected = List.of("work", "work", "paint", "table");

    final Stemmer[] created = new Stemmer[2];
    final ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      final List<Future<List<String>>> futures = new ArrayList<>(2);
      for (int worker = 0; worker < 2; worker++) {
        final int slot = worker;
        futures.add(pool.submit(() -> {
          final Stemmer stemmer = factory.newStemmer();
          created[slot] = stemmer;
          final List<String> stems = new ArrayList<>(inputs.size());
          for (final String input : inputs) {
            stems.add(stemmer.stem(input).toString());
          }
          return stems;
        }));
      }
      final List<String> first = futures.get(0).get();
      final List<String> second = futures.get(1).get();
      Assertions.assertEquals(expected, first);
      Assertions.assertEquals(expected, second);
    } finally {
      pool.shutdownNow();
    }
    Assertions.assertNotSame(created[0], created[1]);
  }

  /**
   * Verifies that the file-based entry point rejects each {@code null} path with the
   * documented exception naming the offending argument.
   *
   * @param tempDir A scratch directory managed by the test framework.
   */
  @Test
  void testNullPathsAreRejected(@TempDir Path tempDir) {
    final Path present = tempDir.resolve("present.aff");
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> HunspellDictionary.load(null, present));
    Assertions.assertEquals("affixFile must not be null", e.getMessage());

    e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> HunspellDictionary.load(present, null));
    Assertions.assertEquals("dictionaryFile must not be null", e.getMessage());
  }
}
