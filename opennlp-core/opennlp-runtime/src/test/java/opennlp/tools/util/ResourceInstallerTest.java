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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ResourceInstallerTest {

  /** Writes one ustar entry: a 512-byte header block and padded content. */
  private static void tarEntry(ByteArrayOutputStream tar, String name, byte[] content)
      throws IOException {
    final byte[] header = new byte[512];
    final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
    final String size = String.format("%011o", content.length);
    System.arraycopy(size.getBytes(StandardCharsets.US_ASCII), 0, header, 124, 11);
    header[156] = '0';
    tar.write(header);
    tar.write(content);
    final int padding = (512 - content.length % 512) % 512;
    tar.write(new byte[padding]);
  }

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

  private static String sha256(byte[] content) throws Exception {
    final StringBuilder hex = new StringBuilder();
    for (final byte b : MessageDigest.getInstance("SHA-256").digest(content)) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16))
          .append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
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

    Assertions.assertThrows(IOException.class, () -> ResourceInstaller.install(
        file.toUri(), target, "00000000000000000000000000000000"
            + "00000000000000000000000000000000"));
    Assertions.assertEquals(0, Files.list(target).count());
  }

  @Test
  void testEscapingEntriesAreRejected(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {{"../escape.txt", "bad"}});
    final Path file = source.resolve("evil.tar.gz");
    Files.write(file, archive);

    Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(file.toUri(), target));
    Assertions.assertTrue(Files.notExists(target.getParent().resolve("escape.txt")));
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
