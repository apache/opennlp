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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * Builds miniature, project-authored gzip-compressed ustar archives in memory for the
 * tests of this package; no external archive data is involved.
 */
final class TarGzArchives {

  /** The tar block size; headers, content, and padding are all complete blocks. */
  private static final int BLOCK = 512;

  /** The header field offsets and lengths this builder writes, in bytes. */
  private static final int NAME_LENGTH = 100;
  private static final int MODE_OFFSET = 100;
  private static final int SIZE_OFFSET = 124;
  private static final int SIZE_LENGTH = 12;
  private static final int CHECKSUM_OFFSET = 148;
  private static final int CHECKSUM_LENGTH = 8;
  private static final int TYPE_OFFSET = 156;

  /** The type flag of a regular file entry. */
  private static final char REGULAR_FILE = '0';

  private TarGzArchives() {
  }

  /**
   * One archive entry: a path name, the bytes stored after the header, and the size
   * field written into the header (which may differ from the stored content length so
   * budget checks can be exercised without allocating the declared payload).
   *
   * @param name The entry name including any directory prefix.
   * @param content The bytes written after the header; may be shorter than
   *                {@code declaredSize}.
   * @param declaredSize The octal size field stored in the header.
   */
  record Entry(String name, byte[] content, long declaredSize) {

    /**
     * Builds an entry with a declared size that matches its UTF-8 content length.
     *
     * @param name The entry name.
     * @param content The entry text.
     * @return The entry. Not {@code null}.
     */
    static Entry of(String name, String content) {
      final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
      return new Entry(name, bytes, bytes.length);
    }

    /**
     * Builds an entry with a declared size that matches its content length.
     *
     * @param name The entry name.
     * @param content The entry bytes.
     * @return The entry. Not {@code null}.
     */
    static Entry of(String name, byte[] content) {
      return new Entry(name, content, content.length);
    }

    /**
     * Builds an entry with a header size field that is set independently of the stored
     * content, for oversized-entry budget tests.
     *
     * @param name The entry name.
     * @param content The bytes stored after the header; typically empty for header-only
     *                oversized cases.
     * @param declaredSize The size field written into the header.
     * @return The entry. Not {@code null}.
     */
    static Entry withDeclaredSize(String name, byte[] content, long declaredSize) {
      return new Entry(name, content, declaredSize);
    }
  }

  /**
   * Builds a gzip-compressed tar archive from name and content pairs, the layout a
   * dictionary distribution ships in.
   *
   * @param entries The entries as {@code {name, content}} pairs. Must not be
   *                {@code null}.
   * @return The compressed archive bytes. Not {@code null}.
   * @throws IOException Thrown if writing to the in-memory streams fails.
   */
  static byte[] gzippedTar(String[][] entries) throws IOException {
    final Entry[] typed = new Entry[entries.length];
    for (int i = 0; i < entries.length; i++) {
      typed[i] = Entry.of(entries[i][0], entries[i][1]);
    }
    return gzippedTar(typed);
  }

  /**
   * Builds a gzip-compressed tar archive from typed entries.
   *
   * @param entries The entries to store. Must not be {@code null}.
   * @return The compressed archive bytes. Not {@code null}.
   * @throws IOException Thrown if writing to the in-memory streams fails.
   */
  static byte[] gzippedTar(Entry... entries) throws IOException {
    return gzip(tar(entries));
  }

  /**
   * Builds an archive with an incorrect checksum in its first header.
   *
   * @param entries The entries to store. Must not be {@code null} or empty.
   * @return The compressed archive bytes. Not {@code null}.
   * @throws IOException Thrown if writing to the in-memory streams fails.
   */
  static byte[] gzippedTarWithInvalidHeaderChecksum(Entry... entries) throws IOException {
    final byte[] tar = tar(entries);
    tar[CHECKSUM_OFFSET] = tar[CHECKSUM_OFFSET] == '0' ? (byte) '1' : (byte) '0';
    return gzip(tar);
  }

