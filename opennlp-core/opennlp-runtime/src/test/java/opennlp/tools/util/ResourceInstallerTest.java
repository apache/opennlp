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
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ResourceInstallerTest {

  /**
   * Writes one tar entry into the given buffer: a 512-byte header block carrying the
   * name, the octal content size, and the regular-file type flag, followed by the
   * content padded to the next 512-byte block boundary.
   *
   * @param tar The buffer receiving the entry bytes. Must not be {@code null}.
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param content The entry content. Must not be {@code null}.
   * @throws IOException Thrown if writing to the buffer fails.
   * @throws IllegalArgumentException Thrown if the name exceeds the tar name field.
   */
  private static void tarEntry(ByteArrayOutputStream tar, String name, byte[] content)
      throws IOException {
    final byte[] header = new byte[512];
    final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    if (nameBytes.length > 100) {
      throw new IllegalArgumentException("entry name exceeds 100 bytes: " + name);
    }
    System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
    final String size = String.format("%011o", content.length);
    System.arraycopy(size.getBytes(StandardCharsets.US_ASCII), 0, header, 124, 11);
    header[156] = '0';
    tar.write(header);
    tar.write(content);
    final int padding = (512 - content.length % 512) % 512;
    tar.write(new byte[padding]);
  }

  /**
   * Builds a gzip-compressed tar archive from name and content pairs, terminated by
   * the two all-zero blocks that end a tar archive.
   *
   * @param entries Pairs of entry name and UTF-8 text content. Must not be {@code null}.
   * @return The archive bytes. Never {@code null}.
   * @throws IOException Thrown if assembling the archive fails.
   */
  private static byte[] tarGz(String[][] entries) throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    for (final String[] entry : entries) {
      tarEntry(tar, entry[0], entry[1].getBytes(StandardCharsets.UTF_8));
    }
    tar.write(new byte[1024]);
    final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write(tar.toByteArray());
    }
    return compressed.toByteArray();
  }

  /**
   * Computes the SHA-256 of the given bytes as a lowercase hex string.
   *
   * @param content The bytes to digest. Must not be {@code null}.
   * @return The 64-character lowercase hex digest. Never {@code null}.
   * @throws Exception Thrown if the digest algorithm is unavailable.
   */
  private static String sha256(byte[] content) throws Exception {
    final StringBuilder hex = new StringBuilder();
    for (final byte b : MessageDigest.getInstance("SHA-256").digest(content)) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16))
          .append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }

  /**
   * Computes the SHA-256 of the given bytes as an uppercase hex string, built from an
   * uppercase digit table, so tests can prove that checksum comparison does not depend
   * on the hex letter case.
   *
   * @param content The bytes to digest. Must not be {@code null}.
   * @return The 64-character uppercase hex digest. Never {@code null}.
   * @throws Exception Thrown if the digest algorithm is unavailable.
   */
  private static String sha256UpperCase(byte[] content) throws Exception {
    final String digits = "0123456789ABCDEF";
    final StringBuilder hex = new StringBuilder();
    for (final byte b : MessageDigest.getInstance("SHA-256").digest(content)) {
      hex.append(digits.charAt((b >> 4) & 0xF)).append(digits.charAt(b & 0xF));
    }
    return hex.toString();
  }

  /**
   * Lists every regular file below the given directory as relative paths with forward
   * slashes, sorted lexicographically, so tests can assert the exact installed file
   * set.
   *
   * @param root The directory to walk. Must not be {@code null}.
   * @return The sorted relative paths. Never {@code null}.
   * @throws IOException Thrown if walking the directory fails.
   */
  private static List<String> installedFiles(Path root) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile)
          .map(file -> root.relativize(file).toString().replace('\\', '/'))
          .sorted()
          .toList();
    }
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
    Assertions.assertEquals(0, Files.list(target).count());
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
    Assertions.assertEquals(0, Files.list(target).count());
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
    Assertions.assertEquals(0, Files.list(target).count());
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
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceInstaller.install(null, target));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceInstaller.install(target.toUri(), null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceInstaller.install(target.toUri(), target, " "));
  }
}
