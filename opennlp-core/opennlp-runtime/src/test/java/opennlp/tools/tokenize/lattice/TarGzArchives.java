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
    tar.write(new byte[1024]);
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
    if (nameBytes.length == 0 || nameBytes.length > 100) {
      throw new IllegalArgumentException(
          "entry name must be 1..100 bytes, got " + nameBytes.length);
    }
    final byte[] header = new byte[512];
    System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
    final byte[] mode = "0000644".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(mode, 0, header, 100, mode.length);
    final String size = String.format("%011o", content.length);
    System.arraycopy(size.getBytes(StandardCharsets.US_ASCII), 0, header, 124, 11);
    header[156] = '0';
    for (int i = 148; i < 156; i++) {
      header[i] = ' ';
    }
    int checksum = 0;
    for (final byte b : header) {
      checksum += b & 0xFF;
    }
    final String checksumText = String.format("%06o", checksum);
    System.arraycopy(checksumText.getBytes(StandardCharsets.US_ASCII), 0, header, 148, 6);
    header[154] = 0;
    header[155] = ' ';
    tar.write(header);
    tar.write(content);
    final int padding = (512 - content.length % 512) % 512;
    tar.write(new byte[padding]);
  }
}
