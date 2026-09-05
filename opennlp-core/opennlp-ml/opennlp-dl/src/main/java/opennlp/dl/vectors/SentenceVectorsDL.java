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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import opennlp.dl.AbstractDL;
import opennlp.dl.Tokens;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.tokenize.SubwordTokenizer;

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
 * {@link opennlp.tools.tokenize.WordpieceEncoder}) before wordpiece.
 * Output vectors change with the corrected encoding and tokenization;
 * any embeddings persisted from the previous behavior are not
 * comparable with the corrected output and must be re-embedded.</p>
 *
 * <p>This class is thread-safe and may be shared across threads: {@link #getVectors(String)}
 * holds no per-call instance state and the underlying {@link OrtSession} supports
 * concurrent execution. This thread-safety guarantee applies until {@link #close()}
 * is called; callers must not race {@code close()} with inference methods.</p>
 *
 * <p>{@link #getVectors(String)} is the primary entry point; {@link #embed(CharSequence)}
 * adapts it to the {@link TextEmbedder} contract. {@link #embedAll(List)} runs one batched
 * session per distinct tokenized length, so a batch of same-length inputs costs one
 * inference instead of one per input.</p>
 */
@ThreadSafe
public class SentenceVectorsDL extends AbstractDL implements TextEmbedder {

  // The hidden dimension declared by the model's output metadata, or a value <= 0 when the
  // model declares it dynamically; dimension() then probes once and caches here.
  private volatile int dimension;

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
    this.dimension = declaredOutputDimension(session);

  }

  /**
   * Generates vectors given a sentence.
   * 
   * @param sentence The input sentence.
   * @return The sentence vector.
   *
   * @throws IllegalArgumentException Thrown if {@code sentence} is {@code null}.
   * @throws OrtException Thrown if an error occurs during inference.
   */
  public float[] getVectors(final String sentence) throws OrtException {

    if (sentence == null) {
      throw new IllegalArgumentException("sentence must not be null");
    }

    final Tokens tokens = encode(sentence, tokenizer);

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
   * {@inheritDoc}
   *
   * <p>Adapts {@link #getVectors(String)} to the {@link TextEmbedder} contract. Empty or
   * unrecognized input is still run through the model, which returns the vector for the
   * wrapped {@code [CLS] ... [SEP]} sequence rather than a zero vector.</p>
   *
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   * @throws IllegalStateException Thrown if inference fails; the cause carries the
   *     underlying {@link OrtException}.
   */
  @Override
  public float[] embed(final CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    try {
      return getVectors(text instanceof String s ? s : text.toString());
    } catch (OrtException e) {
      throw new IllegalStateException("Sentence vector inference failed.", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Batched execution: the inputs are tokenized up front, grouped by tokenized length,
   * and each group runs through the session once with shape {@code [group size, length]}.
   * Grouping by length means a batch never pads, so every row is computed from exactly the
   * tensors its single-input call would have used. A length group of one executes the
   * same {@code [1, length]} shapes as {@link #getVectors(String)}.</p>
   *
   * @throws IllegalArgumentException Thrown if {@code texts} is {@code null} or contains
   *     {@code null}.
   * @throws IllegalStateException Thrown if inference fails; the cause carries the
   *     underlying {@link OrtException}.
   */
  @Override
  public float[][] embedAll(final List<? extends CharSequence> texts) {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    final float[][] vectors = new float[texts.size()][];
    if (texts.isEmpty()) {
      return vectors;
    }
    final Tokens[] encoded = new Tokens[texts.size()];
    final Map<Integer, List<Integer>> byLength = new HashMap<>();
    for (int i = 0; i < texts.size(); i++) {
      final CharSequence text = texts.get(i);
      if (text == null) {
        throw new IllegalArgumentException("texts[" + i + "] must not be null");
      }
      encoded[i] = encodeTokens(text);
      byLength.computeIfAbsent(encoded[i].ids().length, length -> new ArrayList<>()).add(i);
    }
    try {
      for (final List<Integer> group : byLength.values()) {
        runBatch(encoded, group, vectors);
      }
    } catch (OrtException e) {
      throw new IllegalStateException("Sentence vector inference failed.", e);
    }
    return vectors;
  }

  /**
   * Runs one inference over a group of same-length encodings and stores each row's
   * {@code [CLS]}-position vector under its original input index.
   *
   * @param encoded The tokenized inputs, indexed by input position.
   * @param group The input positions sharing one tokenized length, in input order.
   * @param vectors The output array to fill, indexed by input position.
   * @throws OrtException Thrown if an error occurs during inference.
   */
  private void runBatch(final Tokens[] encoded, final List<Integer> group,
      final float[][] vectors) throws OrtException {

    final int batch = group.size();
    final int length = encoded[group.get(0)].ids().length;
    final long[] ids = new long[batch * length];
    final long[] mask = new long[batch * length];
    final long[] types = new long[batch * length];
    for (int b = 0; b < batch; b++) {
      final Tokens tokens = encoded[group.get(b)];
      System.arraycopy(tokens.ids(), 0, ids, b * length, length);
      System.arraycopy(tokens.mask(), 0, mask, b * length, length);
      System.arraycopy(tokens.types(), 0, types, b * length, length);
    }

    final Map<String, OnnxTensor> inputs = new HashMap<>();
    final long[] shape = {batch, length};

    try {
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));

      inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape));

      inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(types), shape));

      try (OrtSession.Result result = session.run(inputs)) {
        // getValue() copies the tensor into Java arrays, so the result can be closed safely.
        final float[][][] v = (float[][][]) result.get(0).getValue();
        for (int b = 0; b < batch; b++) {
          vectors[group.get(b)] = v[b][0];
        }
      }
    } finally {
      inputs.values().forEach(OnnxTensor::close);
    }

  }

  /**
   * {@inheritDoc}
   *
   * <p>Read from the model's declared output metadata when it is static; a model that declares
   * the hidden dimension dynamically is probed with one inference on the first call and the
   * result cached.</p>
   */
  @Override
  public int dimension() {
    final int declared = dimension;
    if (declared > 0) {
      return declared;
    }
    synchronized (this) {
      if (dimension <= 0) {
        dimension = embed("a").length;
      }
      return dimension;
    }
  }

  /**
   * {@return the last dimension of the first output's declared shape, or {@code -1} when the
   * model declares it dynamically}
   *
   * @param session The model's ONNX session.
   * @throws OrtException Thrown if reading the output metadata fails.
   */
  private static int declaredOutputDimension(final OrtSession session) throws OrtException {
    final Iterator<NodeInfo> outputs = session.getOutputInfo().values().iterator();
    if (!outputs.hasNext() || !(outputs.next().getInfo() instanceof TensorInfo tensorInfo)) {
      return -1;
    }
    final long[] shape = tensorInfo.getShape();
    final long last = shape.length > 0 ? shape[shape.length - 1] : -1;
    return last > 0 && last <= Integer.MAX_VALUE ? (int) last : -1;
  }

  /** Encodes one sentence with model vocabulary ids. */
  static Tokens encode(String text, SubwordTokenizer tokenizer) {
    return encodeTokens(tokenizer, text);
  }

}
