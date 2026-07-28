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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The tokenizer side of a teacher model, distilled the way Model2Vec distills it. The class reads
 * the teacher's {@code tokenizer.json} (and, when present, its {@code tokenizer_config.json} for
 * the pad token), decides which vocabulary rows survive into the static table, and rewrites the
 * {@code tokenizer.json} so it describes the distilled table.
 *
 * <p>The cleaning mirrors Model2Vec: tokens matching {@code \[unused\d+\]} are removed, the
 * added-token overlay is pruned to the unknown and pad tokens (the only special tokens a distilled
 * table keeps), the post-processor is dropped (a static table is pooled from content pieces, never
 * wrapped in {@code [CLS]}/{@code [SEP]}), and the surviving tokens keep their original id order
 * but are renumbered to a gapless id space. That new order is the matrix row order.</p>
 *
 * <p>For the forward pass the class reports, per surviving token, its id in the <i>teacher's</i>
 * id space plus the teacher's begin/end-of-sequence wrapper ids: Model2Vec feeds each vocabulary
 * token to the teacher as {@code [bos, token, eos]} and mean-pools the hidden states.</p>
 *
 * <p>The rewrite copies every field it does not change byte for byte (the normalizer, the
 * pre-tokenizer, the Unigram scores), so the cleaned {@code tokenizer.json} stays a faithful
 * fast-tokenizer description of the distilled table.</p>
 */
final class TeacherTokenizer {

  /** Model2Vec's default token removal pattern; matched from the start, like Python re.match. */
  private static final Pattern UNUSED_TOKEN_PATTERN = Pattern.compile("\\[unused\\d+\\]");

  /** The WordPiece {@code model.type} of a BERT-family teacher. */
  static final String WORDPIECE = "WordPiece";

  /** The Unigram {@code model.type} of a SentencePiece-family teacher. */
  static final String UNIGRAM = "Unigram";

  private final String json;
  private final String inputName;
  private final String modelType;
  private final List<String> tokensByOriginalId;
  private final int[] keptOriginalIds;
  private final int originalUnkId;
  private final String unkToken;
  private final String padToken;
  private final int padTokenId;
  private final int[] bosIds;
  private final int[] eosIds;

  /** Holds the parsed state; built by {@link #read(Path, Path)}. */
  private TeacherTokenizer(String json, String inputName, String modelType,
                           List<String> tokensByOriginalId, int[] keptOriginalIds,
                           int originalUnkId, String unkToken, String padToken, int padTokenId,
                           int[] bosIds, int[] eosIds) {
    this.json = json;
    this.inputName = inputName;
    this.modelType = modelType;
    this.tokensByOriginalId = tokensByOriginalId;
    this.keptOriginalIds = keptOriginalIds;
    this.originalUnkId = originalUnkId;
    this.unkToken = unkToken;
    this.padToken = padToken;
    this.padTokenId = padTokenId;
    this.bosIds = bosIds;
    this.eosIds = eosIds;
  }

