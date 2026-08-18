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

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import opennlp.tools.util.DictionaryCatalog;
import opennlp.tools.util.ResourceInstaller;

/**
 * Fetches and unpacks a MeCab-format dictionary archive into a local directory, so the
 * dictionary is acquired by the user at install time and never ships with this library.
 * No dictionary data is bundled. Fetching, verification, and unpacking are done by
 * {@link ResourceInstaller} under {@link ResourceInstaller.Limits#DEFAULT} and its
 * startup property overrides: an {@code http} or {@code https} archive requires an
 * expected checksum, and the ustar, pax, and GNU tar formats are all read. Built-in
 * catalog URLs are opt-in via {@link #installFromCatalog(String, Path)}.
 *
 * <p>Only the dictionary payload is installed: the {@code *.csv} lexicon files and
 * {@code *.def} definition files that a {@link MecabDictionary} reads, plus the
 * {@code dicrc} configuration file distributions ship alongside them, taken from the
 * archive root only (at most one leading directory deep). Deeper entries are skipped:
 * mecab-ko-dic, for example, nests {@code user-dic} templates whose numeric fields are
 * empty because they are input for {@code mecab-dict-index}, not loadable lexicon
 * data. Installed files are flattened to their base names. A file whose base name
 * already exists in the target is refused, so refreshing a dictionary means removing
 * its old files first.</p>
 *
 * @since 3.0.0
 */
public final class MecabDictionaryInstaller {

  /** The deepest entry path, relative to the archive root, that holds payload. */
  private static final int MAX_PAYLOAD_DEPTH = 2;

  private MecabDictionaryInstaller() {
    // This class exposes only static methods and is never instantiated.
  }

  /**
   * Unpacks a local {@code file:} archive URI. Any other scheme requires
   * {@link #install(URI, Path, String)} with an expected checksum.
   *
   * @param archive The archive location, a gzip-compressed tar. Must not be
   *                {@code null}.
   * @param targetDirectory The directory to unpack into; created when absent. Must not
   *                        be {@code null}.
   * @return The number of dictionary files installed.
   * @throws IOException Thrown if reading or writing fails, the archive contains no
   *         dictionary file, an installation limit is exceeded, or the target already
   *         contains one of the files.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null} or
   *         {@code archive} is not a readable {@code file:} URI.
   */
  public static int install(URI archive, Path targetDirectory) throws IOException {
    return install(archive, targetDirectory, null);
  }

  /**
   * Downloads a dictionary archive when needed, verifies its checksum, and unpacks it
   * through {@link ResourceInstaller#install(URI, Path, String)}. A {@code file:} URI
   * may omit the checksum and is then opened as trusted caller input.
   *
   * @param archive The archive location, a gzip-compressed tar. Must not be
   *                {@code null}.
   * @param targetDirectory The directory to unpack into; created when absent. Must not
   *                        be {@code null}.
   * @param expectedChecksum The expected digest of the archive bytes as a hex string,
   *                         64 characters for SHA-256 or 128 for SHA-512. Required for
   *                         an http or https source; pass {@code null} to skip
   *                         verification for a file source.
   * @return The number of dictionary files installed.
   * @throws IOException Thrown if fetching, verification, reading, or writing fails,
   *         the archive contains no dictionary file, an installation limit is
   *         exceeded, or the target already contains one of the files.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}, the URI is
   *         not supported by {@link ResourceInstaller}, or an http or https source
   *         carries no checksum.
   */
  public static int install(URI archive, Path targetDirectory, String expectedChecksum)
      throws IOException {
    if (archive == null) {
      throw new IllegalArgumentException("archive must not be null");
    }
    if (targetDirectory == null) {
      throw new IllegalArgumentException("targetDirectory must not be null");
    }
    final Path unpacked = Files.createTempDirectory("mecab-dict-");
    try {
      ResourceInstaller.install(archive, unpacked, expectedChecksum);
      return promoteDictionaryFiles(unpacked, targetDirectory);
    } finally {
      deleteRecursively(unpacked);
    }
  }

  /**
   * Downloads a dictionary named in {@link DictionaryCatalog} and unpacks it. Requires
   * {@code -Dopennlp.download.remote=true}.
   *
   * @param dictionaryId The catalog id, for example {@code mecab.ipadic} or
   *                     {@code mecab.ko-dic}. Must not be {@code null}.
   * @param targetDirectory The directory to unpack into; created when absent. Must not
   *                        be {@code null}.
   * @return The number of dictionary files installed.
   * @throws IOException Thrown if the catalog entry is missing, remote downloads are
   *         disabled, or install fails.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static int installFromCatalog(String dictionaryId, Path targetDirectory)
      throws IOException {
    if (dictionaryId == null) {
      throw new IllegalArgumentException("dictionaryId must not be null");
    }
    if (targetDirectory == null) {
      throw new IllegalArgumentException("targetDirectory must not be null");
    }
    final Path unpacked = Files.createTempDirectory("mecab-dict-");
    try {
      DictionaryCatalog.loadDefault().install(dictionaryId, unpacked);
      return promoteDictionaryFiles(unpacked, targetDirectory);
    } finally {
      deleteRecursively(unpacked);
    }
  }

  /**
   * Moves the dictionary payload files from an unpacked archive tree into the target
   * directory, flattened to their base names. Every destination is checked before the
   * first move, so a collision leaves the target unchanged.
   *
   * @param unpacked The directory the archive was unpacked into.
   * @param targetDirectory The directory to install into; created when absent.
   * @return The number of dictionary files installed.
   * @throws IOException Thrown if the tree holds no dictionary file, two entries
   *         flatten to the same base name, a target file already exists, or moving
   *         fails.
   */
  private static int promoteDictionaryFiles(Path unpacked, Path targetDirectory)
      throws IOException {
    Files.createDirectories(targetDirectory);
    final List<Path> candidates;
    try (Stream<Path> files = Files.walk(unpacked, MAX_PAYLOAD_DEPTH)) {
      candidates = files.filter(Files::isRegularFile).toList();
    }
    final List<Path> payload = new ArrayList<>();
    final Set<String> baseNames = new HashSet<>();
    for (final Path file : candidates) {
      final String baseName = file.getFileName().toString();
      if (!isDictionaryFile(baseName)) {
        continue;
      }
      if (!baseNames.add(baseName)) {
        throw new IOException(
            "the archive flattens two entries to the same name: " + baseName);
      }
      payload.add(file);
    }
    if (payload.isEmpty()) {
      throw new IOException("the archive contains no dictionary file");
    }
    for (final Path file : payload) {
      final Path destination = targetDirectory.resolve(file.getFileName().toString());
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("target already contains: " + destination);
      }
    }
    for (final Path file : payload) {
      Files.move(file, targetDirectory.resolve(file.getFileName().toString()));
    }
    return payload.size();
  }

  /**
   * Recognizes the file names a {@link MecabDictionary} loads.
   *
   * @param baseName The file name without any directory prefix.
   * @return {@code true} when the name is dictionary payload.
   */
  private static boolean isDictionaryFile(String baseName) {
    return baseName.endsWith(".csv") || baseName.endsWith(".def")
        || "dicrc".equals(baseName);
  }

  /**
   * Deletes a directory tree, deepest entries first.
   *
   * @param root The directory to remove.
   * @throws IOException Thrown if a deletion fails.
   */
  private static void deleteRecursively(Path root) throws IOException {
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
          throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path directory, IOException error)
          throws IOException {
        if (error != null) {
          throw error;
        }
        Files.delete(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
