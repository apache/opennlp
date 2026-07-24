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

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Enumerates the byte sequences accepted by a finite-state automaton, independent of which
 * on-disk format encodes it. The two morfologik automaton formats are read: FSA5 (version
 * {@code 0x05}) and CFSA2 (version {@code 0xc6}).
 *
 * <p>Thread safety is implementation specific.</p>
 */
public interface FsaSequenceReader {

  /** ASCII {@code \fsa}, the shared magic header of both automaton formats. */
  byte[] MAGIC = {0x5c, 0x66, 0x73, 0x61};

  /** Version byte of the FSA5 format. */
  int VERSION_FSA5 = 0x05;

  /** Version byte of the CFSA2 format. */
  int VERSION_CFSA2 = 0xc6;

  /**
   * Passes every accepted byte sequence to {@code action}, in the automaton's stored order. Each
   * sequence is a fresh array owned by the callee.
   *
   * @param action The action to run for each accepted sequence. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code action} is {@code null}.
   */
  void forEachSequence(Consumer<byte[]> action);

  /**
   * Reads an FSA5 or CFSA2 automaton, dispatching on the version byte.
   *
   * @param in The automaton bytes, referenced by an open {@link InputStream}. Must not be
   *           {@code null}.
   * @return A reader over the automaton.
   * @throws IllegalArgumentException if {@code in} is {@code null}.
   * @throws IOException Thrown on IO errors, or if the stream is not a supported FSA automaton.
   */
  static FsaSequenceReader read(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    final byte[] bytes = in.readAllBytes();
    if (bytes.length < MAGIC.length + 1
        || bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1]
        || bytes[2] != MAGIC[2] || bytes[3] != MAGIC[3]) {
      throw new IOException("not an FSA automaton: bad magic header");
    }
    final int version = bytes[4] & 0xff;
    switch (version) {
      case VERSION_CFSA2:
        return CFSA2Reader.fromBytes(bytes);
      case VERSION_FSA5:
        return FSA5Reader.fromBytes(bytes);
      default:
        throw new IOException("unsupported FSA version 0x" + Integer.toHexString(version)
            + "; only FSA5 (0x05) and CFSA2 (0xc6) are read");
    }
  }
}
