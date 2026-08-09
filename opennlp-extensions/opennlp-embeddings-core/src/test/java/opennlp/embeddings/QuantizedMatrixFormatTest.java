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
package opennlp.embeddings;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the loader contract of {@link QuantizedEmbeddingMatrix#read(Path)}: malformed
 * file content fails with the checked {@link InvalidFormatException}, never with an
 * unchecked exception or an allocation failure.
 */
public class QuantizedMatrixFormatTest {

  /** The on-disk magic of a quantized matrix, as written by the writer. */
  private static final int MAGIC = 0x4F4E5131;

  @Test
  void testBadMagicIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("bad-magic.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(0xCAFEBABE);
    }
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(file));
    assertTrue(e.getMessage().contains("magic"), e.getMessage());
  }

  @Test
  void testNegativeRowCountIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("negative-rows.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(MAGIC);
      out.writeInt(-5);
    }
    assertThrows(InvalidFormatException.class, () -> QuantizedEmbeddingMatrix.read(file));
  }

  @Test
  void testImplausibleRowCountFailsBeforeAllocating(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("huge-rows.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(MAGIC);
      out.writeInt(Integer.MAX_VALUE);
      out.writeInt(8);
      out.writeInt(4);
      out.writeLong(17L);
      out.writeInt(16);
    }
    assertThrows(InvalidFormatException.class, () -> QuantizedEmbeddingMatrix.read(file));
  }

  @Test
  void testUnsupportedBitWidthIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("bad-bits.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(MAGIC);
      out.writeInt(1);
      out.writeInt(8);
      out.writeInt(7);
    }
    assertThrows(InvalidFormatException.class, () -> QuantizedEmbeddingMatrix.read(file));
  }

  private static DataOutputStream out(Path file) throws IOException {
    final OutputStream raw = Files.newOutputStream(file);
    return new DataOutputStream(raw);
  }
}
