/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.util.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.InvalidFormatException;

/**
 * Tests the decompressed size limit of {@link BoundedZipInputStream}.
 */
class BoundedZipInputStreamTest {

  private static final int ENTRY_BYTES = 1024 * 1024;

  /** A ratio that the zero-filled test entries never exceed below the unchecked size. */
  private static final int ANY_RATIO = Integer.MAX_VALUE;

  /**
   * An archive that inflates to more bytes than the limit fails.
   *
   * @param limit The decompressed size limit, below the size of the entry.
   */
  @ParameterizedTest
  @ValueSource(longs = {1, ENTRY_BYTES / 2, ENTRY_BYTES - 1})
  void testRejectsAnArchiveBeyondTheDecompressedSizeLimit(long limit) throws IOException {
    try (BoundedZipInputStream zip = new BoundedZipInputStream(
        new ByteArrayInputStream(archive()), limit, ANY_RATIO)) {
      zip.getNextEntry();
      final InvalidFormatException e =
          Assertions.assertThrows(InvalidFormatException.class, () -> zip.readAllBytes());
      Assertions.assertTrue(e.getMessage().contains("maximum decompressed size of " + limit),
          e.getMessage());
    }
  }

  /**
   * An archive that inflates to exactly the limit, or less, is read completely.
   *
   * @param limit The decompressed size limit, at or above the size of the entry.
   */
  @ParameterizedTest
  @ValueSource(longs = {ENTRY_BYTES, ENTRY_BYTES + 1L})
  void testReadsAnArchiveUpToTheDecompressedSizeLimit(long limit) throws IOException {
    try (BoundedZipInputStream zip = new BoundedZipInputStream(
        new ByteArrayInputStream(archive()), limit, ANY_RATIO)) {
      zip.getNextEntry();
      Assertions.assertEquals(ENTRY_BYTES, zip.readAllBytes().length);
    }
  }

  /**
   * {@return a model package with one entry of {@value #ENTRY_BYTES} zero bytes}
   *
   * @throws IOException Thrown if the archive cannot be written.
   */
  private static byte[] archive() throws IOException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("payload.txt"));
      zip.write(new byte[ENTRY_BYTES]);
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }
}
