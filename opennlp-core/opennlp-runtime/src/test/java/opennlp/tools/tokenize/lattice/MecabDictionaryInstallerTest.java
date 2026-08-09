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
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.DigestTestUtil;
import opennlp.tools.util.DownloadUtil;

/**
 * Tests the installer against project-authored, in-memory archives; no external
 * dictionary data and no network access are involved.
 */
public class MecabDictionaryInstallerTest {

  @Test
  void testExtractsDictionaryFilesAndFlattensPaths(@TempDir Path target)
      throws IOException {
    final byte[] archive = TarGzArchives.gzippedTar(new String[][] {
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

  /**
   * Verifies that only files at the archive root count as dictionary payload.
   * mecab-ko-dic ships template user dictionaries under {@code user-dic/} whose
   * numeric fields are empty, input for {@code mecab-dict-index} rather than loadable
   * lexicon data. Flattening them next to the real lexicon fails the subsequent load,
   * and on a case-insensitive file system a template can silently overwrite a real
   * lexicon file of the same base name.
   */
  @Test
  void testNestedTemplateFilesAreNotExtracted(@TempDir Path target) throws IOException {
    final byte[] archive = TarGzArchives.gzippedTar(new String[][] {
        {"dict-1.0/NNP.csv", "cat,1786,3546,2953,noun\n"},
        {"dict-1.0/matrix.def", "1 1\n0 0 0\n"},
        {"dict-1.0/user-dic/person.csv", "template,,,,noun\n"}});

    final int extracted = MecabDictionaryInstaller.extract(
        new ByteArrayInputStream(archive), target);

    Assertions.assertEquals(2, extracted);
    Assertions.assertTrue(Files.exists(target.resolve("NNP.csv")));
    Assertions.assertTrue(Files.notExists(target.resolve("person.csv")));
  }

  @Test
  void testInstallReadsAFileUri(@TempDir Path source, @TempDir Path target)
      throws IOException {
    final Path archiveFile = source.resolve("dict.tar.gz");
    Files.write(archiveFile, TarGzArchives.gzippedTar(new String[][] {
        {"d/words.csv", "cat,0,0,100,noun\n"},
        {"d/matrix.def", "1 1\n0 0 0\n"}}));

    final int extracted =
        MecabDictionaryInstaller.install(archiveFile.toUri(), target);

    Assertions.assertEquals(2, extracted);
    Assertions.assertTrue(Files.exists(target.resolve("words.csv")));
  }

  @Test
  void testRemoteInstallWithoutDigestFailsLoud(@TempDir Path target) {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionaryInstaller.install(
            URI.create("https://example.invalid/dict.tar.gz"), target));
  }

  @Test
  void testInstallVerifiesDigest(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final byte[] archive = TarGzArchives.gzippedTar(new String[][] {
        {"d/words.csv", "cat,0,0,100,noun\n"},
        {"d/matrix.def", "1 1\n0 0 0\n"}});
    final Path archiveFile = source.resolve("dict.tar.gz");
    Files.write(archiveFile, archive);

    final int extracted = MecabDictionaryInstaller.install(
        archiveFile.toUri(), target, DigestTestUtil.sha512(archive));
    Assertions.assertEquals(2, extracted);

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionaryInstaller.install(archiveFile.toUri(),
            target.resolve("other"), DigestTestUtil.sha512(new byte[] {1})));
    Assertions.assertTrue(e.getMessage().contains("SHA512 checksum validation failed"));
  }

  @Test
  void testInstallFromCatalogRequiresRemoteProperty(@TempDir Path target) {
    final String previous = System.getProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
    System.clearProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
    try {
      final IOException e = Assertions.assertThrows(IOException.class,
          () -> MecabDictionaryInstaller.installFromCatalog("mecab.ipadic", target));
      Assertions.assertTrue(e.getMessage().contains(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY));
    } finally {
      if (previous == null) {
        System.clearProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
      } else {
        System.setProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY, previous);
      }
    }
  }

