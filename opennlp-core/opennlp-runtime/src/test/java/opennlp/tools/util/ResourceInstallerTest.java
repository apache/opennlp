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
import java.net.URI;
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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.archive.TarArchives;

import static opennlp.tools.util.InstallerTestSupport.BLOCK;
import static opennlp.tools.util.InstallerTestSupport.KIBIBYTE;
import static opennlp.tools.util.InstallerTestSupport.MEBIBYTE;
import static opennlp.tools.util.InstallerTestSupport.TERMINATOR_SIZE;
import static opennlp.tools.util.InstallerTestSupport.gzip;
import static opennlp.tools.util.InstallerTestSupport.installedFiles;
import static opennlp.tools.util.InstallerTestSupport.sha256;
import static opennlp.tools.util.InstallerTestSupport.sha512;
import static opennlp.tools.util.InstallerTestSupport.tarEntry;
import static opennlp.tools.util.InstallerTestSupport.tarGz;

public class ResourceInstallerTest {

  private static final String CHECKSUM_ARGUMENT_ERROR =
      "checksum must be 64 (SHA-256) or 128 (SHA-512) hex characters; pass null to skip";
  private static final String ESCAPE_ERROR =
      "archive entry escapes the target directory: ";
  private static final String EXPANSION_CEILING_ERROR =
      "expanded content exceeds the ceiling of " + KIBIBYTE + " bytes";

