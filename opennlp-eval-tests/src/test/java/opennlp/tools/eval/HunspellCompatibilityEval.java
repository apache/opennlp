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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import opennlp.tools.stemmer.hunspell.HunspellDictionary;
import opennlp.tools.stemmer.hunspell.HunspellStemmer;

/**
 * Evaluates external Hunspell dictionaries with optional native comparisons.
 * Configure {@code opennlp.hunspell.dict.dir} and, for native comparisons,
 * {@code opennlp.hunspell.reference}. No data or executable is downloaded.
 * See {@code dev/README-hunspell-dictionaries.md} for input versions and commands.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HunspellCompatibilityEval {

  private static final String DICTIONARY_PROPERTY = "opennlp.hunspell.dict.dir";
  private static final String REFERENCE_PROPERTY = "opennlp.hunspell.reference";
  private static final String WORD_LIST_PROPERTY = "opennlp.hunspell.eval.words.dir";
  private static final String STEM = "stem";
  private static final String ANALYZE = "analyze";
  private static final String SPELL = "spell";
  private static final int THREADS = 4;
  private static final int REPETITIONS = 10;
  private static final int TIMEOUT_SECONDS = 60;
  private static final int MAX_WORDS = 10_000;
  private static final String UNKNOWN = "zyzzyvax";

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  private Path temporary;

  private final Map<Dictionary, HunspellStemmer> stemmers = new EnumMap<>(Dictionary.class);

  /** Files from LibreOffice dictionaries revision 32b006a2c22a4ac7e8ed3f03346f7b3d85a970a4. */
  private enum Dictionary {
    ENGLISH("en_US", StandardCharsets.UTF_8,
        "e746c882dd6f303c2c46e7452804b9201115a6942cfeb15f18f8edf774d2e24e",
        "f0b1a234bd178bdd01875b2a392a9647f888b8fe879f79c52aae62c2759b3647",
        List.of("workers", "cats", "unhappiest", "quickly", "looked", "reading",
            "dogs", "books", "walked", "walking", "talked", "talking", "played",
            "playing", "helped", "helping", "houses", "children", "feet", "better",
            "Workers", "WORKERS", "cAtS", "worker's", "well-known", "unhappy", "undone", UNKNOWN)),
    GERMAN("de_DE_frami", StandardCharsets.ISO_8859_1,
        "646bf3333ac69c23e9d794533ee5241d6f755c359e8fe10a648f87613743d594",
        "4ca3c958b0e5545910999bc246f668840bf8ede3df8e5e6790d05edd5a586c38",
        List.of("gegangen", "Kinder", "Häuser", "schnellsten", "Freunden", "Vorschläge",
            "Haustür", "Kinderzimmer", "Abbildungsverzeichnis", "Haus", "Baum", "Buch", "schnell", UNKNOWN)),
    HUNGARIAN("hu_HU", StandardCharsets.UTF_8,
        "f3a2748dd535cfde2142ab17d0f7f8e4787b03fb25a60829c69ac8d493db4802",
        "97293d670ad4a3b8e7eebef7e25c6e8e939b914c64b6b4672b2bf416b768f990",
        List.of("kutyák", "asztalon", "könyveket", "házak", "emberek", "kutyáknak", UNKNOWN));

    private final String id;
    private final Charset encoding;
    private final String affixHash;
    private final String dictionaryHash;
    private final List<String> inputs;

    /**
     * Defines an external dictionary and original evaluation inputs.
     *
     * @param id The file base name.
     * @param encoding The native input and output encoding.
     * @param affixHash The affix SHA-256 digest.
     * @param dictionaryHash The dictionary SHA-256 digest.
     * @param inputs The input forms.
     */
    Dictionary(String id, Charset encoding, String affixHash, String dictionaryHash,
        List<String> inputs) {
      this.id = id;
      this.encoding = encoding;
      this.affixHash = affixHash;
      this.dictionaryHash = dictionaryHash;
      this.inputs = inputs;
    }
  }

  /**
   * Distinct stemming and morphology output, without changing result text.
   *
   * @param stems The stems.
   * @param analyses The morphological fields in application order.
   */
  private record Result(Set<String> stems, Set<String> analyses) { }

  /**
   * An explicit expected difference between implementations.
   *
   * @param nativeResult The reference output.
   * @param javaResult The OpenNLP output.
   */
  private record Difference(Result nativeResult, Result javaResult) { }

  /** Classification of complete results from both implementations. */
  private enum Outcome {
    EXACT, EXPECTED_DIFFERENCE, IDENTITY_FALLBACK, UNEXPECTED
  }

  /** Checks that missing, changed or invalid results cannot pass classification. */
  @Test
  void comparisonValidation() {
    final Result complete = new Result(Set.of("card"), Set.of("st:card"));
    final Result empty = new Result(Set.of(), Set.of());
    final Result identity = new Result(Set.of(UNKNOWN), Set.of());
    final Difference known = new Difference(empty, complete);
    Assertions.assertAll(
        () -> Assertions.assertEquals(Outcome.EXACT,
            classify("card", complete, complete, "1", null)),
        () -> Assertions.assertEquals(Outcome.EXPECTED_DIFFERENCE,
            classify("card", empty, complete, "1", known)),
        () -> Assertions.assertEquals(Outcome.IDENTITY_FALLBACK,
            classify(UNKNOWN, empty, identity, "0", null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED,
            classify("card", complete, empty, "1", null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED,
            classify("card", empty, complete, "1", null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED,
            classify("card", complete, complete, "0", null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED,
            classify("card", complete, complete, "2", null)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED,
            classify("card", complete, complete, "1", known)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED,
            classify("card", empty, empty, "1", known)),
        () -> Assertions.assertEquals(Outcome.UNEXPECTED,
            classify(UNKNOWN, empty, identity, "1", null)));
  }

  /** Checks field order, tab-separated alternatives, and empty native results. */
  @Test
  void nativeResultParsing() {
    Assertions.assertAll(
        () -> Assertions.assertEquals(Set.of(), fields("", true)),
        () -> Assertions.assertEquals(Set.of("card", "cards"), fields("card\tcards", false)),
        () -> Assertions.assertEquals(Set.of("st:card po:noun", "fl:A st:card"),
            fields("  st:card   po:noun\tfl:A st:card ", true)),
        () -> Assertions.assertNotEquals(fields("st:card fl:A", true), fields("fl:A st:card", true)));
  }

  /**
   * Verifies strict loading without ignored unsupported directives.
   *
   * @param dictionary The external dictionary.
   * @throws Exception If input validation or loading fails.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void strictLoading(Dictionary dictionary) throws Exception {
    final Path affix = file(dictionary, HunspellDictionary.AFFIX_FILE_SUFFIX);
    final Path words = file(dictionary, HunspellDictionary.DICTIONARY_FILE_SUFFIX);
    stemmer(dictionary);
    final HunspellDictionary partial = HunspellDictionary.load(affix, words,
        HunspellDictionary.LoadMode.ALLOW_PARTIAL);
    Assertions.assertTrue(partial.getUnsupportedDirectives().isEmpty());
  }

  /**
   * Checks expected stems without requiring a native executable.
   *
   * @param dictionary The external dictionary.
   * @throws Exception If input validation or loading fails.
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
   * Checks native stems, morphological fields and recognition independently.
   *
   * @param dictionary The external dictionary.
   * @param reporter The JUnit report destination.
   * @throws Exception If input validation or native execution fails.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void nativeCompatibility(Dictionary dictionary, TestReporter reporter) throws Exception {
    compare(dictionary, dictionary.inputs, reporter);
  }

  /**
   * Evaluates an optional UTF-8 input file without accepting new result differences.
   *
   * @param dictionary The external dictionary.
   * @param reporter The JUnit report destination.
   * @throws Exception If reading inputs or native execution fails.
   */
  @ParameterizedTest
  @EnumSource(Dictionary.class)
  void externalWordList(Dictionary dictionary, TestReporter reporter) throws Exception {
    final String directory = System.getProperty(WORD_LIST_PROPERTY);
    Assumptions.assumeTrue(directory != null, "no external word lists configured");
    Assertions.assertFalse(directory.isBlank(), "empty " + WORD_LIST_PROPERTY);
    final Path input = Path.of(directory, dictionary.id + ".txt");
    final List<String> words = Files.readAllLines(input, StandardCharsets.UTF_8);
    Assertions.assertFalse(words.isEmpty(), "empty word list: " + input);
    Assertions.assertTrue(words.size() <= MAX_WORDS, "word list exceeds " + MAX_WORDS);
    Assertions.assertTrue(words.stream().noneMatch(String::isBlank), "blank input in " + input);
    report(reporter, dictionary.id + ".wordListSha256", fingerprint(input));
    compare(dictionary, words, reporter);
  }

  /**
   * Checks deterministic stems and analyses using a shared dictionary and stemmer.
   *
   * @param dictionary The external dictionary.
   * @throws Exception If loading or a worker fails.
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
   * Loads and verifies a dictionary once for this evaluation instance.
   *
   * @param dictionary The external dictionary.
   * @return The shared stemmer.
   * @throws Exception If files do not match the expected revision or cannot load.
   */
  private HunspellStemmer stemmer(Dictionary dictionary) throws Exception {
    if (!stemmers.containsKey(dictionary)) {
      final Path affix = file(dictionary, HunspellDictionary.AFFIX_FILE_SUFFIX);
      final Path words = file(dictionary, HunspellDictionary.DICTIONARY_FILE_SUFFIX);
      verifyHash(affix, dictionary.affixHash);
      verifyHash(words, dictionary.dictionaryHash);
      final HunspellDictionary loaded = HunspellDictionary.load(affix, words);
      Assertions.assertTrue(loaded.getUnsupportedDirectives().isEmpty());
      stemmers.put(dictionary, new HunspellStemmer(loaded));
    }
    return stemmers.get(dictionary);
  }

  /**
   * Resolves a required file after the user enables dictionary evaluation.
   *
   * @param dictionary The external dictionary.
   * @param suffix The file extension.
   * @return The configured path.
   */
  private Path file(Dictionary dictionary, String suffix) {
    final String directory = System.getProperty(DICTIONARY_PROPERTY);
    Assumptions.assumeTrue(directory != null, "no external dictionaries configured");
    Assertions.assertFalse(directory.isBlank(), "empty " + DICTIONARY_PROPERTY);
    final Path path = Path.of(directory, dictionary.id + suffix);
    Assertions.assertTrue(Files.isReadable(path), "missing dictionary file: " + path);
    return path;
  }

  /**
   * Verifies an external file without storing dictionary bytes in reports.
   *
   * @param path The external file.
   * @param expected The SHA-256 digest.
   * @throws Exception If hashing fails.
   */
  private void verifyHash(Path path, String expected) throws Exception {
    Assertions.assertEquals(expected, fingerprint(path), path.toString());
  }

  /**
   * Computes a file digest for input validation and evaluation reports.
   *
   * @param path The file.
   * @return The SHA-256 digest.
   * @throws Exception If hashing fails.
   */
  private String fingerprint(Path path) throws Exception {
    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (var input = Files.newInputStream(path)) {
      final byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        digest.update(buffer, 0, count);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  /**
   * Obtains Java output without sorting fields within an analysis.
   *
   * @param stemmer The implementation under evaluation.
   * @param word The input.
   * @return Distinct output strings.
   */
  private Result result(HunspellStemmer stemmer, String word) {
    return new Result(new LinkedHashSet<>(stemmer.stemAll(word).stream()
        .map(CharSequence::toString).toList()), new LinkedHashSet<>(stemmer.analyze(word)));
  }

  /**
   * Reports complete batch counts and fails on any unexpected output.
   *
   * @param dictionary The external dictionary.
   * @param words Input forms, including duplicates when supplied.
   * @param reporter The JUnit report destination.
   * @throws Exception If native execution or file access fails.
   */
  private void compare(Dictionary dictionary, List<String> words, TestReporter reporter) throws Exception {
    final HunspellStemmer stemmer = stemmer(dictionary);
    final List<String> stems = reference(dictionary, words, STEM);
    final List<String> analyses = reference(dictionary, words, ANALYZE);
    final List<String> recognition = reference(dictionary, words, SPELL);
    report(reporter, dictionary.id + ".inputs", "affixSha256=" + dictionary.affixHash
        + ", dictionarySha256=" + dictionary.dictionaryHash + ", referenceSha256="
        + fingerprint(Path.of(System.getProperty(REFERENCE_PROPERTY))));
    int exact = 0;
    int differences = 0;
    int fallback = 0;
    final List<String> failures = new ArrayList<>();
    for (int index = 0; index < words.size(); index++) {
      final String word = words.get(index);
      final Result nativeResult = new Result(fields(stems.get(index), false),
          fields(analyses.get(index), true));
      final Result javaResult = result(stemmer, word);
      final String accepted = recognition.get(index);
      final Difference difference = expectedDifference(dictionary, word);
      switch (classify(word, nativeResult, javaResult, accepted, difference)) {
        case EXACT -> exact++;
        case EXPECTED_DIFFERENCE -> differences++;
        case IDENTITY_FALLBACK -> fallback++;
        case UNEXPECTED -> failures.add(word + ": spell=" + accepted
            + ", native=" + nativeResult + ", Java=" + javaResult);
      }
    }
    report(reporter, dictionary.id, "inputs=" + words.size() + ", exact=" + exact
        + ", expectedDifferences=" + differences + ", identityFallback=" + fallback
        + ", unexpected=" + failures.size());
    Assertions.assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
  }

  /**
   * Records evaluation results in JUnit events and Maven test output.
   *
   * @param reporter The JUnit report destination.
   * @param name The report label.
   * @param value The report content.
   */
  private void report(TestReporter reporter, String name, String value) {
    reporter.publishEntry(name, value);
    System.out.println(name + ": " + value);
  }

  /**
   * Classifies results without treating documented differences as exact matches.
   *
   * @param word The input.
   * @param nativeResult The native stems and analyses.
   * @param javaResult The OpenNLP stems and analyses.
   * @param accepted Native recognition, either {@code 0} or {@code 1}.
   * @param difference The expected difference, or {@code null}.
   * @return The comparison classification.
   */
  private Outcome classify(String word, Result nativeResult, Result javaResult,
      String accepted, Difference difference) {
    if (difference != null) {
      return accepted.equals("1") && difference.nativeResult.equals(nativeResult)
          && difference.javaResult.equals(javaResult) ? Outcome.EXPECTED_DIFFERENCE : Outcome.UNEXPECTED;
    }
    if (accepted.equals("1") && nativeResult.equals(javaResult)) {
      return Outcome.EXACT;
    }
    if (accepted.equals("0") && nativeResult.stems.isEmpty() && nativeResult.analyses.isEmpty()
        && javaResult.stems.equals(Set.of(word)) && javaResult.analyses.isEmpty()) {
      return Outcome.IDENTITY_FALLBACK;
    }
    return Outcome.UNEXPECTED;
  }

  /**
   * Runs a batch through the external C API driver with the dictionary encoding.
   *
   * @param dictionary The external dictionary.
   * @param words The input forms.
   * @param operation The native operation.
   * @return One output line per input, including empty results.
   * @throws Exception If a process fails, times out or returns invalid output.
   */
  private List<String> reference(Dictionary dictionary, List<String> words, String operation)
      throws Exception {
    final String executable = System.getProperty(REFERENCE_PROPERTY);
    Assumptions.assumeTrue(executable != null, "no native reference configured");
    Assertions.assertTrue(Files.isExecutable(Path.of(executable)), "invalid native reference: " + executable);
    final Path directory = Files.createTempDirectory(temporary, dictionary.id + "-");
    final Path input = directory.resolve("input.txt");
    final Path output = directory.resolve("output.txt");
    final Path errors = directory.resolve("errors.txt");
    Files.writeString(input, String.join("\n", words) + "\n", dictionary.encoding);
    final Process process = new ProcessBuilder(executable,
        file(dictionary, HunspellDictionary.AFFIX_FILE_SUFFIX).toString(),
        file(dictionary, HunspellDictionary.DICTIONARY_FILE_SUFFIX).toString(), operation)
        .redirectInput(input.toFile()).redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
    try {
      Assertions.assertTrue(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS), "native reference timed out");
      Assertions.assertEquals(0, process.exitValue(), () -> "native reference failed; see " + errors);
      final List<String> lines = Files.readAllLines(output, dictionary.encoding);
      Assertions.assertEquals(words.size(), lines.size(), "native result count for " + operation);
      return lines;
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
    }
  }

  /**
   * Parses tab-separated results, normalizing only separators between morphology fields.
   *
   * @param line The native output line.
   * @param morphology Whether results contain morphological fields.
   * @return Distinct results with field order preserved.
   */
  private Set<String> fields(String line, boolean morphology) {
    final Set<String> results = new LinkedHashSet<>();
    final StringTokenizer entries = new StringTokenizer(line, "\t");
    while (entries.hasMoreTokens()) {
      final String value = entries.nextToken();
      if (morphology) {
        final StringTokenizer tokens = new StringTokenizer(value);
        final List<String> parts = new ArrayList<>();
        while (tokens.hasMoreTokens()) {
          parts.add(tokens.nextToken());
        }
        results.add(String.join(" ", parts));
      } else {
        results.add(value);
      }
    }
    return results;
  }

  /**
   * Specifies compound-only, compound, and BREAK output differences.
   *
   * @param dictionary The external dictionary.
   * @param word The input form.
   * @return The expected difference, or {@code null} for exact comparison.
   */
  private Difference expectedDifference(Dictionary dictionary, String word) {
    if (dictionary == Dictionary.ENGLISH && word.equals("well-known")) {
      return new Difference(new Result(Set.of(), Set.of()),
          new Result(Set.of("well", "known"), Set.of("pa:well st:well pa:known st:known")));
    }
    if (dictionary != Dictionary.GERMAN) {
      return null;
    }
    return switch (word) {
      case "Kinder" -> new Difference(
          new Result(Set.of("kinder", "kind", "Kind"),
              Set.of("st:kinder fl:k", "st:kind fl:R", "st:Kind fl:R")),
          new Result(Set.of("Kind"), Set.of("st:Kind fl:R")));
      case "Häuser" -> new Difference(
          new Result(Set.of("Haus", "haus", "häuser"),
              Set.of("st:Haus fl:p", "st:haus fl:p", "st:häuser fl:k")),
          new Result(Set.of("Haus"), Set.of("st:Haus fl:p")));
      case "schnellsten" -> new Difference(
          new Result(Set.of("schnell"), Set.of("fl:k st:schnell fl:C", "st:schnell fl:C")),
          new Result(Set.of("schnell"), Set.of("st:schnell fl:C")));
      case "Freunden" -> new Difference(
          new Result(Set.of("freunden", "freund", "Freund"),
              Set.of("st:freund fl:P", "st:freunden", "st:Freund fl:P")),
          new Result(Set.of("freunden", "Freund"), Set.of("st:freunden", "st:Freund fl:P")));
      case "Vorschläge" -> new Difference(new Result(Set.of(), Set.of()),
          new Result(Set.of("Vor", "schlag"), Set.of("pa:Vor st:Vor fl:j pa:schläge st:schlag fl:p")));
      case "Haustür" -> new Difference(new Result(Set.of(), Set.of("pa:tür")),
          new Result(Set.of("Haus", "tür"), Set.of("pa:Haus st:Haus fl:j pa:tür")));
      case "Kinderzimmer" -> new Difference(new Result(Set.of(), Set.of("pa:zimmer")),
          new Result(Set.of("Kinder", "zimmer"), Set.of("pa:Kinder st:Kinder fl:j pa:zimmer")));
      case "Abbildungsverzeichnis" -> new Difference(new Result(Set.of(), Set.of("pa:verzeichnis")),
          new Result(Set.of("Abbildungs", "verzeichnis"),
              Set.of("pa:Abbildungs st:Abbildungs fl:j pa:verzeichnis")));
      case "Haus" -> new Difference(new Result(Set.of("haus", "Haus"), Set.of("st:haus fl:k", "st:Haus")),
          new Result(Set.of("Haus"), Set.of("st:Haus")));
      case "Baum" -> new Difference(new Result(Set.of("baum", "Baum"), Set.of("st:baum fl:k", "st:Baum")),
          new Result(Set.of("Baum"), Set.of("st:Baum")));
      case "Buch" -> new Difference(new Result(Set.of("buch", "Buch"), Set.of("st:buch fl:k", "st:Buch")),
          new Result(Set.of("Buch"), Set.of("st:Buch")));
      case "schnell" -> new Difference(new Result(Set.of("schnell"), Set.of("st:schnell", "st:schnell fl:k")),
          new Result(Set.of("schnell"), Set.of("st:schnell")));
      default -> null;
    };
  }
}
