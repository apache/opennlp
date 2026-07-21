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

import opennlp.tools.util.InvalidFormatException;

/**
 * Reads the binary {@code ModelProto} serialization of a SentencePiece {@code .model} file.
 *
 * <p>The format is the standard protocol-buffer wire encoding of one flat message
 * ({@code sentencepiece_model.proto}, Apache License 2.0). This reader walks the tag stream
 * directly and keeps only the fields inference needs: the pieces with scores and types, the
 * normalizer spec, the trainer-spec fields that change runtime behavior, and the embedded
 * self-test samples. Unknown fields are skipped, and malformed input fails loudly.</p>
 *
 * @see <a href=
 *     "https://github.com/google/sentencepiece/blob/master/src/sentencepiece_model.proto">
 *     sentencepiece_model.proto</a>
 */
final class ModelProtoReader {

  // Wire types of the protocol-buffer encoding.
  private static final int WIRE_VARINT = 0;
  private static final int WIRE_FIXED64 = 1;
  private static final int WIRE_LEN = 2;
  private static final int WIRE_FIXED32 = 5;

  // Field numbers of the ModelProto message in sentencepiece_model.proto.
  private static final int FIELD_MODEL_PIECES = 1;
  private static final int FIELD_MODEL_TRAINER_SPEC = 2;
  private static final int FIELD_MODEL_NORMALIZER_SPEC = 3;
  private static final int FIELD_MODEL_SELF_TEST_DATA = 4;

  // Field numbers of the ModelProto.SentencePiece sub-message.
  private static final int FIELD_PIECE_PIECE = 1;
  private static final int FIELD_PIECE_SCORE = 2;
  private static final int FIELD_PIECE_TYPE = 3;

  // Field numbers of the TrainerSpec sub-message.
  private static final int FIELD_TRAINER_MODEL_TYPE = 3;
  private static final int FIELD_TRAINER_TREAT_WHITESPACE_AS_SUFFIX = 24;
  private static final int FIELD_TRAINER_BYTE_FALLBACK = 35;
  private static final int FIELD_TRAINER_UNK_ID = 40;

  // Field numbers of the NormalizerSpec sub-message.
  private static final int FIELD_NORMALIZER_PRECOMPILED_CHARSMAP = 2;
  private static final int FIELD_NORMALIZER_ADD_DUMMY_PREFIX = 3;
  private static final int FIELD_NORMALIZER_REMOVE_EXTRA_WHITESPACES = 4;
  private static final int FIELD_NORMALIZER_ESCAPE_WHITESPACES = 5;

  // Field numbers of the SelfTestData sub-message and its Sample entries.
  private static final int FIELD_SELF_TEST_SAMPLES = 1;
  private static final int FIELD_SAMPLE_INPUT = 1;
  private static final int FIELD_SAMPLE_EXPECTED = 2;

  private final byte[] data;
  private int pos;

  /**
   * Prepares a reader positioned at the start of the given bytes; {@link #read(byte[])} drives
   * the actual parse.
   *
   * @param data The raw bytes of a {@code .model} file.
   */
  private ModelProtoReader(byte[] data) {
    this.data = data;
  }

  /**
   * Parses a serialized {@code ModelProto}.
   *
   * @param data The raw bytes of a {@code .model} file; must not be null.
   * @return The parsed model description.
   * @throws IllegalArgumentException Thrown if {@code data} is null.
   * @throws InvalidFormatException Thrown if the bytes are not a well-formed model.
   */
  static RawModel read(byte[] data) throws InvalidFormatException {
    if (data == null) {
      throw new IllegalArgumentException("The model data must not be null.");
    }
    final ModelProtoReader reader = new ModelProtoReader(data);
    final RawModel model = new RawModel();
    while (reader.pos < data.length) {
      final long tag = reader.varint();
      switch (fieldOf(tag)) {
        case FIELD_MODEL_PIECES -> reader.piece(model, reader.lenPayload(tag));
        case FIELD_MODEL_TRAINER_SPEC -> reader.trainerSpec(model, reader.lenPayload(tag));
        case FIELD_MODEL_NORMALIZER_SPEC -> reader.normalizerSpec(model, reader.lenPayload(tag));
        case FIELD_MODEL_SELF_TEST_DATA -> reader.selfTestData(model, reader.lenPayload(tag));
        default -> reader.skip(tag);
      }
    }
    if (model.pieces.isEmpty()) {
      throw new InvalidFormatException("The model defines no pieces.");
    }
    return model;
  }

