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
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Reads a CFSA2 finite-state automaton and enumerates the byte sequences it accepts.
 *
 * <p>CFSA2 is the compact automaton format defined by the morfologik project (BSD); the
 * {@code .dict} files distributed for many languages are CFSA2 automata paired with a plain
 * {@code .info} metadata file. This is a clean-room reader written from the published format,
 * with no dependency on that library. It exposes the raw accepted byte sequences; interpreting
 * them as morphological entries (surface form, separator, encoded base form, separator, tag) is
 * left to the caller, which also owns the character encoding declared by the dictionary.</p>
 *
 * <p>Thread safety is implementation specific; a constructed instance holds only immutable state
 * and its {@link #forEachSequence(Consumer)} may be called concurrently.</p>
 */
public final class CFSA2Reader implements FsaSequenceReader {

  private static final byte VERSION = (byte) 0xc6;

  private static final int FLAG_NUMBERS = 0x0100;

  private static final int BIT_TARGET_NEXT = 0x80;
  private static final int BIT_LAST_ARC = 0x40;
  private static final int BIT_FINAL_ARC = 0x20;
  private static final int LABEL_INDEX_MASK = 0x1f;

  private static final int HEADER_SIZE = 8;
  private static final int TERMINAL_NODE = 0;
  private static final int NO_ARC = 0;

  /** Guards against runaway recursion on a malformed automaton. */
  private static final int MAX_SEQUENCE_LENGTH = 8192;

  private final byte[] arcs;
  private final byte[] labelMapping;
  private final boolean hasNumbers;
  private final int rootNode;

  private CFSA2Reader(byte[] arcs, byte[] labelMapping, boolean hasNumbers) {
    this.arcs = arcs;
    this.labelMapping = labelMapping;
    this.hasNumbers = hasNumbers;
    this.rootNode = destinationNode(firstArc(TERMINAL_NODE));
  }

  /**
   * Reads a CFSA2 automaton from a stream.
   *
   * @param in The automaton bytes, referenced by an open {@link InputStream}. Must not be
   *           {@code null}.
   * @return A reader over the automaton.
   * @throws IllegalArgumentException if {@code in} is {@code null}.
   * @throws IOException Thrown on IO errors, or if the stream is not a CFSA2 automaton.
   */
  public static CFSA2Reader read(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    return fromBytes(in.readAllBytes());
  }

  static CFSA2Reader fromBytes(byte[] bytes) throws IOException {
    if (bytes.length < HEADER_SIZE
        || bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1]
        || bytes[2] != MAGIC[2] || bytes[3] != MAGIC[3]) {
      throw new IOException("not an FSA automaton: bad magic header");
    }
    if (bytes[4] != VERSION) {
      throw new IOException("unsupported FSA version 0x"
          + Integer.toHexString(bytes[4] & 0xff) + "; only CFSA2 (0xc6) is read");
    }
    final int flags = ((bytes[5] & 0xff) << 8) | (bytes[6] & 0xff);
    final int labelMappingSize = bytes[7] & 0xff;
    final int arcsStart = HEADER_SIZE + labelMappingSize;
    if (arcsStart > bytes.length) {
      throw new IOException("truncated CFSA2 header: label table runs past end of data");
    }
    final byte[] labelMapping = Arrays.copyOfRange(bytes, HEADER_SIZE, arcsStart);
    final byte[] arcs = Arrays.copyOfRange(bytes, arcsStart, bytes.length);
    return new CFSA2Reader(arcs, labelMapping, (flags & FLAG_NUMBERS) != 0);
  }

  /**
   * Passes every byte sequence the automaton accepts to {@code action}, in stored (lexicographic)
   * order. Each sequence is a fresh array owned by the callee.
   *
   * @param action The action to run for each accepted sequence. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code action} is {@code null}.
   * @throws IllegalStateException if a path exceeds {@value #MAX_SEQUENCE_LENGTH} bytes, which
   *                               indicates a malformed automaton.
   */
  @Override
  public void forEachSequence(Consumer<byte[]> action) {
    if (action == null) {
      throw new IllegalArgumentException("action must not be null");
    }
    enumerate(rootNode, new GrowableByteSequence(), action);
  }

  private void enumerate(int node, GrowableByteSequence path, Consumer<byte[]> action) {
    if (path.length() > MAX_SEQUENCE_LENGTH) {
      throw new IllegalStateException(
          "CFSA2 sequence exceeds " + MAX_SEQUENCE_LENGTH + " bytes; automaton may be malformed");
    }
    for (int arc = firstArc(node); arc != NO_ARC; arc = nextArc(arc)) {
      path.push(arcLabel(arc));
      if ((arcs[arc] & BIT_FINAL_ARC) != 0) {
        action.accept(path.toByteArray());
      }
      final int destination = destinationNode(arc);
      if (destination != TERMINAL_NODE) {
        enumerate(destination, path, action);
      }
      path.pop();
    }
  }

  private int firstArc(int node) {
    return hasNumbers ? skipVInt(node) : node;
  }

  private int nextArc(int arc) {
    return (arcs[arc] & BIT_LAST_ARC) != 0 ? NO_ARC : skipArc(arc);
  }

  private byte arcLabel(int arc) {
    final int index = arcs[arc] & LABEL_INDEX_MASK;
    return index > 0 ? labelMapping[index] : arcs[arc + 1];
  }

  private int destinationNode(int arc) {
    if ((arcs[arc] & BIT_TARGET_NEXT) != 0) {
      int last = arc;
      while ((arcs[last] & BIT_LAST_ARC) == 0) {
        last = skipArc(last);
      }
      return skipArc(last);
    }
    return readVInt(arc + ((arcs[arc] & LABEL_INDEX_MASK) == 0 ? 2 : 1));
  }

  private int skipArc(int offset) {
    final int flag = arcs[offset++];
    if ((flag & LABEL_INDEX_MASK) == 0) {
      offset++;
    }
    if ((flag & BIT_TARGET_NEXT) == 0) {
      offset = skipVInt(offset);
    }
    return offset;
  }

  private int readVInt(int offset) {
    byte b = arcs[offset];
    int value = b & 0x7f;
    for (int shift = 7; b < 0; shift += 7) {
      b = arcs[++offset];
      value |= (b & 0x7f) << shift;
    }
    return value;
  }

  private int skipVInt(int offset) {
    while (arcs[offset] < 0) {
      offset++;
    }
    return offset + 1;
  }
}
