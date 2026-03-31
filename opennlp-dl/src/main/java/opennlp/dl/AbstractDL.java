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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WordpieceTokenizer;

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

  private static final Pattern JSON_ENTRY_PATTERN =
      Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*(\\d+)");

  /**
   * Loads a vocabulary {@link File} from disk.
   * Supports both plain text files (one token per
   * line) and JSON files mapping tokens to integer
   * IDs.
   *
   * @param vocabFile The vocabulary file.
   * @return A map of vocabulary words to IDs.
   * @throws IOException Thrown if the vocabulary
   *     file cannot be opened or read.
   */
  public Map<String, Integer> loadVocab(
      final File vocabFile) throws IOException {

    final Path vocabPath =
        Path.of(vocabFile.getPath());
    final String content = Files.readString(
        vocabPath, StandardCharsets.UTF_8);
    final String trimmed = content.trim();

    // Detect JSON format by leading brace
    if (trimmed.startsWith("{")) {
      return loadJsonVocab(trimmed);
    }

    final Map<String, Integer> vocab =
        new HashMap<>();
    final AtomicInteger counter =
        new AtomicInteger(0);

    try (Stream<String> lines = Files.lines(
        vocabPath, StandardCharsets.UTF_8)) {
      lines.forEach(line ->
          vocab.put(line, counter.getAndIncrement())
      );
    }

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

  private Map<String, Integer> loadJsonVocab(final String json) {

    final Map<String, Integer> vocab = new HashMap<>();
    final Matcher matcher = JSON_ENTRY_PATTERN.matcher(json);

    while (matcher.find()) {
      final String token = matcher.group(1)
          .replace("\\\"", "\"")
          .replace("\\\\", "\\")
          .replace("\\/", "/")
          .replace("\\n", "\n")
          .replace("\\t", "\t");
      final int id = Integer.parseInt(matcher.group(2));
      vocab.put(token, id);
    }

    return vocab;
  }

  /**
   * Closes this resource, relinquishing any underlying resources.
   *
   * @throws OrtException Thrown if it failed to close Ort resources.
   * @throws IllegalStateException Thrown if the underlying resources were already closed.
   */
  @Override
  public void close() throws OrtException, IllegalStateException {
    if (session != null) {
      session.close();
    }
    if (env != null) {
      env.close();
    }
  }

}
