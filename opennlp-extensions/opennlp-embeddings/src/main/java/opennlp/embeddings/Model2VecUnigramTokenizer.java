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
package opennlp.embeddings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.subword.sentencepiece.SentencePieceTokenizer;
import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.tokenize.SubwordTokenizer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Span;
import opennlp.tools.util.normalizer.AlignedText;
import opennlp.tools.util.normalizer.Alignment;

/**
 * Runs a supported Hugging Face Unigram tokenizer through OpenNLP's SentencePiece decoder.
 * Normalization occurs in memory, and returned offsets refer to the original input text.
 */
final class Model2VecUnigramTokenizer implements SubwordTokenizer {

  private static final int TYPE_NORMAL = 1;
  private static final int TYPE_UNKNOWN = 2;
  private static final int TYPE_CONTROL = 3;
  private static final int TYPE_BYTE = 6;
  private static final String UNIGRAM = "Unigram";
  private static final String METASPACE = "Metaspace";
  private static final String SEQUENCE = "Sequence";
  private static final String PRECOMPILED = "Precompiled";
  private static final String REPLACE = "Replace";
  private static final String STRIP = "Strip";
  private static final String MARKER = "▁";
  private static final char MARKER_CHAR = '▁';

  private final SentencePieceTokenizer normalizer;
  private final SentencePieceTokenizer segmenter;
  private final List<NormalizationOperation> operations;
  private final int unknownId;
  private final Set<Integer> controlIds;

  /**
   * Creates a tokenizer from validated configuration.
   *
   * @param parsed The tokenizer configuration.
   * @throws IOException Thrown if an internal SentencePiece model cannot be loaded.
   */
  private Model2VecUnigramTokenizer(Parsed parsed) throws IOException {
    final byte[] normalizerModel = modelBytes(
        List.of(new Piece("<unk>", 0f, TYPE_UNKNOWN)), 0, false,
        parsed.precompiledCharsMap(), true);
    normalizer = SentencePieceTokenizer.load(new ByteArrayInputStream(normalizerModel));
    segmenter = SentencePieceTokenizer.load(new ByteArrayInputStream(modelBytes(
        parsed.pieces(), parsed.unknownId(), parsed.byteFallback(), new byte[0], false)));
    operations = List.copyOf(parsed.operations());
    unknownId = parsed.unknownId();
    controlIds = Set.copyOf(parsed.controlIds());
  }

  /**
   * Reads and validates a supported Model2Vec Unigram tokenizer.
   *
   * @param tokenizerJson The Hugging Face tokenizer configuration.
   * @return A tokenizer for the configuration.
   * @throws IllegalArgumentException Thrown if {@code tokenizerJson} is null or not a regular file.
   * @throws IOException Thrown if the configuration cannot be read or loaded.
   */
  static Model2VecUnigramTokenizer load(Path tokenizerJson) throws IOException {
    if (tokenizerJson == null) {
      throw new IllegalArgumentException("tokenizerJson must not be null");
    }
    if (!Files.isRegularFile(tokenizerJson)) {
      throw new IllegalArgumentException(
          "File does not exist or is not a regular file: " + tokenizerJson);
    }
    return new Model2VecUnigramTokenizer(parse(tokenizerJson));
  }

  /** {@inheritDoc} */
  @Override
  public List<SubwordPiece> encode(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    final String original = text.toString();
    AlignedText aligned = normalizer.normalizeAligned(original);
    for (NormalizationOperation operation : operations) {
      final AlignedText next = operation.applyAligned(aligned.normalizedString());
      aligned = new AlignedText(original, next.normalized(),
          aligned.alignment().andThen(next.alignment()));
    }
    final List<SubwordPiece> normalizedPieces = segmenter.encode(aligned.normalized());
    final List<SubwordPiece> originalPieces = new ArrayList<>(normalizedPieces.size());
    for (SubwordPiece piece : normalizedPieces) {
      final Span span = aligned.toOriginalSpan(piece.start(), piece.end());
      originalPieces.add(
          new SubwordPiece(piece.piece(), piece.id(), span.getStart(), span.getEnd()));
    }
    return originalPieces;
  }

