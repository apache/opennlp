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

package opennlp.tools.tokenize.lattice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MecabDictionaryInstallerTest {

  /** Writes one ustar entry: a 512-byte header block and padded content. */
  private static void tarEntry(ByteArrayOutputStream tar, String name, byte[] content)
      throws IOException {
    final byte[] header = new byte[512];
    final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
    final byte[] mode = "0000644".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(mode, 0, header, 100, mode.length);
    final String size = String.format("%011o", content.length);
    System.arraycopy(size.getBytes(StandardCharsets.US_ASCII), 0, header, 124, 11);
    header[156] = '0';
    for (int i = 148; i < 156; i++) {
      header[i] = ' ';
    }
    int checksum = 0;
    for (final byte b : header) {
      checksum += b & 0xFF;
    }
    final String checksumText = String.format("%06o", checksum);
    System.arraycopy(checksumText.getBytes(StandardCharsets.US_ASCII), 0, header, 148, 6);
    header[154] = 0;
    header[155] = ' ';
    tar.write(header);
    tar.write(content);
    final int padding = (512 - content.length % 512) % 512;
    tar.write(new byte[padding]);
  }

  private static byte[] archive(String[][] entries) throws IOException {
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

  @Test
  void testExtractsDictionaryFilesAndFlattensPaths(@TempDir Path target)
      throws IOException {
    final byte[] archive = archive(new String[][] {
        {"dict-1.0/lexicon.csv", "cat,0,0,100,noun\n"},
        {"dict-1.0/matrix.def", "1 1\n0 0 0\n"},
        {"dict-1.0/char.def", "DEFAULT 0 1 0\n"},
        {"dict-1.0/unk.def", "DEFAULT,0,0,10000,unknown\n"},
        {"dict-1.0/README", "not a dictionary file"},
        {"dict-1.0/dicrc", "config"}});

    final int extracted = MecabDictionaryInstaller.extract(
        new ByteArrayInputStream(archive), target);

    Assertions.assertEquals(5, extracted);
    Assertions.assertTrue(Files.exists(target.resolve("lexicon.csv")));
    Assertions.assertTrue(Files.exists(target.resolve("matrix.def")));
    Assertions.assertTrue(Files.exists(target.resolve("char.def")));
    Assertions.assertTrue(Files.exists(target.resolve("unk.def")));
    Assertions.assertTrue(Files.exists(target.resolve("dicrc")));
    Assertions.assertTrue(Files.notExists(target.resolve("README")));
    Assertions.assertEquals("cat,0,0,100,noun\n",
        Files.readString(target.resolve("lexicon.csv")));
  }

  @Test
  void testInstallReadsAFileUri(@TempDir Path source, @TempDir Path target)
      throws IOException {
    final Path archiveFile = source.resolve("dict.tar.gz");
    Files.write(archiveFile, archive(new String[][] {
        {"d/words.csv", "cat,0,0,100,noun\n"},
        {"d/matrix.def", "1 1\n0 0 0\n"}}));

    final int extracted =
        MecabDictionaryInstaller.install(archiveFile.toUri(), target);

    Assertions.assertEquals(2, extracted);
    Assertions.assertTrue(Files.exists(target.resolve("words.csv")));
  }

  @Test
  void testArchivesWithoutDictionaryFilesFailLoud(@TempDir Path target)
      throws IOException {
    final byte[] archive = archive(new String[][] {{"readme.txt", "nothing here"}});
    Assertions.assertThrows(IOException.class, () -> MecabDictionaryInstaller.extract(
        new ByteArrayInputStream(archive), target));
  }

  @Test
  void testInvalidArguments(@TempDir Path target) {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionaryInstaller.install(null, target));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionaryInstaller.extract(null, target));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionaryInstaller.extract(
            new ByteArrayInputStream(new byte[0]), null));
  }
}
