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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import opennlp.tools.util.archive.TarArchives;

/**
 * Shared fixtures for the {@code ResourceInstaller} test classes: tar archive building,
 * gzip compression, digest computation, and installed-file listing.
 */
final class InstallerTestSupport {

  static final int BLOCK = TarArchives.BLOCK;
  static final int TERMINATOR_SIZE = TarArchives.TERMINATOR_SIZE;

  /** One kibibyte, a convenient small ceiling for limit tests. */
  static final long KIBIBYTE = 1024;

  /** One mebibyte, a convenient generous ceiling for tests that do not exercise it. */
  static final long MEBIBYTE = 1024 * KIBIBYTE;

  private InstallerTestSupport() {
  }

  /**
   * Writes one regular-file tar entry into the given buffer.
   *
   * @param tar The buffer receiving the entry bytes. Must not be {@code null}.
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param content The entry content. Must not be {@code null}.
   * @throws IOException Thrown if writing to the buffer fails.
   * @throws IllegalArgumentException Thrown if the name exceeds the tar name field.
   */
  static void tarEntry(ByteArrayOutputStream tar, String name, byte[] content)
      throws IOException {
    TarArchives.entry(tar, name, content);
  }

  /**
   * Builds a gzip-compressed tar archive from name and content pairs.
   *
   * @param entries Pairs of entry name and UTF-8 text content. Must not be {@code null}.
   * @return The archive bytes. Never {@code null}.
   * @throws IOException Thrown if assembling the archive fails.
   */
  static byte[] tarGz(String[][] entries) throws IOException {
    return TarArchives.gzippedTar(entries);
  }

  /**
   * Compresses the given bytes with gzip.
   *
   * @param content The bytes to compress. Must not be {@code null}.
   * @return The gzip-compressed bytes. Never {@code null}.
   * @throws IOException Thrown if compressing fails.
   */
  static byte[] gzip(byte[] content) throws IOException {
    return TarArchives.gzip(content);
  }

  /**
   * Computes the SHA-256 of the given bytes as a lowercase hex string.
   *
   * @param content The bytes to digest. Must not be {@code null}.
   * @return The 64-character lowercase hex digest. Never {@code null}.
   */
  static String sha256(byte[] content) {
    return DigestTestUtil.sha256(content);
  }

  /**
   * Computes the SHA-512 of the given bytes as a lowercase hex string.
   *
   * @param content The bytes to digest. Must not be {@code null}.
   * @return The 128-character lowercase hex digest. Never {@code null}.
   */
  static String sha512(byte[] content) {
    return DigestTestUtil.sha512(content);
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
  static List<String> installedFiles(Path root) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile)
          .map(file -> root.relativize(file).toString().replace('\\', '/'))
          .sorted()
          .toList();
    }
  }
}
