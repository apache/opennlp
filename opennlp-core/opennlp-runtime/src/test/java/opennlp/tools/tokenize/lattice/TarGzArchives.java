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

  /** The tar block size; headers, content, and padding are all whole blocks. */
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
   * Builds a gzip-compressed tar archive from name and content pairs, the layout a
   * dictionary distribution ships in.
   *
   * @param entries The entries as {@code {name, content}} pairs. Must not be
   *                {@code null}.
   * @return The compressed archive bytes. Never {@code null}.
   * @throws IOException Thrown if writing to the in-memory streams fails.
   */
  static byte[] gzippedTar(String[][] entries) throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    for (final String[] entry : entries) {
      tarEntry(tar, entry[0], entry[1].getBytes(StandardCharsets.UTF_8));
    }
    // Two zero blocks end a tar archive.
    tar.write(new byte[2 * BLOCK]);
    final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write(tar.toByteArray());
    }
    return compressed.toByteArray();
  }

  /**
   * Appends one ustar file entry to a growing tar image: a 512-byte header block
   * followed by the content padded to a block boundary.
   *
   * @param tar The tar image under construction. Must not be {@code null}.
   * @param name The entry name including any directory prefix. Must not be
   *             {@code null}, empty, or longer than the 100-byte header name field.
   * @param content The entry content bytes. Must not be {@code null}.
   * @throws IOException Thrown if writing to the in-memory stream fails.
   * @throws IllegalArgumentException Thrown if {@code name} does not fit the header.
   */
  private static void tarEntry(ByteArrayOutputStream tar, String name, byte[] content)
      throws IOException {
    final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    if (nameBytes.length == 0 || nameBytes.length > NAME_LENGTH) {
      throw new IllegalArgumentException(
          "entry name must be 1.." + NAME_LENGTH + " bytes, got " + nameBytes.length);
    }
    final byte[] header = new byte[BLOCK];
    System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
    final byte[] mode = "0000644".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(mode, 0, header, MODE_OFFSET, mode.length);
    // Both numeric fields hold octal digits followed by one terminator byte.
    final byte[] size = String.format("%0" + (SIZE_LENGTH - 1) + "o", content.length)
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
    tar.write(content);
    final int padding = (BLOCK - content.length % BLOCK) % BLOCK;
    tar.write(new byte[padding]);
  }
}
