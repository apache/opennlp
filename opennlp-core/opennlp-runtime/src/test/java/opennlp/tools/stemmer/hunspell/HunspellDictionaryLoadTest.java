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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.stemmer.hunspell.HunspellDictionary.LoadMode;
import opennlp.tools.stemmer.hunspell.HunspellDictionary.UnsupportedDirective;

/** Tests the loading policy with project-authored affix and dictionary content. */
class HunspellDictionaryLoadTest {

  private static final String WORDS = "1\ndog/A\n";
  private static final String RULES = "SFX A Y 1\nSFX A 0 s .\n";

  @TempDir
  private Path directory;

  /**
   * Rejects directives with behavior not implemented by the stemmer.
   *
   * @param line An unsupported directive with arguments.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "UNSUPPORTED_CONVERSION 1", "UNSUPPORTED_CASE k", "UNSUPPORTED_SYLLABLES ABC",
      "UNSUPPORTED_LEMMA L", "UNRECOGNIZED value"
  })
  void testUnsupportedDirectiveFailsByDefault(String line) {
    final IOException error = Assertions.assertThrows(IOException.class,
        () -> HunspellDictionary.load(stream("SET UTF-8\n" + line + "\n" + RULES),
            stream(WORDS)));
    final int separator = line.indexOf(' ');
    final String directive = separator < 0 ? line : line.substring(0, separator);
    Assertions.assertTrue(error.getMessage().contains(directive));
    Assertions.assertTrue(error.getMessage().contains("affix stream"));
    Assertions.assertTrue(error.getMessage().contains("line 2"));
  }

  /**
   * Counts logical lines with each supported line separator.
   *
   * @param separator A supported line separator.
   */
  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", "\r"})
  void testUnsupportedDirectiveLineNumber(String separator) {
    final String affix = "# comment" + separator + separator + "  UNSUPPORTED_CASE K";
    final IOException error = Assertions.assertThrows(IOException.class,
        () -> HunspellDictionary.load(stream(affix), stream(WORDS)));
    Assertions.assertTrue(error.getMessage().contains("UNSUPPORTED_CASE"));
    Assertions.assertTrue(error.getMessage().contains("line 3"));
  }

  /** Rejects an unsupported directive immediately after a UTF-8 byte-order mark. */
  @Test
  void testByteOrderMarkDoesNotHideUnsupportedDirective() {
    final IOException error = Assertions.assertThrows(IOException.class,
        () -> HunspellDictionary.load(stream("\uFEFFUNSUPPORTED_CONVERSION 1\n"), stream(WORDS)));
    Assertions.assertTrue(error.getMessage().contains("UNSUPPORTED_CONVERSION"));
    Assertions.assertTrue(error.getMessage().contains("line 1"));
  }

  /**
   * Identifies the source file when path-based loading rejects a directive.
   *
   * @throws IOException If writing a fixture fails.
   */
  @Test
  void testPathErrorIdentifiesAffixFile() throws IOException {
    final Path affix = directory.resolve("sample.aff");
    final Path words = directory.resolve("sample.dic");
    Files.writeString(affix, "SET UTF-8\nUNSUPPORTED_CASE K\n");
    Files.writeString(words, WORDS);
    final IOException error = Assertions.assertThrows(IOException.class,
        () -> HunspellDictionary.load(affix, words));
    Assertions.assertTrue(error.getMessage().contains(affix.toString()));
    Assertions.assertTrue(error.getMessage().contains("line 2"));
  }

  /**
   * Loads settings outside the stemmer's operations without a diagnostic.
   *
   * @param setting A metadata or suggestion setting.
   * @throws IOException If loading fails.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "NAME Example", "HOME https://example.org", "VERSION 1", "KEY abc|def",
      "TRY abc", "REP 1\nREP ph f", "MAP 1\nMAP aá", "PHONE 1\nPHONE ph f",
      "NOSUGGEST N", "MAXCPDSUGS 0", "MAXNGRAMSUGS 0", "MAXDIFF 5",
      "ONLYMAXDIFF", "NOSPLITSUGS", "SUGSWITHDOTS", "WARN W",
      "SUBSTANDARD S", "WORDCHARS -"
  })
  void testSettingsOutsideStemmingDoNotPreventStrictLoading(String setting)
      throws IOException {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        stream(setting + "\n" + RULES), stream(WORDS));
    Assertions.assertEquals("dog", new HunspellStemmer(dictionary).stem("dogs").toString());
    Assertions.assertTrue(dictionary.getUnsupportedDirectives().isEmpty());
  }

  /**
   * Keeps a number sign that is a directive value, which the reference format allows
   * for flags, separators, and affix material.
   *
   * @param affix Affix content in which {@code #} is a value.
   * @param input The stemmed word.
   * @param expected The stem.
   * @throws IOException If loading fails.
   */
  @ParameterizedTest
  @MethodSource("numberSignValues")
  void testNumberSignValuesAreKept(String affix, String words, String input, String expected)
      throws IOException {
    final HunspellDictionary dictionary = HunspellDictionary.load(stream(affix), stream(words));
    Assertions.assertEquals(expected, new HunspellStemmer(dictionary).stem(input).toString());
  }

