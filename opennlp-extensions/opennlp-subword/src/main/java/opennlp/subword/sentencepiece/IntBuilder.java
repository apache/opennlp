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
package opennlp.subword.sentencepiece;

import java.util.Arrays;

/** A growable int buffer supporting append, indexed read, and truncate. */
final class IntBuilder {

  private int[] data;
  private int length;

  /**
   * Instantiates the buffer.
   *
   * @param capacity The initial capacity hint.
   */
  IntBuilder(int capacity) {
    data = new int[Math.max(capacity, 16)];
  }

  /**
   * Appends one value.
   *
   * @param value The value to append.
   */
  void append(int value) {
    if (length == data.length) {
      data = Arrays.copyOf(data, data.length + (data.length >> 1));
    }
    data[length++] = value;
  }

  /**
   * Reads a value by index.
   *
   * @param index An index in {@code [0, length())}.
   * @return The value at {@code index}.
   * @throws IndexOutOfBoundsException Thrown if {@code index} is out of range.
   */
  int get(int index) {
    if (index >= length) {
      throw new IndexOutOfBoundsException("index " + index + " is outside [0, " + length + ")");
    }
    return data[index];
  }

  /** {@return the number of valid values} */
  int length() {
    return length;
  }

  /**
   * Shrinks the valid length.
   *
   * @param newLength The new length, not greater than the current length.
   */
  void truncate(int newLength) {
    length = newLength;
  }

  /** {@return a trimmed copy of the valid values} */
  int[] toArray() {
    return Arrays.copyOf(data, length);
  }

  /** {@return the backing array, valid up to {@link #length()}} */
  int[] array() {
    return data;
  }
}
