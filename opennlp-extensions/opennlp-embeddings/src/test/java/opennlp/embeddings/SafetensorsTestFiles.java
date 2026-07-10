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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

/**
 * Writes small well-formed safetensors fixtures for tests and benchmarks, replacing the writer
 * that used to be copied into every test class. Negative tests that need deliberately malformed
 * bytes still hand-roll them.
 */
final class SafetensorsTestFiles {

  private SafetensorsTestFiles() {
  }

  /** One F32 tensor to write: a name, a shape, and the values in row-major order. */
  record Tensor(String name, int[] shape, float[] values) {
  }

  static Tensor matrix(String name, float[][] rows) {
    final int dimension = rows[0].length;
    final float[] values = new float[rows.length * dimension];
    for (int r = 0; r < rows.length; r++) {
      System.arraycopy(rows[r], 0, values, r * dimension, dimension);
    }
    return new Tensor(name, new int[] {rows.length, dimension}, values);
  }

  static Tensor vector(String name, float[] values) {
    return new Tensor(name, new int[] {values.length}, values);
  }

  /**
   * Writes a safetensors file holding the given F32 tensors, header first, data in declaration
   * order.
   */
  static void write(Path file, Tensor... tensors) throws IOException {
    final ByteArrayOutputStream data = new ByteArrayOutputStream();
    final StringJoiner header = new StringJoiner(",", "{", "}");
    int offset = 0;
    for (final Tensor tensor : tensors) {
      final ByteBuffer buffer =
          ByteBuffer.allocate(tensor.values().length * Float.BYTES)
              .order(ByteOrder.LITTLE_ENDIAN);
      for (final float value : tensor.values()) {
        buffer.putFloat(value);
      }
      data.writeBytes(buffer.array());
      final StringJoiner shape = new StringJoiner(",", "[", "]");
      for (final int dimension : tensor.shape()) {
        shape.add(Integer.toString(dimension));
      }
      final int end = offset + tensor.values().length * Float.BYTES;
      header.add("\"" + tensor.name() + "\":{\"dtype\":\"F32\",\"shape\":" + shape
          + ",\"data_offsets\":[" + offset + "," + end + "]}");
      offset = end;
    }
    final byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);
    final ByteBuffer out = ByteBuffer.allocate(8 + headerBytes.length + data.size())
        .order(ByteOrder.LITTLE_ENDIAN);
    out.putLong(headerBytes.length);
    out.put(headerBytes);
    out.put(data.toByteArray());
    Files.write(file, out.array());
  }
}