  /**
   * Reads a teacher's tokenizer configuration.
   *
   * @param tokenizerJsonFile   The teacher's {@code tokenizer.json}. Must not be {@code null}
   *                            and must exist.
   * @param tokenizerConfigFile The teacher's {@code tokenizer_config.json}, consulted for the
   *                            pad token only; may be {@code null} (no pad token then).
   * @return The parsed teacher tokenizer.
   * @throws IllegalArgumentException Thrown if the files are missing or malformed, the tokenizer
   *     model is neither WordPiece nor Unigram, the vocabulary ids are not a gapless range, the
   *     unknown token is missing, or the post-processor is of an unsupported type.
   * @throws IOException Thrown if reading a file fails.
   */
  static TeacherTokenizer read(Path tokenizerJsonFile, Path tokenizerConfigFile)
      throws IOException {
    if (tokenizerJsonFile == null) {
      throw new IllegalArgumentException("TokenizerJsonFile must not be null");
    }
    if (!Files.isRegularFile(tokenizerJsonFile)) {
      throw new IllegalArgumentException("File does not exist or is not a regular file: "
          + tokenizerJsonFile);
    }
    final String padToken = tokenizerConfigFile != null && Files.isRegularFile(tokenizerConfigFile)
        ? FlatJsonFields.topLevelString(tokenizerConfigFile, "pad_token") : null;
    final String json = Files.readString(tokenizerJsonFile);
    final String inputName = tokenizerJsonFile.getFileName().toString();
    final JsonCursor cursor = new JsonCursor(json, inputName);
    cursor.skipWhitespace();
    cursor.expect('{');
    cursor.skipWhitespace();
    String modelType = null;
    List<String> tokensById = null;
    String unkToken = null;
    Long unkId = null;
    Set<String> addedContents = Set.of();
    PostProcessor postProcessor = new PostProcessor(List.of(), List.of(), null, null, Map.of());
    if (cursor.peek() == '}') {
      cursor.consume();
    } else {
      while (true) {
        cursor.skipWhitespace();
        final String key = cursor.parseString();
        cursor.skipWhitespace();
        cursor.expect(':');
        cursor.skipWhitespace();
        switch (key) {
          case "model" -> {
            final ModelSection model = parseModel(cursor);
            modelType = model.type();
            tokensById = model.tokensById();
            unkToken = model.unkToken();
            unkId = model.unkId();
          }
          case "added_tokens" -> addedContents = parseAddedTokenContents(cursor);
          case "post_processor" -> postProcessor = parsePostProcessor(cursor);
          default -> cursor.skipValue();
        }
        cursor.skipWhitespace();
        final char next = cursor.consume();
        if (next == ',') {
          continue;
        }
        if (next == '}') {
          break;
        }
        throw cursor.malformed("Expected ',' or '}' after a field, got '" + next + "'");
      }
    }
    cursor.requireEnd("Trailing content after the top-level object");
    if (modelType == null || tokensById == null) {
      throw new IllegalArgumentException(tokenizerJsonFile + " has no model with a vocabulary; "
          + "it does not look like a teacher's tokenizer.json");
    }
    if (!WORDPIECE.equals(modelType) && !UNIGRAM.equals(modelType)) {
      throw new IllegalArgumentException(tokenizerJsonFile + " has a '" + modelType
          + "' tokenizer model; only " + WORDPIECE + " and " + UNIGRAM
          + " teachers are supported");
    }
    final Map<String, Integer> idByToken = new HashMap<>(tokensById.size() * 2);
    for (int id = 0; id < tokensById.size(); id++) {
      idByToken.putIfAbsent(tokensById.get(id), id);
    }
    if (unkToken == null) {
      if (unkId == null || unkId < 0 || unkId >= tokensById.size()) {
        throw new IllegalArgumentException(tokenizerJsonFile + " does not name an unknown token "
            + "(no model.unk_token / model.unk_id); a distilled table needs one");
      }
      unkToken = tokensById.get(unkId.intValue());
    }
    final Integer originalUnkId = idByToken.get(unkToken);
    if (originalUnkId == null) {
      throw new IllegalArgumentException(tokenizerJsonFile + " names the unknown token '"
          + unkToken + "' but it is not in the vocabulary");
    }
    // The wrapper ids come from the cls/sep pairs of a BertProcessing/RobertaProcessing
    // post-processor, or from resolving a TemplateProcessing's special token names through its
    // special_tokens table, falling back to the vocabulary.
    final int[] bosIds = postProcessor.clsId() != null
        ? new int[] {postProcessor.clsId().intValue()}
        : resolveNames(postProcessor.bosNames(), postProcessor.specialTokenIds(), idByToken,
            tokenizerJsonFile);
    final int[] eosIds = postProcessor.sepId() != null
        ? new int[] {postProcessor.sepId().intValue()}
        : resolveNames(postProcessor.eosNames(), postProcessor.specialTokenIds(), idByToken,
            tokenizerJsonFile);
    final Integer padId = padToken == null ? null : idByToken.get(padToken);
    final int padTokenId = padId == null ? 0 : padId;
    final Set<String> keepSpecial = new HashSet<>();
    keepSpecial.add(unkToken);
    if (padToken != null) {
      keepSpecial.add(padToken);
    }
    final List<Integer> kept = new ArrayList<>(tokensById.size());
    for (int id = 0; id < tokensById.size(); id++) {
      final String token = tokensById.get(id);
      if (UNUSED_TOKEN_PATTERN.matcher(token).lookingAt()) {
        continue;
      }
      if (addedContents.contains(token) && !keepSpecial.contains(token)) {
        continue;
      }
      kept.add(id);
    }
    return new TeacherTokenizer(json, inputName, modelType, tokensById,
        kept.stream().mapToInt(Integer::intValue).toArray(), originalUnkId, unkToken, padToken,
        padTokenId, bosIds, eosIds);
  }