  /**
   * Extracts the field number from a wire-format tag.
   *
   * @param tag The field tag.
   * @return The field number.
   */
  private static int fieldOf(long tag) {
    return (int) (tag >>> 3);
  }

  /**
   * Extracts the wire type from a wire-format tag.
   *
   * @param tag The field tag.
   * @return The wire type.
   */
  private static int wireTypeOf(long tag) {
    return (int) (tag & 7);
  }

  /**
   * Parses one {@code SentencePiece} sub-message and appends its piece, score, and type.
   *
   * @param model The model to append to.
   * @param end   The exclusive end offset of the sub-message payload.
   * @throws InvalidFormatException Thrown if the sub-message is malformed or defines an empty
   *     piece or a non-finite score.
   */
  private void piece(RawModel model, int end) throws InvalidFormatException {
    String piece = null;
    float score = 0;
    int type = RawModel.TYPE_NORMAL;
    while (pos < end) {
      final long tag = varint();
      switch (fieldOf(tag)) {
        case FIELD_PIECE_PIECE -> piece = utf8(lenPayload(tag));
        case FIELD_PIECE_SCORE -> score = fixed32Float(tag);
        case FIELD_PIECE_TYPE -> type = (int) varintOf(tag);
        default -> skip(tag);
      }
    }
    if (piece == null || piece.isEmpty()) {
      throw new InvalidFormatException(
          "The model contains an empty piece at index " + model.pieces.size() + ".");
    }
    if (Float.isNaN(score) || Float.isInfinite(score)) {
      throw new InvalidFormatException("The score of piece '" + piece + "' is not finite.");
    }
    model.pieces.add(piece);
    model.scores.add(score);
    model.types.add(type);
  }

  /**
   * Parses the {@code TrainerSpec} sub-message, keeping the fields that change runtime behavior.
   *
   * @param model The model to populate.
   * @param end   The exclusive end offset of the sub-message payload.
   * @throws InvalidFormatException Thrown if the sub-message is malformed.
   */
  private void trainerSpec(RawModel model, int end) throws InvalidFormatException {
    while (pos < end) {
      final long tag = varint();
      switch (fieldOf(tag)) {
        case FIELD_TRAINER_MODEL_TYPE -> model.modelType = (int) varintOf(tag);
        case FIELD_TRAINER_TREAT_WHITESPACE_AS_SUFFIX ->
            model.treatWhitespaceAsSuffix = varintOf(tag) != 0;
        case FIELD_TRAINER_BYTE_FALLBACK -> model.byteFallback = varintOf(tag) != 0;
        case FIELD_TRAINER_UNK_ID -> model.unkId = (int) varintOf(tag);
        default -> skip(tag);
      }
    }
  }

  /**
   * Parses the {@code NormalizerSpec} sub-message: the precompiled character map and the
   * whitespace-handling flags.
   *
   * @param model The model to populate.
   * @param end   The exclusive end offset of the sub-message payload.
   * @throws InvalidFormatException Thrown if the sub-message is malformed.
   */
  private void normalizerSpec(RawModel model, int end) throws InvalidFormatException {
    while (pos < end) {
      final long tag = varint();
      switch (fieldOf(tag)) {
        case FIELD_NORMALIZER_PRECOMPILED_CHARSMAP ->
            model.precompiledCharsMap = bytes(lenPayload(tag));
        case FIELD_NORMALIZER_ADD_DUMMY_PREFIX -> model.addDummyPrefix = varintOf(tag) != 0;
        case FIELD_NORMALIZER_REMOVE_EXTRA_WHITESPACES ->
            model.removeExtraWhitespaces = varintOf(tag) != 0;
        case FIELD_NORMALIZER_ESCAPE_WHITESPACES -> model.escapeWhitespaces = varintOf(tag) != 0;
        default -> skip(tag);
      }
    }
  }

