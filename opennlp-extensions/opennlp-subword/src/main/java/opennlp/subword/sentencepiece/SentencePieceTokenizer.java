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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.tokenize.SubwordTokenizer;
import opennlp.tools.util.normalizer.AlignedText;
import opennlp.tools.util.normalizer.Alignment;
import opennlp.tools.util.normalizer.OffsetAwareNormalizer;

/**
 * A {@link SubwordTokenizer} over a trained SentencePiece {@code .model} file, implemented purely
 * in Java. The file carries the vocabulary with piece scores and types, the segmentation algorithm
 * (unigram language model or byte-pair encoding), and the text normalizer, all of which this class
 * runs.
 *
 * <p>Every piece carries the exact span of the caller's original text it came from, mapped back
 * through the model's own normalizer, which is also exposed through {@link OffsetAwareNormalizer}
 * for reuse outside tokenization.</p>
 *
 * <p>Instances are immutable after loading and safe for concurrent use by multiple threads.</p>
 */
public final class SentencePieceTokenizer implements SubwordTokenizer, OffsetAwareNormalizer {

  // Serializable through the OffsetAwareNormalizer contract.
  private static final long serialVersionUID = -7114394869301531147L;

  /** The segmentation algorithm a model was trained with. */
  public enum Algorithm {
    /** Unigram language model, decoded by best-path search. */
    UNIGRAM,
    /** Byte-pair encoding, decoded by greedy highest-score merging. */
    BPE
  }

  // Piece types of the model format.
  private static final int TYPE_NORMAL = ModelProtoReader.RawModel.TYPE_NORMAL;
  private static final int TYPE_UNKNOWN = ModelProtoReader.RawModel.TYPE_UNKNOWN;
  private static final int TYPE_CONTROL = ModelProtoReader.RawModel.TYPE_CONTROL;
  private static final int TYPE_USER_DEFINED = ModelProtoReader.RawModel.TYPE_USER_DEFINED;
  private static final int TYPE_UNUSED = ModelProtoReader.RawModel.TYPE_UNUSED;
  private static final int TYPE_BYTE = ModelProtoReader.RawModel.TYPE_BYTE;

  private static final int MAX_PIECE_LENGTH = 8000;

  private final Algorithm algorithm;
  private final String[] pieces;
  private final float[] scores;
  private final int[] types;
  private final int unkId;
  private final boolean byteFallback;
  private final Map<String, Integer> mainPieces;
  private final Map<String, Integer> reservedPieces;
  private final int[] byteToId;
  private final SentencePieceNormalizer normalizer;
  private final UnigramEncoder unigramEncoder;
  private final BpeEncoder bpeEncoder;
  private final List<String> selfTestInputs;
  private final List<String> selfTestExpected;