  /**
   * {@return the ids the named special tokens resolve to, through the post-processor's
   * special-token table first and the vocabulary second}
   *
   * @param names           The special token names in order.
   * @param specialTokenIds The post-processor's name-to-id table.
   * @param idByToken       The vocabulary, token to id.
   * @param file            The source file, for error messages.
   * @throws IllegalArgumentException Thrown if a name resolves nowhere.
   */
  private static int[] resolveNames(List<String> names, Map<String, Long> specialTokenIds,
                                    Map<String, Integer> idByToken, Path file) {
    final int[] ids = new int[names.size()];
    for (int i = 0; i < names.size(); i++) {
      final Long specialId = specialTokenIds.get(names.get(i));
      final Integer vocabId = idByToken.get(names.get(i));
      if (specialId != null) {
        ids[i] = specialId.intValue();
      } else if (vocabId != null) {
        ids[i] = vocabId;
      } else {
        throw new IllegalArgumentException(file + " wraps sequences in the special token '"
            + names.get(i) + "' but neither the post-processor nor the vocabulary defines it");
      }
    }
    return ids;
  }

  /** {@return the tokenizer family, {@code "WordPiece"} or {@code "Unigram"}} */
  String modelType() {
    return modelType;
  }

  /** {@return the number of surviving tokens, the matrix row count} */
  int vocabularySize() {
    return keptOriginalIds.length;
  }

  /** {@return the surviving tokens' ids in the teacher's id space, in matrix row order} */
  int[] keptOriginalIds() {
    return keptOriginalIds.clone();
  }

  /** {@return the teacher's pad token id, used to pad batches; 0 when the teacher names none} */
  int padTokenId() {
    return padTokenId;
  }

  /** {@return the unknown token's string} */
  String unkToken() {
    return unkToken;
  }

  /** {@return the pad token's string, or {@code null} when the teacher names none} */
  String padToken() {
    return padToken;
  }

  /**
   * The teacher input sequence for one matrix row: the begin-of-sequence ids, the token's
   * original id, and the end-of-sequence ids.
   *
   * @param row The matrix row.
   * @return The teacher input ids.
   */
  long[] inputSequence(int row) {
    final long[] sequence = new long[bosIds.length + 1 + eosIds.length];
    int i = 0;
    for (final int id : bosIds) {
      sequence[i++] = id;
    }
    sequence[i++] = keptOriginalIds[row];
    for (final int id : eosIds) {
      sequence[i++] = id;
    }
    return sequence;
  }

