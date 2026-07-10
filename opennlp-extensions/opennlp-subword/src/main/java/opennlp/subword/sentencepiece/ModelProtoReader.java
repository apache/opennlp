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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the binary {@code ModelProto} serialization of a SentencePiece {@code .model} file.
 *
 * <p>The format is standard protocol-buffer wire encoding of one flat message
 * ({@code sentencepiece_model.proto}, Apache License 2.0), so this reader walks the tag stream
 * directly and keeps only the fields inference needs: the pieces with scores and types, the
 * normalizer spec, the handful of trainer-spec fields that change runtime behavior, and the
 * embedded self-test samples. Unknown fields are skipped, malformed input fails loudly.</p>
 */
final class ModelProtoReader {

  // Wire types of the protocol-buffer encoding.
  private static final int WIRE_VARINT = 0;
  private static final int WIRE_FIXED64 = 1;
  private static final int WIRE_LEN = 2;
  private static final int WIRE_FIXED32 = 5;

  private final byte[] data;
  private int pos;

  private ModelProtoReader(byte[] data) {
    this.data = data;
  }

  /**
   * Parses a serialized {@code ModelProto}.
   *
   * @param data The raw bytes of a {@code .model} file; must not be null.
   * @return The parsed model description.
   * @throws IllegalArgumentException Thrown if the bytes are not a well-formed model.
   */
  static RawModel read(byte[] data) {
    if (data == null) {
      throw new IllegalArgumentException("The model data must not be null.");
    }
    final ModelProtoReader reader = new ModelProtoReader(data);
    final RawModel model = new RawModel();
    while (reader.pos < data.length) {
      final long tag = reader.varint();
      final int field = (int) (tag >>> 3);
      switch (field) {
        case 1 -> reader.piece(model, reader.lenPayload(tag));
        case 2 -> reader.trainerSpec(model, reader.lenPayload(tag));
        case 3 -> reader.normalizerSpec(model, reader.lenPayload(tag));
        case 4 -> reader.selfTestData(model, reader.lenPayload(tag));
        default -> reader.skip(tag);
      }
    }
    if (model.pieces.isEmpty()) {
      throw new IllegalArgumentException("The model defines no pieces.");
    }
    return model;
  }

  private void piece(RawModel model, int end) {
    String piece = null;
    float score = 0;
    int type = RawModel.TYPE_NORMAL;
    while (pos < end) {
      final long tag = varint();
      switch ((int) (tag >>> 3)) {
        case 1 -> piece = utf8(lenPayload(tag));
        case 2 -> score = fixed32Float(tag);
        case 3 -> type = (int) varintOf(tag);
        default -> skip(tag);
      }
    }
    if (piece == null || piece.isEmpty()) {
      throw new IllegalArgumentException(
          "The model contains an empty piece at index " + model.pieces.size() + ".");
    }
    if (Float.isNaN(score) || Float.isInfinite(score)) {
      throw new IllegalArgumentException("The score of piece '" + piece + "' is not finite.");
    }
    model.pieces.add(piece);
    model.scores.add(score);
    model.types.add(type);
  }

  private void trainerSpec(RawModel model, int end) {
    while (pos < end) {
      final long tag = varint();
      switch ((int) (tag >>> 3)) {
        case 3 -> model.modelType = (int) varintOf(tag);
        case 24 -> model.treatWhitespaceAsSuffix = varintOf(tag) != 0;
        case 35 -> model.byteFallback = varintOf(tag) != 0;
        case 40 -> model.unkId = (int) varintOf(tag);
        default -> skip(tag);
      }
    }
  }

  private void normalizerSpec(RawModel model, int end) {
    while (pos < end) {
      final long tag = varint();
      switch ((int) (tag >>> 3)) {
        case 2 -> model.precompiledCharsMap = bytes(lenPayload(tag));
        case 3 -> model.addDummyPrefix = varintOf(tag) != 0;
        case 4 -> model.removeExtraWhitespaces = varintOf(tag) != 0;
        case 5 -> model.escapeWhitespaces = varintOf(tag) != 0;
        default -> skip(tag);
      }
    }
  }

