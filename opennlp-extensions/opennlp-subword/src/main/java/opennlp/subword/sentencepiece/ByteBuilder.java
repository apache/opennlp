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

/** A growable byte buffer supporting append, truncate, and suffix comparison. */
final class ByteBuilder {

  /** The smallest backing array, so tiny requested capacities still grow geometrically. */
  private static final int MIN_CAPACITY = 16;

  private byte[] data;
  private int length;

  /**
   * Instantiates the buffer.
   *
   * @param capacity The initial capacity hint.
   */
  ByteBuilder(int capacity) {
    data = new byte[Math.max(capacity, MIN_CAPACITY)];
  }

  /**
   * Appends one byte.
   *
   * @param b The byte to append.
   */
  void append(byte b) {
    if (length == data.length) {
      data = Arrays.copyOf(data, grownLength());
    }
    data[length++] = b;
  }

  /**
   * Appends a run of bytes.
   *
   * @param source The source array.
   * @param from   The inclusive start offset in {@code source}.
   * @param count  The number of bytes to append.
   */
  void append(byte[] source, int from, int count) {
    while (length + count > data.length) {
      data = Arrays.copyOf(data, grownLength());
    }
    System.arraycopy(source, from, data, length, count);
    length += count;
  }

  /** {@return the next backing-array length under the 1.5x growth policy} */
  private int grownLength() {
    return data.length + (data.length >> 1);
  }

  /** {@return the number of valid bytes} */
  int length() {
    return length;
  }

  /**
   * Shrinks the valid length.
   *
   * @param newLength The new length, not negative and not greater than the current length.
   * @throws IllegalArgumentException Thrown if {@code newLength} is negative or greater than the
   *     current length.
   */
  void truncate(int newLength) {
    if (newLength < 0 || newLength > length) {
      throw new IllegalArgumentException(
          "The new length " + newLength + " is outside [0, " + length + "].");
    }
    length = newLength;
  }

  /**
   * Tests whether the valid bytes end with the given suffix.
   *
   * @param suffix The suffix to test.
   * @return {@code true} when the buffer ends with {@code suffix}.
   */
  boolean endsWith(byte[] suffix) {
    if (length < suffix.length) {
      return false;
    }
    return Arrays.equals(data, length - suffix.length, length, suffix, 0, suffix.length);
  }

  /** {@return a trimmed copy of the valid bytes} */
  byte[] toArray() {
    return Arrays.copyOf(data, length);
  }

  /** {@return the backing array, valid up to {@link #length()}} */
  byte[] array() {
    return data;
  }
}