  /**
   * Writes the cleaned {@code tokenizer.json}: the surviving vocabulary renumbered, the
   * added-token overlay pruned to the unknown and pad tokens, the post-processor nulled, and
   * every other field copied byte for byte from the teacher's file.
   *
   * @param file The file to write. Must not be {@code null}.
   * @throws IOException Thrown if writing fails.
   */
  void writeCleaned(Path file) throws IOException {
    final Map<Integer, Integer> newIdByOriginal = new HashMap<>(keptOriginalIds.length * 2);
    for (int row = 0; row < keptOriginalIds.length; row++) {
      newIdByOriginal.put(keptOriginalIds[row], row);
    }
    final JsonCursor cursor = new JsonCursor(json, inputName);
    final StringBuilder out = new StringBuilder(json.length());
    cursor.skipWhitespace();
    cursor.expect('{');
    out.append('{');
    cursor.skipWhitespace();
    if (cursor.peek() == '}') {
      cursor.consume();
    } else {
      boolean first = true;
      while (true) {
        cursor.skipWhitespace();
        final int keyStart = cursor.position();
        final String key = cursor.parseString();
        final String rawKey = json.substring(keyStart, cursor.position());
        cursor.skipWhitespace();
        cursor.expect(':');
        cursor.skipWhitespace();
        if (!first) {
          out.append(',');
        }
        first = false;
        out.append(rawKey).append(':');
        switch (key) {
          case "model" -> rewriteModel(cursor, out, newIdByOriginal);
          case "added_tokens" -> {
            cursor.skipValue();
            out.append(rewrittenAddedTokens(newIdByOriginal));
          }
          case "post_processor" -> {
            cursor.skipValue();
            out.append("null");
          }
          default -> out.append(copyRawValue(cursor));
        }
        cursor.skipWhitespace();
        final char next = cursor.consume();
        if (next == ',') {
          continue;
        }
        if (next == '}') {
          break;
        }
        throw cursor.malformed("Expected ',' or '}' after a field, got '" + next + "'");
      }
    }
    cursor.requireEnd("Trailing content after the top-level object");
    out.append('}');
    Files.writeString(file, out.toString());
  }

  /**
   * Rewrites the {@code model} object: the vocabulary renumbered to the surviving rows, the
   * Unigram {@code unk_id} remapped, every other field copied byte for byte.
   *
   * @param cursor          The cursor, positioned at the object's opening brace.
   * @param out             The output accumulator.
   * @param newIdByOriginal The original-to-new id map.
   */
  private void rewriteModel(JsonCursor cursor, StringBuilder out,
                            Map<Integer, Integer> newIdByOriginal) {
    cursor.expect('{');
    out.append('{');
    cursor.skipWhitespace();
    if (cursor.peek() == '}') {
      cursor.consume();
      out.append('}');
      return;
    }
    boolean first = true;
    while (true) {
      cursor.skipWhitespace();
      final int keyStart = cursor.position();
      final String key = cursor.parseString();
      final String rawKey = json.substring(keyStart, cursor.position());
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      if (!first) {
        out.append(',');
      }
      first = false;
      out.append(rawKey).append(':');
      switch (key) {
        case "vocab" -> out.append(rewrittenVocab(cursor, newIdByOriginal));
        case "unk_id" -> {
          cursor.skipValue();
          out.append(newIdByOriginal.getOrDefault(originalUnkId, 0));
        }
        default -> out.append(copyRawValue(cursor));
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        break;
      }
      throw cursor.malformed("Expected ',' or '}' after a model field, got '" + next + "'");
    }
    out.append('}');
  }

