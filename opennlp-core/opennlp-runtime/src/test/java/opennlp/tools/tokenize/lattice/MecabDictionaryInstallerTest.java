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
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
  void testArchivesWithoutDictionaryFilesFailLoud(@TempDir Path target)
      throws IOException {
    final byte[] archive =
        TarGzArchives.gzippedTar(new String[][] {{"readme.txt", "nothing here"}});
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
