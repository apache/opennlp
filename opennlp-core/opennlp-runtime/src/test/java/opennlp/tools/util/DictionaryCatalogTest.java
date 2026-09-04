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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.stemmer.hunspell.HunspellDictionary;

/**
 * Tests the opt-in dictionary catalog against an in-memory properties file and a
 * local file URI so no network access is required.
 */
public class DictionaryCatalogTest {

  private static final String SHA_512 = "0".repeat(128);

  /** Checks the validation promised by the public catalog entry record. */
  @Test
  void testEntryValidatesItsDocumentedFields() {
    final URI absolute = URI.create("https://example.invalid/dictionary.tar.gz");

    Assertions.assertDoesNotThrow(() ->
        new DictionaryCatalog.Entry("demo", absolute, "A".repeat(128), null));
    Assertions.assertAll(
        () -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> new DictionaryCatalog.Entry(null, absolute, SHA_512, null)),
        () -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> new DictionaryCatalog.Entry("demo", null, SHA_512, null)),
        () -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> new DictionaryCatalog.Entry("demo", URI.create("dictionary.tar.gz"),
                SHA_512, null)),
        () -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> new DictionaryCatalog.Entry("demo", absolute, null, null)),
        () -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> new DictionaryCatalog.Entry("demo", absolute, "not-a-digest", null)));
  }

  /**
   * Checks that invalid property values are reported as catalog I/O errors.
   *
   * @throws IOException Thrown if a fixture catalog cannot be loaded.
   */
  @Test
  void testGetRejectsInvalidCatalogValues() throws IOException {
    final DictionaryCatalog relativeUri = DictionaryCatalog.load(new ByteArrayInputStream(
        ("demo.url=dictionary.tar.gz\ndemo.sha512=" + SHA_512 + "\n")
            .getBytes(StandardCharsets.UTF_8)));
    final DictionaryCatalog invalidDigest = DictionaryCatalog.load(new ByteArrayInputStream(
        "demo.url=https://example.invalid/dictionary.tar.gz\ndemo.sha512=invalid\n"
            .getBytes(StandardCharsets.UTF_8)));

    Assertions.assertAll(
        () -> Assertions.assertThrows(IOException.class, () -> relativeUri.get("demo")),
        () -> Assertions.assertThrows(IOException.class, () -> invalidDigest.get("demo")));
  }

  /**
   * Checks argument validation before the opt-in remote setting is read.
   *
   * @param dir A scratch directory managed by the test framework.
   * @throws IOException Thrown if the fixture catalog cannot be loaded.
   */
  @Test
  void testInstallValidatesArgumentsBeforeTheRemoteSetting(@TempDir Path dir)
      throws IOException {
    final DictionaryCatalog catalog = DictionaryCatalog.load(
        new ByteArrayInputStream(new byte[0]));
    final String previous = System.getProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    System.clearProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    try {
      Assertions.assertAll(
          () -> Assertions.assertThrows(IllegalArgumentException.class,
              () -> catalog.install(null, dir)),
          () -> Assertions.assertThrows(IllegalArgumentException.class,
              () -> catalog.install("demo", null)));
    } finally {
      restore(previous);
    }
  }

  /**
   * Verifies that a catalog install without the remote-download property fails with
   * the property name in the message, before anything is fetched or created.
   *
   * @param dir A scratch directory managed by the test framework.
   * @throws Exception Thrown if the fixture catalog cannot be prepared.
   */
  @Test
  void testInstallRequiresRemoteProperty(@TempDir Path dir) throws Exception {
    final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
    final DictionaryCatalog loaded = demoCatalog(dir, payload);
    final Path target = dir.resolve("out");

    final String previous =
        System.getProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    System.clearProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    try {
      final IOException e = Assertions.assertThrows(IOException.class,
          () -> loaded.install("demo", target));
      Assertions.assertTrue(
          e.getMessage().contains(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY));
      Assertions.assertTrue(Files.notExists(target));
    } finally {
      restore(previous);
    }
  }

  /**
   * Verifies that an enabled catalog install fetches the entry and stores its
   * digest-verified bytes under the source name in the target directory.
   *
   * @param dir A scratch directory managed by the test framework.
   * @throws Exception Thrown if the fixture catalog cannot be prepared or fetched.
   */
  @Test
  void testInstallStoresTheEntryUnderItsSourceName(@TempDir Path dir) throws Exception {
    final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
    final DictionaryCatalog loaded = demoCatalog(dir, payload);
    final Path target = dir.resolve("out");

    final String previous =
        System.getProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    System.setProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY, "true");
    try {
      loaded.install("demo", target);
      Assertions.assertArrayEquals(payload,
          Files.readAllBytes(target.resolve("dict.bin")));
    } finally {
      restore(previous);
    }
  }

  /**
   * Verifies that the example catalog holds the MeCab and Hunspell entries, each with
   * a full-length SHA-512 digest. Applications load their own catalog through the same
   * {@link DictionaryCatalog#load(InputStream)} entry point.
   *
   * @throws IOException Thrown if the example catalog fails to load.
   */
  @Test
  void testExampleCatalogContainsMecabAndHunspellEntries() throws IOException {
    final DictionaryCatalog catalog = loadExampleCatalog();
    Assertions.assertTrue(catalog.ids().contains("mecab.ipadic"));
    Assertions.assertTrue(catalog.ids().contains("mecab.ko-dic"));
    Assertions.assertTrue(catalog.ids().contains(
        "hunspell.en_US" + HunspellDictionary.AFFIX_FILE_SUFFIX));
    Assertions.assertEquals(128, catalog.get("mecab.ipadic").sha512().length());
    Assertions.assertEquals(128, catalog.get(
        "hunspell.en_US"
            + HunspellDictionary.DICTIONARY_FILE_SUFFIX)
        .sha512().length());
  }

  /**
   * Loads the example catalog from test resources rather than a production classpath
   * default.
   *
   * @return The example catalog. Never {@code null}.
   * @throws IOException Thrown if the resource is absent or cannot be read.
   */
  private static DictionaryCatalog loadExampleCatalog() throws IOException {
    try (InputStream in = DictionaryCatalogTest.class.getResourceAsStream(
        "/opennlp/tools/util/dictionary-catalog.properties")) {
      if (in == null) {
        throw new IOException("missing example dictionary catalog");
      }
      return DictionaryCatalog.load(in);
    }
  }

  /**
   * Builds a one-entry catalog whose URL is a local file holding {@code payload}, so
   * downloads need no network.
   *
   * @param dir The directory to write the payload file into.
   * @param payload The bytes the catalog entry points at.
   * @return The loaded catalog. Never {@code null}.
   * @throws IOException Thrown if the payload file cannot be written.
   */
  private static DictionaryCatalog demoCatalog(Path dir, byte[] payload)
      throws IOException {
    final Path source = dir.resolve("dict.bin");
    Files.write(source, payload);
    final String catalog = "demo.url=" + source.toUri() + "\n"
        + "demo.sha512=" + DigestTestUtil.sha512(payload) + "\n";
    return DictionaryCatalog.load(
        new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Restores the remote-download property to its value before the test.
   *
   * @param previous The saved value, or {@code null} when the property was unset.
   */
  private static void restore(String previous) {
    if (previous == null) {
      System.clearProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    } else {
      System.setProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY, previous);
    }
  }
}