  private SentencePieceTokenizer(ModelProtoReader.RawModel model) {
    final int count = model.pieces.size();
    pieces = model.pieces.toArray(new String[0]);
    scores = new float[count];
    types = new int[count];
    for (int i = 0; i < count; i++) {
      scores[i] = model.scores.get(i);
      types[i] = model.types.get(i);
    }
    byteFallback = model.byteFallback;
    algorithm = switch (model.modelType) {
      case ModelProtoReader.RawModel.MODEL_TYPE_UNIGRAM -> Algorithm.UNIGRAM;
      case ModelProtoReader.RawModel.MODEL_TYPE_BPE -> Algorithm.BPE;
      default -> throw new IllegalArgumentException(
          "The model type " + model.modelType + " is not supported; only the unigram and BPE"
              + " algorithms are.");
    };

    // Splits the vocabulary the way the reference does: pieces of the normal, user-defined, and
    // unused types participate in segmentation, all others are reserved ids.
    mainPieces = new HashMap<>(count * 2);
    reservedPieces = new HashMap<>();
    final List<String> userDefined = new ArrayList<>();
    byteToId = new int[256];
    java.util.Arrays.fill(byteToId, -1);
    int foundUnkId = -1;
    float minScore = Float.MAX_VALUE;
    for (int i = 0; i < count; i++) {
      final String piece = pieces[i];
      if (piece.length() >= MAX_PIECE_LENGTH) {
        throw new IllegalArgumentException("The piece with id " + i + " is longer than "
            + MAX_PIECE_LENGTH + " characters.");
      }
      if (piece.indexOf(0) >= 0) {
        throw new IllegalArgumentException(
            "The piece with id " + i + " contains a null character.");
      }
      final boolean isMain =
          types[i] == TYPE_NORMAL || types[i] == TYPE_USER_DEFINED || types[i] == TYPE_UNUSED;
      final Map<String, Integer> target =
          isMain || algorithm == Algorithm.BPE ? mainPieces : reservedPieces;
      if (mainPieces.containsKey(piece) || reservedPieces.containsKey(piece)) {
        throw new IllegalArgumentException("The piece '" + piece + "' is defined more than once.");
      }
      target.put(piece, i);
      switch (types[i]) {
        case TYPE_NORMAL -> minScore = Math.min(minScore, scores[i]);
        case TYPE_USER_DEFINED -> userDefined.add(piece);
        case TYPE_UNKNOWN -> {
          if (foundUnkId >= 0) {
            throw new IllegalArgumentException("The model defines more than one unknown piece.");
          }
          foundUnkId = i;
        }
        case TYPE_BYTE -> {
          if (!byteFallback) {
            throw new IllegalArgumentException("The model defines the byte piece '" + piece
                + "' although byte fallback is disabled.");
          }
          final int b = parseBytePiece(piece);
          if (b < 0) {
            throw new IllegalArgumentException("The byte piece '" + piece + "' is invalid.");
          }
          byteToId[b] = i;
        }
        default -> {
          // CONTROL and UNUSED need no bookkeeping here.
        }
      }
    }
    if (foundUnkId < 0) {
      throw new IllegalArgumentException("The model defines no unknown piece.");
    }
    unkId = foundUnkId;
    if (byteFallback) {
      for (int b = 0; b < 256; b++) {
        if (byteToId[b] < 0) {
          throw new IllegalArgumentException("The model enables byte fallback but defines no"
              + " piece for byte " + b + ".");
        }
      }
    }

    final PieceTrie userDefinedMatcher = userDefined.isEmpty() ? null : trieOf(userDefined, id -> 0);

    normalizer = new SentencePieceNormalizer(model.precompiledCharsMap, model.addDummyPrefix,
        model.removeExtraWhitespaces, model.escapeWhitespaces, model.treatWhitespaceAsSuffix,
        userDefinedMatcher);

    final boolean[] unusedFlags = new boolean[count];
    final boolean[] userDefinedFlags = new boolean[count];
    final boolean[] reservedFlags = new boolean[count];
    for (int i = 0; i < count; i++) {
      unusedFlags[i] = types[i] == TYPE_UNUSED;
      userDefinedFlags[i] = types[i] == TYPE_USER_DEFINED;
      reservedFlags[i] = types[i] != TYPE_NORMAL && types[i] != TYPE_USER_DEFINED
          && types[i] != TYPE_UNUSED;
    }

    if (algorithm == Algorithm.UNIGRAM) {
      final List<String> mainList = new ArrayList<>(mainPieces.size());
      final List<Integer> mainIds = new ArrayList<>(mainPieces.size());
      for (int i = 0; i < count; i++) {
        if (!reservedFlags[i]) {
          mainList.add(pieces[i]);
          mainIds.add(i);
        }
      }
      final PieceTrie vocabulary = trieOf(mainList, mainIds::get);
      unigramEncoder = new UnigramEncoder(vocabulary, scores, unusedFlags, userDefinedFlags,
          minScore, unkId);
      bpeEncoder = null;
    } else {
      unigramEncoder = null;
      bpeEncoder = new BpeEncoder(mainPieces, scores, unusedFlags, reservedFlags, unkId,
          userDefinedMatcher);
    }

    selfTestInputs = List.copyOf(model.selfTestInputs);
    selfTestExpected = List.copyOf(model.selfTestExpected);
  }

