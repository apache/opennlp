/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

package opennlp.tools.eval;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import opennlp.tools.stemmer.hunspell.HunspellDictionary;
import opennlp.tools.stemmer.hunspell.HunspellStemmer;

/**
 * Evaluates the Hunspell stemmer with the LibreOffice English, German, and Hungarian
 * dictionaries under the {@code hunspell} directory of {@code OPENNLP_DATA_DIR}: strict
 * loading, expected inflections and compounds, concurrent use, and agreement with the
 * stems and the recognition recorded from Hunspell as described in
 * {@code dev/README-hunspell-dictionaries.md}.
 */
public class HunspellCompatibilityEval extends AbstractEvalTest {

  private static final String DATA_DIRECTORY = "hunspell";
  private static final int THREADS = 4;
  private static final int REPETITIONS = 10;
  private static final int TIMEOUT_SECONDS = 60;
  private static final String UNKNOWN = "zyzzyvax";

  private static final Map<Dictionary, HunspellStemmer> STEMMERS = new EnumMap<>(Dictionary.class);

  /** The external dictionaries, their MD5 digests, and the evaluated inputs. */
  private enum Dictionary {
    ENGLISH("en_US", "bbb118ea006c22ebe9ef7dbfe0dbfc2a", "7e671db5244b0496f9888e9f0176c360",
        List.of("workers", "cats", "unhappiest", "quickly", "looked", "reading",
            "dogs", "books", "walked", "walking", "talked", "talking", "played",
            "playing", "helped", "helping", "houses", "children", "feet", "better",
            "Workers", "WORKERS", "cAtS", "worker's", "well-known", "unhappy", "undone", UNKNOWN)),
    GERMAN("de_DE_frami", "9fd6eb96145bdccb2dc0213e1df5a46e", "07c5fd0780eca6ba448cba8c7be170cd",
        List.of("gegangen", "Kinder", "Häuser", "schnellsten", "Freunden", "Vorschläge",
            "Haustür", "Kinderzimmer", "Abbildungsverzeichnis", "Haus", "Baum", "Buch", "schnell", UNKNOWN)),
    HUNGARIAN("hu_HU", "c13482742489921ce2d8d19ff5122986", "66bab5478e829ba5e33afcde24a7259c",
        List.of("kutyák", "asztalon", "könyveket", "házak", "emberek", "kutyáknak", UNKNOWN));

    private final String id;
    private final BigInteger affixChecksum;
    private final BigInteger dictionaryChecksum;
    private final List<String> inputs;

    /**
     * Describes one external dictionary.
     *
     * @param id The file name without suffix.
     * @param affixChecksum The MD5 digest of the affix file, in hexadecimal.
     * @param dictionaryChecksum The MD5 digest of the word list, in hexadecimal.
     * @param inputs The evaluated inputs.
     */
    Dictionary(String id, String affixChecksum, String dictionaryChecksum, List<String> inputs) {
      this.id = id;
      this.affixChecksum = new BigInteger(affixChecksum, 16);
      this.dictionaryChecksum = new BigInteger(dictionaryChecksum, 16);
      this.inputs = inputs;
    }
  }

  /**
   * The outcome recorded from Hunspell for one input.
   *
   * @param accepted Whether the Hunspell spell checker accepted the input.
   * @param stems The distinct stems the Hunspell stemmer returned.
   */
  private record Recorded(boolean accepted, Set<String> stems) { }

  /** The classification of one comparison. */
  private enum Outcome {
    EXACT, EXPECTED_DIFFERENCE, IDENTITY_FALLBACK, UNEXPECTED
  }

  /**
   * Verifies the dictionary digests and loads each dictionary once, strictly.
   *
   * @throws Exception If a file is missing, changed, or malformed.
   */
  @BeforeAll
  static void verifyAndLoadDictionaries() throws Exception {
    for (Dictionary dictionary : Dictionary.values()) {
      final Path affix = file(dictionary, HunspellDictionary.AFFIX_FILE_SUFFIX);
      final Path words = file(dictionary, HunspellDictionary.DICTIONARY_FILE_SUFFIX);
      verifyFileChecksum(affix, dictionary.affixChecksum);
      verifyFileChecksum(words, dictionary.dictionaryChecksum);
      final HunspellDictionary loaded = HunspellDictionary.load(affix, words);
      Assertions.assertTrue(loaded.getUnsupportedDirectives().isEmpty());
      STEMMERS.put(dictionary, new HunspellStemmer(loaded));
    }
  }

