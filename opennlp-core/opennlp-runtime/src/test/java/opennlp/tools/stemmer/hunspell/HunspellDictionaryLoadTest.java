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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
      "ICONV 1", "OCONV 1", "COMPLEXPREFIXES", "COMPOUNDRULE 1",
      "COMPOUNDMORESUFFIXES", "COMPOUNDROOT R", "CHECKCOMPOUNDREP",
      "SIMPLIFIEDTRIPLE", "CHECKCOMPOUNDPATTERN 1", "FORCEUCASE U",
      "COMPOUNDSYLLABLE 6 aeiou", "SYLLABLENUM ABC", "LANG tr",
      "CHECKSHARPS", "BREAK 1", "FORBIDWARN", "IGNORE x", "KEEPCASE k",
      "AM 1", "LEMMA_PRESENT L", "UNRECOGNIZED value"
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
    final String affix = "# comment" + separator + separator + "  KEEPCASE K";
    final IOException error = Assertions.assertThrows(IOException.class,
        () -> HunspellDictionary.load(stream(affix), stream(WORDS)));
    Assertions.assertTrue(error.getMessage().contains("KEEPCASE"));
    Assertions.assertTrue(error.getMessage().contains("line 3"));
  }

  /** Rejects an unsupported directive immediately after a UTF-8 byte-order mark. */
  @Test
  void testByteOrderMarkDoesNotHideUnsupportedDirective() {
    final IOException error = Assertions.assertThrows(IOException.class,
        () -> HunspellDictionary.load(stream("\uFEFFICONV 1\n"), stream(WORDS)));
    Assertions.assertTrue(error.getMessage().contains("ICONV"));
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
    Files.writeString(affix, "SET UTF-8\nKEEPCASE K\n");
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
   * Reports the first location for each skipped directive in source order.
   *
   * @param separator A supported line separator.
   * @throws IOException If partial loading fails.
   */
  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", "\r"})
  void testPartialLoadingReportsFirstOccurrences(String separator) throws IOException {
    final String affix = String.join(separator, "ICONV 1", "ICONV a b",
        "KEEPCASE K", "UNRECOGNIZED 1", "UNRECOGNIZED x", RULES);
    final HunspellDictionary dictionary = HunspellDictionary.load(
        stream(affix), stream(WORDS), LoadMode.ALLOW_PARTIAL);
    final List<UnsupportedDirective> diagnostics = dictionary.getUnsupportedDirectives();
    Assertions.assertEquals(List.of(
        new UnsupportedDirective("ICONV", "affix stream", 1),
        new UnsupportedDirective("KEEPCASE", "affix stream", 3),
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
    Files.writeString(affix, "SET UTF-8\nKEEPCASE K\n" + RULES);
    Files.writeString(words, WORDS);
    final HunspellDictionary dictionary = HunspellDictionary.load(
        affix, words, LoadMode.ALLOW_PARTIAL);
    Assertions.assertEquals(List.of(new UnsupportedDirective(
        "KEEPCASE", affix.toString(), 2)), dictionary.getUnsupportedDirectives());
    Assertions.assertEquals("dog", new HunspellStemmer(dictionary).stem("dogs").toString());
  }

  /**
   * Rejects supported malformed content under either loading policy.
   *
   * @param malformed Malformed affix content.
   */
  @ParameterizedTest
  @ValueSource(strings = {"AF -1\n", "FLAG num\nSFX 65001 Y 0\n",
      "COMPOUNDMIN -1\n", "SFX A Y 2\nSFX A 0 s .\n", "FLAG short\n"})
  void testPartialLoadingDoesNotIgnoreMalformedRules(String malformed) {
    Assertions.assertThrows(IOException.class, () -> HunspellDictionary.load(
        stream(malformed), stream("1\ndog\n"), LoadMode.STRICT));
    Assertions.assertThrows(IOException.class, () -> HunspellDictionary.load(
        stream("KEEPCASE K\n" + malformed), stream("1\ndog\n"), LoadMode.ALLOW_PARTIAL));
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
        ? new ByteArrayInputStream(concat("SET UTF-8\nKEEPCASE K\nSFX A Y 1\nSFX A 0 ",
            malformed)) : stream("KEEPCASE K\n");
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
        () -> new UnsupportedDirective("ICONV", null, 1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new UnsupportedDirective("ICONV", " ", 1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new UnsupportedDirective("ICONV", "source", 0));
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