  private static PieceTrie trieOf(List<String> pieceList,
                                  java.util.function.IntUnaryOperator idOf) {
    final byte[][] keys = new byte[pieceList.size()][];
    final int[] ids = new int[pieceList.size()];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = pieceList.get(i).getBytes(StandardCharsets.UTF_8);
      ids[i] = idOf.applyAsInt(i);
    }
    return PieceTrie.build(keys, ids);
  }

  /**
   * Loads a model from a file.
   *
   * @param modelFile The {@code .model} file to load; must not be null.
   * @return The ready-to-use tokenizer.
   * @throws IOException Thrown if the file cannot be read.
   * @throws IllegalArgumentException Thrown if the file is not a valid model.
   */
  public static SentencePieceTokenizer load(Path modelFile) throws IOException {
    if (modelFile == null) {
      throw new IllegalArgumentException("The model file must not be null.");
    }
    return new SentencePieceTokenizer(ModelProtoReader.read(Files.readAllBytes(modelFile)));
  }

  /**
   * Loads a model from a stream. The stream is read fully but not closed.
   *
   * @param in The stream positioned at the start of a {@code .model} serialization; must not be
   *           null.
   * @return The ready-to-use tokenizer.
   * @throws IOException Thrown if the stream cannot be read.
   * @throws IllegalArgumentException Thrown if the bytes are not a valid model.
   */
  public static SentencePieceTokenizer load(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("The input stream must not be null.");
    }
    return new SentencePieceTokenizer(ModelProtoReader.read(in.readAllBytes()));
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException Thrown if {@code text} is null.
   */
  @Override
  public List<SubwordPiece> encode(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    final Utf8Text input = Utf8Text.of(text);
    final SentencePieceNormalizer.Normalized normalized =
        normalizer.normalize(input.bytes(), input.byteLength());
    final List<Segment> segments = algorithm == Algorithm.UNIGRAM
        ? unigramEncoder.encode(normalized.bytes(), normalized.length())
        : bpeEncoder.encode(normalized.bytes(), normalized.length());

    final List<SubwordPiece> out = new ArrayList<>(segments.size());
    final byte[] norm = normalized.bytes();
    final int[] normToOrig = normalized.normToOrig();

    // Accumulates a run of adjacent unknown pieces into one, as the reference does, so a decoder
    // sees a single unknown token per unknown region.
    StringBuilder pendingUnk = null;
    int pendingUnkStart = 0;
    int pendingUnkEnd = 0;

    for (final Segment segment : segments) {
      final boolean isUnk = segment.id() == unkId;
      final boolean isControl = types[segment.id()] == TYPE_CONTROL;
      // A non-unknown segment reuses its vocabulary string; only unknown segments need decoding.
      final String piece = isUnk
          ? new String(norm, segment.from(), segment.to() - segment.from(), StandardCharsets.UTF_8)
          : pieces[segment.id()];

      if (isControl) {
        if (pendingUnk != null) {
          out.add(new SubwordPiece(pendingUnk.toString(), unkId, pendingUnkStart, pendingUnkEnd));
          pendingUnk = null;
        }
        final int at = input.charOffset(normToOrig[segment.from()]);
        out.add(new SubwordPiece(piece, segment.id(), at, at));
        continue;
      }

      final int origBegin = input.charOffset(normToOrig[segment.from()]);
      final int origEnd = input.charOffset(normToOrig[segment.to()]);

      if (isUnk && byteFallback) {
        if (pendingUnk != null) {
          out.add(new SubwordPiece(pendingUnk.toString(), unkId, pendingUnkStart, pendingUnkEnd));
          pendingUnk = null;
        }
        // Decomposes the unknown region into byte pieces; the last one carries the surface span.
        for (int i = segment.from(); i < segment.to(); i++) {
          final int b = norm[i] & 0xFF;
          final boolean last = i == segment.to() - 1;
          out.add(new SubwordPiece(BYTE_PIECES[b], byteToId[b], origBegin,
              last ? origEnd : origBegin));
        }
      } else if (isUnk) {
        if (pendingUnk == null) {
          pendingUnk = new StringBuilder(piece);
          pendingUnkStart = origBegin;
        } else {
          pendingUnk.append(piece);
        }
        pendingUnkEnd = origEnd;
      } else {
        if (pendingUnk != null) {
          out.add(new SubwordPiece(pendingUnk.toString(), unkId, pendingUnkStart, pendingUnkEnd));
          pendingUnk = null;
        }
        out.add(new SubwordPiece(piece, segment.id(), origBegin, origEnd));
      }
    }
    if (pendingUnk != null) {
      out.add(new SubwordPiece(pendingUnk.toString(), unkId, pendingUnkStart, pendingUnkEnd));
    }
    return out;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException Thrown if {@code text} is null.
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    return normalizeAligned(text).normalized();
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException Thrown if {@code text} is null.
   */
  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    final Utf8Text input = Utf8Text.of(text);
    final SentencePieceNormalizer.Normalized result =
        normalizer.normalize(input.bytes(), input.byteLength());
    final String normalized = new String(result.bytes(), 0, result.length(),
        StandardCharsets.UTF_8);
    final int[] normToOrig = result.normToOrig();
    final byte[] norm = result.bytes();

    // Walks the normalized code points, grouping neighbors that came from the same original
    // block into one replace run; gaps between blocks are deletions.
    final Alignment.Builder builder = new Alignment.Builder();
    int cursor = 0;
    int groupOrigStart = -1;
    int groupOrigEnd = -1;
    int groupChars = 0;
    int b = 0;
    final int normLength = result.length();
    while (b < normLength) {
      final int byteLength = Math.min(SentencePieceNormalizer.utf8Length(norm[b]),
          normLength - b);
      final int origStart = input.charOffset(normToOrig[b]);
      final int origEnd = input.charOffset(normToOrig[b + byteLength]);
      final int chars = byteLength == 4 ? 2 : 1;
      if (groupChars > 0 && origStart == groupOrigStart && origEnd == groupOrigEnd) {
        groupChars += chars;
      } else {
        cursor = flushGroup(builder, cursor, groupOrigStart, groupOrigEnd, groupChars);
        groupOrigStart = origStart;
        groupOrigEnd = origEnd;
        groupChars = chars;
      }
      b += byteLength;
    }
    cursor = flushGroup(builder, cursor, groupOrigStart, groupOrigEnd, groupChars);
    if (cursor < input.charLength()) {
      builder.replace(input.charLength() - cursor, 0);
    }
    return new AlignedText(text, normalized, builder.build(input.charLength()));
  }

  /**
   * Emits the pending alignment group as a replace run, preceded by a deletion for any original
   * text skipped before it, and returns the advanced cursor.
   *
   * @param builder   The alignment builder to append to.
   * @param cursor    The original-text offset reached so far.
   * @param origStart The inclusive original-text start of the group.
   * @param origEnd   The exclusive original-text end of the group.
   * @param chars     The number of normalized chars in the group; zero flushes nothing.
   * @return The original-text offset after the group.
   */
  private static int flushGroup(Alignment.Builder builder, int cursor, int origStart, int origEnd,
                                int chars) {
    if (chars == 0) {
      return cursor;
    }
    if (origStart > cursor) {
      builder.replace(origStart - cursor, 0);
    }
    builder.replace(origEnd - Math.max(origStart, cursor), chars);
    return Math.max(origEnd, cursor);
  }

  /** {@return the segmentation algorithm of the loaded model} */
  public Algorithm algorithm() {
    return algorithm;
  }

  /** {@return the number of pieces in the vocabulary} */
  public int vocabularySize() {
    return pieces.length;
  }

  /**
   * Returns the piece string of an id.
   *
   * @param id A vocabulary id in {@code [0, vocabularySize())}.
   * @return The piece string.
   * @throws IllegalArgumentException Thrown if {@code id} is out of range.
   */
  public String idToPiece(int id) {
    checkId(id);
    return pieces[id];
  }

  /**
   * Returns the id of a piece string.
   *
   * @param piece The piece to look up; must not be null.
   * @return The id, or the unknown id when the vocabulary does not contain the piece.
   */
  public int pieceToId(String piece) {
    if (piece == null) {
      throw new IllegalArgumentException("The piece must not be null.");
    }
    final Integer reserved = reservedPieces.get(piece);
    if (reserved != null) {
      return reserved;
    }
    return mainPieces.getOrDefault(piece, unkId);
  }

  /**
   * Returns the score of a piece.
   *
   * @param id A vocabulary id in {@code [0, vocabularySize())}.
   * @return The score; a log-probability for unigram models, a merge rank for BPE models.
   * @throws IllegalArgumentException Thrown if {@code id} is out of range.
   */
  public float score(int id) {
    checkId(id);
    return scores[id];
  }

  /** {@return the id of the unknown piece} */
  public int unknownId() {
    return unkId;
  }

  /**
   * Checks whether an id is the unknown piece.
   *
   * @param id A vocabulary id in {@code [0, vocabularySize())}.
   * @return {@code true} for the unknown piece.
   * @throws IllegalArgumentException Thrown if {@code id} is out of range.
   */
  public boolean isUnknown(int id) {
    checkId(id);
    return types[id] == TYPE_UNKNOWN;
  }

  /**
   * Checks whether an id is a control piece.
   *
   * @param id A vocabulary id in {@code [0, vocabularySize())}.
   * @return {@code true} for control pieces.
   * @throws IllegalArgumentException Thrown if {@code id} is out of range.
   */
  public boolean isControl(int id) {
    checkId(id);
    return types[id] == TYPE_CONTROL;
  }

  /**
   * Checks whether an id is a byte-fallback piece.
   *
   * @param id A vocabulary id in {@code [0, vocabularySize())}.
   * @return {@code true} for byte pieces.
   * @throws IllegalArgumentException Thrown if {@code id} is out of range.
   */
  public boolean isByte(int id) {
    checkId(id);
    return types[id] == TYPE_BYTE;
  }

  /**
   * Verifies that an id is a valid vocabulary id.
   *
   * @param id The id to check.
   * @throws IllegalArgumentException Thrown if {@code id} is outside {@code [0, vocabularySize())}.
   */
  private void checkId(int id) {
    if (id < 0 || id >= pieces.length) {
      throw new IllegalArgumentException(
          "The id " + id + " is outside [0, " + pieces.length + ").");
    }
  }

  /** {@return the embedded self-test input samples} */
  List<String> selfTestInputs() {
    return selfTestInputs;
  }

  /** {@return the embedded self-test expected segmentations} */
  List<String> selfTestExpected() {
    return selfTestExpected;
  }

  // "<0xAB>" piece strings for all byte values, as byte fallback emits them.
  private static final String[] BYTE_PIECES = new String[256];

  static {
    final char[] hex = "0123456789ABCDEF".toCharArray();
    for (int b = 0; b < 256; b++) {
      BYTE_PIECES[b] = "<0x" + hex[b >>> 4] + hex[b & 0xF] + ">";
    }
  }

  /**
   * Parses a byte-fallback piece string of the form {@code <0xAB>} into its byte value.
   *
   * @param piece The piece string.
   * @return The byte value in {@code [0, 255]}, or {@code -1} when the string is not a byte piece.
   */
  private static int parseBytePiece(String piece) {
    if (piece.length() != 6 || !piece.startsWith("<0x") || piece.charAt(5) != '>') {
      return -1;
    }
    final int high = Character.digit(piece.charAt(3), 16);
    final int low = Character.digit(piece.charAt(4), 16);
    return high < 0 || low < 0 ? -1 : (high << 4) | low;
  }
}