  /**
   * Directive values consisting of a number sign.
   *
   * @return Affix content, word list, input, and expected stem.
   */
  private static Stream<Arguments> numberSignValues() {
    return Stream.of(
        Arguments.of("BREAK 1\nBREAK #\n" + RULES, WORDS, "dogs#dogs", "dog"),
        Arguments.of("NEEDAFFIX #\n" + RULES, "1\ndog/#A\n", "dogs", "dog"),
        Arguments.of("FLAG long\nAF 1\nAF #A\nSFX #A Y 1\nSFX #A 0 s .\n", "1\ndog/1\n", "dogs", "dog"),
        Arguments.of("SFX A Y 1\nSFX A 0 # .\n", WORDS, "dog#", "dog"),
        Arguments.of("SFX A Y 1\nSFX A # s [#]\n", "1\ndog#/A\n", "dogs", "dog#"));
  }

  /**
   * Ignores trailing comments after the fields a directive consumes, as the reference
   * implementation ignores those fields.
   *
   * @param affix Affix content with a trailing comment.
   * @throws IOException If loading fails.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "COMPOUNDMIN 3 # comment\nSFX A Y 1 # comment\nSFX A 0 s . # comment",
      "NEEDAFFIX X # comment\nSFX A Y 1\nSFX A 0 s .",
      "SET UTF-8 # comment\nFLAG UTF-8 # comment\nSFX A Y 1\nSFX A 0 s .",
      "AF 1\nAF A # comment\nSFX A Y 1\nSFX A 0 s ."
  })
  void testTrailingCommentsAreIgnored(String affix) throws IOException {
    final String words = affix.startsWith("AF") ? "1\ndog/1\n" : WORDS;
    final HunspellDictionary dictionary = HunspellDictionary.load(stream(affix), stream(words));
    Assertions.assertEquals("dog", new HunspellStemmer(dictionary).stem("dogs").toString());
  }

  /**
   * Reports the first location for each skipped directive in source order.
   *
   * @param separator A supported line separator.
   * @throws IOException If partial loading fails.
   */
  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", "\r"})
  void testPartialLoadingReportsFirstOccurrences(String separator) throws IOException {
    final String affix = String.join(separator, "UNSUPPORTED_CONVERSION 1", "UNSUPPORTED_CONVERSION a b",
        "UNSUPPORTED_CASE K", "UNRECOGNIZED 1", "UNRECOGNIZED x", RULES);
    final HunspellDictionary dictionary = HunspellDictionary.load(
        stream(affix), stream(WORDS), LoadMode.ALLOW_PARTIAL);
    final List<UnsupportedDirective> diagnostics = dictionary.getUnsupportedDirectives();
    Assertions.assertEquals(List.of(
        new UnsupportedDirective("UNSUPPORTED_CONVERSION", "affix stream", 1),
        new UnsupportedDirective("UNSUPPORTED_CASE", "affix stream", 3),
        new UnsupportedDirective("UNRECOGNIZED", "affix stream", 4)), diagnostics);
    Assertions.assertThrows(UnsupportedOperationException.class, diagnostics::clear);
    Assertions.assertEquals("dog", new HunspellStemmer(dictionary).stem("dogs").toString());
  }

  /**
   * Includes the affix path in partial-loading diagnostics.
   *
   * @throws IOException If writing or loading fixtures fails.
   */
  @Test
  void testPartialLoadingReportsFilePath() throws IOException {
    final Path affix = directory.resolve("partial.aff");
    final Path words = directory.resolve("partial.dic");
    Files.writeString(affix, "SET UTF-8\nUNSUPPORTED_CASE K\n" + RULES);
    Files.writeString(words, WORDS);
    final HunspellDictionary dictionary = HunspellDictionary.load(
        affix, words, LoadMode.ALLOW_PARTIAL);
    Assertions.assertEquals(List.of(new UnsupportedDirective(
        "UNSUPPORTED_CASE", affix.toString(), 2)), dictionary.getUnsupportedDirectives());
    Assertions.assertEquals("dog", new HunspellStemmer(dictionary).stem("dogs").toString());
  }

