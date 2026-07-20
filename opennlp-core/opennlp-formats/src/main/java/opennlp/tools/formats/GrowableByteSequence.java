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

package opennlp.tools.formats;

import java.util.Arrays;

/**
 * A growable byte buffer used as the path stack while walking a finite-state automaton. One
 * instance is reused for a whole traversal: labels are {@link #push(byte)}ed on the way down and
 * {@link #pop()}ped on the way back up, so only accepted sequences allocate, via
 * {@link #toByteArray()}. Not thread-safe; each traversal creates its own.
 */
final class GrowableByteSequence {

  private byte[] data = new byte[64];
  private int length;

  int length() {
    return length;
  }

  void push(byte value) {
    if (length == data.length) {
      data = Arrays.copyOf(data, data.length << 1);
    }
    data[length++] = value;
  }

  void pop() {
    length--;
  }

  byte[] toByteArray() {
    return Arrays.copyOf(data, length);
  }
}
