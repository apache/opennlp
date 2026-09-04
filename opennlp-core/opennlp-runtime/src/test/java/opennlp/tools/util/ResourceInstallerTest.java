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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.junit.jupiter.params.provider.ValueSource;

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
  private static final String EXPANSION_LIMIT_ERROR =
      "expanded content exceeds the limit of " + KIBIBYTE + " bytes";
  private static final String ENTRY_LIMIT_ERROR =
      "archive entry count exceeds the limit of 2 entries";
  private static final String COLLISION_ERROR = "target already contains: ";
  private static final String DUPLICATE_ENTRY_ERROR =
      "archive contains duplicate file entry: ";
  private static final String RATIO_ERROR =
      "content expands beyond 100 times its compressed size";
  private static final String ZIP_MISMATCH_ERROR =
      "zip local headers and central directory list different files";

  /** The property name used by the parser tests; never read by the installer. */
  private static final String TEST_LIMIT_PROPERTY = "opennlp.test.limit";

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
   * given size limits, so limit tests state only the values they exercise.
   *
   * @param maxDownloadBytes The download limit in bytes.
   * @param maxExpandedBytes The expansion limit in bytes.
   * @return The limits. Never {@code null}.
   */
  private static ResourceInstaller.Limits limits(long maxDownloadBytes,
      long maxExpandedBytes) {
    return new ResourceInstaller.Limits(Duration.ofSeconds(10), Duration.ofSeconds(10),
        5, maxDownloadBytes, maxExpandedBytes,
        ResourceInstaller.Limits.DEFAULT.maxEntries(),
        ResourceInstaller.Limits.DEFAULT.maxExpansionRatio());
  }

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
  void testChecksumIgnoresUnicodeWhitespace(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final byte[] archive = tarGz(new String[][] {{"corpus/data.txt", "verified"}});
    final Path file = source.resolve("unicode-space.tar.gz");
    Files.write(file, archive);
    final String emSpace = Character.toString(0x2003);

    ResourceInstaller.install(file.toUri(), target,
        emSpace + sha256(archive) + emSpace);

    Assertions.assertEquals("verified",
        Files.readString(target.resolve("corpus/data.txt")));
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

  @Test
  void testEscapingTarDirectoryEntryIsRejected(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    TarArchives.entry(tar, "../outside/", new byte[0], TarArchives.TYPE_DIRECTORY);
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("directory-escape.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    assertInstallFails(file, target, ESCAPE_ERROR + "../outside/");
  }

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
  void testEscapingZipDirectoryEntryIsRejected(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("../outside/"));
      zip.closeEntry();
    }
    final Path file = source.resolve("directory-escape.zip");
    Files.write(file, out.toByteArray());

    assertInstallFails(file, target, ESCAPE_ERROR + "../outside/");
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
   * Checks that ZIP validation works when the target uses a non-default file system.
   *
   * @param source The directory containing the source ZIP file.
   * @param scratch The directory containing the target file system.
   * @throws IOException Thrown if the fixture or installation cannot be read or written.
   */
  @Test
  void testZipUnpacksIntoANonDefaultFileSystem(@TempDir Path source,
      @TempDir Path scratch) throws IOException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("payload/data.txt"));
      zip.write("content".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    final Path archive = source.resolve("payload.zip");
    Files.write(archive, bytes.toByteArray());
    final URI targetUri = URI.create("jar:" + scratch.resolve("target.zip").toUri());

    try (FileSystem fileSystem =
             FileSystems.newFileSystem(targetUri, Map.of("create", "true"))) {
      final Path target = fileSystem.getPath("/installed");

      ResourceInstaller.install(archive.toUri(), target);

      Assertions.assertEquals("content",
          Files.readString(target.resolve("payload/data.txt")));
    }
  }

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
  void testPlainGzipWithoutABaseNameUsesResource(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final Path file = source.resolve(".gz");
    Files.write(file, gzip("word\tlemma\n".getBytes(StandardCharsets.UTF_8)));

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals("word\tlemma\n",
        Files.readString(target.resolve("resource")));
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
   * Checks each required installation parameter.
   *
   * @param argument The invalid method parameter.
   * @param target A scratch directory managed by the test framework.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"source", "targetDirectory", "limits"})
  void testInvalidArguments(String argument, @TempDir Path target) {
    final Executable call = switch (argument) {
      case "source" -> () -> ResourceInstaller.install(null, target);
      case "targetDirectory" -> () -> ResourceInstaller.install(target.toUri(), null);
      case "limits" ->
          () -> ResourceInstaller.install(target.toUri(), target, null, null);
      default -> throw new IllegalArgumentException("unknown argument: " + argument);
    };

    assertArgumentError(argument + " must not be null", call);
  }

  /**
   * Supplies checksum arguments that are neither a valid SHA-256 nor a valid SHA-512
   * digest.
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

  @Test
  void testDuplicateTarFileEntryIsRejected(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "corpus/data.txt", "first".getBytes(StandardCharsets.UTF_8));
    tarEntry(tar, "corpus/data.txt", "second".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("duplicate.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    assertInstallFails(file, target, DUPLICATE_ENTRY_ERROR + "corpus/data.txt");
  }

  @Test
  void testEquivalentZipFileEntriesAreRejected(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("corpus/data.txt"));
      zip.write("first".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("corpus/./data.txt"));
      zip.write("second".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    final Path file = source.resolve("duplicate.zip");
    Files.write(file, out.toByteArray());

    assertInstallFails(file, target, DUPLICATE_ENTRY_ERROR + "corpus/./data.txt");
  }

  @Test
  void testPlainFileBeginningWithPkIsStored(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] content = "PK plain dictionary".getBytes(StandardCharsets.UTF_8);
    final Path file = source.resolve("dictionary.dat");
    Files.write(file, content);

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertArrayEquals(content,
        Files.readAllBytes(target.resolve("dictionary.dat")));
  }

  @Test
  void testTruncatedZipHeaderIsRejected(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final Path file = source.resolve("truncated.zip");
    Files.write(file, new byte[] {'P', 'K', 3, 4});

    assertInstallFails(file, target, "malformed zip archive");
  }

  @Test
  void testZipWithoutCentralDirectoryIsRejected(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("corpus/data.txt"));
      zip.write("content".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    final byte[] complete = out.toByteArray();
    int centralDirectory = -1;
    for (int i = 0; i <= complete.length - 4; i++) {
      if (complete[i] == 'P' && complete[i + 1] == 'K'
          && complete[i + 2] == 1 && complete[i + 3] == 2) {
        centralDirectory = i;
        break;
      }
    }
    Assertions.assertTrue(centralDirectory > 0);
    final Path file = source.resolve("missing-central-directory.zip");
    Files.write(file, Arrays.copyOf(complete, centralDirectory));

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals("malformed zip archive", thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testTruncatedEmptyZipHeaderIsRejected(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final Path file = source.resolve("truncated-empty.zip");
    Files.write(file, new byte[] {'P', 'K', 5, 6});

    assertInstallFails(file, target, "malformed zip archive");
  }

  @Test
  void testValidEmptyZipInstallsNothing(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] endHeader = new byte[22];
    endHeader[0] = 'P';
    endHeader[1] = 'K';
    endHeader[2] = 5;
    endHeader[3] = 6;
    final Path file = source.resolve("empty.zip");
    Files.write(file, endHeader);

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testValidEmptyTarGzInstallsNothing(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final Path file = source.resolve("empty.tar.gz");
    Files.write(file, gzip(new byte[TERMINATOR_SIZE]));

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testInvalidZipEntryPathLeavesNoStagingFiles(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("good.txt"));
      zip.write("good".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("bad\0name.txt"));
      zip.write("bad".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    final Path file = source.resolve("invalid-path.zip");
    Files.write(file, out.toByteArray());

    assertInstallFails(file, target,
        "archive entry has an invalid path: bad\0name.txt");
  }

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
   * Supplies invalid {@code Limits} constructions with the argument error each one
   * must raise.
   *
   * @return One case per invalid argument. Never {@code null}.
   */
  static Stream<Arguments> invalidLimits() {
    final Duration valid = Duration.ofSeconds(10);
    return Stream.of(
        Arguments.of("null connectTimeout", (Executable)
            () -> new ResourceInstaller.Limits(null, valid, 5, 1024, 1024, 10, 100),
            "connectTimeout must not be null"),
        Arguments.of("zero connectTimeout", (Executable)
            () -> new ResourceInstaller.Limits(Duration.ZERO, valid, 5, 1024, 1024, 10, 100),
            "connectTimeout must be positive"),
        Arguments.of("null readTimeout", (Executable)
            () -> new ResourceInstaller.Limits(valid, null, 5, 1024, 1024, 10, 100),
            "readTimeout must not be null"),
        Arguments.of("negative readTimeout", (Executable)
            () -> new ResourceInstaller.Limits(valid, Duration.ofSeconds(-1),
                5, 1024, 1024, 10, 100),
            "readTimeout must be positive"),
        Arguments.of("negative maxRedirects", (Executable)
            () -> new ResourceInstaller.Limits(valid, valid, -1, 1024, 1024, 10, 100),
            "maxRedirects must not be negative"),
        Arguments.of("zero maxDownloadBytes", (Executable)
            () -> new ResourceInstaller.Limits(valid, valid, 5, 0, 1024, 10, 100),
            "maxDownloadBytes must be positive"),
        Arguments.of("zero maxExpandedBytes", (Executable)
            () -> new ResourceInstaller.Limits(valid, valid, 5, 1024, 0, 10, 100),
            "maxExpandedBytes must be positive"),
        Arguments.of("zero maxEntries", (Executable)
            () -> new ResourceInstaller.Limits(valid, valid, 5, 1024, 1024, 0, 100),
            "maxEntries must be positive"),
        Arguments.of("zero maxExpansionRatio", (Executable)
            () -> new ResourceInstaller.Limits(valid, valid, 5, 1024, 1024, 10, 0),
            "maxExpansionRatio must be positive"),
        Arguments.of("builder zero connectTimeout", (Executable) () ->
            ResourceInstaller.Limits.builder().connectTimeout(Duration.ZERO).build(),
            "connectTimeout must be positive"),
        Arguments.of("builder null readTimeout", (Executable) () ->
            ResourceInstaller.Limits.builder().readTimeout(null).build(),
            "readTimeout must not be null"),
        Arguments.of("builder negative maxRedirects", (Executable) () ->
            ResourceInstaller.Limits.builder().maxRedirects(-1).build(),
            "maxRedirects must not be negative"),
        Arguments.of("builder zero maxDownloadBytes", (Executable) () ->
            ResourceInstaller.Limits.builder().maxDownloadBytes(0).build(),
            "maxDownloadBytes must be positive"),
        Arguments.of("builder negative maxExpandedBytes", (Executable) () ->
            ResourceInstaller.Limits.builder().maxExpandedBytes(-1).build(),
            "maxExpandedBytes must be positive"),
        Arguments.of("builder zero maxEntries", (Executable) () ->
            ResourceInstaller.Limits.builder().maxEntries(0).build(),
            "maxEntries must be positive"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidLimits")
  void testLimitsRejectInvalidValues(String label, Executable construction,
      String message) {
    final IllegalArgumentException thrown =
        Assertions.assertThrows(IllegalArgumentException.class, construction);
    Assertions.assertEquals(message, thrown.getMessage());
  }

  @Test
  void testDownloadLimitRejectsOversizedSource(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final Path file = source.resolve("large.txt");
    Files.write(file, new byte[8192]);

    assertInstallFails(file, target, limits(KIBIBYTE, MEBIBYTE),
        "download exceeds the limit of " + KIBIBYTE + " bytes");
  }

  @Test
  void testTarExpansionLimitRejectsArchive(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "bomb/zeros.bin", new byte[64 * 1024]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("bomb.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    assertInstallFails(file, target, limits(MEBIBYTE, KIBIBYTE),
        EXPANSION_LIMIT_ERROR);
  }

  @Test
  void testTarExpansionLimitCountsBytesAfterTheTerminator(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "corpus/data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    tar.write(new byte[64 * 1024]);
    final Path file = source.resolve("trailing-data.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    assertInstallFails(file, target, limits(MEBIBYTE, 16 * KIBIBYTE),
        "expanded content exceeds the limit of " + 16 * KIBIBYTE + " bytes");
  }

  @Test
  void testTarGzipTrailerIsVerified(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "corpus/data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    tar.write(new byte[64 * 1024]);
    final byte[] archive = gzip(tar.toByteArray());
    archive[archive.length - 8] ^= 1;
    final Path file = source.resolve("bad-trailer.tar.gz");
    Files.write(file, archive);

    Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testTarMetadataCountsTowardExpansionLimit(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    TarArchives.entry(tar, "pax_global_header",
        TarArchives.paxRecord("comment", "a".repeat(64 * 1024)), 'g');
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("metadata-bomb.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    assertInstallFails(file, target, limits(MEBIBYTE, KIBIBYTE),
        EXPANSION_LIMIT_ERROR);
  }

  @Test
  void testZipExpansionLimitRejectsArchive(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("bomb/zeros.bin"));
      zip.write(new byte[64 * 1024]);
      zip.closeEntry();
    }
    final Path file = source.resolve("bomb.zip");
    Files.write(file, out.toByteArray());

    assertInstallFails(file, target, limits(MEBIBYTE, KIBIBYTE),
        EXPANSION_LIMIT_ERROR);
  }

  @Test
  void testZipDirectoryContentCountsTowardExpansionLimit(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("payload/"));
      zip.write(new byte[64 * 1024]);
      zip.closeEntry();
    }
    final Path file = source.resolve("directory-content.zip");
    Files.write(file, out.toByteArray());

    assertInstallFails(file, target, limits(MEBIBYTE, KIBIBYTE),
        EXPANSION_LIMIT_ERROR);
  }

  @Test
  void testPlainGzipExpansionLimitRejectsFile(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final Path file = source.resolve("zeros.bin.gz");
    Files.write(file, gzip(new byte[64 * 1024]));

    assertInstallFails(file, target, limits(MEBIBYTE, KIBIBYTE),
        EXPANSION_LIMIT_ERROR);
  }

  @Test
  void testInstallWithinCustomLimitsSucceeds(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {{"corpus/data.txt", "small"}});
    final Path file = source.resolve("small.tar.gz");
    Files.write(file, archive);

    ResourceInstaller.install(file.toUri(), target, sha256(archive),
        limits(MEBIBYTE, MEBIBYTE));

    Assertions.assertEquals("small", Files.readString(target.resolve("corpus/data.txt")));
  }

  @Test
  void testDefaultLimitsArePinned() {
    final ResourceInstaller.Limits defaults = ResourceInstaller.Limits.DEFAULT;
    Assertions.assertEquals(Duration.ofSeconds(20), defaults.connectTimeout());
    Assertions.assertEquals(Duration.ofSeconds(60), defaults.readTimeout());
    Assertions.assertEquals(5, defaults.maxRedirects());
    Assertions.assertEquals(1L << 30, defaults.maxDownloadBytes());
    Assertions.assertEquals(4L << 30, defaults.maxExpandedBytes());
    Assertions.assertEquals(100_000L, defaults.maxEntries());
    Assertions.assertEquals(100L, defaults.maxExpansionRatio());
  }

  @Test
  void testLimitPropertyNames() {
    Assertions.assertEquals("opennlp.download.max.bytes",
        ResourceInstaller.Limits.MAX_DOWNLOAD_BYTES_PROPERTY);
    Assertions.assertEquals("opennlp.install.max.total.bytes",
        ResourceInstaller.Limits.MAX_EXPANDED_BYTES_PROPERTY);
    Assertions.assertEquals("opennlp.install.max.entries",
        ResourceInstaller.Limits.MAX_ENTRIES_PROPERTY);
    Assertions.assertEquals("opennlp.install.max.expansion.ratio",
        ResourceInstaller.Limits.MAX_EXPANSION_RATIO_PROPERTY);
  }

  @Test
  void testLimitPropertyOverrideIsRead() {
    System.setProperty(TEST_LIMIT_PROPERTY, " 123 ");
    try {
      Assertions.assertEquals(123L,
          ResourceInstaller.Limits.longProperty(TEST_LIMIT_PROPERTY, 7L));
    } finally {
      System.clearProperty(TEST_LIMIT_PROPERTY);
    }
  }

  /**
   * Supplies property values that must fall back to the built-in default: absent,
   * not a number, zero, negative, and empty.
   *
   * @return One case per unusable value. Never {@code null}.
   */
  static Stream<Arguments> unusableLimitProperties() {
    return Stream.of(
        Arguments.of("absent", null),
        Arguments.of("not a number", "abc"),
        Arguments.of("zero", "0"),
        Arguments.of("negative", "-5"),
        Arguments.of("empty", ""));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unusableLimitProperties")
  void testLimitPropertyFallsBackOnUnusableValues(String label, String value) {
    if (value == null) {
      System.clearProperty(TEST_LIMIT_PROPERTY);
    } else {
      System.setProperty(TEST_LIMIT_PROPERTY, value);
    }
    try {
      Assertions.assertEquals(7L,
          ResourceInstaller.Limits.longProperty(TEST_LIMIT_PROPERTY, 7L));
    } finally {
      System.clearProperty(TEST_LIMIT_PROPERTY);
    }
  }

  /**
   * Builds installation limits with the given entry limit and otherwise default
   * values, so entry-count tests state only the value they exercise.
   *
   * @param maxEntries The entry limit.
   * @return The limits. Never {@code null}.
   */
  private static ResourceInstaller.Limits entryLimit(long maxEntries) {
    return ResourceInstaller.Limits.builder().maxEntries(maxEntries).build();
  }

  @Test
  void testTarEntryCountLimitRejectsArchive(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {
        {"corpus/one.txt", "1"},
        {"corpus/two.txt", "2"},
        {"corpus/three.txt", "3"}});
    final Path file = source.resolve("many.tar.gz");
    Files.write(file, archive);

    assertInstallFails(file, target, entryLimit(2),
        ENTRY_LIMIT_ERROR);
  }

  /**
   * Checks that tar extension headers count toward the archive entry limit.
   *
   * @param source A scratch directory for the source archive.
   * @param target A scratch installation directory.
   * @throws Exception Thrown if the fixture cannot be created or installed.
   */
  @Test
  void testTarEntryCountLimitIncludesMetadata(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    TarArchives.entry(tar, "PaxHeaders/one",
        TarArchives.paxRecord("comment", "first"), 'x');
    TarArchives.entry(tar, "PaxHeaders/b",
        TarArchives.paxRecord("comment", "right"), 'x');
    tarEntry(tar, "payload/data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("metadata-entries.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    assertInstallFails(file, target, entryLimit(2), ENTRY_LIMIT_ERROR);
  }

  @Test
  void testZipEntryCountLimitRejectsArchive(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (int i = 0; i < 3; i++) {
        zip.putNextEntry(new ZipEntry("corpus/entry-" + i + ".txt"));
        zip.write("x".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    final Path file = source.resolve("many.zip");
    Files.write(file, out.toByteArray());

    assertInstallFails(file, target, entryLimit(2),
        ENTRY_LIMIT_ERROR);
  }

  @Test
  void testEntryCountExactlyAtLimitSucceeds(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {
        {"corpus/one.txt", "1"},
        {"corpus/two.txt", "2"}});
    final Path file = source.resolve("exact.tar.gz");
    Files.write(file, archive);

    ResourceInstaller.install(file.toUri(), target, sha256(archive), entryLimit(2));

    Assertions.assertEquals(List.of("corpus/one.txt", "corpus/two.txt"),
        installedFiles(target));
  }

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

  @Test
  void testDownloadExactlyAtLimitSucceeds(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final Path file = source.resolve("exact.dat");
    Files.write(file, new byte[1024]);

    ResourceInstaller.install(file.toUri(), target, null, limits(KIBIBYTE, MEBIBYTE));

    Assertions.assertEquals(List.of("exact.dat"), installedFiles(target));
    Assertions.assertEquals(1024, Files.size(target.resolve("exact.dat")));
  }

  @Test
  void testExpansionExactlyAtLimitSucceeds(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "corpus/exact.bin", new byte[1024]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("exact.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    ResourceInstaller.install(file.toUri(), target, null,
        limits(MEBIBYTE, tar.size()));

    Assertions.assertEquals(List.of("corpus/exact.bin"), installedFiles(target));
    Assertions.assertEquals(1024, Files.size(target.resolve("corpus/exact.bin")));
  }

  @Test
  void testCumulativeExpansionAcrossEntriesHitsLimit(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tarEntry(tar, "corpus/first.bin", new byte[768]);
    tarEntry(tar, "corpus/second.bin", new byte[768]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final Path file = source.resolve("cumulative.tar.gz");
    Files.write(file, gzip(tar.toByteArray()));

    assertInstallFails(file, target, limits(MEBIBYTE, KIBIBYTE),
        EXPANSION_LIMIT_ERROR);
  }

  @Test
  void testReinstallOverAnExistingFileIsRejected(@TempDir Path source, @TempDir Path target)
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
        COLLISION_ERROR + target.resolve("corpus/data.txt"),
        thrown.getMessage());
    Assertions.assertEquals("version one",
        Files.readString(target.resolve("corpus/data.txt")));

    Files.delete(target.resolve("corpus/data.txt"));
    ResourceInstaller.install(secondFile.toUri(), target, sha256(second));
    Assertions.assertEquals("version two",
        Files.readString(target.resolve("corpus/data.txt")));
  }

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
        COLLISION_ERROR + target.resolve("corpus/data.txt"),
        thrown.getMessage());
    Assertions.assertEquals("keep", Files.readString(target.resolve("corpus/data.txt")));
    Assertions.assertTrue(Files.notExists(target.resolve("corpus/fresh.txt")));
    Assertions.assertEquals(List.of("corpus/data.txt"), installedFiles(target));
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void testPromotionRejectsToFollowASymlinkedDirectory(@TempDir Path source,
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
   * Supplies source schemes the installer rejects because they are outside the bounded
   * HTTP and local-file paths.
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

  @Test
  void testUnsupportedSourceSchemeIsRejectedBeforeCreatingTheTarget(@TempDir Path parent) {
    final Path target = parent.resolve("not-created-yet");

    assertArgumentError(
        "source scheme must be http, https, or file, but was: ftp://example.invalid/c.gz",
        () -> ResourceInstaller.install(URI.create("ftp://example.invalid/c.gz"), target));
    Assertions.assertTrue(Files.notExists(target));
  }




  @Test
  void testBuilderWithoutOverridesEqualsTheDefaults() {
    Assertions.assertEquals(ResourceInstaller.Limits.DEFAULT,
        ResourceInstaller.Limits.builder().build());
  }

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
    Assertions.assertEquals(ResourceInstaller.Limits.DEFAULT.maxEntries(),
        limits.maxEntries());
  }

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

  static Stream<Arguments> expansionBombs() throws IOException {
    // Four mebibytes of repeated text compress to a few kibibytes in either format: far
    // beyond the ratio ceiling, far below the absolute expansion limit. Repeated text
    // rather than zeros, because a gzip stream of zeros reads as an empty tar archive.
    final byte[] repetitive =
        "opennlp ".repeat(512 * 1024).getBytes(StandardCharsets.UTF_8);
    return Stream.of(
        Arguments.of("corpus.txt.gz", gzip(repetitive)),
        Arguments.of("corpus.zip", zipOf("corpus.txt", repetitive)));
  }

  @ParameterizedTest
  @MethodSource("expansionBombs")
  void testExpansionRatioIsBounded(String name, byte[] content, @TempDir Path source,
      @TempDir Path target) throws Exception {
    final Path file = source.resolve(name);
    Files.write(file, content);

    assertInstallFails(file, target, RATIO_ERROR);
  }

  @ParameterizedTest
  @MethodSource("expansionBombs")
  void testRaisingTheExpansionRatioAcceptsWhatTheDefaultRejects(String name,
      byte[] content, @TempDir Path source, @TempDir Path target) throws Exception {
    final Path file = source.resolve(name);
    Files.write(file, content);

    // Raising the byte limit alone cannot lift the ratio: the tighter budget wins.
    final ResourceInstaller.Limits raised = ResourceInstaller.Limits.builder()
        .maxExpansionRatio(2000)
        .build();
    ResourceInstaller.install(file.toUri(), target, null, raised);

    Assertions.assertEquals(4 << 20, Files.size(target.resolve("corpus.txt")));
  }

  @Test
  void testMoveIntoPlaceDoesNotReplaceAnExistingFile(@TempDir Path directory)
      throws Exception {
    final Path staged = directory.resolve("staged.txt");
    final Path destination = directory.resolve("installed.txt");
    Files.writeString(staged, "new");
    Files.writeString(destination, "old");

    Assertions.assertThrows(FileAlreadyExistsException.class,
        () -> ResourceInstaller.moveIntoPlace(staged, destination));

    Assertions.assertEquals("old", Files.readString(destination));
    Assertions.assertEquals("new", Files.readString(staged));
  }

  @Test
  void testModelSuffixIsMatchedIgnoringCase(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("manifest.properties"));
      zip.write("OpenNLP-Version: 0.0.0\n".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    final Path file = source.resolve("en-ner-person.BIN");
    Files.write(file, out.toByteArray());

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals(List.of("en-ner-person.BIN"), installedFiles(target));
  }

  @Test
  void testGzipSuffixIsStrippedIgnoringCase(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final Path file = source.resolve("lexicon.tsv.GZ");
    Files.write(file, gzip("word\tlemma\n".getBytes(StandardCharsets.UTF_8)));

    ResourceInstaller.install(file.toUri(), target);

    Assertions.assertEquals(List.of("lexicon.tsv"), installedFiles(target));
  }

  @Test
  void testStaleWorkFilesOfAKilledInstallAreRemoved(@TempDir Path source,
      @TempDir Path target) throws Exception {
    final Path staleStaging = Files.createDirectory(target.resolve(".opennlp-stagingOLD"));
    Files.writeString(staleStaging.resolve("partial.txt"), "half");
    Files.writeString(target.resolve(".opennlp-downloadOLD.part"), "half");
    final Path file = source.resolve("corpus.tar.gz");
    Files.write(file, tarGz(new String[][] {{"corpus/data.txt", "content"}}));

    ResourceInstaller.install(file.toUri(), target);

    try (Stream<Path> entries = Files.list(target)) {
      Assertions.assertEquals(List.of("corpus"),
          entries.map(path -> path.getFileName().toString()).sorted().toList());
    }
  }

  @Test
  void testFailedInstallRemovesTheTargetDirectoryItCreated(@TempDir Path source,
      @TempDir Path parent) throws Exception {
    final byte[] archive = tarGz(new String[][] {{"corpus/data.txt", "content"}});
    final Path file = source.resolve("corpus.tar.gz");
    Files.write(file, archive);
    final Path target = parent.resolve("fresh");

    Assertions.assertThrows(IOException.class, () -> ResourceInstaller.install(
        file.toUri(), target, sha256("other".getBytes(StandardCharsets.UTF_8))));

    Assertions.assertTrue(Files.notExists(target));
  }

  @Test
  void testZipLocalHeadersMustMatchTheCentralDirectory(@TempDir Path source,
      @TempDir Path target) throws Exception {
    // The same content under two names, so the local file sections are the same size and
    // one archive's central directory fits the other's local headers.
    final byte[] first = zipOf("a.txt", "content");
    final byte[] second = zipOf("b.txt", "content");
    final int centralFirst = centralDirectoryStart(first);
    final int centralSecond = centralDirectoryStart(second);
    final byte[] hybrid = new byte[centralFirst + second.length - centralSecond];
    System.arraycopy(first, 0, hybrid, 0, centralFirst);
    System.arraycopy(second, centralSecond, hybrid, centralFirst,
        second.length - centralSecond);
    final Path file = source.resolve("hybrid.zip");
    Files.write(file, hybrid);

    assertInstallFails(file, target, ZIP_MISMATCH_ERROR);
  }

  /** Builds a zip archive holding one text entry. */
  private static byte[] zipOf(String name, String content) throws IOException {
    return zipOf(name, content.getBytes(StandardCharsets.UTF_8));
  }

  /** Builds a zip archive holding one entry with the given bytes. */
  private static byte[] zipOf(String name, byte[] content) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content);
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  /** Finds the first central directory header signature in a zip archive. */
  private static int centralDirectoryStart(byte[] zip) {
    for (int i = 0; i + 3 < zip.length; i++) {
      if (zip[i] == 'P' && zip[i + 1] == 'K' && zip[i + 2] == 1 && zip[i + 3] == 2) {
        return i;
      }
    }
    throw new AssertionError("no central directory");
  }
}
