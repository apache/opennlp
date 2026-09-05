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
package opennlp.embeddings.index;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import opennlp.tools.util.InvalidFormatException;

/** Reads and writes the ids and checksum manifest shared by vector indexes. */
final class IndexFiles {

  /** Name of the integrity manifest in an index directory. */
  static final String MANIFEST_FILE = "manifest.sha256";

  private static final String MANIFEST_MAGIC = "OPENNLP-VECTOR-INDEX-1";
  private static final String VECTORS_PREFIX = "vectors=";
  private static final String IDS_PREFIX = "ids=";

  /** Not instantiable. */
  private IndexFiles() {
  }

  /** Writes one vector data file. */
  @FunctionalInterface
  interface VectorWriter {

    /**
     * Writes the vector data.
     *
     * @param file The temporary output file.
     * @throws IOException Thrown if writing fails.
     */
    void write(Path file) throws IOException;
  }

  /**
   * Writes an index through temporary files, replacing the manifest after both data files.
   *
   * @param directory The destination directory.
   * @param vectorsName The vector data file name.
   * @param idsName The id file name.
   * @param ids The ids in row order.
   * @param vectorWriter The vector data writer.
   * @throws IOException Thrown if writing or replacing a file fails.
   */
  static void write(Path directory, String vectorsName, String idsName, List<String> ids,
                    VectorWriter vectorWriter) throws IOException {
    Files.createDirectories(directory);
    Path vectorsTemporary = null;
    Path idsTemporary = null;
    Path manifestTemporary = null;
    Throwable failure = null;
    try {
      vectorsTemporary = Files.createTempFile(directory, "." + vectorsName + "-", ".tmp");
      idsTemporary = Files.createTempFile(directory, "." + idsName + "-", ".tmp");
      manifestTemporary =
          Files.createTempFile(directory, "." + MANIFEST_FILE + "-", ".tmp");
      vectorWriter.write(vectorsTemporary);
      Files.write(idsTemporary, ids, StandardCharsets.UTF_8);
      final List<String> manifest = List.of(
          MANIFEST_MAGIC,
          VECTORS_PREFIX + sha256(vectorsTemporary),
          IDS_PREFIX + sha256(idsTemporary));
      Files.write(manifestTemporary, manifest, StandardCharsets.US_ASCII);

      replace(vectorsTemporary, directory.resolve(vectorsName));
      replace(idsTemporary, directory.resolve(idsName));
      replace(manifestTemporary, directory.resolve(MANIFEST_FILE));
    } catch (IOException | RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      cleanup(failure, manifestTemporary, idsTemporary, vectorsTemporary);
    }
  }

  /**
   * Verifies an index manifest, then reads and validates its ids.
   *
   * @param directory The index directory.
   * @param vectorsName The vector data file name.
   * @param idsName The id file name.
   * @return The immutable ids in row order.
   * @throws IllegalArgumentException Thrown if the directory or a required file is missing.
   * @throws InvalidFormatException Thrown if the manifest or ids are invalid.
   * @throws IOException Thrown if reading fails.
   */
  static List<String> readIds(Path directory, String vectorsName, String idsName)
      throws IOException {
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException(
          "Index directory does not exist or is not a directory: " + directory);
    }
    final Path vectorsFile = directory.resolve(vectorsName);
    final Path idsFile = directory.resolve(idsName);
    final Path manifestFile = directory.resolve(MANIFEST_FILE);
    if (!Files.isRegularFile(vectorsFile) || !Files.isRegularFile(idsFile)
        || !Files.isRegularFile(manifestFile)) {
      throw new IllegalArgumentException("Index directory " + directory + " does not contain "
          + vectorsName + ", " + idsName + ", and " + MANIFEST_FILE);
    }

    final List<String> manifest = readLines(manifestFile, StandardCharsets.US_ASCII);
    if (manifest.size() != 3 || !MANIFEST_MAGIC.equals(manifest.get(0))
        || !isHashLine(manifest.get(1), VECTORS_PREFIX)
        || !isHashLine(manifest.get(2), IDS_PREFIX)) {
      throw new InvalidFormatException(manifestFile + " is not a supported index manifest");
    }
    if (!manifest.get(1).substring(VECTORS_PREFIX.length()).equals(sha256(vectorsFile))) {
      throw new InvalidFormatException(vectorsFile + " does not match " + manifestFile);
    }
    if (!manifest.get(2).substring(IDS_PREFIX.length()).equals(sha256(idsFile))) {
      throw new InvalidFormatException(idsFile + " does not match " + manifestFile);
    }

    final List<String> ids = readLines(idsFile, StandardCharsets.UTF_8);
    final Set<String> seen = new HashSet<>();
    for (final String id : ids) {
      if (id.isBlank()) {
        throw new InvalidFormatException(idsFile + " contains a blank id");
      }
      if (!seen.add(id)) {
        throw new InvalidFormatException(idsFile + " contains id '" + id + "' more than once");
      }
    }
    return List.copyOf(ids);
  }

  /**
   * Reads text while reporting invalid byte sequences as an invalid index format.
   *
   * @param file The text file.
   * @param charset The required character encoding.
   * @return The decoded lines.
   * @throws InvalidFormatException Thrown if a byte sequence is invalid for {@code charset}.
   * @throws IOException Thrown if reading fails.
   */
  private static List<String> readLines(Path file, Charset charset) throws IOException {
    try {
      return Files.readAllLines(file, charset);
    } catch (CharacterCodingException e) {
      throw new InvalidFormatException(file + " is not valid " + charset.name(), e);
    }
  }

  /**
   * Checks a manifest checksum line without regular expressions.
   *
   * @param line The manifest line.
   * @param prefix The required field prefix.
   * @return {@code true} if the line contains a lower-case SHA-256 value.
   */
  private static boolean isHashLine(String line, String prefix) {
    if (!line.startsWith(prefix) || line.length() != prefix.length() + 64) {
      return false;
    }
    for (int i = prefix.length(); i < line.length(); i++) {
      final char c = line.charAt(i);
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Computes the SHA-256 checksum of a file.
   *
   * @param file The file to read.
   * @return The lower-case hexadecimal checksum.
   * @throws IOException Thrown if reading fails.
   */
  private static String sha256(Path file) throws IOException {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
    final byte[] block = new byte[8192];
    try (InputStream in = Files.newInputStream(file)) {
      int length;
      while ((length = in.read(block)) != -1) {
        digest.update(block, 0, length);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  /**
   * Replaces a file atomically when the file system supports it.
   *
   * @param source The completed temporary file.
   * @param target The destination file.
   * @throws IOException Thrown if replacement fails.
   */
  private static void replace(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Deletes temporary files without hiding the failure that caused cleanup.
   *
   * @param failure The earlier failure, or {@code null} after a successful write.
   * @param files The temporary files.
   * @throws IOException Thrown if cleanup alone fails.
   */
  private static void cleanup(Throwable failure, Path... files) throws IOException {
    final List<IOException> cleanupFailures = new ArrayList<>();
    for (final Path file : files) {
      if (file == null) {
        continue;
      }
      try {
        Files.deleteIfExists(file);
      } catch (IOException e) {
        cleanupFailures.add(e);
      }
    }
    if (cleanupFailures.isEmpty()) {
      return;
    }
    final IOException first = cleanupFailures.get(0);
    for (int i = 1; i < cleanupFailures.size(); i++) {
      first.addSuppressed(cleanupFailures.get(i));
    }
    if (failure != null) {
      failure.addSuppressed(first);
    } else {
      throw first;
    }
  }
}
