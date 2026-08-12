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

package opennlp.tools.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static opennlp.tools.util.InstallerTestSupport.BLOCK;
import static opennlp.tools.util.InstallerTestSupport.TERMINATOR_SIZE;
import static opennlp.tools.util.InstallerTestSupport.gzip;
import static opennlp.tools.util.InstallerTestSupport.installedFiles;
import static opennlp.tools.util.InstallerTestSupport.sha256;
import static opennlp.tools.util.InstallerTestSupport.sha512;
import static opennlp.tools.util.InstallerTestSupport.tarEntry;
import static opennlp.tools.util.InstallerTestSupport.tarGz;

public class ResourceInstallerTest {

  /**
   * Computes the SHA-256 of the given bytes as an uppercase hex string, so tests can
   * prove that checksum comparison does not depend on the hex letter case.
   *
   * @param content The bytes to digest. Must not be {@code null}.
   * @return The 64-character uppercase hex digest. Never {@code null}.
   * @throws NoSuchAlgorithmException Thrown if the digest algorithm is unavailable.
   */
  private static String sha256UpperCase(byte[] content) throws NoSuchAlgorithmException {
    return sha256(content).toUpperCase(Locale.ROOT);
  }

  /**
   * Builds installation limits with generous timeouts and redirect allowance but the
   * given size ceilings, so ceiling tests state only the values they exercise.
   *
   * @param maxDownloadBytes The download ceiling in bytes.
   * @param maxExpandedBytes The expansion ceiling in bytes.
   * @return The limits. Never {@code null}.
   */
  private static ResourceInstaller.Limits ceilings(long maxDownloadBytes,
      long maxExpandedBytes) {
    return new ResourceInstaller.Limits(Duration.ofSeconds(10), Duration.ofSeconds(10),
        5, maxDownloadBytes, maxExpandedBytes);
  }

  /**
   * Demonstrates the intended end-to-end flow: package a small corpus as tar.gz,
   * compute its real SHA-256, install it through the public API, and verify that
   * exactly the archived files, and nothing else, appear on disk with their exact
   * content.
   */
  @Test
  void testInstallEndToEndUsageExample(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {
        {"corpus/README", "A tiny example corpus.\n"},
        {"corpus/tokens.txt", "the\ncat\n"},
        {"corpus/pos/tags.tsv", "the\tDET\ncat\tNOUN\n"}});
    final Path file = source.resolve("corpus.tar.gz");
    Files.write(file, archive);

    final Path result = ResourceInstaller.install(file.toUri(), target, sha256(archive));

