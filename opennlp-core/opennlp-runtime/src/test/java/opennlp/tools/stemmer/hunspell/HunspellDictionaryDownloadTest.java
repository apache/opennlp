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

package opennlp.tools.stemmer.hunspell;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.DictionaryCatalog;
import opennlp.tools.util.DigestTestUtil;

/**
 * Pins the Hunspell catalog download gate; network fetches are not exercised here.
 */
public class HunspellDictionaryDownloadTest {

  /**
   * Verifies that a catalog download without the remote-download property fails with
   * the property name in the message, leaving the previous property value restored.
   *
   * @param target A scratch directory managed by the test framework.
   * @throws IOException Thrown if the local catalog cannot be prepared.
  */
  @Test
  void testDownloadRequiresRemoteProperty(@TempDir Path target) throws IOException {
    final DictionaryCatalog catalog = localCatalog(target);
    final String previous =
        System.getProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    System.clearProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    try {
      final IOException e = Assertions.assertThrows(IOException.class,
          () -> HunspellDictionaryDownload.downloadFromCatalog(catalog, "demo", target));
      Assertions.assertTrue(
          e.getMessage().contains(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY));
    } finally {
      restore(previous);
    }
  }

  /**
   * Verifies that an application-supplied catalog downloads a Hunspell pair and its
   * license readme under the names declared by that catalog.
   *
   * @param target A scratch directory managed by the test framework.
   * @throws IOException Thrown if the local catalog cannot be prepared or downloaded.
   */
  @Test
  void testDownloadsFromApplicationCatalog(@TempDir Path target) throws IOException {
    final DictionaryCatalog catalog = localCatalog(target);
    final Path output = target.resolve("output");
    final String previous =
        System.getProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    System.setProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY, "true");
    try {
      HunspellDictionaryDownload.downloadFromCatalog(catalog, "demo", output);
      Assertions.assertEquals("SET UTF-8\n",
          Files.readString(output.resolve("demo" + HunspellDictionary.AFFIX_FILE_SUFFIX)));
      Assertions.assertEquals("1\nword\n",
          Files.readString(output.resolve("demo" + HunspellDictionary.DICTIONARY_FILE_SUFFIX)));
      Assertions.assertEquals("license\n", Files.readString(output.resolve("README.txt")));
    } finally {
      restore(previous);
    }
  }

  /**
   * Verifies that each required parameter is checked before a download begins.
   *
   * @param argument The invalid method parameter.
   * @param target A scratch directory managed by the test framework.
   * @throws IOException Thrown if the local catalog cannot be prepared.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"catalog", "dictionaryId", "targetDirectory"})
  void testRejectsNullParameters(String argument, @TempDir Path target)
      throws IOException {
    final DictionaryCatalog catalog = localCatalog(target);
    final Executable download = switch (argument) {
      case "catalog" -> () ->
          HunspellDictionaryDownload.downloadFromCatalog(null, "demo", target);
      case "dictionaryId" -> () ->
          HunspellDictionaryDownload.downloadFromCatalog(catalog, null, target);
      case "targetDirectory" -> () ->
          HunspellDictionaryDownload.downloadFromCatalog(catalog, "demo", null);
      default -> throw new IllegalArgumentException("unknown argument: " + argument);
    };

    final IllegalArgumentException thrown =
        Assertions.assertThrows(IllegalArgumentException.class, download);
    Assertions.assertEquals(argument + " must not be null", thrown.getMessage());
  }

  /**
   * Creates an application-supplied catalog backed by local files.
   *
   * @param directory The directory to hold the source files.
   * @return The loaded catalog. Never {@code null}.
   * @throws IOException Thrown if the source files cannot be written.
   */
  private static DictionaryCatalog localCatalog(Path directory) throws IOException {
    final byte[] affix = "SET UTF-8\n".getBytes(StandardCharsets.UTF_8);
    final byte[] dictionary = "1\nword\n".getBytes(StandardCharsets.UTF_8);
    final byte[] readme = "license\n".getBytes(StandardCharsets.UTF_8);
    final Path affixSource = directory.resolve(
        "source" + HunspellDictionary.AFFIX_FILE_SUFFIX);
    final Path dictionarySource = directory.resolve(
        "source" + HunspellDictionary.DICTIONARY_FILE_SUFFIX);
    final Path readmeSource = directory.resolve("source-readme.txt");
    Files.write(affixSource, affix);
    Files.write(dictionarySource, dictionary);
    Files.write(readmeSource, readme);

    final String prefix = "hunspell.demo";
    final String catalog = entry(prefix + HunspellDictionary.AFFIX_FILE_SUFFIX,
        affixSource, affix, "demo" + HunspellDictionary.AFFIX_FILE_SUFFIX)
        + entry(prefix + HunspellDictionary.DICTIONARY_FILE_SUFFIX,
            dictionarySource, dictionary,
            "demo" + HunspellDictionary.DICTIONARY_FILE_SUFFIX)
        + entry(prefix + ".readme", readmeSource, readme, "README.txt");
    return DictionaryCatalog.load(
        new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8)));
  }

  /** Builds one properties entry for a local source file. */
  private static String entry(String id, Path source, byte[] content, String filename) {
    return id + ".url=" + source.toUri() + "\n"
        + id + ".sha512=" + DigestTestUtil.sha512(content) + "\n"
        + id + ".filename=" + filename + "\n";
  }

  /** Restores the remote-download property to its previous value. */
  private static void restore(String previous) {
    if (previous == null) {
      System.clearProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY);
    } else {
      System.setProperty(DictionaryCatalog.REMOTE_DOWNLOAD_PROPERTY, previous);
    }
  }
}