  /**
   * Installs the given file under the default limits and asserts that it fails with the
   * expected message, leaving the target directory without a single installed file.
   *
   * @param file The source file to install.
   * @param target The target directory, which must have been empty before the attempt.
   * @param message The exact failure message expected.
   * @throws IOException Thrown if listing the target directory fails.
   */
  private static void assertInstallFails(Path file, Path target, String message)
      throws IOException {
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals(message, thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Installs the given file under the given limits and asserts that it fails with the
   * expected message, leaving the target directory without a single installed file.
   *
   * @param file The source file to install.
   * @param target The target directory, which must have been empty before the attempt.
   * @param limits The limits to install under.
   * @param message The exact failure message expected.
   * @throws IOException Thrown if listing the target directory fails.
   */
  private static void assertInstallFails(Path file, Path target,
      ResourceInstaller.Limits limits, String message) throws IOException {
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target, null, limits));
    Assertions.assertEquals(message, thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

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

    assertInstallFails(file, target, ESCAPE_ERROR + "../escape.txt");
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

    assertInstallFails(file, target,
        ESCAPE_ERROR + "/absolute-escape-attempt/evil.txt");
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

    assertInstallFails(file, target, ESCAPE_ERROR + "../zip-escape.txt");
    Assertions.assertTrue(Files.notExists(target.getParent().resolve("zip-escape.txt")));
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

  /**
   * Proves that every argument the public API rejects is rejected with its own message,
   * before anything is fetched.
   */
  @Test
  void testInvalidArguments(@TempDir Path target) {
    // assertAll so an unvalidated argument does not hide the others.
    Assertions.assertAll(
        () -> assertArgumentError("source must not be null",
            () -> ResourceInstaller.install(null, target)),
        () -> assertArgumentError("targetDirectory must not be null",
            () -> ResourceInstaller.install(target.toUri(), null)),
        () -> assertArgumentError("limits must not be null",
            () -> ResourceInstaller.install(target.toUri(), target, null, null)));
  }

  /**
   * Enumerates checksum arguments that are neither a valid SHA-256 nor a valid SHA-512
   * digest, so a typo cannot masquerade as a checksum mismatch.
   *
   * @return One case per rejected digest. Never {@code null}.
   */
  static Stream<Arguments> rejectedChecksums() {
    return Stream.of(
        Arguments.of("blank", " "),
        Arguments.of("too short", "abc123"),
        Arguments.of("right length, non-hex characters", "g".repeat(64)),
        Arguments.of("between the two supported lengths", "a".repeat(96)),
        Arguments.of("longer than SHA-512", "a".repeat(129)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedChecksums")
  void testInvalidChecksumIsRejected(String label, String checksum, @TempDir Path target) {
    assertArgumentError(CHECKSUM_ARGUMENT_ERROR,
        () -> ResourceInstaller.install(target.toUri(), target, checksum));
  }

  /**
   * Asserts that the given call fails as an argument error carrying the exact message.
   *
   * @param message The exact failure message expected.
   * @param call The call under test.
   */
  private static void assertArgumentError(String message, Executable call) {
    final IllegalArgumentException thrown =
        Assertions.assertThrows(IllegalArgumentException.class, call);
    Assertions.assertEquals(message, thrown.getMessage());
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

    assertInstallFails(file, target, ESCAPE_ERROR + "../escape.txt");
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

    assertInstallFails(file, target, ESCAPE_ERROR + "../zip-escape.txt");
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

    assertInstallFails(file, target, ceilings(KIBIBYTE, MEBIBYTE),
        "download exceeds the ceiling of " + KIBIBYTE + " bytes");
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

    assertInstallFails(file, target, ceilings(MEBIBYTE, KIBIBYTE),
        EXPANSION_CEILING_ERROR);
  }

  /**
   * Proves that tar metadata is part of the expanded stream budget. A gzip stream made
   * only of highly compressible pax metadata must not bypass the expansion ceiling just
   * because it installs no regular-file content.
   */
  @Test
  void testTarMetadataCountsTowardExpansionCeiling(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    TarArchives.entry(tar, "pax_global_header",
        TarArchives.paxRecord("comment", "a".repeat(64 * 1024)), 'g');
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("metadata-bomb.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    assertInstallFails(file, target, ceilings(MEBIBYTE, KIBIBYTE),
        EXPANSION_CEILING_ERROR);
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

    assertInstallFails(file, target, ceilings(MEBIBYTE, KIBIBYTE),
        EXPANSION_CEILING_ERROR);
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

    assertInstallFails(file, target, ceilings(MEBIBYTE, KIBIBYTE),
        EXPANSION_CEILING_ERROR);
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
        ceilings(MEBIBYTE, MEBIBYTE));

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

    ResourceInstaller.install(file.toUri(), target, null, ceilings(KIBIBYTE, MEBIBYTE));

    Assertions.assertEquals(List.of("exact.dat"), installedFiles(target));
    Assertions.assertEquals(1024, Files.size(target.resolve("exact.dat")));
  }

  /**
   * Proves the accept side of the expansion ceiling: a gzip stream that expands to
   * exactly the ceiling installs, so the boundary is exclusive of failure.
   */
  @Test
  void testExpansionExactlyAtCeilingSucceeds(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "corpus/exact.bin", new byte[1024]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("exact.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    ResourceInstaller.install(file.toUri(), target, null,
        ceilings(MEBIBYTE, tar.size()));

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

    assertInstallFails(file, target, ceilings(MEBIBYTE, KIBIBYTE),
        EXPANSION_CEILING_ERROR);
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

  /**
   * Proves that promotion refuses to replace a file that already exists in the target:
   * the second installation fails naming the file, the first installation's content
   * survives, and after the operator removes it the reinstall succeeds.
   */
  @Test
  void testReinstallOverAnExistingFileIsRefused(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] first = tarGz(new String[][] {{"corpus/data.txt", "version one"}});
    final byte[] second = tarGz(new String[][] {{"corpus/data.txt", "version two"}});
    final Path firstFile = source.resolve("first.tar.gz");
    final Path secondFile = source.resolve("second.tar.gz");
    Files.write(firstFile, first);
    Files.write(secondFile, second);
    ResourceInstaller.install(firstFile.toUri(), target, sha256(first));

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(secondFile.toUri(), target, sha256(second)));

    Assertions.assertEquals(
        "target already contains: " + target.resolve("corpus/data.txt"),
        thrown.getMessage());
    Assertions.assertEquals("version one",
        Files.readString(target.resolve("corpus/data.txt")));

    Files.delete(target.resolve("corpus/data.txt"));
    ResourceInstaller.install(secondFile.toUri(), target, sha256(second));
    Assertions.assertEquals("version two",
        Files.readString(target.resolve("corpus/data.txt")));
  }

  /**
   * Proves that a collision is detected before anything is promoted: an archive whose
   * first entry is new and whose second entry collides installs neither, so a failed
   * installation never leaves a mix of old and new files.
   */
  @Test
  void testCollidingInstallPromotesNothing(@TempDir Path source, @TempDir Path target)
      throws Exception {
    Files.createDirectories(target.resolve("corpus"));
    Files.writeString(target.resolve("corpus/data.txt"), "keep");
    final byte[] archive = tarGz(new String[][] {
        {"corpus/fresh.txt", "new"},
        {"corpus/data.txt", "replacement"}});
    final Path file = source.resolve("colliding.tar.gz");
    Files.write(file, archive);

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target, sha256(archive)));

    Assertions.assertEquals(
        "target already contains: " + target.resolve("corpus/data.txt"),
        thrown.getMessage());
    Assertions.assertEquals("keep", Files.readString(target.resolve("corpus/data.txt")));
    Assertions.assertTrue(Files.notExists(target.resolve("corpus/fresh.txt")));
    Assertions.assertEquals(List.of("corpus/data.txt"), installedFiles(target));
  }

  /**
   * Proves that promotion refuses to follow a symbolic link that already exists below
   * the target. Every archive entry here stays inside the staging directory, so the
   * entry-name guard never fires; without a check at promotion time the content lands
   * wherever the link points.
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void testPromotionRefusesToFollowASymlinkedDirectory(@TempDir Path source,
      @TempDir Path target, @TempDir Path outside) throws Exception {
    final Path link = target.resolve("link");
    Files.createSymbolicLink(link, outside);
    final byte[] archive = tarGz(new String[][] {{"link/planted.txt", "escaped"}});
    final Path file = source.resolve("symlink.tar.gz");
    Files.write(file, archive);

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));

    Assertions.assertEquals("installation path crosses a symbolic link: " + link,
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(outside));
    Assertions.assertTrue(Files.notExists(outside.resolve("planted.txt")));
  }

  /**
   * Proves that an ordinary directory of the same shape still installs, so the symlink
   * guard rejects the link rather than nested paths in general.
   */
  @Test
  void testNestedDirectoryThatIsNotASymlinkStillInstalls(@TempDir Path source,
      @TempDir Path target) throws Exception {
    Files.createDirectory(target.resolve("link"));
    final byte[] archive = tarGz(new String[][] {{"link/planted.txt", "fine"}});
    final Path file = source.resolve("nested.tar.gz");
    Files.write(file, archive);

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals("fine",
        Files.readString(target.resolve("link/planted.txt")));
  }

  /**
   * Enumerates source schemes the installer refuses, each of which would otherwise be
   * handed to whatever URL handler the runtime has installed, outside the timeouts and
   * ceilings this class enforces.
   *
   * @return One case per rejected scheme. Never {@code null}.
   */
  static Stream<Arguments> rejectedSourceSchemes() {
    return Stream.of(
        Arguments.of("ftp", URI.create("ftp://example.invalid/corpus.tar.gz")),
        Arguments.of("jar", URI.create("jar:file:/tmp/a.jar!/corpus.tar.gz")),
        Arguments.of("mailto", URI.create("mailto:someone@example.invalid")),
        Arguments.of("no scheme at all", URI.create("corpus.tar.gz")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedSourceSchemes")
  void testUnsupportedSourceSchemeIsRejected(String label, URI source,
      @TempDir Path target) throws IOException {
    assertArgumentError("source scheme must be http, https, or file, but was: " + source,
        () -> ResourceInstaller.install(source, target));
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  /**
   * Proves that the scheme is rejected before the target directory is touched, so an
   * unusable location cannot leave an empty directory behind.
   */
  @Test
  void testUnsupportedSourceSchemeIsRejectedBeforeCreatingTheTarget(@TempDir Path parent) {
    final Path target = parent.resolve("not-created-yet");

    assertArgumentError(
        "source scheme must be http, https, or file, but was: ftp://example.invalid/c.gz",
        () -> ResourceInstaller.install(URI.create("ftp://example.invalid/c.gz"), target));
    Assertions.assertTrue(Files.notExists(target));
  }




  /**
   * Proves that a builder with nothing set produces exactly the default limits, so
   * {@code builder()} is a safe starting point rather than a second set of defaults.
   */
  @Test
  void testBuilderWithoutOverridesEqualsTheDefaults() {
    Assertions.assertEquals(ResourceInstaller.Limits.DEFAULT,
        ResourceInstaller.Limits.builder().build());
  }

  /**
   * Proves that each builder setter changes its own value and leaves the other four at
   * their defaults.
   */
  @Test
  void testBuilderOverridesOnlyWhatIsSet() {
    final ResourceInstaller.Limits limits = ResourceInstaller.Limits.builder()
        .maxDownloadBytes(MEBIBYTE)
        .maxExpandedBytes(2 * MEBIBYTE)
        .build();

    Assertions.assertEquals(MEBIBYTE, limits.maxDownloadBytes());
    Assertions.assertEquals(2 * MEBIBYTE, limits.maxExpandedBytes());
    Assertions.assertEquals(ResourceInstaller.Limits.DEFAULT.connectTimeout(),
        limits.connectTimeout());
    Assertions.assertEquals(ResourceInstaller.Limits.DEFAULT.readTimeout(),
        limits.readTimeout());
    Assertions.assertEquals(ResourceInstaller.Limits.DEFAULT.maxRedirects(),
        limits.maxRedirects());
  }

  /**
   * Proves that the builder validates exactly as the canonical constructor does, so it
   * cannot be used to sneak past a limit check.
   */
  @Test
  void testBuilderRejectsInvalidValues() {
    Assertions.assertAll(
        () -> assertArgumentError("connectTimeout must be positive",
            () -> ResourceInstaller.Limits.builder().connectTimeout(Duration.ZERO).build()),
        () -> assertArgumentError("readTimeout must be positive",
            () -> ResourceInstaller.Limits.builder().readTimeout(null).build()),
        () -> assertArgumentError("maxRedirects must not be negative",
            () -> ResourceInstaller.Limits.builder().maxRedirects(-1).build()),
        () -> assertArgumentError("maxDownloadBytes must be positive",
            () -> ResourceInstaller.Limits.builder().maxDownloadBytes(0).build()),
        () -> assertArgumentError("maxExpandedBytes must be positive",
            () -> ResourceInstaller.Limits.builder().maxExpandedBytes(-1).build()));
  }

  /**
   * Proves that the download is written on the target's filesystem rather than in the
   * system temporary directory, where a 1 GiB default ceiling could exhaust a small
   * {@code /tmp} while the target has room. The file is hidden and deleted before
   * {@code install} returns, so a leak surfaces in
   * {@link #testInstallationLeavesNoStagingResidue} instead.
   */
  @Test
  void testDownloadFileIsCreatedInTheTargetDirectory(@TempDir Path target)
      throws Exception {
    final Path downloaded = ResourceInstaller.createDownloadFile(target);
    try {
      Assertions.assertEquals(target, downloaded.getParent());
      Assertions.assertTrue(
          downloaded.getFileName().toString().startsWith(".opennlp-download"),
          downloaded.getFileName().toString());
    } finally {
      Files.deleteIfExists(downloaded);
    }
  }
}
