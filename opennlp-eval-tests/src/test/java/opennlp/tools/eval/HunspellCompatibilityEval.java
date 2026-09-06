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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import opennlp.tools.stemmer.hunspell.HunspellDictionary;
import opennlp.tools.stemmer.hunspell.HunspellStemmer;

/**
 * Evaluates the Hunspell stemmer with the LibreOffice English, German, and Hungarian
 * dictionaries under the {@code hunspell} directory of {@code OPENNLP_DATA_DIR}: strict
 * loading, expected inflections and compounds, concurrent use, and agreement with the
 * stems, analyses, and recognition recorded from the reference implementation as
 * described in {@code dev/README-hunspell-dictionaries.md}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HunspellCompatibilityEval extends AbstractEvalTest {

  private static final String DATA_DIRECTORY = "hunspell";
  private static final int THREADS = 4;
  private static final int REPETITIONS = 10;
  private static final int TIMEOUT_SECONDS = 60;
  private static final String UNKNOWN = "zyzzyvax";

  private final Map<Dictionary, HunspellStemmer> stemmers = new EnumMap<>(Dictionary.class);

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
   * The distinct stems and analyses of one input.
   *
   * @param stems The stems.
   * @param analyses The morphological analyses.
   */
  private record Result(Set<String> stems, Set<String> analyses) { }

  /**
   * The reference implementation's recorded outcome for one input.
   *
   * @param accepted Whether the reference spell checker accepted the input.
   * @param stems The reference stems.
   * @param analyses The reference analyses with separator whitespace normalized.
   */
  private record Recorded(boolean accepted, Set<String> stems, Set<String> analyses) {

    /** {@return the recorded stems and analyses as a result} */
    Result result() {
      return new Result(stems, analyses);
    }
  }

  /** The classification of one comparison. */
  private enum Outcome {
    EXACT, EXPECTED_DIFFERENCE, IDENTITY_FALLBACK, UNEXPECTED
  }

  /** Checks the comparison classification on synthetic results. */
  @Test
  void comparisonValidation() {
    final Result complete = new Result(Set.of("card"), Set.of("st:card"));
    final Result empty = new Result(Set.of(), Set.of());
    final Result identity = new Result(Set.of(UNKNOWN), Set.of());
    final Recorded accepted = new Recorded(true, complete.stems(), complete.analyses());
    final Recorded acceptedEmpty = new Recorded(true, Set.of(), Set.of());
    final Recorded rejected = new Recorded(false, Set.of(), Set.of());
    Assertions.assertAll(
        () -> Assertions.assertEquals(Outcome.EXACT, classify("card", accepted, complete, null)),
        () -> Assertions.assertEquals(Outcome.EXPECTED_DIFFERENCE,
            classify("card", acceptedEmpty, complete, complete)),
        () -> Assertions.assertEquals(Outcome.IDENTITY_FALLBACK, classify(UNKNOWN, rejected, identity, null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", accepted, empty, null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", acceptedEmpty, complete, null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", rejected, complete, null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", accepted, complete, complete)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify("card", acceptedEmpty, empty, complete)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED, classify(UNKNOWN, accepted, identity, null)));
  }

  /**
   * Loads each dictionary strictly and confirms that partial loading skips nothing.
   *
   * @param dictionary The external dictionary.
   * @throws Exception If a file is missing, changed, or malformed.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void strictLoading(Dictionary dictionary) throws Exception {
    stemmer(dictionary);
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
   * @throws Exception If loading fails.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void expectedInflections(Dictionary dictionary) throws Exception {
    final HunspellStemmer stemmer = stemmer(dictionary);
    final Map<String, String> expected = switch (dictionary) {
      case ENGLISH -> Map.of("workers", "worker", "cats", "cat", "unhappiest", "unhappy",
          "quickly", "quick", "looked", "look");
      case GERMAN -> Map.of("Kinder", "Kind", "Häuser", "Haus", "schnellsten", "schnell");
      case HUNGARIAN -> Map.of("kutyák", "kutya", "asztalon", "asztal", "könyveket", "könyv");
    };
    expected.forEach((word, stem) -> Assertions.assertEquals(stem,
        stemmer.stem(word).toString(), word));
    Assertions.assertEquals(List.of(UNKNOWN), stemmer.stemAll(UNKNOWN));
    Assertions.assertTrue(stemmer.analyze(UNKNOWN).isEmpty());
    if (dictionary == Dictionary.GERMAN) {
      for (String word : List.of("Haustür", "Kinderzimmer", "Abbildungsverzeichnis")) {
        Assertions.assertTrue(stemmer.stemAll(word).size() >= 2, word);
        Assertions.assertTrue(stemmer.analyze(word).stream()
            .anyMatch(analysis -> analysis.startsWith("pa:")), word);
      }
    }
  }

  /**
   * Compares stems, analyses, and recognition with the recorded reference results and
   * fails on any result that is neither exact, an expected difference, nor an identity
   * fallback for an input the reference rejects.
   *
   * @param dictionary The external dictionary.
   * @param reporter The test reporter for the counts.
   * @throws Exception If loading fails.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void referenceCompatibility(Dictionary dictionary, TestReporter reporter) throws Exception {
    final HunspellStemmer stemmer = stemmer(dictionary);
    final Map<String, Recorded> recorded = recorded(dictionary);
    int exact = 0;
    int differences = 0;
    int fallback = 0;
    final List<String> failures = new ArrayList<>();
    for (String word : dictionary.inputs) {
      final Recorded reference = recorded.get(word);
      Assertions.assertNotNull(reference, "no recorded reference result for " + word);
      final Result javaResult = result(stemmer, word);
      switch (classify(word, reference, javaResult, expectedDifference(dictionary, word))) {
        case EXACT -> exact++;
        case EXPECTED_DIFFERENCE -> differences++;
        case IDENTITY_FALLBACK -> fallback++;
        case UNEXPECTED -> failures.add(word + ": reference=" + reference + ", OpenNLP=" + javaResult);
      }
    }
    final String summary = "inputs=" + dictionary.inputs.size() + ", exact=" + exact
        + ", expectedDifferences=" + differences + ", identityFallback=" + fallback
        + ", unexpected=" + failures.size();
    reporter.publishEntry(dictionary.id, summary);
    System.out.println(dictionary.id + ": " + summary);
    Assertions.assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
  }

  /**
   * Repeats the evaluated inputs from several threads on one shared stemmer and
   * compares each result with the single-threaded result.
   *
   * @param dictionary The external dictionary.
   * @throws Exception If loading fails or a task fails.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void concurrentAnalysis(Dictionary dictionary) throws Exception {
    final HunspellStemmer stemmer = stemmer(dictionary);
    final Map<String, Result> expected = new LinkedHashMap<>();
    dictionary.inputs.forEach(word -> expected.put(word, result(stemmer, word)));
    final List<Callable<Void>> tasks = new ArrayList<>();
    for (int thread = 0; thread < THREADS; thread++) {
      final int offset = thread;
      tasks.add(() -> {
        for (int repeat = 0; repeat < REPETITIONS; repeat++) {
          for (int index = 0; index < dictionary.inputs.size(); index++) {
            final String word = dictionary.inputs.get((index + offset + repeat) % dictionary.inputs.size());
            Assertions.assertEquals(expected.get(word), result(stemmer, word), word);
          }
        }
        return null;
      });
    }
    try (var executor = Executors.newFixedThreadPool(THREADS)) {
      for (var future : executor.invokeAll(tasks, TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        Assertions.assertFalse(future.isCancelled(), "concurrent analysis timed out");
        future.get();
      }
    }
  }

  /**
   * Loads a dictionary once after verifying its digests.
   *
   * @param dictionary The external dictionary.
   * @return The shared stemmer.
   * @throws Exception If a file is missing, changed, or malformed.
   */
  private HunspellStemmer stemmer(Dictionary dictionary) throws Exception {
    if (!stemmers.containsKey(dictionary)) {
      final Path affix = file(dictionary, HunspellDictionary.AFFIX_FILE_SUFFIX);
      final Path words = file(dictionary, HunspellDictionary.DICTIONARY_FILE_SUFFIX);
      verifyFileChecksum(affix, dictionary.affixChecksum);
      verifyFileChecksum(words, dictionary.dictionaryChecksum);
      final HunspellDictionary loaded = HunspellDictionary.load(affix, words);
      Assertions.assertTrue(loaded.getUnsupportedDirectives().isEmpty());
      stemmers.put(dictionary, new HunspellStemmer(loaded));
    }
    return stemmers.get(dictionary);
  }

  /**
   * Locates a dictionary file under the evaluation data directory.
   *
   * @param dictionary The external dictionary.
   * @param suffix The file suffix.
   * @return The file path.
   * @throws Exception If the data directory is not configured or does not exist.
   */
  private Path file(Dictionary dictionary, String suffix) throws Exception {
    return new File(getOpennlpDataDir(), DATA_DIRECTORY + File.separator + dictionary.id + suffix).toPath();
  }

  /**
   * Collects the distinct stems and analyses of one input.
   *
   * @param stemmer The stemmer.
   * @param word The input.
   * @return The result.
   */
  private Result result(HunspellStemmer stemmer, String word) {
    return new Result(new LinkedHashSet<>(stemmer.stemAll(word).stream()
        .map(CharSequence::toString).toList()), new LinkedHashSet<>(stemmer.analyze(word)));
  }

  /**
   * Classifies one comparison. An expected difference must match the recorded
   * OpenNLP output exactly and must still differ from the reference, so a stale entry
   * is reported.
   *
   * @param word The input.
   * @param reference The recorded reference outcome.
   * @param javaResult The OpenNLP result.
   * @param expected The recorded OpenNLP output for a known difference, or {@code null}.
   * @return The classification.
   */
  private Outcome classify(String word, Recorded reference, Result javaResult, Result expected) {
    if (expected != null) {
      return reference.accepted() && !reference.result().equals(javaResult)
          && expected.equals(javaResult) ? Outcome.EXPECTED_DIFFERENCE : Outcome.UNEXPECTED;
    }
    if (reference.accepted() && reference.result().equals(javaResult)) {
      return Outcome.EXACT;
    }
    if (!reference.accepted() && reference.stems().isEmpty() && reference.analyses().isEmpty()
        && javaResult.stems().equals(Set.of(word)) && javaResult.analyses().isEmpty()) {
      return Outcome.IDENTITY_FALLBACK;
    }
    return Outcome.UNEXPECTED;
  }

  /**
   * Specifies the OpenNLP output for inputs whose recorded reference output differs:
   * compound and break parts against concatenated or missing reference stems, and
   * the reference analyzer's lowercase readings of capitalized German nouns.
   *
   * @param dictionary The external dictionary.
   * @param word The input.
   * @return The expected OpenNLP output, or {@code null} for an exact comparison.
   */
  private Result expectedDifference(Dictionary dictionary, String word) {
    if (dictionary == Dictionary.ENGLISH && word.equals("well-known")) {
      return new Result(Set.of("well", "known"), Set.of("pa:well st:well pa:known st:known"));
    }
    if (dictionary != Dictionary.GERMAN) {
      return null;
    }
    return switch (word) {
      case "Kinder" -> new Result(Set.of("Kind"), Set.of("st:Kind fl:R"));
      case "Häuser" -> new Result(Set.of("Haus"), Set.of("st:Haus fl:p"));
      case "schnellsten" -> new Result(Set.of("schnell"), Set.of("st:schnell fl:C"));
      case "Freunden" -> new Result(Set.of("freunden", "Freund"), Set.of("st:freunden", "st:Freund fl:P"));
      case "Vorschläge" -> new Result(Set.of("Vor", "schlag"),
          Set.of("pa:Vor st:Vor fl:j pa:schläge st:schlag fl:p"));
      case "Haustür" -> new Result(Set.of("Haus", "tür"), Set.of("pa:Haus st:Haus fl:j pa:tür"));
      case "Kinderzimmer" -> new Result(Set.of("Kinder", "zimmer"),
          Set.of("pa:Kinder st:Kinder fl:j pa:zimmer"));
      case "Abbildungsverzeichnis" -> new Result(Set.of("Abbildungs", "verzeichnis"),
          Set.of("pa:Abbildungs st:Abbildungs fl:j pa:verzeichnis"));
      case "Haus" -> new Result(Set.of("Haus"), Set.of("st:Haus"));
      case "Baum" -> new Result(Set.of("Baum"), Set.of("st:Baum"));
      case "Buch" -> new Result(Set.of("Buch"), Set.of("st:Buch"));
      case "schnell" -> new Result(Set.of("schnell"), Set.of("st:schnell"));
      default -> null;
    };
  }

  /**
   * The results recorded from the reference implementation for the evaluated inputs,
   * keyed by input.
   *
   * @param dictionary The external dictionary.
   * @return The recorded outcomes.
   */
  private static Map<String, Recorded> recorded(Dictionary dictionary) {
    return switch (dictionary) {
      case ENGLISH -> Map.ofEntries(
          Map.entry("workers", new Recorded(true,
              Set.of("worker"),
              Set.of("st:worker fl:S"))),
          Map.entry("cats", new Recorded(true,
              Set.of("cat"),
              Set.of("st:cat fl:S"))),
          Map.entry("unhappiest", new Recorded(true,
              Set.of("unhappy"),
              Set.of("st:unhappy fl:T"))),
          Map.entry("quickly", new Recorded(true,
              Set.of("quick"),
              Set.of("st:quick fl:Y"))),
          Map.entry("looked", new Recorded(true,
              Set.of("look"),
              Set.of("st:look fl:D"))),
          Map.entry("reading", new Recorded(true,
              Set.of("reading", "read"),
              Set.of("st:reading", "st:read fl:G"))),
          Map.entry("dogs", new Recorded(true,
              Set.of("dog"),
              Set.of("st:dog fl:S"))),
          Map.entry("books", new Recorded(true,
              Set.of("book"),
              Set.of("st:book fl:S"))),
          Map.entry("walked", new Recorded(true,
              Set.of("walk"),
              Set.of("st:walk fl:D"))),
          Map.entry("walking", new Recorded(true,
              Set.of("walking", "walk"),
              Set.of("st:walking", "st:walk fl:G"))),
          Map.entry("talked", new Recorded(true,
              Set.of("talk"),
              Set.of("st:talk fl:D"))),
          Map.entry("talking", new Recorded(true,
              Set.of("talk"),
              Set.of("st:talk fl:G"))),
          Map.entry("played", new Recorded(true,
              Set.of("play"),
              Set.of("st:play fl:D"))),
          Map.entry("playing", new Recorded(true,
              Set.of("play"),
              Set.of("st:play fl:G"))),
          Map.entry("helped", new Recorded(true,
              Set.of("help"),
              Set.of("st:help fl:D"))),
          Map.entry("helping", new Recorded(true,
              Set.of("helping", "help"),
              Set.of("st:helping", "st:help fl:G"))),
          Map.entry("houses", new Recorded(true,
              Set.of("house"),
              Set.of("st:house fl:S"))),
          Map.entry("children", new Recorded(true,
              Set.of("children"),
              Set.of("st:children"))),
          Map.entry("feet", new Recorded(true,
              Set.of("feet"),
              Set.of("st:feet"))),
          Map.entry("better", new Recorded(true,
              Set.of("better"),
              Set.of("st:better"))),
          Map.entry("Workers", new Recorded(true,
              Set.of("worker"),
              Set.of("st:worker fl:S"))),
          Map.entry("WORKERS", new Recorded(true,
              Set.of("worker"),
              Set.of("st:worker fl:S"))),
          Map.entry("cAtS", new Recorded(false,
              Set.of(),
              Set.of())),
          Map.entry("worker's", new Recorded(true,
              Set.of("worker"),
              Set.of("st:worker fl:M"))),
          Map.entry("well-known", new Recorded(true,
              Set.of(),
              Set.of())),
          Map.entry("unhappy", new Recorded(true,
              Set.of("unhappy", "happy"),
              Set.of("st:unhappy", "un st:happy fl:U"))),
          Map.entry("undone", new Recorded(true,
              Set.of("done"),
              Set.of("un st:done fl:U"))),
          Map.entry("zyzzyvax", new Recorded(false,
              Set.of(),
              Set.of())));
      case GERMAN -> Map.ofEntries(
          Map.entry("gegangen", new Recorded(true,
              Set.of("gegangen"),
              Set.of("st:gegangen"))),
          Map.entry("Kinder", new Recorded(true,
              Set.of("kinder", "kind", "Kind"),
              Set.of("st:kinder fl:k", "st:kind fl:R", "st:Kind fl:R"))),
          Map.entry("Häuser", new Recorded(true,
              Set.of("häuser", "haus", "Haus"),
              Set.of("st:häuser fl:k", "st:haus fl:p", "st:Haus fl:p"))),
          Map.entry("schnellsten", new Recorded(true,
              Set.of("schnell"),
              Set.of("fl:k st:schnell fl:C", "st:schnell fl:C"))),
          Map.entry("Freunden", new Recorded(true,
              Set.of("freunden", "freund", "Freund"),
              Set.of("st:freunden", "st:freund fl:P", "st:Freund fl:P"))),
          Map.entry("Vorschläge", new Recorded(true,
              Set.of(),
              Set.of())),
          Map.entry("Haustür", new Recorded(true,
              Set.of(),
              Set.of("pa:tür"))),
          Map.entry("Kinderzimmer", new Recorded(true,
              Set.of(),
              Set.of("pa:zimmer"))),
          Map.entry("Abbildungsverzeichnis", new Recorded(true,
              Set.of(),
              Set.of("pa:verzeichnis"))),
          Map.entry("Haus", new Recorded(true,
              Set.of("haus", "Haus"),
              Set.of("st:haus fl:k", "st:Haus"))),
          Map.entry("Baum", new Recorded(true,
              Set.of("baum", "Baum"),
              Set.of("st:baum fl:k", "st:Baum"))),
          Map.entry("Buch", new Recorded(true,
              Set.of("buch", "Buch"),
              Set.of("st:buch fl:k", "st:Buch"))),
          Map.entry("schnell", new Recorded(true,
              Set.of("schnell"),
              Set.of("st:schnell", "st:schnell fl:k"))),
          Map.entry("zyzzyvax", new Recorded(false,
              Set.of(),
              Set.of())));
      case HUNGARIAN -> Map.ofEntries(
          Map.entry("kutyák", new Recorded(true,
              Set.of("kutya"),
              Set.of("st:kutya po:noun ts:NOM is:PLUR is:NOM"))),
          Map.entry("asztalon", new Recorded(true,
              Set.of("asztal"),
              Set.of("st:asztal po:noun ts:NOM is:SUE"))),
          Map.entry("könyveket", new Recorded(true,
              Set.of("könyv"),
              Set.of("st:könyv po:noun ts:NOM is:PLUR is:ACC"))),
          Map.entry("házak", new Recorded(true,
              Set.of("ház"),
              Set.of("st:ház po:noun ts:PLUR ts:NOM"))),
          Map.entry("emberek", new Recorded(true,
              Set.of("ember"),
              Set.of("st:ember po:noun ts:NOM is:PLUR is:NOM"))),
          Map.entry("kutyáknak", new Recorded(true,
              Set.of("kutya"),
              Set.of("st:kutya po:noun ts:NOM is:PLUR is:DAT"))),
          Map.entry("zyzzyvax", new Recorded(false,
              Set.of(),
              Set.of())));
    };
  }
}
