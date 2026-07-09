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
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;

/**
 * Reads a <a href="https://github.com/huggingface/safetensors">safetensors</a> file: an 8-byte
 * little-endian header length, a JSON header describing each tensor's dtype, shape, and byte
 * range, followed by the raw tensor bytes. Deliberately not a general tensor-format library:
 * only the {@code F32} decode path {@link #readFloat32(String)} needs is implemented, since
 * that is what a distilled static-embedding table stores.
 *
 * <p><b>Security.</b> Unlike PyTorch's pickle-based checkpoint format, safetensors carries no
 * executable content: the header is data-only JSON and the body is raw tensor bytes, so loading
 * one cannot execute arbitrary code. No hardening beyond ordinary malformed-input handling is
 * needed.</p>
 *
 * <p>Only the header is read eagerly; tensor data is streamed straight into the caller's array
 * with positional reads when requested, so the file size is not limited by Java's int-indexed
 * arrays. The remaining ceiling is per tensor, not per file: one decoded {@code float[]} holds
 * at most {@link Integer#MAX_VALUE} - 8 elements, and {@link #readFloat32(String)} checks that
 * explicitly. The file must stay in place and unchanged between {@link #read(Path)} and later
 * {@link #readFloat32(String)} calls; a file truncated in between fails loud rather than
 * returning partial data.</p>
 *
 * <p>Instances are immutable and safe for concurrent use: every {@link #readFloat32(String)}
 * call opens its own channel and decodes into a fresh array the caller owns.</p>
 */
@ThreadSafe
public final class SafetensorsFile {

  private static final int HEADER_LENGTH_PREFIX_BYTES = 8;

  // Positional-read chunk size, a multiple of Float.BYTES so every filled chunk decodes to
  // whole floats.
  private static final int READ_CHUNK_BYTES = 1 << 20;

  // The JVM refuses array allocations slightly below Integer.MAX_VALUE; the exact headroom is
  // implementation-specific, 8 is the commonly reserved amount.
  private static final long MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

  private final Path file;
  private final long dataStart;
  private final Map<String, TensorInfo> tensorsByName;
  private final Map<String, String> metadata;

  private SafetensorsFile(Path file, long dataStart, Map<String, TensorInfo> tensorsByName,
                           Map<String, String> metadata) {
    this.file = file;
    this.dataStart = dataStart;
    this.tensorsByName = tensorsByName;
    this.metadata = metadata;
  }