  /**
   * Rejects supported malformed content under either loading policy.
   *
   * @param malformed Malformed affix content.
   */
  @ParameterizedTest
  @ValueSource(strings = {"AF -1\n", "FLAG num\nSFX 65536 Y 0\n",
      "COMPOUNDMIN -1\n", "SFX A Y 2\nSFX A 0 s .\n", "FLAG short\n",
      "ICONV 1\n", "ICONV -1\n", "ICONV 1\nICONV a b\nICONV c d\n",
      "OCONV 1\nOCONV _ x\n", "AM 1\n", "AM -1\n",
      "COMPOUNDRULE 1\n", "COMPOUNDRULE 1\nCOMPOUNDRULE *A\n",
      "COMPOUNDRULE 1\nCOMPOUNDRULE (\n", "CHECKCOMPOUNDPATTERN 1\n",
      "CHECKCOMPOUNDPATTERN -1\n", "BREAK 1\n", "BREAK -1\n", "BREAK 1\nBREAK ^\n",
      "IGNORE\n", "LANG\n", "COMPOUNDSYLLABLE -1 ae\n", "KEEPCASE\n",
      "SYLLABLENUM\n", "LEMMA_PRESENT\n", "LEMMA_PRESENT AB\n",
      "AM 1 extra\nAM po:noun\n", "CHECKCOMPOUNDPATTERN 0 extra\n"})
  void testPartialLoadingDoesNotIgnoreMalformedRules(String malformed) {
    Assertions.assertThrows(IOException.class, () -> HunspellDictionary.load(
        stream(malformed), stream("1\ndog\n"), LoadMode.STRICT));
    Assertions.assertThrows(IOException.class, () -> HunspellDictionary.load(
        stream("UNSUPPORTED_CASE K\n" + malformed), stream("1\ndog\n"), LoadMode.ALLOW_PARTIAL));
  }

  /**
   * Rejects invalid AM references in entries and affix fields under either policy.
   *
   * @param reference The invalid reference.
   */
  @ParameterizedTest
  @ValueSource(strings = {"-1", "0", "2", "invalid", "1 1"})
  void testInvalidMorphologyAliases(String reference) {
    final String aliases = "AM 1\nAM po:noun\n";
    for (LoadMode mode : LoadMode.values()) {
      Assertions.assertThrows(IOException.class, () -> HunspellDictionary.load(
          stream(aliases), stream("1\ndog\t" + reference + "\n"), mode));
      Assertions.assertThrows(IOException.class, () -> HunspellDictionary.load(
          stream(aliases + "SFX A Y 1\nSFX A 0 s . " + reference + "\n"), stream(WORDS), mode));
    }
  }

  /**
   * Rejects malformed text in partial mode.
   *
   * @param file The file containing malformed UTF-8.
   */
  @ParameterizedTest
  @ValueSource(strings = {"affix", "dictionary"})
  void testPartialLoadingRejectsMalformedText(String file) {
    final byte[] malformed = {(byte) 0xc3};
    final ByteArrayInputStream affix = "affix".equals(file)
        ? new ByteArrayInputStream(concat("SET UTF-8\nUNSUPPORTED_CASE K\nSFX A Y 1\nSFX A 0 ",
            malformed)) : stream("UNSUPPORTED_CASE K\n");
    final ByteArrayInputStream words = "dictionary".equals(file)
        ? new ByteArrayInputStream(concat("1\n", malformed)) : stream(WORDS);
    final IOException error = Assertions.assertThrows(IOException.class,
        () -> HunspellDictionary.load(affix, words, LoadMode.ALLOW_PARTIAL));
    Assertions.assertEquals(file + " stream is not valid UTF-8", error.getMessage());
  }

