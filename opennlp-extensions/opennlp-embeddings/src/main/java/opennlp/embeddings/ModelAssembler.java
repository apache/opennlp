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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.java.Experimental;

/**
 * Turns a distilled model directory (the layout the
 * <a href="https://github.com/MinishLab/model2vec">Model2Vec</a> {@code save_pretrained} writes)
 * into a directory {@link StaticEmbeddingModel#load(Path)} can open, then verifies it by loading
 * it.
 *
 * <p>A distillation ships {@code model.safetensors}, {@code tokenizer.json}, and
 * {@code config.json}, but not the two files the loader also needs for a WordPiece model
 * ({@code vocab.txt} and {@code tokenizer_config.json}), and not the trained SentencePiece
 * {@code .model} file. This class fills the WordPiece gap from {@code tokenizer.json} itself: the
 * matrix row order is the {@code model.vocab} dictionary in id order, and the casing is the
 * {@code normalizer.lowercase} flag. It cannot fabricate the SentencePiece {@code .model} file,
 * which comes from the teacher, so it reports that as an actionable error.</p>
 *
 * <p>Assembly writes only the missing files and never overwrites an existing one, so a directory
 * a caller already completed by hand is left intact.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
public final class ModelAssembler {

  /** The WordPiece tokenizer family, the {@code model.type} of a BERT-style distillation. */
  private static final String FAMILY_WORDPIECE = "WordPiece";

  /** The Unigram {@code model.type} a SentencePiece distillation's {@code tokenizer.json} uses. */
  private static final String FAMILY_UNIGRAM = "Unigram";

  /** The SentencePiece tokenizer family, reported for an assembled Unigram directory. */
  private static final String FAMILY_SENTENCEPIECE = "SentencePiece";

  /** Not instantiable. */
  private ModelAssembler() {
  }

  /**
   * The outcome of assembling a directory: what family it is, the files that were written, and the
   * stats read back from the loaded model.
   *
   * @param family               {@code "WordPiece"} or {@code "SentencePiece"}.
   * @param dimension            The embedding dimension of the loaded model.
   * @param vocabularySize       The number of subword rows in the loaded model's table.
   * @param termCount            The number of term rows after the subword rows; {@code 0} for a
   *                             model without a term table.
   * @param wroteVocabulary      Whether a {@code vocab.txt} was written.
   * @param wroteTokenizerConfig Whether a {@code tokenizer_config.json} was written.
   */
  public record Result(String family, int dimension, int vocabularySize, int termCount,
                       boolean wroteVocabulary, boolean wroteTokenizerConfig) {
  }

  /**
   * Assembles and verifies a model directory in place.
   *
   * @param modelDirectory The distilled model directory. Must not be {@code null} and must be a
   *                       directory holding at least {@code model.safetensors},
   *                       {@code tokenizer.json}, and {@code config.json}.
   * @return The assembly result.
   * @throws IllegalArgumentException Thrown if {@code modelDirectory} is {@code null}, is not a
   *     directory, is missing a required distillation file, or is a SentencePiece model without
   *     its {@code .model} file.
   * @throws InvalidFormatException Thrown if a model file is malformed, its tokenizer family is
   *     unsupported, or the directory does not load after assembly.
   * @throws IOException Thrown if reading or writing a file fails.
   */
  public static Result assemble(Path modelDirectory) throws IOException {
    if (modelDirectory == null) {
      throw new IllegalArgumentException("ModelDirectory must not be null");
    }
    if (!Files.isDirectory(modelDirectory)) {
      throw new IllegalArgumentException(
          "Model directory does not exist or is not a directory: " + modelDirectory);
    }
    requireFile(modelDirectory, ModelFileNames.SAFETENSORS);
    requireFile(modelDirectory, ModelFileNames.CONFIG);
    final Path tokenizerJson = requireFile(modelDirectory, ModelFileNames.TOKENIZER_JSON);

    final TokenizerJson tokenizer = readTokenizerJson(tokenizerJson);
    return switch (tokenizer.modelType()) {
      case FAMILY_WORDPIECE -> assembleWordpiece(modelDirectory, tokenizer);
      case FAMILY_UNIGRAM -> assembleSentencePiece(modelDirectory);
      default -> throw new InvalidFormatException(tokenizerJson + " has a '"
          + tokenizer.modelType() + "' tokenizer model; only " + FAMILY_WORDPIECE + " and "
          + FAMILY_UNIGRAM + " (" + FAMILY_SENTENCEPIECE + ") distillations are supported");
    };
  }

  /**
   * Assembles a WordPiece directory, deriving {@code vocab.txt} and {@code tokenizer_config.json}
   * from {@code tokenizer.json} when they are absent, then loading to verify.
   *
   * @param modelDirectory The model directory.
   * @param tokenizer      The parsed {@code tokenizer.json}.
   * @return The assembly result.
   * @throws IOException Thrown if reading or writing a file fails.
   */
  private static Result assembleWordpiece(Path modelDirectory, TokenizerJson tokenizer)
      throws IOException {
    final Path vocabularyFile = modelDirectory.resolve(ModelFileNames.VOCABULARY);
    boolean wroteVocabulary = false;
    if (!Files.exists(vocabularyFile)) {
      if (tokenizer.orderedVocabulary() == null) {
        throw new InvalidFormatException("tokenizer.json in " + modelDirectory
            + " has no model.vocab dictionary; cannot derive " + ModelFileNames.VOCABULARY);
      }
      Files.write(vocabularyFile, tokenizer.orderedVocabulary());
      wroteVocabulary = true;
    }
    final Path tokenizerConfigFile = modelDirectory.resolve(ModelFileNames.TOKENIZER_CONFIG);
    boolean wroteTokenizerConfig = false;
    if (!Files.exists(tokenizerConfigFile)) {
      // The BERT normalizer's lowercase flag is the casing; default to lower-casing (the uncased
      // convention) when the tokenizer does not state it, which the load then reads back.
      final boolean lowerCase = tokenizer.lowerCase() == null || tokenizer.lowerCase();
      Files.writeString(tokenizerConfigFile,
          "{\n  \"do_lower_case\": " + lowerCase + "\n}\n", StandardCharsets.UTF_8);
      wroteTokenizerConfig = true;
    }
    final StaticEmbeddingModel model = load(modelDirectory);
    return new Result(FAMILY_WORDPIECE, model.dimension(), model.vocabularySize(),
        model.termCount(), wroteVocabulary, wroteTokenizerConfig);
  }

  /**
   * Assembles a SentencePiece directory: it only needs the trained {@code .model} file to be
   * present, which the distillation does not ship, so a missing one is an actionable error.
   *
   * @param modelDirectory The model directory.
   * @return The assembly result.
   * @throws IOException Thrown if loading fails to read a file.
   */
  private static Result assembleSentencePiece(Path modelDirectory) throws IOException {
    if (ModelFileNames.firstRegularFile(modelDirectory,
        ModelFileNames.SENTENCEPIECE_MODELS) == null) {
      throw new IllegalArgumentException("Model directory " + modelDirectory + " is a "
          + FAMILY_SENTENCEPIECE + " model but has no trained model file (one of "
          + String.join(", ", ModelFileNames.SENTENCEPIECE_MODELS) + "); copy it from the "
          + "teacher model's repository (it is named sentencepiece.bpe.model there) into this "
          + "directory");
    }
    final StaticEmbeddingModel model = load(modelDirectory);
    return new Result(FAMILY_SENTENCEPIECE, model.dimension(), model.vocabularySize(),
        model.termCount(), false, false);
  }

  /**
   * Loads the assembled directory to verify it, translating a load failure into an assembly
   * failure with the same message and the same exception type.
   *
   * @param modelDirectory The assembled directory.
   * @return The loaded model.
   * @throws IOException Thrown if reading a file fails.
   */
  private static StaticEmbeddingModel load(Path modelDirectory) throws IOException {
    try {
      return StaticEmbeddingModel.load(modelDirectory);
    } catch (InvalidFormatException e) {
      throw new InvalidFormatException("Assembled directory " + modelDirectory
          + " does not load: " + e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Assembled directory " + modelDirectory
          + " does not load: " + e.getMessage(), e);
    }
  }

  /**
   * {@return the required file in the directory}
   *
   * @param directory The model directory.
   * @param name      The required file name.
   * @throws IllegalArgumentException Thrown if the file is absent.
   */
  private static Path requireFile(Path directory, String name) {
    final Path file = directory.resolve(name);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("Model directory " + directory + " has no " + name
          + "; it does not look like a distilled model directory");
    }
    return file;
  }

  /**
   * The fields read out of a {@code tokenizer.json} for assembly.
   *
   * @param modelType         The {@code model.type}, e.g. {@code "WordPiece"} or {@code "Unigram"}.
   * @param orderedVocabulary The matrix row order for a WordPiece dictionary vocabulary, or
   *                          {@code null} when the model is not a WordPiece dictionary.
   * @param lowerCase         The {@code normalizer.lowercase} flag, or {@code null} when absent.
   */
  private record TokenizerJson(String modelType, List<String> orderedVocabulary,
                               Boolean lowerCase) {
  }

  /**
   * Reads the {@code model.type}, the WordPiece {@code model.vocab} dictionary in id order, and the
   * {@code normalizer.lowercase} flag out of a {@code tokenizer.json}.
   *
   * @param file The {@code tokenizer.json} file.
   * @return The parsed fields.
   * @throws InvalidFormatException Thrown if the file is not a well-formed {@code tokenizer.json}.
   * @throws IOException Thrown if reading the file fails.
   */
  private static TokenizerJson readTokenizerJson(Path file) throws IOException {
    final String json = Files.readString(file);
    final JsonCursor cursor = new JsonCursor(json, file.getFileName().toString());
    cursor.skipWhitespace();
    cursor.expect('{');
    cursor.skipWhitespace();
    String modelType = null;
    List<String> orderedVocabulary = null;
    Boolean lowerCase = null;
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
            orderedVocabulary = model.orderedVocabulary();
          }
          case "normalizer" -> lowerCase = TeacherTokenizer.parseNormalizerLowercase(cursor);
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
    if (modelType == null) {
      throw new InvalidFormatException(file + " has no model.type");
    }
    return new TokenizerJson(modelType, orderedVocabulary, lowerCase);
  }

  /** The {@code model} object's type and, for a WordPiece dictionary, its rows in id order. */
  private record ModelSection(String type, List<String> orderedVocabulary) {
  }

  /**
   * Parses the {@code model} object for its {@code type} and, when the vocabulary is a WordPiece
   * dictionary, its rows in id order.
   *
   * @param cursor The cursor, positioned at the object's opening brace.
   * @return The parsed type and, for a dictionary vocabulary, the ordered rows.
   */
  private static ModelSection parseModel(JsonCursor cursor) throws InvalidFormatException {
    cursor.expect('{');
    cursor.skipWhitespace();
    String type = null;
    List<String> orderedVocabulary = null;
    if (cursor.peek() == '}') {
      cursor.consume();
      return new ModelSection(null, null);
    }
    while (true) {
      cursor.skipWhitespace();
      final String key = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      if ("type".equals(key)) {
        type = cursor.parseString();
      } else if ("vocab".equals(key) && cursor.peek() == '{') {
        orderedVocabulary = parseVocabularyDictionary(cursor);
      } else {
        cursor.skipValue();
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        return new ModelSection(type, orderedVocabulary);
      }
      throw cursor.malformed("Expected ',' or '}' after a model field, got '" + next + "'");
    }
  }

  /**
   * Parses a WordPiece {@code vocab} dictionary of {@code "token": id} pairs into the token list in
   * id order.
   *
   * @param cursor The cursor, positioned at the dictionary's opening brace.
   * @return The tokens in id order.
   * @throws InvalidFormatException Thrown if an id repeats or the ids are not a gapless range.
   */
  private static List<String> parseVocabularyDictionary(JsonCursor cursor)
      throws InvalidFormatException {
    cursor.expect('{');
    cursor.skipWhitespace();
    final Map<Long, String> tokenById = new LinkedHashMap<>();
    if (cursor.peek() == '}') {
      cursor.consume();
      return List.of();
    }
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
    final List<Map.Entry<Long, String>> entries = new ArrayList<>(tokenById.entrySet());
    entries.sort(Comparator.comparingLong(Map.Entry::getKey));
    final List<String> ordered = new ArrayList<>(entries.size());
    for (int row = 0; row < entries.size(); row++) {
      final Map.Entry<Long, String> entry = entries.get(row);
      if (entry.getKey() != row) {
        throw cursor.malformed("Vocabulary ids are not a gapless range: expected id " + row
            + " but found " + entry.getKey());
      }
      ordered.add(entry.getValue());
    }
    return ordered;
  }

}
