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
import java.nio.ShortBuffer;
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
 * range, followed by the raw tensor bytes. The floating-point decode path
 * {@link #readFloats(String)} supports the {@code F32}, {@code F16} (IEEE half) and {@code BF16}
 * (bfloat16) dtypes, widening the two 16-bit types to {@code float}.
 *
 * <p>Only the header is read eagerly; tensor data is streamed into a fresh array with positional
 * reads on request, so a decoded {@code float[]} is capped at {@link Integer#MAX_VALUE} - 8
 * elements. The file must stay in place and unchanged between {@link #read(Path)} and a later
 * {@link #readFloats(String)} call; a file truncated in between fails loud rather than returning
 * partial data.</p>
 *
 * <p>Instances are immutable and safe for concurrent use: every {@link #readFloats(String)}
 * call opens its own channel and decodes into a fresh array the caller owns.</p>
 */
@ThreadSafe
public final class SafetensorsFile {

  private static final int HEADER_LENGTH_PREFIX_BYTES = 8;

  /** The header's dtype marker for 32-bit IEEE floats. */
  private static final String DTYPE_F32 = "F32";

  /** The header's dtype marker for 16-bit IEEE half floats. */
  private static final String DTYPE_F16 = "F16";

  /** The header's dtype marker for 16-bit bfloat16 floats. */
  private static final String DTYPE_BF16 = "BF16";

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

  /** Holds the parsed header; built by {@link #read(Path)}. */
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
   * @throws IOException Thrown if reading the file fails.
   */
  public static SafetensorsFile read(Path file) throws IOException {
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
   * Decodes a floating-point tensor's data to {@code float[]}, streaming it from the file.
   * Accepts the {@code F32}, {@code F16} (IEEE half) and {@code BF16} (bfloat16) dtypes; the two
   * 16-bit types are widened to {@code float} as they are read. {@code F16} is Model2Vec's
   * default output dtype, so this is the common case for downloaded distilled tables.
   *
   * @param name The tensor's name. Must not be {@code null}.
   * @return The tensor's elements in row-major (shape outermost-first) order.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null}, not a tensor in
   *     this file, not a supported float dtype ({@code F32}, {@code F16}, {@code BF16}), or
   *     larger than a Java array can hold.
   * @throws IllegalStateException Thrown if the file has been truncated since
   *     {@link #read(Path)} validated the tensor's byte range.
   * @throws IOException Thrown if reading the file fails.
   */
  public float[] readFloats(String name) throws IOException {
    final TensorInfo info = tensorInfo(name);
    final int elementBytes = floatElementBytes(info.dtype(), name);
    final long elementCount = info.elementCount();
    if (elementCount < 0 || elementCount > MAX_ARRAY_LENGTH) {
      throw new IllegalArgumentException("Tensor '" + name + "' declares " + elementCount
          + " elements, more than a Java array can hold (" + MAX_ARRAY_LENGTH
          + "); decoding to a float[] is capped there");
    }
    final long byteLength = info.dataOffsetEnd() - info.dataOffsetBegin();
    if (byteLength != elementCount * elementBytes) {
      throw new IllegalArgumentException("Tensor '" + name + "' declares " + elementCount + " "
          + info.dtype() + " elements but its data range is " + byteLength + " bytes");
    }
    final float[] values = new float[(int) elementCount];
    final String dtype = info.dtype();
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      final ByteBuffer chunk = ByteBuffer.allocate((int) Math.min(READ_CHUNK_BYTES, byteLength))
          .order(ByteOrder.LITTLE_ENDIAN);
      long position = dataStart + info.dataOffsetBegin();
      int decoded = 0;
      while (decoded < values.length) {
        chunk.clear();
        final long remainingBytes = byteLength - (long) decoded * elementBytes;
        if (remainingBytes < chunk.capacity()) {
          chunk.limit((int) remainingBytes);
        }
        readFully(channel, chunk, position, file);
        chunk.flip();
        final int count = chunk.remaining() / elementBytes;
        decodeInto(chunk, dtype, values, decoded, count);
        decoded += count;
        position += (long) count * elementBytes;
      }
      return values;
    }
  }

  /**
   * Decodes an {@code F32} tensor, rejecting any other dtype. Use {@link #readFloats(String)} to
   * also accept {@code F16} and {@code BF16}.
   *
   * @param name The tensor's name. Must not be {@code null}.
   * @return The tensor's elements in row-major (shape outermost-first) order.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null}, not a tensor in
   *     this file, not declared with dtype {@code F32}, or larger than a Java array can hold.
   * @throws IllegalStateException Thrown if the file has been truncated since {@link #read(Path)}.
   * @throws IOException Thrown if reading the file fails.
   */
  public float[] readFloat32(String name) throws IOException {
    final TensorInfo info = tensorInfo(name);
    if (!DTYPE_F32.equals(info.dtype())) {
      throw new IllegalArgumentException(
          "Tensor '" + name + "' has dtype " + info.dtype() + ", not " + DTYPE_F32);
    }
    return readFloats(name);
  }

  /**
   * Widens one chunk of raw tensor bytes into the output array according to its dtype.
   *
   * @param chunk  The raw little-endian bytes, positioned at the first element to decode.
   * @param dtype  The tensor's dtype ({@code F32}, {@code F16}, or {@code BF16}).
   * @param out    The destination array.
   * @param offset The index in {@code out} to write the first decoded element to.
   * @param count  The number of elements to decode from {@code chunk}.
   */
  private static void decodeInto(ByteBuffer chunk, String dtype, float[] out, int offset,
                                 int count) {
    switch (dtype) {
      case DTYPE_F32 -> chunk.asFloatBuffer().get(out, offset, count);
      case DTYPE_F16 -> {
        final ShortBuffer shorts = chunk.asShortBuffer();
        for (int i = 0; i < count; i++) {
          out[offset + i] = Float.float16ToFloat(shorts.get());
        }
      }
      case DTYPE_BF16 -> {
        // bfloat16 is the high 16 bits of a float32: shift back up and reinterpret.
        final ShortBuffer shorts = chunk.asShortBuffer();
        for (int i = 0; i < count; i++) {
          out[offset + i] = Float.intBitsToFloat((shorts.get() & 0xFFFF) << 16);
        }
      }
      default -> throw new IllegalArgumentException("Unsupported float dtype: " + dtype);
    }
  }

  /**
   * {@return the number of bytes one element of {@code dtype} occupies}
   *
   * @param dtype      The tensor dtype.
   * @param tensorName The tensor's name, for the error message.
   * @throws IllegalArgumentException Thrown if {@code dtype} is not a supported float type.
   */
  private static int floatElementBytes(String dtype, String tensorName) {
    return switch (dtype) {
      case DTYPE_F32 -> Float.BYTES;
      case DTYPE_F16, DTYPE_BF16 -> Short.BYTES;
      default -> throw new IllegalArgumentException("Tensor '" + tensorName + "' has dtype "
          + dtype + ", not a supported float type (" + DTYPE_F32 + ", " + DTYPE_F16 + ", "
          + DTYPE_BF16 + ")");
    };
  }

  /** {@return whether {@code dtype} is a float type this reader decodes} */
  private static boolean isFloatDtype(String dtype) {
    return DTYPE_F32.equals(dtype) || DTYPE_F16.equals(dtype) || DTYPE_BF16.equals(dtype);
  }

  /**
   * Fills the buffer with bytes starting at the given file position.
   *
   * @param channel  The open channel to read from.
   * @param buffer   The buffer to fill.
   * @param position The starting file position.
   * @param file     The file, for error messages.
   * @throws IOException Thrown if reading fails.
   * @throws IllegalStateException Thrown if the file ends before the buffer is full, which can
   *     only happen when the file shrank after {@link #read(Path)} validated its ranges.
   */
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
   * Finds the single 2-dimensional floating-point tensor in this file (dtype {@code F32},
   * {@code F16}, or {@code BF16}), the shape a static embedding table's weight matrix takes
   * (vocabulary size by hidden dimension). Strict rather than guessing a name convention, so a
   * wrong guess cannot silently load the wrong tensor.
   *
   * @return The name of the single 2-D float tensor.
   * @throws IllegalArgumentException Thrown if the file has zero or more than one 2-D float
   *     tensor; the message lists every candidate so the caller can pick explicitly with
   *     {@link #readFloats(String)}.
   */
  public String singleMatrixTensorName() {
    String found = null;
    for (final TensorInfo info : tensorsByName.values()) {
      if (isFloatDtype(info.dtype()) && info.shape().length == 2) {
        if (found != null) {
          throw new IllegalArgumentException(
              "More than one 2-D float tensor in this file; specify the name explicitly. "
                  + "Candidates: " + tensorsByName.keySet());
        }
        found = info.name();
      }
    }
    if (found == null) {
      throw new IllegalArgumentException(
          "No 2-D float (F32/F16/BF16) tensor in this file. Available tensors: "
              + tensorsByName.keySet());
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
