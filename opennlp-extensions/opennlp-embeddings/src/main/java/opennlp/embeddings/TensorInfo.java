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

import java.util.Arrays;


/**
 * Header metadata for one tensor in a safetensors file, as declared by the file's own JSON
 * header. Carries no data; {@link SafetensorsFile#readFloat32(String)} resolves the bytes.
 *
 * @param name             The tensor's name, the key it was declared under. Never {@code null}.
 * @param dtype            The declared element type (e.g. {@code "F32"}, {@code "F16"},
 *                         {@code "I64"}), exactly as written in the header. Never {@code null}.
 * @param shape            The tensor's dimensions, outermost first. Never {@code null}; empty
 *                         for a scalar.
 * @param dataOffsetBegin  Start byte offset into the file's data section (relative to the end
 *                         of the header, not the start of the file).
 * @param dataOffsetEnd    End byte offset (exclusive) into the data section.
 */
public record TensorInfo(String name, String dtype, int[] shape, long dataOffsetBegin,
                          long dataOffsetEnd) {

  /**
   * Creates the metadata, copying {@code shape} so later mutation of the caller's array cannot
   * corrupt the validated state.
   *
   * @throws IllegalArgumentException Thrown if {@code name}, {@code dtype}, or {@code shape} is
   *     {@code null}.
   */
  public TensorInfo {
    if (name == null) {
      throw new IllegalArgumentException("Name must not be null");
    }
    if (dtype == null) {
      throw new IllegalArgumentException("Dtype must not be null");
    }
    if (shape == null) {
      throw new IllegalArgumentException("Shape must not be null");
    }
    shape = shape.clone();
  }

  /**
   * @return The tensor's dimensions, outermost first, as a copy; mutating it does not affect
   *     this record.
   */
  @Override
  public int[] shape() {
    return shape.clone();
  }

  /**
   * @return The number of elements the tensor holds, the product of {@link #shape()}.
   * @throws IllegalArgumentException Thrown if the product overflows a {@code long}, which only
   *     a crafted header can produce.
   */
  public long elementCount() {
    long count = 1;
    for (int dimension : shape) {
      try {
        count = Math.multiplyExact(count, dimension);
      } catch (ArithmeticException e) {
        throw new IllegalArgumentException("Tensor '" + name + "' declares a shape "
            + Arrays.toString(shape) + " whose element count overflows a long", e);
      }
    }
    return count;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object other) {
    return other instanceof TensorInfo that
        && name.equals(that.name) && dtype.equals(that.dtype)
        && Arrays.equals(shape, that.shape)
        && dataOffsetBegin == that.dataOffsetBegin && dataOffsetEnd == that.dataOffsetEnd;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + dtype.hashCode();
    result = 31 * result + Arrays.hashCode(shape);
    result = 31 * result + Long.hashCode(dataOffsetBegin);
    result = 31 * result + Long.hashCode(dataOffsetEnd);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "TensorInfo[name=" + name + ", dtype=" + dtype + ", shape=" + Arrays.toString(shape)
        + ", dataOffsetBegin=" + dataOffsetBegin + ", dataOffsetEnd=" + dataOffsetEnd + "]";
  }
}
