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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Writes a <a href="https://github.com/huggingface/safetensors">safetensors</a> file holding a
 * single 2-D {@code F32} tensor, the shape a distilled embedding table takes (vocabulary size by
 * output dimension). This is the write side of the format {@link SafetensorsFile} reads; the data
 * is streamed to the file in chunks so the writer's overhead beyond the caller's matrix is
 * constant.
 */
final class SafetensorsWriter {

  /** The name of the embedding matrix tensor, the Model2Vec convention. */
  static final String EMBEDDINGS_TENSOR = "embeddings";

  /** The size of the encoding buffer the matrix is streamed through; a multiple of Float.BYTES. */
  private static final int WRITE_CHUNK_BYTES = 1 << 20;

  /**
   * The boundary the header is space-padded to, so the tensor data starts aligned. The reference
   * safetensors writer pads the same way, and readers that memory-map the data section rely on it.
   */
  private static final int HEADER_ALIGNMENT_BYTES = 8;

  /** The byte the header is padded with; JSON treats it as insignificant whitespace. */
  private static final byte HEADER_PADDING = ' ';

  /** Not instantiable. */
  private SafetensorsWriter() {
  }

  /**
   * Writes a row-major float matrix as a one-tensor safetensors file.
   *
   * @param file   The file to write, replaced when it exists. Must not be {@code null}.
   * @param rows   The number of matrix rows.
   * @param cols   The number of matrix columns.
   * @param values The matrix values in row-major order, {@code rows * cols} of them. Must not be
   *               {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or the value count
   *     does not match the shape.
   * @throws IOException Thrown if writing fails.
   */
  static void writeMatrix(Path file, int rows, int cols, float[] values) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null");
    }
    if (values == null) {
      throw new IllegalArgumentException("Values must not be null");
    }
    if (rows < 1 || cols < 1 || values.length != (long) rows * cols) {
      throw new IllegalArgumentException("Values has " + values.length + " elements, not " + rows
          + " x " + cols);
    }
    final long dataBytes = (long) values.length * Float.BYTES;
    final String header = "{\"" + EMBEDDINGS_TENSOR + "\":{\"dtype\":\"F32\",\"shape\":[" + rows
        + "," + cols + "],\"data_offsets\":[0," + dataBytes + "]}}";
    final byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
    final int padding = (HEADER_ALIGNMENT_BYTES
        - (Long.BYTES + headerBytes.length) % HEADER_ALIGNMENT_BYTES) % HEADER_ALIGNMENT_BYTES;
    final Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE,
        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      final ByteBuffer prefix = ByteBuffer.allocate(Long.BYTES + headerBytes.length + padding)
          .order(ByteOrder.LITTLE_ENDIAN);
      prefix.putLong((long) headerBytes.length + padding);
      prefix.put(headerBytes);
      for (int i = 0; i < padding; i++) {
        prefix.put(HEADER_PADDING);
      }
      prefix.flip();
      writeFully(channel, prefix);
      final ByteBuffer chunk = ByteBuffer.allocate(WRITE_CHUNK_BYTES)
          .order(ByteOrder.LITTLE_ENDIAN);
      int written = 0;
      while (written < values.length) {
        chunk.clear();
        final int count = Math.min(values.length - written, WRITE_CHUNK_BYTES / Float.BYTES);
        chunk.asFloatBuffer().put(values, written, count);
        chunk.limit(count * Float.BYTES);
        writeFully(channel, chunk);
        written += count;
      }
    }
  }

  /**
   * Writes the buffer's remaining bytes to the channel.
   *
   * @param channel The open channel.
   * @param buffer  The buffer to drain.
   * @throws IOException Thrown if writing fails.
   */
  private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      channel.write(buffer);
    }
  }
}
