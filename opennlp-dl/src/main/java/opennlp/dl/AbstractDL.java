/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WordpieceTokenizer;
import opennlp.tools.util.InvalidFormatException;

/**
 * Base class for OpenNLP deep-learning classes using ONNX Runtime.
 */
public abstract class AbstractDL implements AutoCloseable {

  public static final String INPUT_IDS = "input_ids";
  public static final String ATTENTION_MASK = "attention_mask";
  public static final String TOKEN_TYPE_IDS = "token_type_ids";

  protected OrtEnvironment env;
  protected OrtSession session;
  protected Tokenizer tokenizer;
  protected Map<String, Integer> vocab;

  private final AtomicBoolean closed = new AtomicBoolean();

  private static final char BYTE_ORDER_MARK = '\uFEFF';

  /**
   * Loads a vocabulary {@link File} from disk. A file with an opening brace as its first
   * non-whitespace character is read as JSON: one object that maps each token to a non-negative
   * integer ID, as in {@code vocab.json}. Any other file is read as plain text with one token
   * per line, the line number being the ID, as in {@code vocab.txt}. A byte order mark at the
   * start of the file is not content in either format.
   *
   * @param vocabFile The vocabulary file.
   * @return A map of vocabulary words to IDs.
   * @throws IOException Thrown if the vocabulary file cannot be opened or read.
   * @throws InvalidFormatException Thrown if a JSON vocabulary is malformed, has a layout that
   *     is not supported, or contains a value that is not a non-negative integer. The message
   *     names the file and the offset or the token.
   */
  public Map<String, Integer> loadVocab(
      final File vocabFile) throws IOException {

    final String read = Files.readString(Path.of(vocabFile.getPath()), StandardCharsets.UTF_8);
    final String content = !read.isEmpty() && read.charAt(0) == BYTE_ORDER_MARK
        ? read.substring(1) : read;
    final String trimmed = content.trim();

    // Detect JSON format by leading brace
    if (trimmed.startsWith("{")) {
      try {
        return loadJsonVocab(trimmed);
      } catch (IllegalArgumentException e) {
        throw new InvalidFormatException(
            "Vocabulary file " + vocabFile.getName() + ": " + e.getMessage(), e);
      }
    }

    final Map<String, Integer> vocab =
        new HashMap<>();
    final AtomicInteger counter =
        new AtomicInteger(0);

    content.lines().forEach(line ->
        vocab.put(line, counter.getAndIncrement())
    );

    return vocab;
  }

  /**
   * Creates a {@link WordpieceTokenizer} that uses the
   * appropriate special tokens based on the vocabulary.
   * If the vocabulary contains RoBERTa-style tokens,
   * those are used. Otherwise, the BERT defaults are
   * used.
   *
   * @param vocab The vocabulary map.
   * @return A configured {@link WordpieceTokenizer}.
   */
  protected WordpieceTokenizer createTokenizer(
      final Map<String, Integer> vocab) {
    if (vocab.containsKey(
            WordpieceTokenizer.ROBERTA_CLS_TOKEN)
        && vocab.containsKey(
            WordpieceTokenizer.ROBERTA_SEP_TOKEN)) {
      final String unk = vocab.containsKey(
          WordpieceTokenizer.ROBERTA_UNK_TOKEN)
          ? WordpieceTokenizer.ROBERTA_UNK_TOKEN
          : WordpieceTokenizer.BERT_UNK_TOKEN;
      return new WordpieceTokenizer(
          vocab.keySet(),
          WordpieceTokenizer.ROBERTA_CLS_TOKEN,
          WordpieceTokenizer.ROBERTA_SEP_TOKEN,
          unk);
    }
    return new WordpieceTokenizer(vocab.keySet());
  }

  /**
   * Reads a JSON vocabulary as one object mapping tokens to non-negative integer IDs, as in
   * {@code vocab.json}. Keys are decoded from their escapes, and a later entry for the same
   * token overwrites an earlier one.
   *
   * @param json The JSON text of the vocabulary.
   * @return A map of vocabulary tokens to IDs.
   * @throws IllegalArgumentException Thrown if the text is not a single well-formed JSON object,
   *     or if a value of the vocabulary object is not a non-negative integer that fits into an
   *     {@code int}. The message names the offset or the token.
   */
  static Map<String, Integer> loadJsonVocab(final String json) {
    final List<JsonScan.Member> document = JsonScan.document(json);
    final Map<String, Integer> vocab = new HashMap<>();
    for (JsonScan.Member member : document) {
      try {
        vocab.put(member.key(), JsonScan.nonNegativeIntValue(json, member));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Expected one object mapping tokens to integer ids, as in vocab.json: "
                + e.getMessage(), e);
      }
    }
    return vocab;
  }

  /**
   * Closes the {@link OrtSession} owned by this instance.
   *
   * <p>The {@link OrtEnvironment} is not closed: {@link OrtEnvironment#getEnvironment()}
   * returns a process-wide instance shared by all deep-learning components.</p>
   *
   * <p>This method is idempotent: the session is closed on the first call only, and
   * subsequent calls have no effect.</p>
   *
   * @throws OrtException Thrown if it failed to close Ort resources.
   * @throws IllegalStateException Thrown if the session was already closed outside this method.
   */
  @Override
  public void close() throws OrtException, IllegalStateException {
    if (closed.compareAndSet(false, true) && session != null) {
      session.close();
    }
  }

}
