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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * <p>The whole file is read into memory up front (matching the project's existing bundled-data
 * readers), which is appropriate for the small (tens of megabytes) tables this module targets.
 * Instances are immutable and safe for concurrent reads after construction; every
 * {@link #readFloat32(String)} call decodes into a fresh array the caller owns.</p>
 */
@ThreadSafe
public final class SafetensorsFile {

  private static final int HEADER_LENGTH_PREFIX_BYTES = 8;

  private final byte[] bytes;
  private final long dataStart;
  private final Map<String, TensorInfo> tensorsByName;
  private final Map<String, String> metadata;

  private SafetensorsFile(byte[] bytes, long dataStart, Map<String, TensorInfo> tensorsByName,
                           Map<String, String> metadata) {
    this.bytes = bytes;
    this.dataStart = dataStart;
    this.tensorsByName = tensorsByName;
    this.metadata = metadata;
  }

  /**
   * Reads a safetensors file.
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
    final byte[] bytes;
    try {
      bytes = Files.readAllBytes(file);
    }
    catch (IOException e) {
      throw new UncheckedIOException("Unable to read safetensors file " + file, e);
    }
    if (bytes.length < HEADER_LENGTH_PREFIX_BYTES) {
      throw new IllegalArgumentException(
          "File " + file + " is too short to be a safetensors file: " + bytes.length + " bytes");
    }
    final long headerLength = ByteBuffer.wrap(bytes, 0, HEADER_LENGTH_PREFIX_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN).getLong();
    final long dataStart = (long) HEADER_LENGTH_PREFIX_BYTES + headerLength;
    if (headerLength < 0 || dataStart > bytes.length) {
      throw new IllegalArgumentException("File " + file + " declares a header length of "
          + headerLength + ", which does not fit in a file of " + bytes.length + " bytes");
    }
    final String headerJson = new String(bytes, HEADER_LENGTH_PREFIX_BYTES, (int) headerLength,
        StandardCharsets.UTF_8);
    final SafetensorsHeaderParser.Result parsed = SafetensorsHeaderParser.parse(headerJson);
    final Map<String, TensorInfo> tensorsByName = new LinkedHashMap<>(parsed.tensors().size() * 2);
    for (final TensorInfo tensor : parsed.tensors()) {
      if (tensor.dataOffsetBegin() < 0 || tensor.dataOffsetEnd() < tensor.dataOffsetBegin()
          || dataStart + tensor.dataOffsetEnd() > bytes.length) {
        throw new IllegalArgumentException("File " + file + " tensor '" + tensor.name()
            + "' has a data range [" + tensor.dataOffsetBegin() + ", " + tensor.dataOffsetEnd()
            + ") that does not fit in the file");
      }
      if (tensorsByName.putIfAbsent(tensor.name(), tensor) != null) {
        throw new IllegalArgumentException(
            "File " + file + " declares tensor '" + tensor.name() + "' more than once");
      }
    }
    return new SafetensorsFile(bytes, dataStart, Collections.unmodifiableMap(tensorsByName),
        Collections.unmodifiableMap(parsed.metadata()));
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
   * Decodes a {@code F32} tensor's data.
   *
   * @param name The tensor's name. Must not be {@code null}.
   * @return The tensor's elements in row-major (shape outermost-first) order.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null}, not a tensor in
   *     this file, or not declared with dtype {@code F32}.
   */
  public float[] readFloat32(String name) {
    final TensorInfo info = tensorInfo(name);
    if (!"F32".equals(info.dtype())) {
      throw new IllegalArgumentException(
          "Tensor '" + name + "' has dtype " + info.dtype() + ", not F32");
    }
    final long elementCount = info.elementCount();
    final long byteLength = info.dataOffsetEnd() - info.dataOffsetBegin();
    if (byteLength != elementCount * 4L) {
      throw new IllegalArgumentException("Tensor '" + name + "' declares " + elementCount
          + " F32 elements but its data range is " + byteLength + " bytes");
    }
    final float[] values = new float[(int) elementCount];
    final ByteBuffer buffer = ByteBuffer.wrap(bytes,
        (int) (dataStart + info.dataOffsetBegin()), (int) byteLength)
        .order(ByteOrder.LITTLE_ENDIAN);
    buffer.asFloatBuffer().get(values);
    return values;
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
