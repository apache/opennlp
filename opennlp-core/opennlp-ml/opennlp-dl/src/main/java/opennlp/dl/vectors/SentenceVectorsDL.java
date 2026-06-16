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

package opennlp.dl.vectors;

import java.io.File;
import java.io.IOException;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.dl.AbstractDL;
import opennlp.dl.Tokens;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.tokenize.Tokenizer;


/**
 * Facilitates the generation of sentence vectors using
 * a sentence-transformers model converted to ONNX.
 *
 * <p>The model inputs follow the standard single-segment BERT
 * encoding: {@code attention_mask} is {@code 1} for every real
 * token and {@code token_type_ids} is {@code 0} throughout.</p>
 *
 * <p><b>Release note (OpenNLP 3.0.0):</b> prior releases sent an
 * all-zero {@code attention_mask} and all-one {@code token_type_ids},
 * so the encoder attended to nothing and the output vectors were
 * incorrect. Additionally, tokenization now performs BERT basic
 * tokenization (lower casing and accent stripping by default, see
 * {@link opennlp.tools.tokenize.BertTokenizer}) before wordpiece.
 * Output vectors change with the corrected encoding and tokenization;
 * any embeddings persisted from the previous behavior are not
 * comparable with the corrected output and must be re-embedded.</p>
 *
 * <p>This class is thread-safe and may be shared across threads: {@link #getVectors(String)}
 * holds no per-call instance state and the underlying {@link OrtSession} supports
 * concurrent execution. This thread-safety guarantee applies until {@link #close()}
 * is called; callers must not race {@code close()} with inference methods.</p>
 */
@ThreadSafe
public class SentenceVectorsDL extends AbstractDL {

  /**
   * Instantiates a {@link SentenceVectorsDL sentence vector generator} for an
   * uncased model. Input text is lower cased and accent stripped during
   * tokenization, as required by uncased models such as the
   * sentence-transformers MiniLM family.
   *
   * @param model The file name of a sentence vectors ONNX model.
   * @param vocabulary The file name of the vocabulary file for the model.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   */
  public SentenceVectorsDL(final File model, final File vocabulary)
      throws OrtException, IOException {

    this(model, vocabulary, true);

  }

  /**
   * Instantiates a {@link SentenceVectorsDL sentence vector generator} using ONNX models.
   *
   * @param model The file name of a sentence vectors ONNX model.
   * @param vocabulary The file name of the vocabulary file for the model.
   * @param lowerCase {@code true} for uncased models (lower casing and accent
   *     stripping during tokenization), {@code false} for cased models.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   */
  public SentenceVectorsDL(final File model, final File vocabulary, final boolean lowerCase)
      throws OrtException, IOException {

    super(model, vocabulary, new OrtSession.SessionOptions(), lowerCase);

  }

  /**
   * Generates vectors given a sentence.
   * 
   * @param sentence The input sentence.
   * @return The sentence vector.
   *
   * @throws OrtException Thrown if an error occurs during inference.
   */
  public float[] getVectors(final String sentence) throws OrtException {

    final Tokens tokens = tokenize(sentence, tokenizer, vocab);

    final Map<String, OnnxTensor> inputs = new HashMap<>();

    try {
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.ids()),
          new long[] {1, tokens.ids().length}));

      inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env,
          LongBuffer.wrap(tokens.mask()), new long[] {1, tokens.mask().length}));

      inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env,
          LongBuffer.wrap(tokens.types()), new long[] {1, tokens.types().length}));

      try (OrtSession.Result result = session.run(inputs)) {
        // getValue() copies the tensor into Java arrays, so the result can be closed safely.
        final float[][][] v = (float[][][]) result.get(0).getValue();
        return v[0][0];
      }
    } finally {
      inputs.values().forEach(OnnxTensor::close);
    }

  }

  /**
   * Encodes text as model inputs: wordpiece token ids, an attention mask of ones,
   * and single-segment (all zero) token type ids.
   *
   * @param text The text to encode.
   * @param tokenizer The wordpiece tokenizer matching the {@code vocab}.
   * @param vocab The vocabulary map.
   * @return The encoded {@link Tokens}.
   *
   * @throws IllegalArgumentException Thrown if the tokenizer emits a token that is
   *     not present in the vocabulary.
   */
  static Tokens tokenize(final String text, final Tokenizer tokenizer,
      final Map<String, Integer> vocab) {

    final String[] tokens = tokenizer.tokenize(text);

    final long[] ids = new long[tokens.length];

    for (int x = 0; x < tokens.length; x++) {
      final Integer id = vocab.get(tokens[x]);
      if (id == null) {
        throw new IllegalArgumentException("Token '" + tokens[x]
            + "' is not present in the vocabulary; the vocabulary file does not match the model.");
      }
      ids[x] = id;
    }

    final long[] mask = new long[ids.length];
    Arrays.fill(mask, 1);

    final long[] types = new long[ids.length];

    return new Tokens(tokens, ids, mask, types);

  }

}
