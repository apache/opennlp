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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.DictionaryCatalog;
import opennlp.tools.util.DigestTestUtil;
import opennlp.tools.util.archive.TarArchives;

/**
 * Tests the installer against project-authored, in-memory archives; no external
 * dictionary data and no network access are involved. Fetch, verification, and
 * unpacking limits are exercised in {@code opennlp.tools.util.ResourceInstallerTest},
 * which tests the shared installation path this installer delegates to.
 */
public class MecabDictionaryInstallerTest {

  @Test
  void testInstallsDictionaryFilesAndFlattensPaths(@TempDir Path source,
      @TempDir Path target) throws IOException {
    final Path archiveFile = archive(source, new String[][] {
        {"dict-1.0/lexicon.csv", "cat,0,0,100,noun\n"},
        {"dict-1.0/matrix.def", "1 1\n0 0 0\n"},
        {"dict-1.0/char.def", "DEFAULT 0 1 0\n"},
        {"dict-1.0/unk.def", "DEFAULT,0,0,10000,unknown\n"},
        {"dict-1.0/README", "not a dictionary file"},
        {"dict-1.0/dicrc", "config"}});

    final int installed =
        MecabDictionaryInstaller.install(archiveFile.toUri(), target);

    Assertions.assertEquals(5, installed);
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
   * numeric fields are empty. They are input for {@code mecab-dict-index}, not loadable
   * lexicon data. Flattening them next to the real lexicon fails the subsequent load,
   * and on a case-insensitive file system a template can silently overwrite a real
   * lexicon file of the same base name.
   */
  @Test
  void testNestedTemplateFilesAreNotInstalled(@TempDir Path source,
      @TempDir Path target) throws IOException {
    final Path archiveFile = archive(source, new String[][] {
        {"dict-1.0/NNP.csv", "cat,1786,3546,2953,noun\n"},
        {"dict-1.0/matrix.def", "1 1\n0 0 0\n"},
        {"dict-1.0/user-dic/person.csv", "template,,,,noun\n"}});

    final int installed =
        MecabDictionaryInstaller.install(archiveFile.toUri(), target);

    Assertions.assertEquals(2, installed);
    Assertions.assertTrue(Files.exists(target.resolve("NNP.csv")));
    Assertions.assertTrue(Files.notExists(target.resolve("person.csv")));
  }

  /**
   * Verifies that two payload entries that flatten to the same base name are rejected,
   * because keeping either one silently would install an ambiguous dictionary.
   */
  @Test
  void testEntriesFlatteningToTheSameNameAreRejected(@TempDir Path source,
      @TempDir Path target) throws IOException {
    final Path archiveFile = archive(source, new String[][] {
        {"words.csv", "cat,0,0,100,noun\n"},
        {"d/words.csv", "dog,0,0,100,noun\n"}});

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionaryInstaller.install(archiveFile.toUri(), target));
    Assertions.assertEquals(
        "the archive flattens two entries to the same name: words.csv",
        e.getMessage());
    Assertions.assertTrue(Files.notExists(target.resolve("words.csv")));
  }

  @Test
  void testInstallReadsAFileUri(@TempDir Path source, @TempDir Path target)
      throws IOException {
    final Path archiveFile = archive(source, new String[][] {
        {"d/words.csv", "cat,0,0,100,noun\n"},
        {"d/matrix.def", "1 1\n0 0 0\n"}});

    final int installed =
        MecabDictionaryInstaller.install(archiveFile.toUri(), target);

    Assertions.assertEquals(2, installed);
    Assertions.assertTrue(Files.exists(target.resolve("words.csv")));
  }