  /**
   * {@return the rewritten vocabulary value: for a WordPiece dictionary the kept entries with
   * their new ids (raw key spans reused), for a Unigram list the kept {@code [piece, score]}
   * entries byte for byte}
   *
   * @param cursor          The cursor, positioned at the vocabulary's opening character.
   * @param newIdByOriginal The original-to-new id map.
   */
  private String rewrittenVocab(JsonCursor cursor, Map<Integer, Integer> newIdByOriginal) {
    final StringBuilder out = new StringBuilder();
    if (cursor.peek() == '{') {
      cursor.consume();
      out.append('{');
      cursor.skipWhitespace();
      if (cursor.peek() == '}') {
        cursor.consume();
      } else {
        boolean first = true;
        while (true) {
          cursor.skipWhitespace();
          final int keyStart = cursor.position();
          cursor.parseString();
          final String rawKey = json.substring(keyStart, cursor.position());
          cursor.skipWhitespace();
          cursor.expect(':');
          cursor.skipWhitespace();
          final long originalId = cursor.parseLong();
          final Integer row = newIdByOriginal.get((int) originalId);
          if (row != null) {
            if (!first) {
              out.append(',');
            }
            first = false;
            out.append(rawKey).append(':').append(row);
          }
          cursor.skipWhitespace();
          final char next = cursor.consume();
          if (next == ',') {
            continue;
          }
          if (next == '}') {
            break;
          }
          throw cursor.malformed("Expected ',' or '}' after a vocab entry, got '" + next + "'");
        }
      }
      out.append('}');
    } else {
      cursor.expect('[');
      out.append('[');
      cursor.skipWhitespace();
      if (cursor.peek() == ']') {
        cursor.consume();
      } else {
        boolean first = true;
        int originalId = 0;
        while (true) {
          cursor.skipWhitespace();
          final int entryStart = cursor.position();
          cursor.expect('[');
          cursor.skipWhitespace();
          cursor.parseString();
          cursor.skipWhitespace();
          cursor.expect(',');
          cursor.skipWhitespace();
          cursor.skipValue();
          cursor.skipWhitespace();
          cursor.expect(']');
          if (newIdByOriginal.containsKey(originalId++)) {
            if (!first) {
              out.append(',');
            }
            first = false;
            out.append(json, entryStart, cursor.position());
          }
          cursor.skipWhitespace();
          final char next = cursor.consume();
          if (next == ',') {
            continue;
          }
          if (next == ']') {
            break;
          }
          throw cursor.malformed("Expected ',' or ']' after a vocab entry, got '" + next + "'");
        }
      }
      out.append(']');
    }
    return out.toString();
  }

  /**
   * {@return the pruned {@code added_tokens} value: the unknown and pad tokens at their new ids,
   * with the flag convention Model2Vec writes (the pad token strips around itself, the unknown
   * token does not)}
   *
   * @param newIdByOriginal The original-to-new id map.
   */
  private String rewrittenAddedTokens(Map<Integer, Integer> newIdByOriginal) {
    record Added(int id, String content, boolean pad) {
    }
    final List<Added> kept = new ArrayList<>(2);
    for (int id = 0; id < tokensByOriginalId.size(); id++) {
      final String token = tokensByOriginalId.get(id);
      final Integer row = newIdByOriginal.get(id);
      if (row == null) {
        continue;
      }
      if (token.equals(unkToken)) {
        kept.add(new Added(row, token, false));
      } else if (token.equals(padToken)) {
        kept.add(new Added(row, token, true));
      }
    }
    kept.sort(Comparator.comparingInt(Added::id));
    final StringBuilder out = new StringBuilder("[");
    boolean first = true;
    for (final Added added : kept) {
      if (!first) {
        out.append(',');
      }
      first = false;
      out.append("{\"id\":").append(added.id())
          .append(",\"content\":").append(quoted(added.content()))
          .append(",\"single_word\":").append(added.pad())
          .append(",\"lstrip\":").append(added.pad())
          .append(",\"rstrip\":").append(added.pad())
          .append(",\"normalized\":").append(added.pad())
          .append(",\"special\":true}");
    }
    return out.append(']').toString();
  }