  /**
   * Builds the uncompressed tar image.
   *
   * @param entries The entries to store. Must not be {@code null}.
   * @return The tar bytes. Not {@code null}.
   * @throws IOException Thrown if writing to the in-memory stream fails.
   */
  private static byte[] tar(Entry... entries) throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    for (final Entry entry : entries) {
      tarEntry(tar, entry);
    }
    // Two zero blocks end a tar archive.
    tar.write(new byte[2 * BLOCK]);
    return tar.toByteArray();
  }

  /**
   * Compresses a tar image with gzip.
   *
   * @param tar The tar bytes. Must not be {@code null}.
   * @return The compressed bytes. Not {@code null}.
   * @throws IOException Thrown if compression fails.
   */
  private static byte[] gzip(byte[] tar) throws IOException {
    final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write(tar);
    }
    return compressed.toByteArray();
  }

  /**
   * Appends one ustar file entry to a growing tar image: a 512-byte header block
   * followed by the stored content padded to a block boundary of the declared size
   * when content is present, or the header alone when the test supplies no payload.
   *
   * @param tar The tar image under construction. Must not be {@code null}.
   * @param entry The entry to append. Must not be {@code null}.
   * @throws IOException Thrown if writing to the in-memory stream fails.
   * @throws IllegalArgumentException Thrown if {@code name} does not fit the header or
   *         {@code declaredSize} is negative.
   */
  private static void tarEntry(ByteArrayOutputStream tar, Entry entry) throws IOException {
    final byte[] nameBytes = entry.name().getBytes(StandardCharsets.UTF_8);
    if (nameBytes.length == 0 || nameBytes.length > NAME_LENGTH) {
      throw new IllegalArgumentException(
          "entry name must be 1.." + NAME_LENGTH + " bytes, got " + nameBytes.length);
    }
    if (entry.declaredSize() < 0) {
      throw new IllegalArgumentException("declaredSize must not be negative");
    }
    final byte[] header = new byte[BLOCK];
    System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
    final byte[] mode = "0000644".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(mode, 0, header, MODE_OFFSET, mode.length);
    // Both numeric fields hold octal digits followed by one terminator byte.
    final byte[] size = String.format("%0" + (SIZE_LENGTH - 1) + "o", entry.declaredSize())
        .getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(size, 0, header, SIZE_OFFSET, size.length);
    header[TYPE_OFFSET] = REGULAR_FILE;
    // The checksum is computed with its own field read as spaces.
    for (int i = CHECKSUM_OFFSET; i < CHECKSUM_OFFSET + CHECKSUM_LENGTH; i++) {
      header[i] = ' ';
    }
    int checksum = 0;
    for (final byte b : header) {
      checksum += b & 0xFF;
    }
    final byte[] checksumText = String.format("%0" + (CHECKSUM_LENGTH - 2) + "o", checksum)
        .getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(checksumText, 0, header, CHECKSUM_OFFSET, checksumText.length);
    header[CHECKSUM_OFFSET + CHECKSUM_LENGTH - 2] = 0;
    header[CHECKSUM_OFFSET + CHECKSUM_LENGTH - 1] = ' ';
    tar.write(header);
    if (entry.declaredSize() == 0) {
      return;
    }
    if (entry.content().length == 0) {
      // Header-only oversized fixtures: the extractor rejects on the size field before
      // reading a payload, so the declared bytes are not materialised here.
      return;
    }
    tar.write(entry.content());
    final long missing = Math.max(0, entry.declaredSize() - entry.content().length);
    if (missing > 0) {
      writeZeros(tar, missing);
    }
    final int padding = (BLOCK - (int) (entry.declaredSize() % BLOCK)) % BLOCK;
    tar.write(new byte[padding]);
  }

  /**
   * Writes {@code count} zero bytes to the stream.
   *
   * @param out The stream to write to.
   * @param count The number of zero bytes.
   * @throws IOException Thrown if writing fails.
   */
  private static void writeZeros(ByteArrayOutputStream out, long count) throws IOException {
    final byte[] zeros = new byte[8192];
    long remaining = count;
    while (remaining > 0) {
      final int chunk = (int) Math.min(zeros.length, remaining);
      out.write(zeros, 0, chunk);
      remaining -= chunk;
    }
  }
}