  /**
   * Parses the {@code SelfTestData} sub-message, collecting the input and expected-segmentation
   * sample pairs.
   *
   * @param model The model to populate.
   * @param end   The exclusive end offset of the sub-message payload.
   * @throws InvalidFormatException Thrown if the sub-message is malformed.
   */
  private void selfTestData(RawModel model, int end) throws InvalidFormatException {
    while (pos < end) {
      final long tag = varint();
      if (fieldOf(tag) == FIELD_SELF_TEST_SAMPLES) {
        final int sampleEnd = lenPayload(tag);
        String input = null;
        String expected = null;
        while (pos < sampleEnd) {
          final long sampleTag = varint();
          switch (fieldOf(sampleTag)) {
            case FIELD_SAMPLE_INPUT -> input = utf8(lenPayload(sampleTag));
            case FIELD_SAMPLE_EXPECTED -> expected = utf8(lenPayload(sampleTag));
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

  /**
   * Reads the length prefix of a length-delimited field and returns the exclusive end offset of its
   * payload.
   *
   * @param tag The field tag, whose wire type must be length-delimited.
   * @return The exclusive end offset of the payload.
   * @throws InvalidFormatException Thrown if the wire type is wrong or the length runs past the
   *     input.
   */
  private int lenPayload(long tag) throws InvalidFormatException {
    if (wireTypeOf(tag) != WIRE_LEN) {
      throw malformed("field " + fieldOf(tag) + " is not length-delimited");
    }
    final long length = varint();
    if (length < 0 || pos + length > data.length) {
      throw malformed("length " + length + " exceeds the remaining input");
    }
    return pos + (int) length;
  }

  /**
   * Reads the varint value of a field after checking its wire type.
   *
   * @param tag The field tag, whose wire type must be varint.
   * @return The decoded value.
   * @throws InvalidFormatException Thrown if the wire type is wrong or the varint is malformed.
   */
  private long varintOf(long tag) throws InvalidFormatException {
    if (wireTypeOf(tag) != WIRE_VARINT) {
      throw malformed("field " + fieldOf(tag) + " is not a varint");
    }
    return varint();
  }

  /**
   * Reads the little-endian 32-bit float value of a field after checking its wire type.
   *
   * @param tag The field tag, whose wire type must be 32-bit.
   * @return The decoded float.
   * @throws InvalidFormatException Thrown if the wire type is wrong or the input is truncated.
   */
  private float fixed32Float(long tag) throws InvalidFormatException {
    if (wireTypeOf(tag) != WIRE_FIXED32) {
      throw malformed("field " + fieldOf(tag) + " is not a 32-bit value");
    }
    if (pos + 4 > data.length) {
      throw malformed("truncated 32-bit value");
    }
    final int bits = (data[pos] & 0xFF) | (data[pos + 1] & 0xFF) << 8
        | (data[pos + 2] & 0xFF) << 16 | (data[pos + 3] & 0xFF) << 24;
    pos += 4;
    return Float.intBitsToFloat(bits);
  }

  /**
   * Decodes the bytes from the current position up to {@code end} as UTF-8, advancing past them.
   *
   * @param end The exclusive end offset of the payload.
   * @return The decoded string.
   */
  private String utf8(int end) {
    final String s = new String(data, pos, end - pos, StandardCharsets.UTF_8);
    pos = end;
    return s;
  }

  /**
   * Copies the bytes from the current position up to {@code end}, advancing past them.
   *
   * @param end The exclusive end offset of the payload.
   * @return The copied bytes.
   */
  private byte[] bytes(int end) {
    final byte[] b = new byte[end - pos];
    System.arraycopy(data, pos, b, 0, b.length);
    pos = end;
    return b;
  }

  /**
   * Reads a base-128 varint from the current position, advancing past it.
   *
   * @return The decoded value.
   * @throws InvalidFormatException Thrown if the input ends mid-varint or the varint exceeds 64
   *     bits.
   */
  private long varint() throws InvalidFormatException {
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

  /**
   * Skips the value of an unrecognized field according to its wire type.
   *
   * @param tag The field tag.
   * @throws InvalidFormatException Thrown if the wire type is unsupported or the value runs past
   *     the input.
   */
  private void skip(long tag) throws InvalidFormatException {
    switch (wireTypeOf(tag)) {
      case WIRE_VARINT -> varint();
      case WIRE_FIXED64 -> advance(8);
      case WIRE_LEN -> pos = lenPayload(tag);
      case WIRE_FIXED32 -> advance(4);
      default -> throw malformed("unsupported wire type " + wireTypeOf(tag));
    }
  }

  /**
   * Advances the position by a fixed number of bytes.
   *
   * @param count The number of bytes to skip.
   * @throws InvalidFormatException Thrown if fewer than {@code count} bytes remain.
   */
  private void advance(int count) throws InvalidFormatException {
    if (pos + count > data.length) {
      throw malformed("truncated field");
    }
    pos += count;
  }

  /**
   * Creates the exception for malformed input, carrying the current byte position.
   *
   * @param detail A short description of what is malformed.
   * @return The exception to throw.
   */
  private InvalidFormatException malformed(String detail) {
    return new InvalidFormatException(
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