  private void selfTestData(RawModel model, int end) {
    while (pos < end) {
      final long tag = varint();
      if ((int) (tag >>> 3) == 1) {
        final int sampleEnd = lenPayload(tag);
        String input = null;
        String expected = null;
        while (pos < sampleEnd) {
          final long sampleTag = varint();
          switch ((int) (sampleTag >>> 3)) {
            case 1 -> input = utf8(lenPayload(sampleTag));
            case 2 -> expected = utf8(lenPayload(sampleTag));
            default -> skip(sampleTag);
          }
        }
        if (input != null && expected != null) {
          model.selfTestInputs.add(input);
          model.selfTestExpected.add(expected);
        }
      } else {
        skip(tag);
      }
    }
  }

  // Returns the exclusive end offset of a length-delimited payload, verifying the wire type.
  private int lenPayload(long tag) {
    if ((tag & 7) != WIRE_LEN) {
      throw malformed("field " + (tag >>> 3) + " is not length-delimited");
    }
    final long length = varint();
    if (length < 0 || pos + length > data.length) {
      throw malformed("length " + length + " exceeds the remaining input");
    }
    return pos + (int) length;
  }

  private long varintOf(long tag) {
    if ((tag & 7) != WIRE_VARINT) {
      throw malformed("field " + (tag >>> 3) + " is not a varint");
    }
    return varint();
  }

  private float fixed32Float(long tag) {
    if ((tag & 7) != WIRE_FIXED32) {
      throw malformed("field " + (tag >>> 3) + " is not a 32-bit value");
    }
    if (pos + 4 > data.length) {
      throw malformed("truncated 32-bit value");
    }
    final int bits = (data[pos] & 0xFF) | (data[pos + 1] & 0xFF) << 8
        | (data[pos + 2] & 0xFF) << 16 | (data[pos + 3] & 0xFF) << 24;
    pos += 4;
    return Float.intBitsToFloat(bits);
  }

  private String utf8(int end) {
    final String s = new String(data, pos, end - pos, StandardCharsets.UTF_8);
    pos = end;
    return s;
  }

  private byte[] bytes(int end) {
    final byte[] b = new byte[end - pos];
    System.arraycopy(data, pos, b, 0, b.length);
    pos = end;
    return b;
  }

  private long varint() {
    long value = 0;
    for (int shift = 0; shift < 64; shift += 7) {
      if (pos >= data.length) {
        throw malformed("truncated varint");
      }
      final byte b = data[pos++];
      value |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return value;
      }
    }
    throw malformed("varint exceeds 64 bits");
  }

  private void skip(long tag) {
    switch ((int) (tag & 7)) {
      case WIRE_VARINT -> varint();
      case WIRE_FIXED64 -> advance(8);
      case WIRE_LEN -> pos = lenPayload(tag);
      case WIRE_FIXED32 -> advance(4);
      default -> throw malformed("unsupported wire type " + (tag & 7));
    }
  }

  private void advance(int count) {
    if (pos + count > data.length) {
      throw malformed("truncated field");
    }
    pos += count;
  }

  private IllegalArgumentException malformed(String detail) {
    return new IllegalArgumentException(
        "The model data is malformed at byte " + pos + ": " + detail + ".");
  }

  /** The fields of a {@code ModelProto} that inference needs, with the proto's defaults. */
  static final class RawModel {

    static final int TYPE_NORMAL = 1;
    static final int TYPE_UNKNOWN = 2;
    static final int TYPE_CONTROL = 3;
    static final int TYPE_USER_DEFINED = 4;
    static final int TYPE_UNUSED = 5;
    static final int TYPE_BYTE = 6;

    static final int MODEL_TYPE_UNIGRAM = 1;
    static final int MODEL_TYPE_BPE = 2;

    final List<String> pieces = new ArrayList<>();
    final List<Float> scores = new ArrayList<>();
    final List<Integer> types = new ArrayList<>();

    int modelType = MODEL_TYPE_UNIGRAM;
    boolean byteFallback = false;
    boolean treatWhitespaceAsSuffix = false;
    int unkId = 0;

    byte[] precompiledCharsMap = new byte[0];
    boolean addDummyPrefix = true;
    boolean removeExtraWhitespaces = true;
    boolean escapeWhitespaces = true;

    final List<String> selfTestInputs = new ArrayList<>();
    final List<String> selfTestExpected = new ArrayList<>();
  }
}