  /** {@return whether the row is the tokenizer's unknown piece} */
  boolean isUnknown(int id) {
    return id == unknownId;
  }

  /** {@return whether the row is a special control piece} */
  boolean isControl(int id) {
    return controlIds.contains(id);
  }

  /** {@return the number of tokenizer rows} */
  int vocabularySize() {
    return segmenter.vocabularySize();
  }

  /** {@return the piece at the given tokenizer row} */
  String idToPiece(int id) {
    return segmenter.idToPiece(id);
  }

  /** Reads and validates the tokenizer configuration. */
  private static Parsed parse(Path file) throws IOException {
    final JsonCursor cursor = new JsonCursor(Files.readString(file), file.getFileName().toString());
    cursor.skipWhitespace();
    cursor.expect('{');
    cursor.skipWhitespace();
    ParsedModel model = null;
    ParsedNormalizer normalizer = null;
    boolean metaspace = false;
    List<AddedToken> addedTokens = List.of();
    final Set<String> fields = new HashSet<>();
    if (cursor.peek() != '}') {
      while (true) {
        final String key = uniqueKey(cursor, fields, "top-level");
        cursor.skipWhitespace();
        cursor.expect(':');
        cursor.skipWhitespace();
        switch (key) {
          case "model" -> model = parseModel(cursor);
          case "normalizer" -> normalizer = parseNormalizer(cursor);
          case "pre_tokenizer" -> metaspace = parsePreTokenizer(cursor);
          case "added_tokens" -> addedTokens = parseAddedTokens(cursor);
          default -> cursor.skipValue();
        }
        cursor.skipWhitespace();
        final char next = cursor.consume();
        if (next == ',') {
          cursor.skipWhitespace();
          continue;
        }
        if (next == '}') {
          break;
        }
        throw cursor.malformed("Expected ',' or '}' after a top-level field");
      }
    } else {
      cursor.consume();
    }
    cursor.requireEnd("Trailing content after the top-level object");
    if (model == null || !UNIGRAM.equals(model.type())) {
      throw new InvalidFormatException(file + " does not define a Unigram tokenizer model");
    }
    if (model.pieces() == null || model.pieces().isEmpty()) {
      throw new InvalidFormatException(file + " has no model.vocab entries");
    }
    if (normalizer == null || normalizer.precompiledCharsMap() == null) {
      throw new InvalidFormatException(file + " has no supported Precompiled normalizer");
    }
    if (!metaspace) {
      throw new InvalidFormatException(file + " has no supported Metaspace pre-tokenizer");
    }
    final List<Piece> pieces = new ArrayList<>(model.pieces());
    final Set<Integer> controls = new HashSet<>();
    final List<AddedToken> sorted = new ArrayList<>(addedTokens);
    sorted.sort(Comparator.comparingInt(AddedToken::id));
    for (AddedToken added : sorted) {
      if (added.id() >= pieces.size()) {
        throw new InvalidFormatException(file + " declares added token id " + added.id()
            + " outside the model vocabulary of " + pieces.size() + " rows");
      }
      if (!pieces.get(added.id()).text().equals(added.content())) {
        throw new InvalidFormatException(file + " contradicts model.vocab at added token id "
            + added.id());
      }
      if (added.special() && added.id() != model.unknownId()) {
        controls.add(added.id());
      }
    }
    if (model.unknownId() < 0 || model.unknownId() >= pieces.size()) {
      throw new InvalidFormatException(file + " has an invalid model.unk_id");
    }
    for (int id = 0; id < pieces.size(); id++) {
      final Piece piece = pieces.get(id);
      final int type = id == model.unknownId() ? TYPE_UNKNOWN
          : controls.contains(id) ? TYPE_CONTROL
          : model.byteFallback() && isBytePiece(piece.text()) ? TYPE_BYTE : TYPE_NORMAL;
      pieces.set(id, new Piece(piece.text(), piece.score(), type));
    }
    return new Parsed(pieces, model.unknownId(), model.byteFallback(),
        normalizer.precompiledCharsMap(), normalizer.operations(), controls);
  }

