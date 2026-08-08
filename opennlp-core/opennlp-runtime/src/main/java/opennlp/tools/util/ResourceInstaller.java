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
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import opennlp.tools.util.archive.TarStream;

/**
 * Fetches a third-party resource, such as a training corpus, a dictionary archive, or a
 * lexicon, into a local directory. The caller supplies the location and thereby accepts
 * that resource's license; no locations are built in and no data is bundled.
 *
 * <p>An optional SHA-256 checksum is verified against the downloaded bytes before
 * anything is unpacked. The content format is detected from the bytes, not from the
 * name: gzip-compressed tar archives and zip archives are unpacked with their relative
 * structure, entries that would escape the target directory are rejected, plain gzip
 * files are decompressed, and anything else is stored as a file under its source
 * name. One name rule overrides byte detection: a {@code *.bin} source is always
 * stored packed, because an OpenNLP model file is itself a zip archive that its
 * consumers load packed.</p>
 *
 * @see DownloadUtil
 * @since 3.0.0
 */
public final class ResourceInstaller {

  private static final String SHA_256 = "SHA-256";
  private static final String GZIP_SUFFIX = ".gz";

  /** OpenNLP model files are packed zip archives; install them packed. */
  private static final String MODEL_SUFFIX = ".bin";
  private static final String DEFAULT_RESOURCE_NAME = "resource";
  private static final int BUFFER_SIZE = 8192;
  private static final int MAGIC_LENGTH = 2;
  private static final int GZIP_MAGIC_FIRST = 0x1F;
  private static final int GZIP_MAGIC_SECOND = 0x8B;
  private static final int ZIP_MAGIC_FIRST = 'P';
  private static final int ZIP_MAGIC_SECOND = 'K';

  private ResourceInstaller() {
  }

  /**
   * Fetches and unpacks a resource without checksum verification.
   *
   * @param source The resource location. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @return The target directory. Never {@code null}.
   * @throws IOException Thrown if fetching or unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source} or
   *         {@code targetDirectory} is {@code null}.
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
    if (source == null) {
      throw new IllegalArgumentException("source must not be null");
    }
    if (targetDirectory == null) {
      throw new IllegalArgumentException("targetDirectory must not be null");
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
   *
   * @param file The file to digest.
   * @param expected The expected hex digest.
   * @throws IOException Thrown if the file cannot be read or the digests differ.
   */
  private static void verify(Path file, String expected) throws IOException {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(SHA_256);
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(SHA_256 + " is unavailable in this runtime", e);
    }
    try (InputStream in = Files.newInputStream(file)) {
      final byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = in.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
    }
    final String actual = HexFormat.of().formatHex(digest.digest());
    if (!actual.equalsIgnoreCase(expected.trim())) {
      throw new IOException(
          "checksum mismatch: expected " + expected + " but downloaded " + actual);
    }
  }

  /**
   * Detects the content format from its leading bytes and unpacks accordingly.
   * One exception: a source named {@code *.bin} is stored verbatim even when its
   * bytes are a zip archive, because that is exactly what an OpenNLP model file
   * is: a zipped artifact that consumers load packed. Unpacking it would
   * deliver its innards ({@code manifest.properties}, {@code *.model}) where
   * the operator asked for the model.
   *
   * @param downloaded The fetched file.
   * @param name The file name derived from the source location.
   * @param target The directory to unpack into.
   * @throws IOException Thrown if reading or unpacking fails.
   */
  private static void unpack(Path downloaded, String name, Path target)
      throws IOException {
    try (InputStream raw = new BufferedInputStream(Files.newInputStream(downloaded))) {
      raw.mark(MAGIC_LENGTH);
      final int first = raw.read();
      final int second = raw.read();
      raw.reset();
      if (name.endsWith(MODEL_SUFFIX)) {
        Files.copy(raw, safeChild(target, name), StandardCopyOption.REPLACE_EXISTING);
      } else if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
        unpackGzip(raw, name, target);
      } else if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
        unpackZip(raw, target);
      } else {
        Files.copy(raw, safeChild(target, name), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  /**
   * Unpacks gzip content: a tar archive inside when present, a plain file otherwise. A
   * plain file loses the {@code .gz} suffix of its source name.
   *
   * @param raw The gzip-compressed content.
   * @param name The file name derived from the source location.
   * @param target The directory to unpack into.
   * @throws IOException Thrown if decompressing or unpacking fails.
   */
  private static void unpackGzip(InputStream raw, String name, Path target)
      throws IOException {
    final InputStream decompressed =
        new BufferedInputStream(new GZIPInputStream(raw), BUFFER_SIZE);
    if (TarStream.startsWithHeader(decompressed)) {
      unpackTar(decompressed, target);
    } else {
      final String plainName = name.endsWith(GZIP_SUFFIX)
          ? name.substring(0, name.length() - GZIP_SUFFIX.length()) : name;
      Files.copy(decompressed, safeChild(target, plainName),
          StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Unpacks every regular tar entry to its relative location beneath the target.
   *
   * @param decompressed The uncompressed tar content.
   * @param target The directory to unpack into.
   * @throws IOException Thrown if the archive is malformed or an entry escapes the
   *         target directory.
   */
  private static void unpackTar(InputStream decompressed, Path target)
      throws IOException {
    final TarStream entries = new TarStream(decompressed);
    while (entries.next()) {
      if (!entries.isFile()) {
        continue;
      }
      final Path file = safeChild(target, entries.name());
      Files.createDirectories(file.getParent());
      Files.copy(entries.entryStream(), file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Unpacks every regular zip entry to its relative location beneath the target.
   *
   * @param raw The zip content.
   * @param target The directory to unpack into.
   * @throws IOException Thrown if the archive is malformed or an entry escapes the
   *         target directory.
   */
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

  /**
   * Resolves an archive entry inside the target, rejecting escaping paths.
   *
   * @param target The directory to unpack into.
   * @param entryName The entry name as stored in the archive.
   * @return The resolved path beneath the target. Never {@code null}.
   * @throws IOException Thrown if the entry resolves outside the target directory.
   */
  private static Path safeChild(Path target, String entryName) throws IOException {
    final Path resolved = target.resolve(entryName).normalize();
    if (!resolved.startsWith(target.normalize())) {
      throw new IOException("archive entry escapes the target directory: " + entryName);
    }
    return resolved;
  }

  /**
   * Derives a file name from the source URI for non-archive content.
   *
   * @param source The resource location.
   * @return The last path segment, or {@code resource} if the location has none.
   */
  private static String sourceName(URI source) {
    final String path = source.getPath();
    if (path == null || path.isEmpty()) {
      return DEFAULT_RESOURCE_NAME;
    }
    final int slash = path.lastIndexOf('/');
    final String name = slash < 0 ? path : path.substring(slash + 1);
    return name.isEmpty() ? DEFAULT_RESOURCE_NAME : name;
  }
}
