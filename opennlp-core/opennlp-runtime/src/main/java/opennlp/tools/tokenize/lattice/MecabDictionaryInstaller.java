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
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;

/**
 * Fetches and unpacks a mecab-format dictionary archive into a local directory, so the
 * dictionary is acquired by the user at install time and never ships with this library.
 * The archive location is user-supplied; no dictionary data is bundled and no download
 * location is built in.
 *
 * <p>The installer reads gzip-compressed tar archives, the format the common
 * distributions use, and extracts only the dictionary payload: the {@code *.csv}
 * lexicon files and {@code *.def} definition files that a {@link MecabDictionary}
 * reads, plus the {@code dicrc} configuration file distributions ship alongside them.
 * Entries are flattened to their base names, which also means no archive path can
 * escape the target directory.</p>
 *
 * @since 3.0.0
 */
public final class MecabDictionaryInstaller {

  private static final int TAR_BLOCK = 512;
  private static final int TAR_NAME_LENGTH = 100;
  private static final int TAR_SIZE_OFFSET = 124;
  private static final int TAR_SIZE_LENGTH = 12;
  private static final int TAR_TYPE_OFFSET = 156;

  private MecabDictionaryInstaller() {
    // This class exposes only static methods and is never instantiated.
  }

  /**
   * Downloads a dictionary archive and unpacks it.
   *
   * @param archive The archive location, a gzip-compressed tar. Must not be
   *                {@code null}.
   * @param targetDirectory The directory to unpack into; created when absent. Must not
   *                        be {@code null}.
   * @return The number of dictionary files extracted.
   * @throws IOException Thrown if fetching, reading, or writing fails, or the archive
   *         contains no dictionary file.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null} or
   *         {@code archive} is not an absolute URI.
   */
  public static int install(URI archive, Path targetDirectory) throws IOException {
    if (archive == null) {
      throw new IllegalArgumentException("archive must not be null");
    }
    if (targetDirectory == null) {
      throw new IllegalArgumentException("targetDirectory must not be null");
    }
    try (InputStream in = archive.toURL().openStream()) {
      return extract(in, targetDirectory);
    }
  }

  /**
   * Unpacks a dictionary archive stream.
   *
   * @param archiveStream The gzip-compressed tar content. Must not be {@code null}.
   *                      Not closed.
   * @param targetDirectory The directory to unpack into; created when absent. Must not
   *                        be {@code null}.
   * @return The number of dictionary files extracted.
   * @throws IOException Thrown if reading or writing fails, or the archive contains no
   *         dictionary file.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static int extract(InputStream archiveStream, Path targetDirectory)
      throws IOException {
    if (archiveStream == null) {
      throw new IllegalArgumentException("archiveStream must not be null");
    }
    if (targetDirectory == null) {
      throw new IllegalArgumentException("targetDirectory must not be null");
    }
    Files.createDirectories(targetDirectory);
    final InputStream tar = new GZIPInputStream(archiveStream);
    final byte[] header = new byte[TAR_BLOCK];
    int extracted = 0;
    while (readBlock(tar, header)) {
      if (isEndBlock(header)) {
        break;
      }
      final String name = headerName(header);
      final long size = headerSize(header);
      final char type = (char) header[TAR_TYPE_OFFSET];
      final String baseName = baseName(name);
      final boolean wanted = (type == '0' || type == 0)
          && (baseName.endsWith(".csv") || baseName.endsWith(".def")
              || "dicrc".equals(baseName));
      if (wanted) {
        final Path file = targetDirectory.resolve(baseName);
        try (InputStream entry = boundedStream(tar, size)) {
          Files.copy(entry, file, StandardCopyOption.REPLACE_EXISTING);
        }
        extracted++;
        skip(tar, padding(size));
      } else {
        skip(tar, size + padding(size));
      }
    }
    if (extracted == 0) {
      throw new IOException("the archive contains no dictionary file");
    }
    return extracted;
  }

  /**
   * Fills one tar block from the stream.
   *
   * @param in The tar stream.
   * @param block The block buffer to fill completely.
   * @return {@code true} when a full block was read, {@code false} at a clean end of
   *         stream before any byte of the block.
   * @throws IOException Thrown if the stream ends inside the block or reading fails.
   */
  private static boolean readBlock(InputStream in, byte[] block) throws IOException {
    int filled = 0;
    while (filled < block.length) {
      final int read = in.read(block, filled, block.length - filled);
      if (read < 0) {
        if (filled == 0) {
          return false;
        }
        throw new IOException("truncated tar header");
      }
      filled += read;
    }
    return true;
  }