  /**
   * Accepts legacy bytes in recognized metadata without decoding them as rules.
   *
   * @throws IOException If loading fails.
   */
  @Test
  void testLegacyMetadataBytesAreIgnored() throws IOException {
    final byte[] affix = concat("SET UTF-8\nNAME ", new byte[] {(byte) 0xc3});
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(affix), stream(WORDS));
    Assertions.assertNotNull(dictionary.lookup("dog"));
    Assertions.assertTrue(dictionary.getUnsupportedDirectives().isEmpty());
  }

  /**
   * Preserves raw flag bytes in compound-boundary conditions without changing word text.
   *
   * @param matchingFlag Whether the left entry has the boundary flag.
   * @throws IOException If loading fails.
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testCompoundPatternByteFlag(boolean matchingFlag) throws IOException {
    final byte[] prefix = concat("SET UTF-8\nCOMPOUNDFLAG C\nCOMPOUNDMIN 1\n"
        + "CHECKCOMPOUNDPATTERN 1\nCHECKCOMPOUNDPATTERN er/", new byte[] {(byte) 0xc3});
    final byte[] affix = Arrays.copyOf(prefix, prefix.length + 3);
    System.arraycopy(" b\n".getBytes(StandardCharsets.UTF_8), 0, affix, prefix.length, 3);
    final byte[] wordPrefix = concat("2\nriver/C", matchingFlag
        ? new byte[] {(byte) 0xc3} : new byte[0]);
    final byte[] ending = "\nboat/C\n".getBytes(StandardCharsets.UTF_8);
    final byte[] words = Arrays.copyOf(wordPrefix, wordPrefix.length + ending.length);
    System.arraycopy(ending, 0, words, wordPrefix.length, ending.length);
    final HunspellStemmer stemmer = new HunspellStemmer(HunspellDictionary.load(
        new ByteArrayInputStream(affix), new ByteArrayInputStream(words)));
    Assertions.assertEquals(matchingFlag ? List.of("riverboat") : List.of("river", "boat"),
        stemmer.stemAll("riverboat"));
  }

  /**
   * Loads supported affix rules after a UTF-8 byte-order mark.
   *
   * @throws IOException If loading fails.
   */
  @Test
  void testByteOrderMarkDoesNotHideSupportedDirective() throws IOException {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        stream("\uFEFF" + RULES), stream(WORDS));
    Assertions.assertEquals("dog", new HunspellStemmer(dictionary).stem("dogs").toString());
  }

  /**
   * Rejects null arguments before reading either stream or opening a file.
   *
   * @param mode The loading policy.
   */
  @ParameterizedTest
  @EnumSource(LoadMode.class)
  void testNullArgumentsAreRejected(LoadMode mode) {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> HunspellDictionary.load((Path) null, directory, mode));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> HunspellDictionary.load(directory, null, mode));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> HunspellDictionary.load(null, stream(WORDS), mode));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> HunspellDictionary.load(stream(RULES), null, mode));
  }

  /** Rejects a null loading policy through both public entry points. */
  @Test
  void testNullModeIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> HunspellDictionary.load(directory, directory, null));
    final ByteArrayInputStream affix = stream(RULES);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> HunspellDictionary.load(affix, stream(WORDS), null));
    Assertions.assertEquals(RULES.getBytes(StandardCharsets.UTF_8).length, affix.available());
  }

  /**
   * Preserves ownership of input streams on success and failure.
   *
   * @param mode The loading policy.
   * @throws IOException If valid content fails to load.
   */
  @ParameterizedTest
  @EnumSource(LoadMode.class)
  void testStreamsAreNotClosed(LoadMode mode) throws IOException {
    final TrackedStream affix = new TrackedStream(RULES);
    final TrackedStream words = new TrackedStream(WORDS);
    HunspellDictionary.load(affix, words, mode);
    Assertions.assertFalse(affix.closed);
    Assertions.assertFalse(words.closed);
    final TrackedStream invalid = new TrackedStream("AF -1\n");
    Assertions.assertThrows(IOException.class,
        () -> HunspellDictionary.load(invalid, words, mode));
    Assertions.assertFalse(invalid.closed);
    Assertions.assertFalse(words.closed);
  }

  /** Verifies validation of a diagnostic's public fields. */
  @Test
  void testDiagnosticArgumentsAreValidated() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new UnsupportedDirective(null, "source", 1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new UnsupportedDirective(" ", "source", 1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new UnsupportedDirective("UNSUPPORTED_CONVERSION", null, 1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new UnsupportedDirective("UNSUPPORTED_CONVERSION", " ", 1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new UnsupportedDirective("UNSUPPORTED_CONVERSION", "source", 0));
  }

  /** Detects close calls while allowing further input operations. */
  private static final class TrackedStream extends ByteArrayInputStream {
    private boolean closed;

    /**
     * Creates an encoded fixture stream.
     *
     * @param content The fixture text.
     */
    private TrackedStream(String content) {
      super(content.getBytes(StandardCharsets.UTF_8));
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
      closed = true;
    }
  }

  /**
   * Appends raw bytes to a UTF-8 fixture prefix.
   *
   * @param prefix The fixture prefix.
   * @param bytes The raw suffix.
   * @return The combined content.
   */
  private byte[] concat(String prefix, byte[] bytes) {
    final byte[] encoded = prefix.getBytes(StandardCharsets.UTF_8);
    final byte[] result = Arrays.copyOf(encoded, encoded.length + bytes.length);
    System.arraycopy(bytes, 0, result, encoded.length, bytes.length);
    return result;
  }

  /**
   * Creates a UTF-8 stream for a fixture.
   *
   * @param content The fixture text.
   * @return The encoded stream.
   */
  private ByteArrayInputStream stream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