  /**
   * {@return the JSON string literal for the given content, escaping the quote, the backslash,
   * and control characters}
   *
   * @param content The string to quote.
   */
  private static String quoted(String content) {
    final StringBuilder out = new StringBuilder(content.length() + 2).append('"');
    for (int i = 0; i < content.length(); i++) {
      final char c = content.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }

  /**
   * {@return the raw text of the JSON value at the cursor, unchanged}
   *
   * @param cursor The cursor, positioned at the value.
   */
  private String copyRawValue(JsonCursor cursor) {
    final int start = cursor.position();
    cursor.skipValue();
    return json.substring(start, cursor.position());
  }

  /** The fields read out of the {@code model} object. */
  private record ModelSection(String type, List<String> tokensById, String unkToken, Long unkId) {
  }

  /**
   * Parses the {@code model} object for its type, its vocabulary in id order, and its unknown
   * token (by name for WordPiece, by id for Unigram).
   *
   * @param cursor The cursor, positioned at the object's opening brace.
   * @return The parsed section.
   */
  private static ModelSection parseModel(JsonCursor cursor) {
    cursor.expect('{');
    cursor.skipWhitespace();
    String type = null;
    List<String> tokensById = null;
    String unkToken = null;
    Long unkId = null;
    if (cursor.peek() == '}') {
      cursor.consume();
      return new ModelSection(null, null, null, null);
    }
    while (true) {
      cursor.skipWhitespace();
      final String key = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      switch (key) {
        case "type" -> type = cursor.parseString();
        case "unk_token" -> unkToken = cursor.parseString();
        case "unk_id" -> {
          if (!cursor.consumeLiteral("null")) {
            unkId = cursor.parseLong();
          }
        }
        case "vocab" -> tokensById = parseVocab(cursor);
        default -> cursor.skipValue();
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        return new ModelSection(type, tokensById, unkToken, unkId);
      }
      throw cursor.malformed("Expected ',' or '}' after a model field, got '" + next + "'");
    }
  }

  /**
   * {@return the vocabulary in id order, either from a WordPiece {@code "token": id} dictionary
   * or from a Unigram {@code [piece, score]} list; dictionary ids must form a gapless range}
   *
   * @param cursor The cursor, positioned at the vocabulary's opening character.
   */
  private static List<String> parseVocab(JsonCursor cursor) {
    if (cursor.peek() == '{') {
      cursor.consume();
      cursor.skipWhitespace();
      final Map<Long, String> tokenById = new HashMap<>();
      if (cursor.peek() == '}') {
        cursor.consume();
      } else {
        while (true) {
          cursor.skipWhitespace();
          final String token = cursor.parseString();
          cursor.skipWhitespace();
          cursor.expect(':');
          cursor.skipWhitespace();
          final long id = cursor.parseLong();
          if (tokenById.putIfAbsent(id, token) != null) {
            throw cursor.malformed("Vocabulary id " + id + " is assigned more than once");
          }
          cursor.skipWhitespace();
          final char next = cursor.consume();
          if (next == ',') {
            continue;
          }
          if (next == '}') {
            break;
          }
          throw cursor.malformed("Expected ',' or '}' after a vocab entry, got '" + next + "'");
        }
      }
      final List<Map.Entry<Long, String>> entries = new ArrayList<>(tokenById.entrySet());
      entries.sort(Comparator.comparingLong(Map.Entry::getKey));
      final List<String> ordered = new ArrayList<>(entries.size());
      for (int row = 0; row < entries.size(); row++) {
        if (entries.get(row).getKey() != row) {
          throw cursor.malformed("Vocabulary ids are not a gapless range: expected id " + row
              + " but found " + entries.get(row).getKey());
        }
        ordered.add(entries.get(row).getValue());
      }
      return ordered;
    }
    cursor.expect('[');
    cursor.skipWhitespace();
    final List<String> pieces = new ArrayList<>();
    if (cursor.peek() == ']') {
      cursor.consume();
      return pieces;
    }
    while (true) {
      cursor.skipWhitespace();
      cursor.expect('[');
      cursor.skipWhitespace();
      pieces.add(cursor.parseString());
      cursor.skipWhitespace();
      cursor.expect(',');
      cursor.skipWhitespace();
      cursor.skipValue();
      cursor.skipWhitespace();
      cursor.expect(']');
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == ']') {
        return pieces;
      }
      throw cursor.malformed("Expected ',' or ']' after a vocab entry, got '" + next + "'");
    }
  }

  /**
   * {@return the contents of the {@code added_tokens} overlay}
   *
   * @param cursor The cursor, positioned at the list's opening bracket.
   */
  private static Set<String> parseAddedTokenContents(JsonCursor cursor) {
    cursor.expect('[');
    cursor.skipWhitespace();
    final Set<String> contents = new HashSet<>();
    if (cursor.peek() == ']') {
      cursor.consume();
      return contents;
    }
    while (true) {
      cursor.skipWhitespace();
      cursor.expect('{');
      cursor.skipWhitespace();
      String content = null;
      if (cursor.peek() == '}') {
        cursor.consume();
      } else {
        while (true) {
          cursor.skipWhitespace();
          final String key = cursor.parseString();
          cursor.skipWhitespace();
          cursor.expect(':');
          cursor.skipWhitespace();
          if ("content".equals(key)) {
            content = cursor.parseString();
          } else {
            cursor.skipValue();
          }
          cursor.skipWhitespace();
          final char next = cursor.consume();
          if (next == ',') {
            continue;
          }
          if (next == '}') {
            break;
          }
          throw cursor.malformed("Expected ',' or '}' after an added token field, got '" + next
              + "'");
        }
      }
      if (content != null) {
        contents.add(content);
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == ']') {
        return contents;
      }
      throw cursor.malformed("Expected ',' or ']' after an added token, got '" + next + "'");
    }
  }