  /**
   * Checks installation when the target uses a different filesystem provider.
   *
   * @param source The directory containing the fixture archive.
   * @param scratch The directory containing the ZIP filesystem.
   * @throws IOException Thrown if creating or installing the fixture fails.
   */
  @Test
  void testInstallIntoANonDefaultFileSystem(@TempDir Path source, @TempDir Path scratch)
      throws IOException {
    final Path archiveFile = archive(source, new String[][] {
        {"d/words.csv", "cat,0,0,100,noun\n"},
        {"d/matrix.def", "1 1\n0 0 0\n"}});
    final URI zip = URI.create("jar:" + scratch.resolve("target.zip").toUri());

    try (FileSystem targetFileSystem =
             FileSystems.newFileSystem(zip, Map.of("create", "true"))) {
      final Path target = targetFileSystem.getPath("/dictionary");

      Assertions.assertEquals(2,
          MecabDictionaryInstaller.install(archiveFile.toUri(), target));
      Assertions.assertEquals("cat,0,0,100,noun\n",
          Files.readString(target.resolve("words.csv")));
    }
  }

  /**
   * Verifies that a pax long-name entry installs under its real name. The common
   * distributions ship plain ustar today, but {@code bsdtar} and
   * {@code tar --format=posix} write pax archives, whose over-100-byte names live in an
   * extension header instead of the header name field.
   */
  @Test
  void testPaxLongNamedEntryInstallsUnderItsRealName(@TempDir Path source,
      @TempDir Path target) throws IOException {
    final String longBaseName = "a".repeat(110) + ".csv";
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    TarArchives.entry(tar, "PaxHeaders.0/lexicon",
        TarArchives.paxRecord("path", "dict-1.0/" + longBaseName), 'x');
    TarArchives.entry(tar, "dict-1.0/" + "a".repeat(88),
        "cat,0,0,100,noun\n".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TarArchives.TERMINATOR_SIZE]);
    final Path archiveFile = source.resolve("dict.tar.gz");
    Files.write(archiveFile, TarArchives.gzip(tar.toByteArray()));

    final int installed =
        MecabDictionaryInstaller.install(archiveFile.toUri(), target);