  /** Reads the Unigram model object. */
  private static ParsedModel parseModel(JsonCursor cursor) throws InvalidFormatException {
    cursor.expect('{');
    cursor.skipWhitespace();
    String type = null;
    int unknownId = -1;
    boolean byteFallback = false;
    List<Piece> pieces = null;
    final Set<String> fields = new HashSet<>();
    while (cursor.peek() != '}') {
      final String key = uniqueKey(cursor, fields, "model");
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      switch (key) {
        case "type" -> type = cursor.parseString();
        case "unk_id" -> unknownId = checkedInt(cursor.parseLong(), cursor, "model.unk_id");
        case "byte_fallback" -> byteFallback = cursor.parseBoolean();
        case "vocab" -> pieces = parseVocabulary(cursor);
        default -> cursor.skipValue();
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        cursor.skipWhitespace();
        if (cursor.peek() == '}') {
          throw cursor.malformed("Trailing comma in model object");
        }
      } else if (next != '}') {
        throw cursor.malformed("Expected ',' or '}' after a model field");
      } else {
        return new ParsedModel(type, unknownId, byteFallback, pieces);
      }
    }
    cursor.consume();
    return new ParsedModel(type, unknownId, byteFallback, pieces);
  }

  /** Reads the ordered Unigram vocabulary. */
  private static List<Piece> parseVocabulary(JsonCursor cursor) throws InvalidFormatException {
    cursor.expect('[');
    cursor.skipWhitespace();
    final List<Piece> pieces = new ArrayList<>();
    while (cursor.peek() != ']') {
      cursor.expect('[');
      cursor.skipWhitespace();
      final String text = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(',');
      cursor.skipWhitespace();
      final double score = cursor.parseDouble();
      if (score < -Float.MAX_VALUE || score > Float.MAX_VALUE) {
        throw cursor.malformed("Unigram score is outside the float range");
      }
      cursor.skipWhitespace();
      cursor.expect(']');
      pieces.add(new Piece(text, (float) score, TYPE_NORMAL));
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        cursor.skipWhitespace();
        if (cursor.peek() == ']') {
          throw cursor.malformed("Trailing comma in model vocabulary");
        }
      } else if (next != ']') {
        throw cursor.malformed("Expected ',' or ']' after a vocabulary entry");
      } else {
        return pieces;
      }
    }
    cursor.consume();
    return pieces;
  }

  /** Reads the tokenizer normalizer. */
  private static ParsedNormalizer parseNormalizer(JsonCursor cursor)
      throws InvalidFormatException {
    final NormalizerBuilder builder = new NormalizerBuilder();
    parseNormalizerObject(cursor, builder);
    return new ParsedNormalizer(builder.precompiledCharsMap, builder.operations);
  }

  /** Adds one normalizer object to {@code builder}. */
  private static void parseNormalizerObject(JsonCursor cursor, NormalizerBuilder builder)
      throws InvalidFormatException {
    cursor.expect('{');
    cursor.skipWhitespace();
    String type = null;
    String precompiled = null;
    List<ParsedNormalizer> children = null;
    PatternValue pattern = null;
    String content = null;
    boolean stripLeft = false;
    boolean stripRight = false;
    final Set<String> fields = new HashSet<>();
    while (cursor.peek() != '}') {
      final String key = uniqueKey(cursor, fields, "normalizer");
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      switch (key) {
        case "type" -> type = cursor.parseString();
        case "precompiled_charsmap" -> precompiled = cursor.parseString();
        case "normalizers" -> children = parseNormalizerChildren(cursor);
        case "pattern" -> pattern = parsePattern(cursor);
        case "content" -> content = cursor.parseString();
        case "strip_left" -> stripLeft = cursor.parseBoolean();
        case "strip_right" -> stripRight = cursor.parseBoolean();
        default -> cursor.skipValue();
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        cursor.skipWhitespace();
      } else if (next != '}') {
        throw cursor.malformed("Expected ',' or '}' after a normalizer field");
      } else {
        break;
      }
    }
    if (type == null) {
      throw cursor.malformed("Normalizer has no type");
    }
    switch (type) {
      case SEQUENCE -> {
        if (children == null) {
          throw cursor.malformed("Sequence normalizer has no normalizers list");
        }
        for (ParsedNormalizer child : children) {
          if (child.precompiledCharsMap() != null) {
            if (builder.precompiledCharsMap != null) {
              throw cursor.malformed("More than one Precompiled normalizer is not supported");
            }
            if (!builder.operations.isEmpty()) {
              throw cursor.malformed(
                  "Precompiled normalizer must precede other normalization steps");
            }
            builder.precompiledCharsMap = child.precompiledCharsMap();
          }
          builder.operations.addAll(child.operations());
        }
      }
      case PRECOMPILED -> {
        if (precompiled == null) {
          throw cursor.malformed("Precompiled normalizer has no character map");
        }
        try {
          builder.precompiledCharsMap = Base64.getDecoder().decode(precompiled);
        } catch (IllegalArgumentException e) {
          throw cursor.malformed("Precompiled normalizer has malformed base64");
        }
      }
      case REPLACE -> builder.operations.add(replacement(pattern, content, cursor));
      case STRIP -> builder.operations.add(new StripOperation(stripLeft, stripRight));
      default -> throw cursor.malformed("Unsupported normalizer type '" + type + "'");
    }
  }

  /** Reads the children of a Sequence normalizer. */
  private static List<ParsedNormalizer> parseNormalizerChildren(JsonCursor cursor)
      throws InvalidFormatException {
    cursor.expect('[');
    cursor.skipWhitespace();
    final List<ParsedNormalizer> children = new ArrayList<>();
    while (cursor.peek() != ']') {
      final NormalizerBuilder child = new NormalizerBuilder();
      parseNormalizerObject(cursor, child);
      children.add(new ParsedNormalizer(child.precompiledCharsMap, child.operations));
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        cursor.skipWhitespace();
        if (cursor.peek() == ']') {
          throw cursor.malformed("Trailing comma in normalizer array");
        }
      } else if (next != ']') {
        throw cursor.malformed("Expected ',' or ']' after a normalizer");
      } else {
        return children;
      }
    }
    cursor.consume();
    return children;
  }

  /** Reads a Replace normalizer pattern. */
  private static PatternValue parsePattern(JsonCursor cursor) throws InvalidFormatException {
    cursor.expect('{');
    cursor.skipWhitespace();
    final String kind = cursor.parseString();
    cursor.skipWhitespace();
    cursor.expect(':');
    cursor.skipWhitespace();
    final String value = cursor.parseString();
    cursor.skipWhitespace();
    cursor.expect('}');
    return new PatternValue(kind, value);
  }

  /** Creates a supported Replace operation. */
  private static NormalizationOperation replacement(
      PatternValue pattern, String content, JsonCursor cursor) throws InvalidFormatException {
    if (pattern == null || content == null) {
      throw cursor.malformed("Replace normalizer needs pattern and content");
    }
    if ("String".equals(pattern.kind())) {
      if (pattern.value().isEmpty()) {
        throw cursor.malformed("Replace normalizer has an unsupported empty literal pattern");
      }
      if (!content.equals(" " + pattern.value() + " ")) {
        throw cursor.malformed("Only spacing literal replacements are supported");
      }
      return new SurroundOperation(pattern.value());
    }
    if ("Regex".equals(pattern.kind())
        && ("\\s+".equals(pattern.value()) || " {2,}".equals(pattern.value()))
        && " ".equals(content)) {
      return CollapseOperation.INSTANCE;
    }
    throw cursor.malformed("Unsupported Replace normalizer pattern");
  }

  /** Reads the pre-tokenizer and reports whether it is the supported Metaspace form. */
  private static boolean parsePreTokenizer(JsonCursor cursor) throws InvalidFormatException {
    cursor.expect('{');
    cursor.skipWhitespace();
    String type = null;
    String replacement = null;
    String prependScheme = null;
    boolean split = true;
    final Set<String> fields = new HashSet<>();
    while (cursor.peek() != '}') {
      final String key = uniqueKey(cursor, fields, "pre-tokenizer");
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      switch (key) {
        case "type" -> type = cursor.parseString();
        case "replacement" -> replacement = cursor.parseString();
        case "prepend_scheme" -> prependScheme = cursor.parseString();
        case "split" -> split = cursor.parseBoolean();
        default -> cursor.skipValue();
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        cursor.skipWhitespace();
      } else if (next != '}') {
        throw cursor.malformed("Expected ',' or '}' after a pre-tokenizer field");
      } else {
        break;
      }
    }
    return METASPACE.equals(type) && MARKER.equals(replacement)
        && "always".equals(prependScheme) && !split;
  }

  /** Reads tokenizer rows that carry added-token metadata. */
  private static List<AddedToken> parseAddedTokens(JsonCursor cursor)
      throws InvalidFormatException {
    cursor.expect('[');
    cursor.skipWhitespace();
    final List<AddedToken> tokens = new ArrayList<>();
    final Set<Integer> tokenIds = new HashSet<>();
    while (cursor.peek() != ']') {
      cursor.expect('{');
      cursor.skipWhitespace();
      int id = -1;
      String content = null;
      boolean special = false;
      final Set<String> fields = new HashSet<>();
      while (cursor.peek() != '}') {
        final String key = uniqueKey(cursor, fields, "added token");
        cursor.skipWhitespace();
        cursor.expect(':');
        cursor.skipWhitespace();
        switch (key) {
          case "id" -> id = checkedInt(cursor.parseLong(), cursor, "added token id");
          case "content" -> content = cursor.parseString();
          case "special" -> special = cursor.parseBoolean();
          default -> cursor.skipValue();
        }
        cursor.skipWhitespace();
        final char next = cursor.consume();
        if (next == ',') {
          cursor.skipWhitespace();
        } else if (next != '}') {
          throw cursor.malformed("Expected ',' or '}' after an added-token field");
        } else {
          break;
        }
      }
      if (id < 0 || content == null) {
        throw cursor.malformed("Added token needs id and content");
      }
      if (!tokenIds.add(id)) {
        throw cursor.malformed("added token id " + id + " occurs more than once");
      }
      tokens.add(new AddedToken(id, content, special));
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        cursor.skipWhitespace();
        if (cursor.peek() == ']') {
          throw cursor.malformed("Trailing comma in added-token array");
        }
      } else if (next != ']') {
        throw cursor.malformed("Expected ',' or ']' after an added token");
      } else {
        return tokens;
      }
    }
    cursor.consume();
    return tokens;
  }

  /**
   * Reads an object field name and rejects a name already seen in that object.
   *
   * @param cursor The JSON cursor positioned at the field name.
   * @param fields The names already read from the current object.
   * @param object A short object name for error messages.
   * @return The field name.
   * @throws InvalidFormatException Thrown if the field name occurs more than once.
   */
  private static String uniqueKey(JsonCursor cursor, Set<String> fields, String object)
      throws InvalidFormatException {
    final String key = cursor.parseString();
    if (!fields.add(key)) {
      throw cursor.malformed(object + " field '" + key + "' occurs more than once");
    }
    return key;
  }

  /** Converts a non-negative JSON integer to an {@code int}. */
  private static int checkedInt(long value, JsonCursor cursor, String field)
      throws InvalidFormatException {
    if (value < 0 || value > Integer.MAX_VALUE) {
      throw cursor.malformed(field + " is outside the supported range");
    }
    return (int) value;
  }

  /** {@return whether {@code piece} has the ASCII form {@code <0xNN>}} */
  private static boolean isBytePiece(String piece) {
    if (piece.length() != 6 || piece.charAt(0) != '<' || piece.charAt(1) != '0'
        || piece.charAt(2) != 'x' || piece.charAt(5) != '>') {
      return false;
    }
    return isAsciiHexDigit(piece.charAt(3)) && isAsciiHexDigit(piece.charAt(4));
  }

  /** {@return whether {@code c} is an ASCII hexadecimal digit} */
  private static boolean isAsciiHexDigit(char c) {
    return c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f';
  }

  /** Encodes the supplied tokenizer data as a SentencePiece model. */
  private static byte[] modelBytes(List<Piece> pieces, int unknownId, boolean byteFallback,
                                   byte[] precompiledCharsMap, boolean normalizing) {
    final ProtoWriter model = new ProtoWriter();
    for (int id = 0; id < pieces.size(); id++) {
      final Piece piece = pieces.get(id);
      final ProtoWriter entry = new ProtoWriter();
      entry.string(1, piece.text());
      entry.float32(2, piece.score());
      entry.varintField(3, id == unknownId ? TYPE_UNKNOWN : piece.type());
      model.message(1, entry.bytes());
    }
    final ProtoWriter trainer = new ProtoWriter();
    trainer.varintField(3, 1);
    if (byteFallback) {
      trainer.varintField(35, 1);
    }
    model.message(2, trainer.bytes());
    final ProtoWriter normalizer = new ProtoWriter();
    if (precompiledCharsMap.length > 0) {
      normalizer.bytesField(2, precompiledCharsMap);
    }
    normalizer.varintField(3, normalizing ? 1 : 0);
    normalizer.varintField(4, normalizing ? 1 : 0);
    normalizer.varintField(5, normalizing ? 1 : 0);
    model.message(3, normalizer.bytes());
    return model.bytes();
  }

  private interface NormalizationOperation {

    /**
     * Applies this operation and records how its output maps to {@code input}.
     *
     * @param input The text produced by the previous normalization stage.
     * @return The operation result and its alignment to {@code input}.
     */
    AlignedText applyAligned(String input);
  }

  private record SurroundOperation(String literal) implements NormalizationOperation {
    /** {@inheritDoc} */
    @Override
    public AlignedText applyAligned(String input) {
      final StringBuilder out = new StringBuilder(input.length() + 8);
      final Alignment.Builder alignment = new Alignment.Builder(input.length() + 8);
      int cursor = 0;
      while (cursor < input.length()) {
        if (input.startsWith(literal, cursor)) {
          appendMarker(out, alignment);
          out.append(literal);
          alignment.equal(literal.length());
          appendMarker(out, alignment);
          cursor += literal.length();
        } else {
          final int codePoint = input.codePointAt(cursor);
          out.appendCodePoint(codePoint);
          final int width = Character.charCount(codePoint);
          alignment.equal(width);
          cursor += width;
        }
      }
      return new AlignedText(input, out.toString(), alignment.build(input.length()));
    }
  }

  private enum CollapseOperation implements NormalizationOperation {
    INSTANCE;

    /** {@inheritDoc} */
    @Override
    public AlignedText applyAligned(String input) {
      final StringBuilder out = new StringBuilder(input.length());
      final Alignment.Builder alignment = new Alignment.Builder(input.length());
      int cursor = 0;
      while (cursor < input.length()) {
        final int codePoint = input.codePointAt(cursor);
        if (codePoint == MARKER_CHAR) {
          final int start = cursor;
          do {
            cursor++;
          } while (cursor < input.length() && input.charAt(cursor) == MARKER_CHAR);
          out.append(MARKER_CHAR);
          alignment.replace(cursor - start, 1);
        } else {
          final int width = Character.charCount(codePoint);
          out.appendCodePoint(codePoint);
          alignment.equal(width);
          cursor += width;
        }
      }
      return new AlignedText(input, out.toString(), alignment.build(input.length()));
    }
  }

  private record StripOperation(boolean left, boolean right) implements NormalizationOperation {
    /** {@inheritDoc} */
    @Override
    public AlignedText applyAligned(String input) {
      int start = 0;
      int end = input.length();
      if (right) {
        while (end > start && input.charAt(end - 1) == MARKER_CHAR) {
          end--;
        }
      }
      if (left) {
        while (start < end && input.charAt(start) == MARKER_CHAR) {
          start++;
        }
        if (start > 0 && start < end) {
          start--;
        }
      }
      if (start == 0 && end == input.length()) {
        return identity(input);
      }
      final Alignment.Builder alignment = new Alignment.Builder(end - start);
      alignment.replace(start, 0);
      alignment.equal(end - start);
      alignment.replace(input.length() - end, 0);
      return new AlignedText(input, input.substring(start, end), alignment.build(input.length()));
    }
  }

  /**
   * Creates an aligned identity result.
   *
   * @param input The text used as both sides of the result.
   * @return An identity alignment over {@code input}.
   */
  private static AlignedText identity(String input) {
    return new AlignedText(input, input,
        new Alignment.Builder(input.length()).equal(input.length()).build(input.length()));
  }

  /**
   * Appends a metaspace marker when the output does not already end with one.
   *
   * @param out       The normalized output.
   * @param alignment The alignment being built for {@code out}.
   */
  private static void appendMarker(StringBuilder out, Alignment.Builder alignment) {
    if (out.isEmpty() || out.charAt(out.length() - 1) != MARKER_CHAR) {
      out.append(MARKER_CHAR);
      alignment.replace(0, 1);
    }
  }

  private record Piece(String text, float score, int type) {
  }

  private record ParsedModel(
      String type, int unknownId, boolean byteFallback, List<Piece> pieces) {
  }

  private record ParsedNormalizer(
      byte[] precompiledCharsMap, List<NormalizationOperation> operations) {
  }

  private record PatternValue(String kind, String value) {
  }

  private record AddedToken(int id, String content, boolean special) {
  }

  private record Parsed(List<Piece> pieces, int unknownId, boolean byteFallback,
                        byte[] precompiledCharsMap, List<NormalizationOperation> operations,
                        Set<Integer> controlIds) {
  }

  private static final class NormalizerBuilder {
    private byte[] precompiledCharsMap;
    private final List<NormalizationOperation> operations = new ArrayList<>();
  }

  private static final class ProtoWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /** Writes an embedded message field. */
    void message(int field, byte[] value) {
      bytesField(field, value);
    }

    /** Writes a UTF-8 string field. */
    void string(int field, String value) {
      bytesField(field, value.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes a length-delimited field. */
    void bytesField(int field, byte[] value) {
      varint((long) field << 3 | 2);
      varint(value.length);
      out.writeBytes(value);
    }

    /** Writes an integer field. */
    void varintField(int field, long value) {
      varint((long) field << 3);
      varint(value);
    }

    /** Writes a 32-bit floating-point field. */
    void float32(int field, float value) {
      varint((long) field << 3 | 5);
      final int bits = Float.floatToIntBits(value);
      out.write(bits & 0xff);
      out.write(bits >>> 8 & 0xff);
      out.write(bits >>> 16 & 0xff);
      out.write(bits >>> 24 & 0xff);
    }

    /** Writes an unsigned variable-length integer. */
    void varint(long value) {
      long remaining = value;
      while ((remaining & ~0x7fL) != 0) {
        out.write((int) (remaining & 0x7f) | 0x80);
        remaining >>>= 7;
      }
      out.write((int) remaining);
    }

    /** {@return the encoded message} */
    byte[] bytes() {
      return out.toByteArray();
    }
  }
}