  /** The wrapper names or ids of a post-processor, plus its special-token id table. */
  private record PostProcessor(List<String> bosNames, List<String> eosNames, Long clsId,
                               Long sepId, Map<String, Long> specialTokenIds) {
  }

  /**
   * Parses the {@code post_processor} for the wrapper a single-sequence encoding adds. Supports
   * the {@code TemplateProcessing} form (string or structured template) and the
   * {@code BertProcessing}/{@code RobertaProcessing} forms with their {@code cls}/{@code sep}
   * pairs; a {@code null} post-processor means no wrapper.
   *
   * @param cursor The cursor, positioned at the value.
   * @return The parsed post-processor.
   * @throws IllegalArgumentException Thrown if the type is not one of the supported forms.
   */
  private static PostProcessor parsePostProcessor(JsonCursor cursor) {
    if (cursor.consumeLiteral("null")) {
      return new PostProcessor(List.of(), List.of(), null, null, Map.of());
    }
    cursor.expect('{');
    cursor.skipWhitespace();
    String type = null;
    List<String> bosNames = List.of();
    List<String> eosNames = List.of();
    Map<String, Long> specialTokenIds = Map.of();
    Long clsId = null;
    Long sepId = null;
    if (cursor.peek() == '}') {
      cursor.consume();
    } else {
      while (true) {
        cursor.skipWhitespace();
        final String key = cursor.parseString();
        cursor.skipWhitespace();
        cursor.expect(':');
        cursor.skipWhitespace();
        switch (key) {
          case "type" -> type = cursor.parseString();
          case "single" -> {
            final List<List<String>> wrapper = parseTemplate(cursor);
            bosNames = wrapper.get(0);
            eosNames = wrapper.get(1);
          }
          case "special_tokens" -> specialTokenIds = parseSpecialTokenIds(cursor);
          case "cls" -> clsId = parseTokenIdPair(cursor);
          case "sep" -> sepId = parseTokenIdPair(cursor);
          default -> cursor.skipValue();
        }
        cursor.skipWhitespace();
        final char next = cursor.consume();
        if (next == ',') {
          continue;
        }
        if (next == '}') {
          break;
        }
        throw cursor.malformed("Expected ',' or '}' after a post-processor field, got '" + next
            + "'");
      }
    }
    if (type == null) {
      return new PostProcessor(List.of(), List.of(), null, null, Map.of());
    }
    return switch (type) {
      case "TemplateProcessing" ->
          new PostProcessor(bosNames, eosNames, null, null, specialTokenIds);
      case "BertProcessing", "RobertaProcessing" ->
          new PostProcessor(List.of(), List.of(), clsId, sepId, specialTokenIds);
      default -> throw new IllegalArgumentException("The post_processor type '" + type
          + "' is not supported; expected TemplateProcessing, BertProcessing, or "
          + "RobertaProcessing");
    };
  }