  /** Checks the comparison classification on synthetic results. */
  @Test
  void comparisonValidation() {
    final Set<String> complete = Set.of("card");
    final Set<String> empty = Set.of();
    final Set<String> identity = Set.of(UNKNOWN);
    final Recorded accepted = new Recorded(true, complete);
    final Recorded acceptedEmpty = new Recorded(true, Set.of());
    final Recorded rejected = new Recorded(false, Set.of());
    Assertions.assertAll(
        () -> Assertions.assertEquals(Outcome.EXACT, classify("card", accepted, complete, null)),
        () -> Assertions.assertEquals(Outcome.EXPECTED_DIFFERENCE,
            classify("card", acceptedEmpty, complete, complete)),
        () -> Assertions.assertEquals(Outcome.IDENTITY_FALLBACK, classify(UNKNOWN, rejected, identity, null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", accepted, empty, null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", acceptedEmpty, complete, null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", rejected, Set.of("cards"), null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", accepted, complete, complete)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", acceptedEmpty, empty, complete)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify(UNKNOWN, accepted, identity, null)));
  }

  /**
   * Confirms that partial loading of a dictionary that loads strictly skips nothing.
   *
   * @param dictionary The external dictionary.
   * @throws Exception If a file is missing or malformed.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void partialLoadingSkipsNothing(Dictionary dictionary) throws Exception {
    final HunspellDictionary partial = HunspellDictionary.load(
        file(dictionary, HunspellDictionary.AFFIX_FILE_SUFFIX),
        file(dictionary, HunspellDictionary.DICTIONARY_FILE_SUFFIX),
        HunspellDictionary.LoadMode.ALLOW_PARTIAL);
    Assertions.assertTrue(partial.getUnsupportedDirectives().isEmpty());
  }

  /**
   * Checks expected stems and compound decompositions.
   *
   * @param dictionary The external dictionary.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void expectedInflections(Dictionary dictionary) {
    final HunspellStemmer stemmer = STEMMERS.get(dictionary);
    final Map<String, String> expected = switch (dictionary) {
      case ENGLISH -> Map.of("workers", "worker", "cats", "cat", "unhappiest", "unhappy",
          "quickly", "quick", "looked", "look");
      case GERMAN -> Map.of("Kinder", "Kind", "Häuser", "Haus", "schnellsten", "schnell");
      case HUNGARIAN -> Map.of("kutyák", "kutya", "asztalon", "asztal", "könyveket", "könyv");
    };
    expected.forEach((word, stem) -> Assertions.assertEquals(stem,
        stemmer.stem(word).toString(), word));
    Assertions.assertEquals(List.of(UNKNOWN), stemmer.stemAll(UNKNOWN));
    if (dictionary == Dictionary.GERMAN) {
      for (String word : List.of("Haustür", "Kinderzimmer", "Abbildungsverzeichnis")) {
        Assertions.assertTrue(stemmer.stemAll(word).size() >= 2, word);
      }
    }
  }

  /**
   * Compares stems and recognition with the recorded Hunspell results and fails on any
   * result that is neither exact, an expected difference, nor an identity fallback for
   * an input Hunspell rejects.
   *
   * @param dictionary The external dictionary.
   * @param reporter The test reporter for the counts.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void referenceCompatibility(Dictionary dictionary, TestReporter reporter) {
    final HunspellStemmer stemmer = STEMMERS.get(dictionary);
    final Map<String, Recorded> recorded = recorded(dictionary);
    int exact = 0;
    int differences = 0;
    int fallback = 0;
    final List<String> failures = new ArrayList<>();
    for (String word : dictionary.inputs) {
      final Recorded reference = recorded.get(word);
      Assertions.assertNotNull(reference, "no recorded reference result for " + word);
      final Set<String> stems = stems(stemmer, word);
      switch (classify(word, reference, stems, expectedDifference(dictionary, word))) {
        case EXACT -> exact++;
        case EXPECTED_DIFFERENCE -> differences++;
        case IDENTITY_FALLBACK -> fallback++;
        case UNEXPECTED -> failures.add(word + ": reference=" + reference + ", OpenNLP=" + stems);
      }
    }
    reporter.publishEntry(dictionary.id, "inputs=" + dictionary.inputs.size() + ", exact=" + exact
        + ", expectedDifferences=" + differences + ", identityFallback=" + fallback
        + ", unexpected=" + failures.size());
    Assertions.assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
  }

  /**
   * Repeats the evaluated inputs from several threads on one shared stemmer and
   * compares each result with the single-threaded result.
   *
   * @param dictionary The external dictionary.
   * @throws Exception If a task fails.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void concurrentStemming(Dictionary dictionary) throws Exception {
    final HunspellStemmer stemmer = STEMMERS.get(dictionary);
    final Map<String, Set<String>> expected = new LinkedHashMap<>();
    dictionary.inputs.forEach(word -> expected.put(word, stems(stemmer, word)));
    final List<Callable<Void>> tasks = new ArrayList<>();
    for (int thread = 0; thread < THREADS; thread++) {
      final int offset = thread;
      tasks.add(() -> {
        for (int repeat = 0; repeat < REPETITIONS; repeat++) {
          for (int index = 0; index < dictionary.inputs.size(); index++) {
            final String word = dictionary.inputs.get((index + offset + repeat) % dictionary.inputs.size());
            Assertions.assertEquals(expected.get(word), stems(stemmer, word), word);
          }
        }
        return null;
      });
    }
    try (var executor = Executors.newFixedThreadPool(THREADS)) {
      for (var future : executor.invokeAll(tasks, TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        Assertions.assertFalse(future.isCancelled(), "concurrent stemming timed out");
        future.get();
      }
    }
  }

  /**
   * Locates a dictionary file under the evaluation data directory.
   *
   * @param dictionary The external dictionary.
   * @param suffix The file suffix.
   * @return The file path.
   * @throws Exception If the data directory is not configured or does not exist.
   */
  private static Path file(Dictionary dictionary, String suffix) throws Exception {
    return new File(getOpennlpDataDir(), DATA_DIRECTORY + File.separator + dictionary.id + suffix).toPath();
  }

  /**
   * Collects the distinct stems of one input.
   *
   * @param stemmer The stemmer.
   * @param word The input.
   * @return The stems in result order.
   */
  private static Set<String> stems(HunspellStemmer stemmer, String word) {
    return new LinkedHashSet<>(stemmer.stemAll(word).stream().map(CharSequence::toString).toList());
  }

  /**
   * Classifies one comparison. An expected difference must match the recorded
   * OpenNLP output exactly and must still differ from the reference, so a stale entry
   * is reported. For an input Hunspell rejects, the unchanged input is the identity
   * fallback of unknown vocabulary; the public API does not tell it apart from a
   * listed word that is its own stem.
   *
   * @param word The input.
   * @param reference The recorded Hunspell outcome.
   * @param stems The OpenNLP stems.
   * @param expected The recorded OpenNLP stems for a known difference, or {@code null}.
   * @return The classification.
   */
  private static Outcome classify(String word, Recorded reference, Set<String> stems, Set<String> expected) {
    if (expected != null) {
      return reference.accepted() && !reference.stems().equals(stems) && expected.equals(stems)
          ? Outcome.EXPECTED_DIFFERENCE : Outcome.UNEXPECTED;
    }
    if (reference.accepted() && reference.stems().equals(stems)) {
      return Outcome.EXACT;
    }
    if (!reference.accepted() && reference.stems().isEmpty() && stems.equals(Set.of(word))) {
      return Outcome.IDENTITY_FALLBACK;
    }
    return Outcome.UNEXPECTED;
  }

  /**
   * Specifies the OpenNLP stems for inputs whose recorded Hunspell stems differ:
   * compound and break parts against concatenated or missing Hunspell stems, and
   * standalone readings of capitalized German nouns against Hunspell's additional
   * lowercase compound-only readings.
   *
   * @param dictionary The external dictionary.
   * @param word The input.
   * @return The expected OpenNLP stems, or {@code null} for an exact comparison.
   */
  private static Set<String> expectedDifference(Dictionary dictionary, String word) {
    if (dictionary == Dictionary.ENGLISH && word.equals("well-known")) {
      return Set.of("well", "known");
    }
    if (dictionary != Dictionary.GERMAN) {
      return null;
    }
    return switch (word) {
      case "Kinder" -> Set.of("Kind");
      case "Häuser" -> Set.of("Haus");
      case "Freunden" -> Set.of("freunden", "Freund");
      case "Vorschläge" -> Set.of("Vor", "schlag");
      case "Haustür" -> Set.of("Haus", "tür");
      case "Kinderzimmer" -> Set.of("Kinder", "zimmer");
      case "Abbildungsverzeichnis" -> Set.of("Abbildungs", "verzeichnis");
      case "Haus" -> Set.of("Haus");
      case "Baum" -> Set.of("Baum");
      case "Buch" -> Set.of("Buch");
      default -> null;
    };
  }

  /**
   * The results recorded from Hunspell for the evaluated inputs, keyed by input.
   *
   * @param dictionary The external dictionary.
   * @return The recorded outcomes.
   */
  private static Map<String, Recorded> recorded(Dictionary dictionary) {
    return switch (dictionary) {
      case ENGLISH -> Map.ofEntries(
          Map.entry("workers", new Recorded(true, Set.of("worker"))),
          Map.entry("cats", new Recorded(true, Set.of("cat"))),
          Map.entry("unhappiest", new Recorded(true, Set.of("unhappy"))),
          Map.entry("quickly", new Recorded(true, Set.of("quick"))),
          Map.entry("looked", new Recorded(true, Set.of("look"))),
          Map.entry("reading", new Recorded(true, Set.of("reading", "read"))),
          Map.entry("dogs", new Recorded(true, Set.of("dog"))),
          Map.entry("books", new Recorded(true, Set.of("book"))),
          Map.entry("walked", new Recorded(true, Set.of("walk"))),
          Map.entry("walking", new Recorded(true, Set.of("walking", "walk"))),
          Map.entry("talked", new Recorded(true, Set.of("talk"))),
          Map.entry("talking", new Recorded(true, Set.of("talk"))),
          Map.entry("played", new Recorded(true, Set.of("play"))),
          Map.entry("playing", new Recorded(true, Set.of("play"))),
          Map.entry("helped", new Recorded(true, Set.of("help"))),
          Map.entry("helping", new Recorded(true, Set.of("helping", "help"))),
          Map.entry("houses", new Recorded(true, Set.of("house"))),
          Map.entry("children", new Recorded(true, Set.of("children"))),
          Map.entry("feet", new Recorded(true, Set.of("feet"))),
          Map.entry("better", new Recorded(true, Set.of("better"))),
          Map.entry("Workers", new Recorded(true, Set.of("worker"))),
          Map.entry("WORKERS", new Recorded(true, Set.of("worker"))),
          Map.entry("cAtS", new Recorded(false, Set.of())),
          Map.entry("worker's", new Recorded(true, Set.of("worker"))),
          Map.entry("well-known", new Recorded(true, Set.of())),
          Map.entry("unhappy", new Recorded(true, Set.of("unhappy", "happy"))),
          Map.entry("undone", new Recorded(true, Set.of("done"))),
          Map.entry(UNKNOWN, new Recorded(false, Set.of())));
      case GERMAN -> Map.ofEntries(
          Map.entry("gegangen", new Recorded(true, Set.of("gegangen"))),
          Map.entry("Kinder", new Recorded(true, Set.of("kinder", "kind", "Kind"))),
          Map.entry("Häuser", new Recorded(true, Set.of("häuser", "haus", "Haus"))),
          Map.entry("schnellsten", new Recorded(true, Set.of("schnell"))),
          Map.entry("Freunden", new Recorded(true, Set.of("freunden", "freund", "Freund"))),
          Map.entry("Vorschläge", new Recorded(true, Set.of())),
          Map.entry("Haustür", new Recorded(true, Set.of())),
          Map.entry("Kinderzimmer", new Recorded(true, Set.of())),
          Map.entry("Abbildungsverzeichnis", new Recorded(true, Set.of())),
          Map.entry("Haus", new Recorded(true, Set.of("haus", "Haus"))),
          Map.entry("Baum", new Recorded(true, Set.of("baum", "Baum"))),
          Map.entry("Buch", new Recorded(true, Set.of("buch", "Buch"))),
          Map.entry("schnell", new Recorded(true, Set.of("schnell"))),
          Map.entry(UNKNOWN, new Recorded(false, Set.of())));
      case HUNGARIAN -> Map.ofEntries(
          Map.entry("kutyák", new Recorded(true, Set.of("kutya"))),
          Map.entry("asztalon", new Recorded(true, Set.of("asztal"))),
          Map.entry("könyveket", new Recorded(true, Set.of("könyv"))),
          Map.entry("házak", new Recorded(true, Set.of("ház"))),
          Map.entry("emberek", new Recorded(true, Set.of("ember"))),
          Map.entry("kutyáknak", new Recorded(true, Set.of("kutya"))),
          Map.entry(UNKNOWN, new Recorded(false, Set.of())));
    };
  }
}