  @Test
  void testArchivesWithoutDictionaryFilesFailLoud(@TempDir Path target)
      throws IOException {
    final byte[] archive =
        TarGzArchives.gzippedTar(new String[][] {{"readme.txt", "nothing here"}});
    Assertions.assertThrows(IOException.class, () -> MecabDictionaryInstaller.extract(
        new ByteArrayInputStream(archive), target));
  }

  /**
   * Verifies that a tar entry whose declared size is above the per-entry ceiling is
   * rejected before any payload is written. The fixture stores only the oversized
   * header so the test does not allocate the declared size.
   */
  @Test
  void testOversizedEntryFailsLoud(@TempDir Path target) throws IOException {
    final long limit = 64;
    final byte[] archive = TarGzArchives.gzippedTar(
        TarGzArchives.Entry.withDeclaredSize("huge.csv", new byte[0], limit + 1));
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionaryInstaller.extract(new ByteArrayInputStream(archive),
            target, limit, 1024, 16, 100));
    Assertions.assertEquals("tar entry size exceeds safe limit of " + limit,
        e.getMessage());
  }

  /**
   * Verifies that extracting dictionary files whose sizes sum above the total-bytes
   * ceiling fails with {@link IOException}.
   */
  @Test
  void testTotalExtractedBytesBudgetFailsLoud(@TempDir Path target) throws IOException {
    final long limit = 30;
    final byte[] archive = TarGzArchives.gzippedTar(new String[][] {
        {"a.csv", "01234567890123456789"},
        {"b.csv", "01234567890123456789"}});
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionaryInstaller.extract(new ByteArrayInputStream(archive),
            target, 1024, limit, 16, 100));
    Assertions.assertEquals("extracted archive size exceeds safe limit of " + limit,
        e.getMessage());
  }

  /**
   * Verifies that an archive with more dictionary files than the entry-count ceiling
   * fails on the entry that would exceed it.
   */
  @Test
  void testExtractedEntryCountBudgetFailsLoud(@TempDir Path target) throws IOException {
    final int limit = 2;
    final byte[] archive = TarGzArchives.gzippedTar(new String[][] {
        {"a.csv", "a\n"},
        {"b.def", "b\n"},
        {"c.csv", "c\n"}});
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionaryInstaller.extract(new ByteArrayInputStream(archive),
            target, 1024, 1024, limit, 100));
    Assertions.assertEquals("extracted entry count exceeds safe limit of " + limit,
        e.getMessage());
  }

  /**
   * Verifies that a highly compressible payload whose expansion exceeds the gzip
   * ratio ceiling fails loud before the inflated content is kept.
   */
  @Test
  void testGzipExpansionRatioBudgetFailsLoud(@TempDir Path target) throws IOException {
    final int ratio = 2;
    final byte[] zeros = new byte[64 * 1024];
    Arrays.fill(zeros, (byte) 0);
    final byte[] archive = TarGzArchives.gzippedTar(
        TarGzArchives.Entry.of("zeros.csv", zeros));
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionaryInstaller.extract(new ByteArrayInputStream(archive),
            target, zeros.length, zeros.length, 16, ratio));
    Assertions.assertEquals("gzip expansion ratio exceeds safe limit of " + ratio,
        e.getMessage());
  }

  @Test
  void testInvalidArguments(@TempDir Path target) {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionaryInstaller.install(null, target));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionaryInstaller.install(target.toUri(), null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionaryInstaller.extract(null, target));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MecabDictionaryInstaller.extract(
            new ByteArrayInputStream(new byte[0]), null));
  }

  @Test
  void testDefaultBudgetsWithoutOverrides() {
    Assertions.assertEquals(512L * 1024 * 1024, MecabDictionaryInstaller.MAX_ENTRY_BYTES);
    Assertions.assertEquals(2L * 1024 * 1024 * 1024,
        MecabDictionaryInstaller.MAX_TOTAL_EXTRACTED_BYTES);
  }
}