  /**
   * {@return a two-element list: the special token names before the sequence placeholder (the
   * begin-of-sequence wrapper) and those after it (the end-of-sequence wrapper); the template is
   * either a string like {@code "[CLS] $A [SEP]"} or a list of {@code SpecialToken}/{@code
   * Sequence} items}
   *
   * @param cursor The cursor, positioned at the template value.
   */
  private static List<List<String>> parseTemplate(JsonCursor cursor) {
    final List<String> bos = new ArrayList<>(1);
    final List<String> eos = new ArrayList<>(1);
    if (cursor.peek() == '"') {
      final String template = cursor.parseString();
      List<String> current = bos;
      for (final String part : template.split(" ")) {
        if (part.isEmpty()) {
          continue;
        }
        if (part.startsWith("$")) {
          current = eos;
        } else {
          current.add(part);
        }
      }
      return List.of(bos, eos);
    }
    cursor.expect('[');
    cursor.skipWhitespace();
    List<String> current = bos;
    if (cursor.peek() == ']') {
      cursor.consume();
      return List.of(bos, eos);
    }
    while (true) {
      cursor.skipWhitespace();
      cursor.expect('{');
      cursor.skipWhitespace();
      final String itemType = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      cursor.expect('{');
      cursor.skipWhitespace();
      String id = null;
      if (cursor.peek() == '}') {
        cursor.consume();
      } else {
        while (true) {
          cursor.skipWhitespace();
          final String key = cursor.parseString();
          cursor.skipWhitespace();
          cursor.expect(':');
          cursor.skipWhitespace();
          if ("id".equals(key)) {
            id = cursor.parseString();
          } else {
            cursor.skipValue();
          }
          cursor.skipWhitespace();
          final char next = cursor.consume();
          if (next == ',') {
            continue;
          }
          if (next == '}') {
            break;
          }
          throw cursor.malformed("Expected ',' or '}' after a template item field, got '" + next
              + "'");
        }
      }
      cursor.skipWhitespace();
      cursor.expect('}');
      if ("SpecialToken".equals(itemType)) {
        current.add(id);
      } else if ("Sequence".equals(itemType)) {
        current = eos;
      } else {
        throw cursor.malformed("Unknown template item type: '" + itemType + "'");
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == ']') {
        break;
      }
      throw cursor.malformed("Expected ',' or ']' after a template item, got '" + next + "'");
    }
    return List.of(bos, eos);
  }

  /**
   * {@return the post-processor's special-token id table, name to the first of its ids}
   *
   * @param cursor The cursor, positioned at the table's opening brace.
   */
  private static Map<String, Long> parseSpecialTokenIds(JsonCursor cursor) {
    cursor.expect('{');
    cursor.skipWhitespace();
    final Map<String, Long> ids = new HashMap<>();
    if (cursor.peek() == '}') {
      cursor.consume();
      return ids;
    }
    while (true) {
      cursor.skipWhitespace();
      final String name = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      cursor.expect('{');
      cursor.skipWhitespace();
      Long id = null;
      if (cursor.peek() == '}') {
        cursor.consume();
      } else {
        while (true) {
          cursor.skipWhitespace();
          final String key = cursor.parseString();
          cursor.skipWhitespace();
          cursor.expect(':');
          cursor.skipWhitespace();
          if ("ids".equals(key)) {
            cursor.expect('[');
            cursor.skipWhitespace();
            id = cursor.parseLong();
            cursor.skipWhitespace();
            while (cursor.consume() == ',') {
              cursor.skipWhitespace();
              cursor.skipValue();
              cursor.skipWhitespace();
            }
          } else {
            cursor.skipValue();
          }
          cursor.skipWhitespace();
          final char next = cursor.consume();
          if (next == ',') {
            continue;
          }
          if (next == '}') {
            break;
          }
          throw cursor.malformed("Expected ',' or '}' after a special token field, got '" + next
              + "'");
        }
      }
      if (id != null) {
        ids.put(name, id);
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        return ids;
      }
      throw cursor.malformed("Expected ',' or '}' after a special token, got '" + next + "'");
    }
  }

  /**
   * {@return the id of a {@code ["token", id]} pair, as {@code cls} and {@code sep} carry it}
   *
   * @param cursor The cursor, positioned at the pair's opening bracket.
   */
  private static Long parseTokenIdPair(JsonCursor cursor) {
    cursor.expect('[');
    cursor.skipWhitespace();
    cursor.parseString();
    cursor.skipWhitespace();
    cursor.expect(',');
    cursor.skipWhitespace();
    final long id = cursor.parseLong();
    cursor.skipWhitespace();
    cursor.expect(']');
    return id;
  }
}
