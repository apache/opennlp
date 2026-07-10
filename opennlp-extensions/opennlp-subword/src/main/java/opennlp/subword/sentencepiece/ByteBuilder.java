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

  private byte[] data;
  private int length;

  ByteBuilder(int capacity) {
    data = new byte[Math.max(capacity, 16)];
  }

  void append(byte b) {
    if (length == data.length) {
      data = Arrays.copyOf(data, data.length + (data.length >> 1));
    }
    data[length++] = b;
  }

  void append(byte[] source, int from, int count) {
    while (length + count > data.length) {
      data = Arrays.copyOf(data, data.length + (data.length >> 1));
    }
    System.arraycopy(source, from, data, length, count);
    length += count;
  }

  int length() {
    return length;
  }

  void truncate(int newLength) {
    length = newLength;
  }

  boolean endsWith(byte[] suffix) {
    if (length < suffix.length) {
      return false;
    }
    return Arrays.equals(data, length - suffix.length, length, suffix, 0, suffix.length);
  }

  byte[] toArray() {
    return Arrays.copyOf(data, length);
  }
}