    Assertions.assertEquals(1, installed);
    Assertions.assertEquals("cat,0,0,100,noun\n",
        Files.readString(target.resolve(longBaseName)));
  }

  /**
   * Verifies that installing into a target that already holds a dictionary file of the
   * same name is rejected, leaving the first installation in place. Refreshing a
   * dictionary means removing its old files first.
   */
  @Test
  void testReinstallOverAnExistingDictionaryIsRejected(@TempDir Path source,
      @TempDir Path target) throws IOException {
    final Path archiveFile = archive(source, new String[][] {
        {"d/words.csv", "cat,0,0,100,noun\n"}});
    Assertions.assertEquals(1,
        MecabDictionaryInstaller.install(archiveFile.toUri(), target));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionaryInstaller.install(archiveFile.toUri(), target));
    Assertions.assertTrue(e.getMessage().contains("target already contains: "));
    Assertions.assertEquals("cat,0,0,100,noun\n",
        Files.readString(target.resolve("words.csv")));
  }

  @Test
  void testRemoteInstallWithoutDigestIsRejected(@TempDir Path target) {
    final IllegalArgumentException e =
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> MecabDictionaryInstaller.install(
                URI.create("https://example.invalid/dict.tar.gz"), target));
    Assertions.assertTrue(
        e.getMessage().contains("checksum must be given for an http or https source"));
  }

  @Test
  void testInstallVerifiesDigest(@TempDir Path source, @TempDir Path target)
      throws Exception {
    final Path archiveFile = archive(source, new String[][] {
        {"d/words.csv", "cat,0,0,100,noun\n"},
        {"d/matrix.def", "1 1\n0 0 0\n"}});
    final byte[] archive = Files.readAllBytes(archiveFile);

    final int installed = MecabDictionaryInstaller.install(
        archiveFile.toUri(), target, DigestTestUtil.sha512(archive));
    Assertions.assertEquals(2, installed);

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionaryInstaller.install(archiveFile.toUri(),
            target.resolve("other"), DigestTestUtil.sha512(new byte[] {1})));
    Assertions.assertTrue(e.getMessage().contains("checksum mismatch: expected"));
  }

  @Test
  void testInstallFromCatalogRequiresRemoteProperty(@TempDir Path target)
      throws IOException {
    final DictionaryCatalog catalog = emptyCatalog();
    final String previous =
        System.getProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    System.clearProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    try {
      final IOException e = Assertions.assertThrows(IOException.class,
          () -> MecabDictionaryInstaller.installFromCatalog(
              catalog, "mecab.ipadic", target));
      Assertions.assertTrue(
          e.getMessage().contains(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY));
    } finally {
      if (previous == null) {
        System.clearProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
      } else {
        System.setProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY, previous);
      }
    }
  }

  @Test
  void testArchivesWithoutDictionaryFilesAreRejected(@TempDir Path source,
      @TempDir Path target) throws IOException {
    final Path archiveFile =
        archive(source, new String[][] {{"readme.txt", "nothing here"}});
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> MecabDictionaryInstaller.install(archiveFile.toUri(), target));
    Assertions.assertEquals("the archive contains no dictionary file", e.getMessage());
  }

  /**
   * Checks each public installer parameter independently.
   *
   * @param argument The invalid parameter.
   * @param target A scratch directory managed by the test framework.
   * @throws IOException Thrown if the empty catalog cannot be loaded.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"archive", "targetDirectory", "catalog", "dictionaryId",
      "catalog targetDirectory"})
  void testInvalidArguments(String argument, @TempDir Path target) throws IOException {
    final DictionaryCatalog catalog = emptyCatalog();
    final Executable install = switch (argument) {
      case "archive" -> () -> MecabDictionaryInstaller.install(null, target);
      case "targetDirectory" -> () ->
          MecabDictionaryInstaller.install(target.toUri(), null);
      case "catalog" -> () ->
          MecabDictionaryInstaller.installFromCatalog(null, "mecab.ipadic", target);
      case "dictionaryId" -> () ->
          MecabDictionaryInstaller.installFromCatalog(catalog, null, target);
      case "catalog targetDirectory" -> () ->
          MecabDictionaryInstaller.installFromCatalog(catalog, "mecab.ipadic", null);
      default -> throw new IllegalArgumentException("unknown argument: " + argument);
    };

    final IllegalArgumentException thrown =
        Assertions.assertThrows(IllegalArgumentException.class, install);
    final String parameter = argument.startsWith("catalog ")
        ? argument.substring("catalog ".length()) : argument;
    Assertions.assertEquals(parameter + " must not be null", thrown.getMessage());
  }

  private static DictionaryCatalog emptyCatalog() throws IOException {
    return DictionaryCatalog.load(new ByteArrayInputStream(new byte[0]));
  }

  /**
   * Writes a gzip-compressed tar archive of the given entries to a file.
   *
   * @param directory The directory to write the archive into.
   * @param entries The entries as {@code {name, content}} pairs.
   * @return The archive file. Never {@code null}.
   * @throws IOException Thrown if writing fails.
   */
  private static Path archive(Path directory, String[][] entries) throws IOException {
    final Path archiveFile = directory.resolve("dict.tar.gz");
    Files.write(archiveFile, TarArchives.gzippedTar(entries));
    return archiveFile;
  }

  @Test
  void testStaleScratchOfAKilledInstallIsRemoved(@TempDir Path source,
      @TempDir Path target) throws IOException {
    final Path stale = Files.createDirectories(target.resolve(".mecab-dict-OLD"));
    Files.writeString(stale.resolve("words.csv"), "half");
    final Path archiveFile = archive(source, new String[][] {
        {"d/words.csv", "cat,0,0,100,noun\n"}});

    Assertions.assertEquals(1,
        MecabDictionaryInstaller.install(archiveFile.toUri(), target));

    try (Stream<Path> entries = Files.list(target)) {
      Assertions.assertEquals(List.of("words.csv"),
          entries.map(path -> path.getFileName().toString()).sorted().toList());
    }
  }
}
