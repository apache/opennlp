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

import opennlp.tools.util.archive.TarArchives;

/**
 * Builds miniature, project-authored gzip-compressed tar archives in memory for the
 * tests of this package, delegating the tar layout to {@link TarArchives}; no external
 * archive data is involved.
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
      TarArchives.entry(tar, entry[0], entry[1].getBytes(StandardCharsets.UTF_8));
    }
    tar.write(new byte[TarArchives.TERMINATOR_SIZE]);
    return gzip(tar.toByteArray());
  }

  /**
   * Compresses raw tar bytes the way a {@code .tar.gz} distribution is shipped.
   *
   * @param content The raw tar bytes. Must not be {@code null}.
   * @return The gzip-compressed bytes. Never {@code null}.
   * @throws IOException Thrown if writing to the in-memory stream fails.
   */
  static byte[] gzip(byte[] content) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream compressed = new GZIPOutputStream(out)) {
      compressed.write(content);
    }
    return out.toByteArray();
  }
}
