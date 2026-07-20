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
 * Reads an FSA5 finite-state automaton and enumerates the byte sequences it accepts.
 *
 * <p>FSA5 is the older of the two morfologik automaton formats (the newer being
 * {@link CFSA2Reader}); its arcs use a fixed-width goto address rather than a variable-length one.
 * This is a clean-room reader written from the published format, with no dependency on that
 * library. Interpreting the accepted sequences as morphological entries is left to the caller.</p>
 *
 * <p>Thread safety is implementation specific; a constructed instance holds only immutable state
 * and its {@link #forEachSequence(Consumer)} may be called concurrently.</p>
 */
public final class FSA5Reader implements FsaSequenceReader {

  private static final byte VERSION = (byte) 0x05;

  private static final int BIT_FINAL_ARC = 0x01;
  private static final int BIT_LAST_ARC = 0x02;
  private static final int BIT_TARGET_NEXT = 0x04;
  private static final int FLAGS_MASK = 0x07;

  /** Offset of the flags/goto field within an arc; the label occupies the byte before it. */
  private static final int ADDRESS_OFFSET = 1;
  private static final int HEADER_SIZE = 8;
  private static final int TERMINAL_NODE = 0;
  private static final int NO_ARC = 0;

  private static final int MAX_SEQUENCE_LENGTH = 8192;

  private final byte[] arcs;
  private final int gtl;
  private final int nodeDataLength;
  private final int rootNode;

  private FSA5Reader(byte[] arcs, int gtl, int nodeDataLength) {
    this.arcs = arcs;
    this.gtl = gtl;
    this.nodeDataLength = nodeDataLength;
    // FSA5 keeps a dummy node ahead of the epsilon node: skip the dummy's arc to reach the
    // epsilon node, then the root is its single arc's destination.
    final int epsilonNode = skipArc(firstArc(TERMINAL_NODE));
    this.rootNode = destinationNode(firstArc(epsilonNode));
  }

  /**
   * Reads an FSA5 automaton from a stream.
   *
   * @param in The automaton bytes, referenced by an open {@link InputStream}. Must not be
   *           {@code null}.
   * @return A reader over the automaton.
   * @throws IllegalArgumentException if {@code in} is {@code null}.
   * @throws IOException Thrown on IO errors, or if the stream is not an FSA5 automaton.
   */
  public static FSA5Reader read(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    return fromBytes(in.readAllBytes());
  }

  static FSA5Reader fromBytes(byte[] bytes) throws IOException {
    if (bytes.length < HEADER_SIZE
        || bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1]
        || bytes[2] != MAGIC[2] || bytes[3] != MAGIC[3]) {
      throw new IOException("not an FSA automaton: bad magic header");
    }
    if (bytes[4] != VERSION) {
      throw new IOException("unsupported FSA version 0x"
          + Integer.toHexString(bytes[4] & 0xff) + "; only FSA5 (0x05) is read here");
    }
    final int hgtl = bytes[7] & 0xff;
    final int gtl = hgtl & 0x0f;
    final int nodeDataLength = (hgtl >>> 4) & 0x0f;
    if (gtl < 1) {
      throw new IOException("invalid FSA5 goto length: " + gtl);
    }
    final byte[] arcs = Arrays.copyOfRange(bytes, HEADER_SIZE, bytes.length);
    return new FSA5Reader(arcs, gtl, nodeDataLength);
  }

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
          "FSA5 sequence exceeds " + MAX_SEQUENCE_LENGTH + " bytes; automaton may be malformed");
    }
    for (int arc = firstArc(node); arc != NO_ARC; arc = nextArc(arc)) {
      path.push(arcs[arc]);
      if ((arcs[arc + ADDRESS_OFFSET] & BIT_FINAL_ARC) != 0) {
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
    return nodeDataLength + node;
  }

  private int nextArc(int arc) {
    return (arcs[arc + ADDRESS_OFFSET] & BIT_LAST_ARC) != 0 ? NO_ARC : skipArc(arc);
  }

  private int destinationNode(int arc) {
    if ((arcs[arc + ADDRESS_OFFSET] & BIT_TARGET_NEXT) != 0) {
      return skipArc(arc);
    }
    int value = 0;
    for (int i = gtl - 1; i >= 0; i--) {
      value = (value << 8) | (arcs[arc + ADDRESS_OFFSET + i] & 0xff);
    }
    return value >>> 3;
  }

  private int skipArc(int arc) {
    return (arcs[arc + ADDRESS_OFFSET] & BIT_TARGET_NEXT) != 0
        ? arc + ADDRESS_OFFSET + 1
        : arc + ADDRESS_OFFSET + gtl;
  }
}