  /**
   * Recognizes the all-zero block that terminates a tar archive.
   *
   * @param block The block to inspect.
   * @return {@code true} when every byte is zero.
   */
  private static boolean isEndBlock(byte[] block) {
    for (final byte b : block) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Reads the NUL-terminated entry name from a tar header block.
   *
   * @param header The header block.
   * @return The entry name. Never {@code null}.
   */
  private static String headerName(byte[] header) {
    int end = 0;
    while (end < TAR_NAME_LENGTH && header[end] != 0) {
      end++;
    }
    return new String(header, 0, end, StandardCharsets.UTF_8);
  }

  /**
   * Reads the octal entry size from a tar header block.
   *
   * @param header The header block.
   * @return The entry size in bytes.
   * @throws IOException Thrown if the size field holds a non-octal digit.
   */
  private static long headerSize(byte[] header) throws IOException {
    long size = 0;
    for (int i = TAR_SIZE_OFFSET; i < TAR_SIZE_OFFSET + TAR_SIZE_LENGTH; i++) {
      final byte b = header[i];
      if (b == 0 || b == ' ') {
        continue;
      }
      if (b < '0' || b > '7') {
        throw new IOException("malformed tar size field");
      }
      size = size * 8 + (b - '0');
    }
    return size;
  }

  /**
   * Strips any directory prefix from an archive entry name.
   *
   * @param name The entry name as stored in the archive.
   * @return The part after the last {@code /}, or the whole name when there is none.
   */
  private static String baseName(String name) {
    final int slash = name.lastIndexOf('/');
    return slash < 0 ? name : name.substring(slash + 1);
  }

  /**
   * Computes the padding after an entry: tar content is stored in whole blocks.
   *
   * @param size The entry size in bytes.
   * @return The number of padding bytes up to the next block boundary.
   */
  private static long padding(long size) {
    final long remainder = size % TAR_BLOCK;
    return remainder == 0 ? 0 : TAR_BLOCK - remainder;
  }

  /**
   * Consumes and discards an exact number of bytes from the stream.
   *
   * @param in The stream to read from.
   * @param bytes The number of bytes to discard.
   * @throws IOException Thrown if the stream ends before that many bytes were read.
   */
  private static void skip(InputStream in, long bytes) throws IOException {
    long remaining = bytes;
    final byte[] buffer = new byte[8192];
    while (remaining > 0) {
      final int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (read < 0) {
        throw new IOException("truncated tar entry");
      }
      remaining -= read;
    }
  }

  /** Wraps the tar stream so exactly one entry's bytes are readable. */
  private static InputStream boundedStream(InputStream in, long size) {
    return new InputStream() {
      private long remaining = size;

      @Override
      public int read() throws IOException {
        if (remaining <= 0) {
          return -1;
        }
        final int b = in.read();
        if (b < 0) {
          throw new IOException("truncated tar entry");
        }
        remaining--;
        return b;
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        if (remaining <= 0) {
          return -1;
        }
        final int read = in.read(buffer, offset, (int) Math.min(length, remaining));
        if (read < 0) {
          throw new IOException("truncated tar entry");
        }
        remaining -= read;
        return read;
      }
    };
  }
}