    Assertions.assertEquals(target, result);
    Assertions.assertEquals(
        List.of("corpus/README", "corpus/pos/tags.tsv", "corpus/tokens.txt"),
        installedFiles(target));
    Assertions.assertEquals("A tiny example corpus.\n",
        Files.readString(target.resolve("corpus/README")));
    Assertions.assertEquals("the\ncat\n",
        Files.readString(target.resolve("corpus/tokens.txt")));
    Assertions.assertEquals("the\tDET\ncat\tNOUN\n",
        Files.readString(target.resolve("corpus/pos/tags.tsv")));
  }

  @Test
  void testTarGzUnpacksWithStructure(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {
        {"corpus-1.0/train.conllu", "# sent_id = 1\n"},
        {"corpus-1.0/sub/readme.txt", "hello"}});
    final Path file = source.resolve("corpus.tgz");
    Files.write(file, archive);

    ResourceInstaller.install(file.toUri(), target, sha256(archive));

    Assertions.assertEquals("# sent_id = 1\n",
        Files.readString(target.resolve("corpus-1.0/train.conllu")));
    Assertions.assertEquals("hello",
        Files.readString(target.resolve("corpus-1.0/sub/readme.txt")));
  }

  @Test
  void testChecksumMismatchFailsBeforeUnpacking(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final byte[] archive = tarGz(new String[][] {{"a/file.txt", "content"}});
    final Path file = source.resolve("archive.tar.gz");
    Files.write(file, archive);

    final String wrong = "0".repeat(64);
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target, wrong));
    Assertions.assertEquals(IOException.class, thrown.getClass());
    Assertions.assertEquals("checksum mismatch: expected " + wrong
        + " but downloaded " + sha256(archive), thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves that the hex digest comparison is case-insensitive: an uppercase rendering
   * of the correct digest passes verification and the archive is unpacked.
   */
  @Test
  void testChecksumComparisonIgnoresHexLetterCase(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final byte[] archive = tarGz(new String[][] {{"data/entry.txt", "payload"}});
    final Path file = source.resolve("cased.tar.gz");
    Files.write(file, archive);
    // The uppercase digest must differ textually from the lowercase one, otherwise
    // this test would not exercise the case handling at all.
    Assertions.assertNotEquals(sha256(archive), sha256UpperCase(archive));

    ResourceInstaller.install(file.toUri(), target, sha256UpperCase(archive));

    Assertions.assertEquals("payload",
        Files.readString(target.resolve("data/entry.txt")));
  }

  @Test
  void testEscapingEntriesAreRejected(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {{"../escape.txt", "bad"}});
    final Path file = source.resolve("evil.tar.gz");
    Files.write(file, archive);

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals(
        "archive entry escapes the target directory: ../escape.txt",
        thrown.getMessage());
    Assertions.assertTrue(Files.notExists(target.getParent().resolve("escape.txt")));
  }

  /**
   * Proves that a tar entry with an absolute name is rejected before anything is
   * written, so a hostile archive cannot place files at arbitrary locations.
   */
  @Test
  void testAbsoluteTarEntryIsRejected(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {
        {"/absolute-escape-attempt/evil.txt", "bad"}});
    final Path file = source.resolve("absolute.tar.gz");
    Files.write(file, archive);

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals(
        "archive entry escapes the target directory: /absolute-escape-attempt/evil.txt",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
    Assertions.assertTrue(Files.notExists(Path.of("/absolute-escape-attempt")));
  }

  /**
   * Proves that the escape guard also covers zip content: an entry whose name climbs
   * out of the target directory is rejected and nothing is written.
   */
  @Test
  void testZipEntryWithTraversalIsRejected(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("../zip-escape.txt"));
      zip.write("bad".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    final Path file = source.resolve("evil.zip");
    Files.write(file, out.toByteArray());

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals(
        "archive entry escapes the target directory: ../zip-escape.txt",
        thrown.getMessage());
    Assertions.assertTrue(Files.notExists(target.getParent().resolve("zip-escape.txt")));
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testZipUnpacks(@TempDir Path source, @TempDir Path target) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("lexicon/words.txt"));
      zip.write("cat 100\n".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    final Path file = source.resolve("lexicon.zip");
    Files.write(file, out.toByteArray());

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals("cat 100\n",
        Files.readString(target.resolve("lexicon/words.txt")));
  }

  /**
   * Proves the OpenNLP model convention: a source named {@code *.bin} is stored
   * verbatim even though every OpenNLP model file is itself a zip archive, so
   * the install delivers the packed model a {@code *Model} loader expects, not
   * its unpacked innards.
   */
  @Test
  void testModelBinIsStoredPacked(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("manifest.properties"));
      zip.write("OpenNLP-Version: 0.0.0\n".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    final Path file = source.resolve("en-ner-person.bin");
    Files.write(file, out.toByteArray());

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals(List.of("en-ner-person.bin"), installedFiles(target));
    Assertions.assertArrayEquals(out.toByteArray(),
        Files.readAllBytes(target.resolve("en-ner-person.bin")));
  }

  @Test
  void testPlainGzipDecompressesToTheSourceName(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write("word\tlemma\n".getBytes(StandardCharsets.UTF_8));
    }
    final Path file = source.resolve("lexicon.tsv.gz");
    Files.write(file, out.toByteArray());

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals("word\tlemma\n",
        Files.readString(target.resolve("lexicon.tsv")));
  }

  @Test
  void testPlainFilesAreStoredUnderTheirSourceName(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final Path file = source.resolve("frequencies.txt");
    Files.writeString(file, "cat 100");

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals("cat 100", Files.readString(target.resolve("frequencies.txt")));
  }

  @Test
  void testInvalidArguments(@TempDir Path target) {
    IllegalArgumentException thrown = Assertions.assertThrows(
        IllegalArgumentException.class, () -> ResourceInstaller.install(null, target));
    Assertions.assertEquals("source must not be null", thrown.getMessage());

    thrown = Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceInstaller.install(target.toUri(), null));
    Assertions.assertEquals("targetDirectory must not be null", thrown.getMessage());

    thrown = Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceInstaller.install(target.toUri(), target, " "));
    Assertions.assertEquals(
        "checksum must be 64 (SHA-256) or 128 (SHA-512) hex characters; pass null to skip",
        thrown.getMessage());

    thrown = Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceInstaller.install(target.toUri(), target, null, null));
    Assertions.assertEquals("limits must not be null", thrown.getMessage());
  }

  /**
   * Proves that a 128-character hex digest selects SHA-512 verification: the correct
   * SHA-512 of the archive passes and the content is installed.
   */
  @Test
  void testSha512ChecksumVerifies(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {{"data/entry.txt", "payload"}});
    final Path file = source.resolve("checked.tar.gz");
    Files.write(file, archive);

    ResourceInstaller.install(file.toUri(), target, sha512(archive));

    Assertions.assertEquals("payload",
        Files.readString(target.resolve("data/entry.txt")));
  }

  @Test
  void testSha512ChecksumMismatchFailsBeforeUnpacking(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final byte[] archive = tarGz(new String[][] {{"a/file.txt", "content"}});
    final Path file = source.resolve("archive.tar.gz");
    Files.write(file, archive);

    final String wrong = "0".repeat(128);
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target, wrong));
    Assertions.assertEquals("checksum mismatch: expected " + wrong
        + " but downloaded " + sha512(archive), thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves that a digest whose length matches neither SHA-256 nor SHA-512 is rejected
   * as an argument error before anything is fetched.
   */
  @Test
  void testChecksumOfUnsupportedLengthIsRejected(@TempDir Path target) {
    final IllegalArgumentException thrown = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> ResourceInstaller.install(target.toUri(), target, "abc123"));
    Assertions.assertEquals(
        "checksum must be 64 (SHA-256) or 128 (SHA-512) hex characters; pass null to skip",
        thrown.getMessage());
  }

  /**
   * Proves that a digest of the right length but with non-hex characters is rejected
   * as an argument error, so a typo cannot masquerade as a checksum mismatch.
   */
  @Test
  void testChecksumWithNonHexCharactersIsRejected(@TempDir Path target) {
    final IllegalArgumentException thrown = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> ResourceInstaller.install(target.toUri(), target, "g".repeat(64)));
    Assertions.assertEquals(
        "checksum must be 64 (SHA-256) or 128 (SHA-512) hex characters; pass null to skip",
        thrown.getMessage());
  }

  /**
   * Proves staged installation for tar content: when a later entry escapes the target
   * directory, the earlier entry that already unpacked cleanly must not appear either.
   */
  @Test
  void testFailedTarUnpackLeavesTargetEmpty(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {
        {"good.txt", "fine"},
        {"../escape.txt", "bad"}});
    final Path file = source.resolve("partial.tar.gz");
    Files.write(file, archive);

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals(
        "archive entry escapes the target directory: ../escape.txt",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves staged installation for zip content: a traversal entry after a clean one
   * leaves the target without the clean entry as well.
   */
  @Test
  void testFailedZipUnpackLeavesTargetEmpty(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("good.txt"));
      zip.write("fine".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("../zip-escape.txt"));
      zip.write("bad".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    final Path file = source.resolve("partial.zip");
    Files.write(file, out.toByteArray());

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals(
        "archive entry escapes the target directory: ../zip-escape.txt",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves staged installation for a truncated archive: the tar breaks off inside its
   * second entry header, and the first entry that unpacked cleanly must not appear.
   */
  @Test
  void testTruncatedTarLeavesTargetEmpty(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "first.txt", "complete".getBytes(StandardCharsets.UTF_8));
    tarEntry(tar, "second.txt", "never finished".getBytes(StandardCharsets.UTF_8));
    final byte[] whole = tar.toByteArray();
    // Cut inside the second entry's header: first entry occupies two 512-byte blocks.
    final byte[] truncated = Arrays.copyOf(whole, 2 * BLOCK + 100);
    final Path file = source.resolve("truncated.tar.gz");
    Files.write(file, gzip(truncated));

    Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves that a failed installation does not disturb files that already existed in
   * the target directory before the attempt.
   */
  @Test
  void testFailedInstallKeepsPreexistingTargetContent(@TempDir Path source,
      @TempDir Path target) throws Exception {
    Files.writeString(target.resolve("existing.txt"), "keep");
    final byte[] archive = tarGz(new String[][] {
        {"good.txt", "fine"},
        {"../escape.txt", "bad"}});
    final Path file = source.resolve("partial.tar.gz");
    Files.write(file, archive);

    Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals(List.of("existing.txt"), installedFiles(target));
    Assertions.assertEquals("keep", Files.readString(target.resolve("existing.txt")));
  }

  /**
   * Proves that neither a successful nor a failed installation leaves hidden staging
   * directories behind in the target.
   */
  @Test
  void testInstallationLeavesNoStagingResidue(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {{"corpus/data.txt", "content"}});
    final Path file = source.resolve("clean.tar.gz");
    Files.write(file, archive);

    ResourceInstaller.install(file.toUri(), target);

    try (Stream<Path> walk = Files.walk(target)) {
      final List<String> hidden = walk
          .filter(path -> !path.equals(target))
          .map(path -> path.getFileName().toString())
          .filter(fileName -> fileName.startsWith("."))
          .toList();
      Assertions.assertEquals(List.of(), hidden);
    }
    Assertions.assertEquals(List.of("corpus/data.txt"), installedFiles(target));
  }

  /**
   * Enumerates invalid {@code Limits} constructions with the argument error each one
   * must raise.
   *
   * @return One case per invalid argument. Never {@code null}.
   */
  static Stream<Arguments> invalidLimits() {
    final Duration valid = Duration.ofSeconds(10);
    return Stream.of(
        Arguments.of("null connectTimeout", (Executable)
            () -> new ResourceInstaller.Limits(null, valid, 5, 1024, 1024),
            "connectTimeout must be positive"),
        Arguments.of("zero connectTimeout", (Executable)
            () -> new ResourceInstaller.Limits(Duration.ZERO, valid, 5, 1024, 1024),
            "connectTimeout must be positive"),
        Arguments.of("null readTimeout", (Executable)
            () -> new ResourceInstaller.Limits(valid, null, 5, 1024, 1024),
            "readTimeout must be positive"),
        Arguments.of("negative readTimeout", (Executable)
            () -> new ResourceInstaller.Limits(valid, Duration.ofSeconds(-1),
                5, 1024, 1024),
            "readTimeout must be positive"),
        Arguments.of("negative maxRedirects", (Executable)
            () -> new ResourceInstaller.Limits(valid, valid, -1, 1024, 1024),
            "maxRedirects must not be negative"),
        Arguments.of("zero maxDownloadBytes", (Executable)
            () -> new ResourceInstaller.Limits(valid, valid, 5, 0, 1024),
            "maxDownloadBytes must be positive"),
        Arguments.of("zero maxExpandedBytes", (Executable)
            () -> new ResourceInstaller.Limits(valid, valid, 5, 1024, 0),
            "maxExpandedBytes must be positive"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidLimits")
  void testLimitsRejectInvalidValues(String label, Executable construction,
      String message) {
    final IllegalArgumentException thrown =
        Assertions.assertThrows(IllegalArgumentException.class, construction);
    Assertions.assertEquals(message, thrown.getMessage());
  }

  /**
   * Proves the download ceiling on a file source: a fetch larger than the configured
   * ceiling is rejected and nothing is installed.
   */
  @Test
  void testDownloadCeilingRejectsOversizedSource(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final Path file = source.resolve("large.txt");
    Files.write(file, new byte[8192]);

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target, null,
            ceilings(1024, 1024 * 1024)));
    Assertions.assertEquals("download exceeds the ceiling of 1024 bytes",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves the expansion ceiling on tar content: a small compressed archive whose
   * entries expand beyond the ceiling is rejected and nothing is installed.
   */
  @Test
  void testTarExpansionCeilingRejectsArchive(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "bomb/zeros.bin", new byte[64 * 1024]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("bomb.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target, null,
            ceilings(1024 * 1024, 1024)));
    Assertions.assertEquals("expanded content exceeds the ceiling of 1024 bytes",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves the expansion ceiling on zip content: highly compressible entries that
   * expand beyond the ceiling are rejected and nothing is installed.
   */
  @Test
  void testZipExpansionCeilingRejectsArchive(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("bomb/zeros.bin"));
      zip.write(new byte[64 * 1024]);
      zip.closeEntry();
    }
    final Path file = source.resolve("bomb.zip");
    Files.write(file, out.toByteArray());

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target, null,
            ceilings(1024 * 1024, 1024)));
    Assertions.assertEquals("expanded content exceeds the ceiling of 1024 bytes",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves the expansion ceiling on plain gzip content: a small gzip file whose
   * decompressed form exceeds the ceiling is rejected and nothing is installed.
   */
  @Test
  void testPlainGzipExpansionCeilingRejectsFile(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final Path file = source.resolve("zeros.bin.gz");
    Files.write(file, gzip(new byte[64 * 1024]));

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target, null,
            ceilings(1024 * 1024, 1024)));
    Assertions.assertEquals("expanded content exceeds the ceiling of 1024 bytes",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves that an installation within custom ceilings succeeds, so the limits
   * overload is usable for its intended purpose and not only for rejection.
   */
  @Test
  void testInstallWithinCustomCeilingsSucceeds(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {{"corpus/data.txt", "small"}});
    final Path file = source.resolve("small.tar.gz");
    Files.write(file, archive);

    ResourceInstaller.install(file.toUri(), target, sha256(archive),
        ceilings(1024 * 1024, 1024 * 1024));

    Assertions.assertEquals("small", Files.readString(target.resolve("corpus/data.txt")));
  }

  /**
   * Pins the default limits the two- and three-argument {@code install} methods
   * apply, so a change to them cannot slip through unnoticed.
   */
  @Test
  void testDefaultLimitsArePinned() {
    final ResourceInstaller.Limits defaults = ResourceInstaller.Limits.DEFAULT;
    Assertions.assertEquals(Duration.ofSeconds(20), defaults.connectTimeout());
    Assertions.assertEquals(Duration.ofSeconds(60), defaults.readTimeout());
    Assertions.assertEquals(5, defaults.maxRedirects());
    Assertions.assertEquals(1L << 30, defaults.maxDownloadBytes());
    Assertions.assertEquals(4L << 30, defaults.maxExpandedBytes());
  }

  /**
   * Proves that SHA-512 comparison is case-insensitive like SHA-256: an uppercase
   * rendering of the correct digest passes verification.
   */
  @Test
  void testSha512ChecksumComparisonIgnoresHexLetterCase(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final byte[] archive = tarGz(new String[][] {{"data/entry.txt", "payload"}});
    final Path file = source.resolve("cased512.tar.gz");
    Files.write(file, archive);
    final String upperCase = sha512(archive).toUpperCase(Locale.ROOT);
    Assertions.assertNotEquals(sha512(archive), upperCase);

    ResourceInstaller.install(file.toUri(), target, upperCase);

    Assertions.assertEquals("payload",
        Files.readString(target.resolve("data/entry.txt")));
  }

  /**
   * Proves the accept side of the download ceiling: a source exactly at the ceiling
   * installs, so the boundary is exclusive of failure.
   */
  @Test
  void testDownloadExactlyAtCeilingSucceeds(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final Path file = source.resolve("exact.dat");
    Files.write(file, new byte[1024]);

    ResourceInstaller.install(file.toUri(), target, null, ceilings(1024, 1024 * 1024));

    Assertions.assertEquals(List.of("exact.dat"), installedFiles(target));
    Assertions.assertEquals(1024, Files.size(target.resolve("exact.dat")));
  }

  /**
   * Proves the accept side of the expansion ceiling: content that expands to exactly
   * the ceiling installs, so the boundary is exclusive of failure.
   */
  @Test
  void testExpansionExactlyAtCeilingSucceeds(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "corpus/exact.bin", new byte[1024]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("exact.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    ResourceInstaller.install(file.toUri(), target, null, ceilings(1024 * 1024, 1024));

    Assertions.assertEquals(List.of("corpus/exact.bin"), installedFiles(target));
    Assertions.assertEquals(1024, Files.size(target.resolve("corpus/exact.bin")));
  }

  /**
   * Proves that the expansion budget is shared across all archive entries: two
   * entries that each fit under the ceiling but cross it together are rejected, and
   * nothing is installed.
   */
  @Test
  void testCumulativeExpansionAcrossEntriesHitsCeiling(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "corpus/first.bin", new byte[768]);
    tarEntry(tar, "corpus/second.bin", new byte[768]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("cumulative.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target, null,
            ceilings(1024 * 1024, 1024)));
    Assertions.assertEquals("expanded content exceeds the ceiling of 1024 bytes",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves that installing over the same target replaces files the previous
   * installation delivered, so an operator can refresh a resource in place.
   */
  @Test
  void testReinstallReplacesExistingFiles(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] first = tarGz(new String[][] {{"corpus/data.txt", "version one"}});
    final byte[] second = tarGz(new String[][] {{"corpus/data.txt", "version two"}});
    final Path firstFile = source.resolve("first.tar.gz");
    final Path secondFile = source.resolve("second.tar.gz");
    Files.write(firstFile, first);
    Files.write(secondFile, second);

    ResourceInstaller.install(firstFile.toUri(), target, sha256(first));
    Assertions.assertEquals("version one",
        Files.readString(target.resolve("corpus/data.txt")));

    ResourceInstaller.install(secondFile.toUri(), target, sha256(second));

    Assertions.assertEquals("version two",
        Files.readString(target.resolve("corpus/data.txt")));
    Assertions.assertEquals(List.of("corpus/data.txt"), installedFiles(target));
  }
}
