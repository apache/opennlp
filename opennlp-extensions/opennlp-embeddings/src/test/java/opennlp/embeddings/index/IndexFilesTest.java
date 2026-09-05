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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests the files shared by the exact and quantized index formats. */
class IndexFilesTest {

  private static final String VECTORS_FILE = "vectors.bin";
  private static final String IDS_FILE = "ids.txt";

  @Test
  void testWriteReadRoundTripPreservesUnicodeIdsAndLeavesNoTemporaryFiles(
      @TempDir Path directory)
      throws IOException {
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Alice", "女王"),
        file -> Files.write(file, new byte[] {1, 2, 3}));

    assertEquals(List.of("Alice", "女王"),
        IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE));
    assertNoTemporaryFiles(directory);
  }

  @Test
  void testWriteReplacesAnExistingIndex(@TempDir Path directory) throws IOException {
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Alice"),
        file -> Files.write(file, new byte[] {1}));
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Queen"),
        file -> Files.write(file, new byte[] {2, 3}));

    assertEquals(List.of("Queen"), IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE));
    assertArrayEquals(new byte[] {2, 3}, Files.readAllBytes(directory.resolve(VECTORS_FILE)));
  }

  @Test
  void testFailedWriteKeepsTheExistingIndexAndRemovesTemporaryFiles(@TempDir Path directory)
      throws IOException {
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Alice"),
        file -> Files.write(file, new byte[] {1}));

    assertThrows(IOException.class,
        () -> IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Queen"), file -> {
          Files.write(file, new byte[] {2});
          throw new IOException("test write failure");
        }));

    assertEquals(List.of("Alice"), IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE));
    assertArrayEquals(new byte[] {1}, Files.readAllBytes(directory.resolve(VECTORS_FILE)));
    assertNoTemporaryFiles(directory);
  }

  @Test
  void testReadRejectsChangedVectorData(@TempDir Path directory) throws IOException {
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Alice"),
        file -> Files.write(file, new byte[] {1, 2, 3}));
    Files.write(directory.resolve(VECTORS_FILE), new byte[] {1, 2, 4});

    assertThrows(InvalidFormatException.class,
        () -> IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE));
  }

  @Test
  void testReadRejectsMalformedManifest(@TempDir Path directory) throws IOException {
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Alice"),
        file -> Files.write(file, new byte[] {1}));
    Files.writeString(directory.resolve(IndexFiles.MANIFEST_FILE), "not a manifest\n",
        StandardCharsets.US_ASCII);

    assertThrows(InvalidFormatException.class,
        () -> IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE));
  }

  @Test
  void testReadRejectsMalformedChecksumLines(@TempDir Path directory) throws IOException {
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Alice"),
        file -> Files.write(file, new byte[] {1}));
    final String zeros = "0".repeat(64);
    final List<List<String>> malformedManifests = List.of(
        List.of("OPENNLP-VECTOR-INDEX-1", "vectors=" + zeros),
        List.of("OPENNLP-VECTOR-INDEX-1", "vector=" + zeros, "ids=" + zeros),
        List.of("OPENNLP-VECTOR-INDEX-1", "vectors=" + "A".repeat(64), "ids=" + zeros),
        List.of("OPENNLP-VECTOR-INDEX-1", "vectors=" + "g".repeat(64), "ids=" + zeros));

    for (final List<String> manifest : malformedManifests) {
      Files.write(directory.resolve(IndexFiles.MANIFEST_FILE), manifest,
          StandardCharsets.US_ASCII);
      assertThrows(InvalidFormatException.class,
          () -> IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE), manifest.toString());
    }
  }

  @Test
  void testReadReportsNonAsciiManifestAsInvalidFormat(@TempDir Path directory)
      throws IOException {
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Alice"),
        file -> Files.write(file, new byte[] {1}));
    Files.write(directory.resolve(IndexFiles.MANIFEST_FILE), new byte[] {(byte) 0x80});

    assertThrows(InvalidFormatException.class,
        () -> IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE));
  }

  @Test
  void testReadReportsMalformedUtf8IdAsInvalidFormat(@TempDir Path directory)
      throws IOException {
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of("Alice"),
        file -> Files.write(file, new byte[] {1}));
    Files.write(directory.resolve(IDS_FILE), new byte[] {(byte) 0xc3, 0x28});
    writeManifest(directory);

    assertThrows(InvalidFormatException.class,
        () -> IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE));
  }

  @Test
  void testReadRejectsBlankId(@TempDir Path directory) throws IOException {
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, List.of(" "),
        file -> Files.write(file, new byte[] {1}));

    assertThrows(InvalidFormatException.class,
        () -> IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE));
  }

  /** Writes a valid manifest for the current data files. */
  private void writeManifest(Path directory) throws IOException {
    Files.write(directory.resolve(IndexFiles.MANIFEST_FILE), List.of(
        "OPENNLP-VECTOR-INDEX-1",
        "vectors=" + sha256(directory.resolve(VECTORS_FILE)),
        "ids=" + sha256(directory.resolve(IDS_FILE))), StandardCharsets.US_ASCII);
  }

  /** Returns the SHA-256 checksum of a test file as lower-case hexadecimal. */
  private String sha256(Path file) throws IOException {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
    return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
  }

  /** Verifies that an index directory contains no unfinished files. */
  private void assertNoTemporaryFiles(Path directory) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(".tmp")));
    }
  }
}