  /**
   * Reads a safetensors file's header.
   *
   * @param file The file to read. Must not be {@code null} and must exist.
   * @return The parsed file, with every tensor's metadata resolved and validated against the
   *     file's actual length.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null} or missing, or the
   *     file is malformed.
   * @throws UncheckedIOException Thrown if reading the file fails.
   */
  public static SafetensorsFile read(Path file) {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null");
    }
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File does not exist or is not a regular file: " + file);
    }
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      final long fileSize = channel.size();
      if (fileSize < HEADER_LENGTH_PREFIX_BYTES) {
        throw new IllegalArgumentException(
            "File " + file + " is too short to be a safetensors file: " + fileSize + " bytes");
      }
      final ByteBuffer prefix = ByteBuffer.allocate(HEADER_LENGTH_PREFIX_BYTES)
          .order(ByteOrder.LITTLE_ENDIAN);
      readFully(channel, prefix, 0, file);
      final long headerLength = prefix.flip().getLong();
      if (headerLength < 0 || headerLength > fileSize - HEADER_LENGTH_PREFIX_BYTES) {
        throw new IllegalArgumentException("File " + file + " declares a header length of "
            + headerLength + ", which does not fit in a file of " + fileSize + " bytes");
      }
      if (headerLength > MAX_ARRAY_LENGTH) {
        throw new IllegalArgumentException("File " + file + " declares a header length of "
            + headerLength + " bytes, too large to decode as a single JSON string");
      }
      final ByteBuffer headerBytes = ByteBuffer.allocate((int) headerLength);
      readFully(channel, headerBytes, HEADER_LENGTH_PREFIX_BYTES, file);
      final String headerJson = new String(headerBytes.array(), StandardCharsets.UTF_8);
      final SafetensorsHeaderParser.Result parsed = SafetensorsHeaderParser.parse(headerJson);
      final long dataStart = HEADER_LENGTH_PREFIX_BYTES + headerLength;
      final long dataLength = fileSize - dataStart;
      final Map<String, TensorInfo> tensorsByName =
          new LinkedHashMap<>(parsed.tensors().size() * 2);
      for (final TensorInfo tensor : parsed.tensors()) {
        if (tensor.dataOffsetBegin() < 0 || tensor.dataOffsetEnd() < tensor.dataOffsetBegin()
            || tensor.dataOffsetEnd() > dataLength) {
          throw new IllegalArgumentException("File " + file + " tensor '" + tensor.name()
              + "' has a data range [" + tensor.dataOffsetBegin() + ", " + tensor.dataOffsetEnd()
              + ") that does not fit in the file");
        }
        if (tensorsByName.putIfAbsent(tensor.name(), tensor) != null) {
          throw new IllegalArgumentException(
              "File " + file + " declares tensor '" + tensor.name() + "' more than once");
        }
      }
      return new SafetensorsFile(file, dataStart, Collections.unmodifiableMap(tensorsByName),
          Collections.unmodifiableMap(parsed.metadata()));
    }
    catch (IOException e) {
      throw new UncheckedIOException("Unable to read safetensors file " + file, e);
    }
  }

  /** {@return the names of every tensor declared in the header, in header order} */
  public Set<String> tensorNames() {
    return tensorsByName.keySet();
  }

  /**
   * Returns the header metadata for one tensor.
   *
   * @param name The tensor's name. Must not be {@code null}.
   * @return The tensor's metadata.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null} or not a tensor in
   *     this file.
   */
  public TensorInfo tensorInfo(String name) {
    if (name == null) {
      throw new IllegalArgumentException("Name must not be null");
    }
    final TensorInfo info = tensorsByName.get(name);
    if (info == null) {
      throw new IllegalArgumentException(
          "No tensor named '" + name + "' in this file; available: " + tensorsByName.keySet());
    }
    return info;
  }

  /**
   * Decodes a {@code F32} tensor's data, streaming it from the file.
   *
   * @param name The tensor's name. Must not be {@code null}.
   * @return The tensor's elements in row-major (shape outermost-first) order.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null}, not a tensor in
   *     this file, not declared with dtype {@code F32}, or larger than a Java array can hold.
   * @throws IllegalStateException Thrown if the file has been truncated since
   *     {@link #read(Path)} validated the tensor's byte range.
   * @throws UncheckedIOException Thrown if reading the file fails.
   */
  public float[] readFloat32(String name) {
    final TensorInfo info = tensorInfo(name);
    if (!"F32".equals(info.dtype())) {
      throw new IllegalArgumentException(
          "Tensor '" + name + "' has dtype " + info.dtype() + ", not F32");
    }
    final long elementCount = info.elementCount();
    if (elementCount < 0 || elementCount > MAX_ARRAY_LENGTH) {
      throw new IllegalArgumentException("Tensor '" + name + "' declares " + elementCount
          + " elements, more than a Java array can hold (" + MAX_ARRAY_LENGTH
          + "); decoding to a float[] is capped there");
    }
    final long byteLength = info.dataOffsetEnd() - info.dataOffsetBegin();
    if (byteLength != elementCount * Float.BYTES) {
      throw new IllegalArgumentException("Tensor '" + name + "' declares " + elementCount
          + " F32 elements but its data range is " + byteLength + " bytes");
    }
    final float[] values = new float[(int) elementCount];
    // When the build baseline reaches JDK 22+, this loop can become a single MemorySegment.copy
    // out of a FileChannel.map'd segment (long-indexed, deterministic unmap via Arena); on the
    // JDK 21 baseline java.lang.foreign is still a preview API, so positional reads are the
    // portable way past the 2 GB byte[]/ByteBuffer ceiling.
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      final ByteBuffer chunk = ByteBuffer.allocate((int) Math.min(READ_CHUNK_BYTES, byteLength))
          .order(ByteOrder.LITTLE_ENDIAN);
      long position = dataStart + info.dataOffsetBegin();
      int decoded = 0;
      while (decoded < values.length) {
        chunk.clear();
        final long remainingBytes = byteLength - (long) decoded * Float.BYTES;
        if (remainingBytes < chunk.capacity()) {
          chunk.limit((int) remainingBytes);
        }
        readFully(channel, chunk, position, file);
        chunk.flip();
        final int floats = chunk.remaining() / Float.BYTES;
        chunk.asFloatBuffer().get(values, decoded, floats);
        decoded += floats;
        position += (long) floats * Float.BYTES;
      }
      return values;
    }
    catch (IOException e) {
      throw new UncheckedIOException("Unable to read tensor '" + name + "' from " + file, e);
    }
  }

  // Fills the buffer with bytes starting at the given file position; fails loud if the file
  // ends first, which can only happen when the file shrank after read(Path) validated ranges
  // against its length.
  private static void readFully(FileChannel channel, ByteBuffer buffer, long position, Path file)
      throws IOException {
    while (buffer.hasRemaining()) {
      final int read = channel.read(buffer, position + buffer.position());
      if (read < 0) {
        throw new IllegalStateException("File " + file + " ended at byte "
            + (position + buffer.position())
            + "; it has been truncated since its header was read");
      }
    }
  }

  /**
   * Finds the single 2-dimensional {@code F32} tensor in this file, the shape a static
   * embedding table's weight matrix takes (vocabulary size by hidden dimension). Deliberately
   * strict rather than guessing a name convention: distillation tools do not agree on one, and a
   * wrong guess would silently load the wrong tensor.
   *
   * @return The name of the single 2-D F32 tensor.
   * @throws IllegalArgumentException Thrown if the file has zero or more than one 2-D F32
   *     tensor; the message lists every candidate so the caller can pick explicitly with
   *     {@link #readFloat32(String)}.
   */
  public String singleMatrixTensorName() {
    String found = null;
    for (final TensorInfo info : tensorsByName.values()) {
      if ("F32".equals(info.dtype()) && info.shape().length == 2) {
        if (found != null) {
          throw new IllegalArgumentException(
              "More than one 2-D F32 tensor in this file; specify the name explicitly. "
                  + "Candidates: " + tensorsByName.keySet());
        }
        found = info.name();
      }
    }
    if (found == null) {
      throw new IllegalArgumentException(
          "No 2-D F32 tensor in this file. Available tensors: " + tensorsByName.keySet());
    }
    return found;
  }

  /** {@return the file's {@code __metadata__} string map, empty when the header has none} */
  public Map<String, String> metadata() {
    return metadata;
  }

  /** {@return the total number of tensors declared in this file} */
  public int size() {
    return tensorsByName.size();
  }
}
