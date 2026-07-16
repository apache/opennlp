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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import opennlp.tools.util.archive.TarStream;

/**
 * Fetches a user-chosen third-party resource, such as a training corpus, a dictionary
 * archive, or a lexicon, into a local directory. The user supplies the location and thereby
 * accepts that resource's license; nothing is bundled and no location is built in,
 * which keeps externally licensed data entirely outside this library's distribution.
 *
 * <p>An optional SHA-256 checksum is verified against the downloaded bytes before
 * anything is unpacked, so a corrupted or substituted archive fails loud without side
 * effects. The content format is detected from the bytes, not the name: gzip-compressed
 * tar archives and zip archives are unpacked with their relative structure, entries
 * that would escape the target directory are rejected, plain gzip files are
 * decompressed, and anything else is stored as a file under its source name.</p>
 *
 * @see DownloadUtil DownloadUtil, which fetches this project's own published models
 * @since 3.0.0
 */
public final class ResourceInstaller {

  private ResourceInstaller() {
    // This class exposes static methods only and is never instantiated.
  }

  /**
   * Fetches and unpacks a resource without checksum verification.
   *
   * @param source The resource location. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @return The target directory. Never {@code null}.
   * @throws IOException Thrown if fetching or unpacking fails.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static Path install(URI source, Path targetDirectory) throws IOException {
    return install(source, targetDirectory, null);
  }

  /**
   * Fetches, verifies, and unpacks a resource.
   *
   * @param source The resource location. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @param sha256 The expected SHA-256 of the downloaded bytes as a hex string, compared
   *               case-insensitively and ignoring leading and trailing whitespace, or
   *               {@code null} to skip verification.
   * @return The target directory. Never {@code null}.
   * @throws IOException Thrown if fetching fails, the checksum does not match, or
   *         unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source} or
   *         {@code targetDirectory} is {@code null}, or {@code sha256} is blank.
   */
  public static Path install(URI source, Path targetDirectory, String sha256)
      throws IOException {
    if (source == null || targetDirectory == null) {
      throw new IllegalArgumentException("source and targetDirectory must not be null");
    }
    if (sha256 != null && sha256.isBlank()) {
      throw new IllegalArgumentException("sha256 must not be blank; pass null to skip");
    }
    Files.createDirectories(targetDirectory);
    final Path downloaded = Files.createTempFile("opennlp-resource", ".download");
    try {
      try (InputStream in = source.toURL().openStream()) {
        Files.copy(in, downloaded, StandardCopyOption.REPLACE_EXISTING);
      }
      if (sha256 != null) {
        verify(downloaded, sha256);
      }
      unpack(downloaded, sourceName(source), targetDirectory);
      return targetDirectory;
    } finally {
      Files.deleteIfExists(downloaded);
    }
  }

  /**
   * Computes the file's SHA-256 and compares it with the expected hex digest, ignoring
   * hex letter case and any leading or trailing whitespace around the expected value.
   */
  private static void verify(Path file, String expected) throws IOException {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-256 is unavailable in this runtime", e);
    }
    try (InputStream in = Files.newInputStream(file)) {
      final byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
    }
    final StringBuilder actual = new StringBuilder();
    for (final byte b : digest.digest()) {
      actual.append(Character.forDigit((b >> 4) & 0xF, 16))
          .append(Character.forDigit(b & 0xF, 16));
    }
    if (!actual.toString().equalsIgnoreCase(expected.trim())) {
      throw new IOException(
          "checksum mismatch: expected " + expected + " but downloaded " + actual);
    }
  }

  /** Detects the content format from its leading bytes and unpacks accordingly. */
  private static void unpack(Path downloaded, String name, Path target)
      throws IOException {
    try (InputStream raw = new BufferedInputStream(Files.newInputStream(downloaded))) {
      raw.mark(2);
      final int first = raw.read();
      final int second = raw.read();
      raw.reset();
      if (first == 0x1F && second == 0x8B) {
        unpackGzip(raw, name, target);
      } else if (first == 'P' && second == 'K') {
        unpackZip(raw, target);
      } else {
        Files.copy(raw, safeChild(target, name), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  /** Unpacks gzip content: a tar archive inside when present, a plain file otherwise. */
  private static void unpackGzip(InputStream raw, String name, Path target)
      throws IOException {
    final BufferedInputStream decompressed =
        new BufferedInputStream(new GZIPInputStream(raw), 8192);
    decompressed.mark(512);
    final boolean tar = looksLikeTar(decompressed);
    decompressed.reset();
    if (tar) {
      final TarStream entries = new TarStream(decompressed);
      while (entries.next()) {
        if (!entries.isFile()) {
          continue;
        }
        final Path file = safeChild(target, entries.name());
        Files.createDirectories(file.getParent());
        Files.copy(entries.entryStream(), file, StandardCopyOption.REPLACE_EXISTING);
      }
    } else {
      final String plainName = name.endsWith(".gz")
          ? name.substring(0, name.length() - 3) : name;
      Files.copy(decompressed, safeChild(target, plainName),
          StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** Unpacks every regular zip entry to its relative location beneath the target. */
  private static void unpackZip(InputStream raw, Path target) throws IOException {
    final ZipInputStream zip = new ZipInputStream(raw);
    ZipEntry entry;
    while ((entry = zip.getNextEntry()) != null) {
      if (entry.isDirectory()) {
        continue;
      }
      final Path file = safeChild(target, entry.getName());
      Files.createDirectories(file.getParent());
      Files.copy(zip, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** Peeks whether gzip content holds a tar archive, by the ustar magic or a header. */
  private static boolean looksLikeTar(InputStream decompressed) throws IOException {
    final byte[] block = new byte[512];
    int filled = 0;
    while (filled < block.length) {
      final int read = decompressed.read(block, filled, block.length - filled);
      if (read < 0) {
        return false;
      }
      filled += read;
    }
    if (block[257] == 'u' && block[258] == 's' && block[259] == 't'
        && block[260] == 'a' && block[261] == 'r') {
      return true;
    }
    // Without the ustar magic, treat the block as a classic tar header when it starts
    // with a name and its size field holds only octal digits, blanks, or NUL padding.
    if (block[0] == 0) {
      return false;
    }
    for (int i = 124; i < 136; i++) {
      final byte b = block[i];
      if (b != 0 && b != ' ' && (b < '0' || b > '7')) {
        return false;
      }
    }
    return true;
  }

  /** Resolves an archive entry inside the target, rejecting escaping paths. */
  private static Path safeChild(Path target, String entryName) throws IOException {
    final Path resolved = target.resolve(entryName).normalize();
    if (!resolved.startsWith(target.normalize())) {
      throw new IOException("archive entry escapes the target directory: " + entryName);
    }
    return resolved;
  }

  /** Derives a file name from the source URI for non-archive content. */
  private static String sourceName(URI source) {
    final String path = source.getPath();
    if (path == null || path.isEmpty()) {
      return "resource";
    }
    final int slash = path.lastIndexOf('/');
    final String name = slash < 0 ? path : path.substring(slash + 1);
    return name.isEmpty() ? "resource" : name;
  }
}
