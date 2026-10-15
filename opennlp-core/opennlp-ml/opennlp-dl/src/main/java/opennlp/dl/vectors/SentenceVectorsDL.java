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
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import opennlp.dl.AbstractDL;
import opennlp.dl.InferenceOptions;
import opennlp.dl.Tokens;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.embeddings.EmbeddingException;
import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.util.ParamChecks;

/**
 * Facilitates the generation of sentence vectors using
 * a sentence-transformers model converted to ONNX.
 *
 * <p>The model inputs follow the standard single-segment BERT
 * encoding: {@code attention_mask} is {@code 1} for every real
 * token and {@code token_type_ids} is {@code 0} throughout.</p>
 *
 * <p>The sentence vector is read from a {@code sentence_embedding} output of shape
 * {@code [batch, hidden]} if the model has one. Otherwise the token vectors of the first
 * {@code [batch, tokens, hidden]} output are pooled with the configured {@link Pooling}. By
 * default the token vectors are averaged and the result is scaled to unit length, as
 * sentence-transformers does for the MiniLM family. Text longer than the maximum length is
 * truncated, keeping the final {@code [SEP]} token.</p>
 *
 * <p><b>Release note (OpenNLP 3.0.0):</b> prior releases sent an
 * all-zero {@code attention_mask} and all-one {@code token_type_ids},
 * so the encoder attended to nothing and the output vectors were
 * incorrect, and they returned the raw {@code [CLS]} vector instead of the pooled
 * sentence vector. Additionally, tokenization now performs BERT basic
 * tokenization (lower casing and accent stripping by default, see
 * {@link opennlp.tools.tokenize.WordpieceEncoder}) before wordpiece.
 * Output vectors change with the corrected encoding, tokenization and pooling;
 * any embeddings persisted from the previous behavior are not
 * comparable with the corrected output and must be re-embedded.</p>
 *
 * <p>This class is thread-safe and may be shared across threads: the inference methods hold no
 * per-call instance state and the underlying {@link OrtSession} supports concurrent execution.
 * This thread-safety guarantee applies until {@link #close()} is called; callers must not race
 * {@code close()} with inference methods.</p>
 *
 * <p>{@link #embedAll(List)} runs one batched session per distinct tokenized length, so a batch
 * of same-length inputs costs one inference instead of one per input. A group larger than
 * 16384 token positions is split into several inferences.</p>
 */
@ThreadSafe
public class SentenceVectorsDL extends AbstractDL implements TextEmbedder {

  /** The maximum number of tokens per input if none is given, the BERT position limit. */
  public static final int DEFAULT_MAX_LENGTH = 512;

  /**
   * The upper bound on the token positions of one inference, its row count times its row length.
   * {@link #embedAll(List)} splits a larger group into consecutive inferences, so the tensors of
   * one inference stay bounded however many texts a call has. A single input longer than the
   * bound still runs on its own. The bound is 32 rows at the BERT length of
   * {@value #DEFAULT_MAX_LENGTH}.
   */
  static final int MAX_BATCH_TOKEN_POSITIONS = 16384;

  private static final String SENTENCE_EMBEDDING = "sentence_embedding";
  private static final int POOLED_RANK = 2;
  private static final int TOKEN_RANK = 3;
  private static final int MIN_LENGTH = 2;

  private final Pooling pooling;
  private final boolean normalize;
  private final int maxLength;
  private final String outputName;
  private final Set<String> outputNames;
  private final boolean pooledOutput;
  private final int dimension;

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
   * @throws opennlp.tools.util.InvalidFormatException Thrown if a JSON {@code vocabulary}
   *     is malformed, has an unsupported layout, or sets a lower casing that disagrees with
   *     the lower casing the component is configured with.
   */
  public SentenceVectorsDL(final File model, final File vocabulary)
      throws OrtException, IOException {

    this(model, vocabulary, true);

  }

  /**
   * Instantiates a {@link SentenceVectorsDL sentence vector generator} that averages the token
   * vectors, scales the result to unit length and truncates input to
   * {@value #DEFAULT_MAX_LENGTH} tokens.
   *
   * @param model The file name of a sentence vectors ONNX model.
   * @param vocabulary The file name of the vocabulary file for the model.
   * @param lowerCase {@code true} for uncased models (lower casing and accent
   *     stripping during tokenization), {@code false} for cased models.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   * @throws opennlp.tools.util.InvalidFormatException Thrown if a JSON {@code vocabulary}
   *     is malformed, has an unsupported layout, or sets a lower casing that disagrees with
   *     the lower casing the component is configured with.
   */
  public SentenceVectorsDL(final File model, final File vocabulary, final boolean lowerCase)
      throws OrtException, IOException {

    this(model, vocabulary, lowerCase, Pooling.MEAN, true, DEFAULT_MAX_LENGTH);

  }

  /**
   * Instantiates a {@link SentenceVectorsDL sentence vector generator} using ONNX models.
   *
   * @param model The file name of a sentence vectors ONNX model.
   * @param vocabulary The file name of the vocabulary file for the model.
   * @param lowerCase {@code true} for uncased models (lower casing and accent
   *     stripping during tokenization), {@code false} for cased models.
   * @param pooling How token vectors are pooled. Not used for a model with a
   *     {@code sentence_embedding} output. Must not be {@code null}.
   * @param normalize {@code true} to scale every vector to unit length.
   * @param maxLength The maximum number of tokens per input, {@code [CLS]} and {@code [SEP]}
   *     included; longer input is truncated. Must be at least {@code 2}.
   *
   * @throws IllegalArgumentException Thrown if {@code pooling} is {@code null}, if
   *     {@code maxLength} is less than {@code 2}, or if the model has no output of shape
   *     {@code [batch, hidden]} or {@code [batch, tokens, hidden]}.
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   * @throws opennlp.tools.util.InvalidFormatException Thrown if a JSON {@code vocabulary}
   *     is malformed, has an unsupported layout, or sets a lower casing that disagrees with
   *     the lower casing the component is configured with.
   */
  public SentenceVectorsDL(final File model, final File vocabulary, final boolean lowerCase,
      final Pooling pooling, final boolean normalize, final int maxLength)
      throws OrtException, IOException {

    this(model, vocabulary, lowerCase, pooling, normalize, maxLength, new InferenceOptions());

  }

  /**
   * Instantiates a {@link SentenceVectorsDL sentence vector generator} using ONNX models and the
   * given {@link InferenceOptions}, so the session can run on a GPU through
   * {@code opennlp-dl-gpu}.
   *
   * @param model The file name of a sentence vectors ONNX model.
   * @param vocabulary The file name of the vocabulary file for the model.
   * @param lowerCase {@code true} for uncased models (lower casing and accent
   *     stripping during tokenization), {@code false} for cased models. A lower case setting in
   *     {@code inferenceOptions} takes precedence.
   * @param pooling How token vectors are pooled. Not used for a model with a
   *     {@code sentence_embedding} output. Must not be {@code null}.
   * @param normalize {@code true} to scale every vector to unit length.
   * @param maxLength The maximum number of tokens per input, {@code [CLS]} and {@code [SEP]}
   *     included; longer input is truncated. Must be at least {@code 2}.
   * @param inferenceOptions The {@link InferenceOptions}, of which the GPU settings and the lower
   *     case setting are used. Must not be {@code null}.
   *
   * @throws IllegalArgumentException Thrown if {@code pooling} or {@code inferenceOptions} is
   *     {@code null}, if {@code inferenceOptions} holds invalid split options or requests a GPU
   *     with a negative device id, if {@code maxLength} is less than {@code 2}, or if the model
   *     has no output of shape {@code [batch, hidden]} or {@code [batch, tokens, hidden]}.
   * @throws OrtException Thrown if the {@code model} cannot be loaded, or if a GPU was requested
   *     and ONNX Runtime cannot provide it. ONNX Runtime does not fall back to the CPU.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   * @throws opennlp.tools.util.InvalidFormatException Thrown if a JSON {@code vocabulary}
   *     is malformed, has an unsupported layout, or sets a lower casing that disagrees with
   *     the lower casing the component is configured with.
   *
   * @since 3.0.0
   */
  public SentenceVectorsDL(final File model, final File vocabulary, final boolean lowerCase,
      final Pooling pooling, final boolean normalize, final int maxLength,
      final InferenceOptions inferenceOptions)
      throws OrtException, IOException {

    super(model, vocabulary, sessionOptions(inferenceOptions),
        resolveLowerCase(inferenceOptions, lowerCase));
    try {
      ParamChecks.requireNonNullArg(pooling, "pooling");
      if (maxLength < MIN_LENGTH) {
        throw new IllegalArgumentException("maxLength must be at least " + MIN_LENGTH);
      }
      this.pooling = pooling;
      this.normalize = normalize;
      this.maxLength = maxLength;
      final Map<String, NodeInfo> outputs = session.getOutputInfo();
      this.outputName = selectOutput(outputs);
      this.outputNames = Set.of(outputName);
      final long[] shape = ((TensorInfo) outputs.get(outputName).getInfo()).getShape();
      this.pooledOutput = shape.length == POOLED_RANK;
      final long declared = shape[shape.length - 1];
      this.dimension = declared > 0 && declared <= Integer.MAX_VALUE
          ? (int) declared : run(new Tokens[] {encodeTokens("")})[0].length;
    } catch (final OrtException | RuntimeException e) {
      try {
        super.close();
      } catch (final OrtException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }

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

    ParamChecks.requireNonNullArg(sentence, "sentence");
    return run(new Tokens[] {encode(sentence)})[0];

  }

  /**
   * {@inheritDoc}
   *
   * <p>Empty or unrecognized input is still run through the model as the wrapped
   * {@code [CLS] [SEP]} sequence rather than returning a zero vector.</p>
   */
  @Override
  public float[] embed(final CharSequence text) {
    ParamChecks.requireNonNullArg(text, "text");
    try {
      return run(new Tokens[] {encode(text)})[0];
    } catch (final OrtException e) {
      throw new EmbeddingException("Sentence vector inference failed.", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>The inputs are grouped by tokenized length, never padded, and each group runs in
   * inferences of at most 16384 token positions. Every row is computed from the tensors its
   * single-input call would have used.</p>
   */
  @Override
  public float[][] embedAll(final List<? extends CharSequence> texts) {
    ParamChecks.requireNonNullArg(texts, "texts");
    final CharSequence[] checked = new CharSequence[texts.size()];
    for (int i = 0; i < checked.length; i++) {
      checked[i] = texts.get(i);
      if (checked[i] == null) {
        throw new IllegalArgumentException("texts[" + i + "] must not be null");
      }
    }
    final Tokens[] encoded = new Tokens[checked.length];
    for (int i = 0; i < checked.length; i++) {
      encoded[i] = encode(checked[i]);
    }
    final float[][] vectors = new float[checked.length][];
    try {
      for (final List<Integer> group : batches(encoded)) {
        final Tokens[] batch = new Tokens[group.size()];
        for (int b = 0; b < batch.length; b++) {
          batch[b] = encoded[group.get(b)];
        }
        final float[][] rows = run(batch);
        for (int b = 0; b < batch.length; b++) {
          vectors[group.get(b)] = rows[b];
        }
      }
    } catch (final OrtException e) {
      throw new EmbeddingException("Sentence vector inference failed.", e);
    }
    return vectors;
  }

  /**
   * Splits the indices of a call into the batches that run as single inferences. A batch contains
   * indices of one tokenized length, in call order, and no more rows than fit in
   * {@value #MAX_BATCH_TOKEN_POSITIONS} token positions, but always at least one, so a row
   * wider than the bound runs alone.
   *
   * @param encoded The encodings of the call, in call order. An encoding contains at least
   *     the start and end tokens, so no length is zero.
   * @return The batches, each a list of indices into {@code encoded}.
   */
  static List<List<Integer>> batches(final Tokens[] encoded) {
    final Map<Integer, List<Integer>> byLength = new LinkedHashMap<>();
    for (int i = 0; i < encoded.length; i++) {
      byLength.computeIfAbsent(encoded[i].ids().length, length -> new ArrayList<>()).add(i);
    }
    final List<List<Integer>> batches = new ArrayList<>();
    for (final Map.Entry<Integer, List<Integer>> group : byLength.entrySet()) {
      final int rows = Math.max(1, MAX_BATCH_TOKEN_POSITIONS / group.getKey());
      final List<Integer> indices = group.getValue();
      for (int from = 0; from < indices.size(); from += rows) {
        batches.add(indices.subList(from, Math.min(from + rows, indices.size())));
      }
    }
    return batches;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Read from the model's declared output shape, or from one inference at construction if
   * the model declares the hidden dimension dynamically.</p>
   */
  @Override
  public int dimension() {
    return dimension;
  }

  /**
   * Closes the ONNX session. An {@link OrtException} from the session is rethrown as the cause
   * of an {@link UncheckedIOException}.
   */
  @Override
  public void close() {
    try {
      super.close();
    } catch (final OrtException e) {
      throw new UncheckedIOException(new IOException("Cannot close the ONNX session.", e));
    }
  }

  /**
   * Encodes a text and truncates it to the maximum length, keeping the final {@code [SEP]}.
   *
   * @param text The text to encode. Must not be {@code null}.
   * @return The encoded text of at most the maximum length.
   */
  private Tokens encode(final CharSequence text) {
    final Tokens tokens = encodeTokens(text);
    final int length = tokens.ids().length;
    if (length <= maxLength) {
      return tokens;
    }
    final String[] pieces = new String[maxLength];
    final long[] ids = new long[maxLength];
    final long[] mask = new long[maxLength];
    final long[] types = new long[maxLength];
    System.arraycopy(tokens.tokens(), 0, pieces, 0, maxLength - 1);
    System.arraycopy(tokens.ids(), 0, ids, 0, maxLength - 1);
    System.arraycopy(tokens.mask(), 0, mask, 0, maxLength);
    System.arraycopy(tokens.types(), 0, types, 0, maxLength);
    pieces[maxLength - 1] = tokens.tokens()[length - 1];
    ids[maxLength - 1] = tokens.ids()[length - 1];
    return new Tokens(pieces, ids, mask, types);
  }

  /**
   * Runs one inference over encodings of the same length and returns one sentence vector per
   * encoding, in order. The input and output tensors live in direct buffers, which ONNX Runtime
   * reads and writes in place, so no tensor is copied through the Java heap.
   *
   * @param batch The encodings, all of the same length.
   * @return The sentence vectors.
   * @throws OrtException Thrown if an error occurs during inference.
   */
  private float[][] run(final Tokens[] batch) throws OrtException {

    final int length = batch[0].ids().length;
    final LongBuffer ids = directLongs(batch.length * length);
    final LongBuffer mask = directLongs(batch.length * length);
    final LongBuffer types = directLongs(batch.length * length);
    for (final Tokens tokens : batch) {
      ids.put(tokens.ids());
      mask.put(tokens.mask());
      types.put(tokens.types());
    }
    ids.rewind();
    mask.rewind();
    types.rewind();

    final Map<String, OnnxTensor> inputs = new HashMap<>();
    final long[] shape = {batch.length, length};
    OnnxTensor pinned = null;

    try {
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, ids, shape));

      inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env, mask, shape));

      inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env, types, shape));

      // dimension is 0 only while the constructor measures it; that first call reads the
      // output tensor instead of a preallocated direct buffer.
      if (dimension > 0) {
        final int rows = pooledOutput ? batch.length : batch.length * length;
        final FloatBuffer values = direct(rows * dimension * Float.BYTES).asFloatBuffer();
        pinned = OnnxTensor.createTensor(env, values, pooledOutput
            ? new long[] {batch.length, dimension} : new long[] {batch.length, length, dimension});
        try (OrtSession.Result ignored = session.run(inputs, Map.of(outputName, pinned))) {
          return vectors(batch, length, dimension, values);
        }
      }
      try (OrtSession.Result result = session.run(inputs, outputNames)) {
        final OnnxTensor output = (OnnxTensor) result.get(outputName).orElseThrow(
            () -> new OrtException("The model returned no output named " + outputName));
        final long[] outputShape = output.getInfo().getShape();
        return vectors(batch, length, (int) outputShape[outputShape.length - 1],
            output.getFloatBuffer());
      }
    } finally {
      inputs.values().forEach(OnnxTensor::close);
      if (pinned != null) {
        pinned.close();
      }
    }

  }

  /**
   * Reads one sentence vector per encoding from the output of an inference.
   *
   * @param batch The encodings of the inference.
   * @param length The row length of the inference.
   * @param hidden The length of one vector.
   * @param values The output, row after row.
   * @return The sentence vectors.
   */
  private float[][] vectors(final Tokens[] batch, final int length, final int hidden,
      final FloatBuffer values) {
    final float[][] vectors = new float[batch.length][];
    for (int b = 0; b < batch.length; b++) {
      if (pooledOutput) {
        vectors[b] = new float[hidden];
        values.get(b * hidden, vectors[b]);
      } else {
        vectors[b] = pool(values, b * length * hidden, hidden, batch[b].mask());
      }
      if (normalize) {
        scaleToUnitLength(vectors[b]);
      }
    }
    return vectors;
  }

  /**
   * {@return a direct buffer of {@code size} longs in native byte order}
   *
   * @param size The number of longs.
   */
  private static LongBuffer directLongs(final int size) {
    return direct(size * Long.BYTES).asLongBuffer();
  }

  /**
   * {@return a direct buffer of {@code bytes} bytes in native byte order, which ONNX Runtime uses
   * without a copy}
   *
   * @param bytes The capacity in bytes.
   */
  private static ByteBuffer direct(final int bytes) {
    return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
  }

  /**
   * Pools the token vectors of one input into its sentence vector.
   *
   * @param values The token vectors of the whole batch, row after row.
   * @param offset The index of the first value of the input in {@code values}.
   * @param hidden The length of one token vector.
   * @param mask The attention mask of the input.
   * @return A new array holding the sentence vector.
   */
  private float[] pool(final FloatBuffer values, final int offset, final int hidden,
      final long[] mask) {
    final float[] sum = new float[hidden];
    if (pooling == Pooling.CLS) {
      values.get(offset, sum);
      return sum;
    }
    int count = 0;
    for (int t = 0; t < mask.length; t++) {
      if (mask[t] != 0) {
        final int start = offset + t * hidden;
        for (int d = 0; d < hidden; d++) {
          sum[d] += values.get(start + d);
        }
        count++;
      }
    }
    for (int d = 0; d < hidden; d++) {
      sum[d] /= Math.max(count, 1);
    }
    return sum;
  }

  /**
   * Scales a vector to unit length in place. A zero vector is left as it is.
   *
   * @param vector The vector to scale.
   */
  private void scaleToUnitLength(final float[] vector) {
    double squares = 0;
    for (final float value : vector) {
      squares += (double) value * value;
    }
    if (squares > 0) {
      final double norm = Math.sqrt(squares);
      for (int d = 0; d < vector.length; d++) {
        vector[d] = (float) (vector[d] / norm);
      }
    }
  }

  /**
   * Selects the output the sentence vector is read from: a {@code sentence_embedding} output of
   * rank 2 if present, otherwise the first output of rank 3, otherwise the first of rank 2.
   *
   * @param outputs The outputs of the model, in declaration order.
   * @return The name of the selected output.
   * @throws IllegalArgumentException Thrown if no output has rank 2 or 3.
   */
  private String selectOutput(final Map<String, NodeInfo> outputs) {
    if (rank(outputs.get(SENTENCE_EMBEDDING)) == POOLED_RANK) {
      return SENTENCE_EMBEDDING;
    }
    String pooled = null;
    for (final Map.Entry<String, NodeInfo> output : outputs.entrySet()) {
      final int rank = rank(output.getValue());
      if (rank == TOKEN_RANK) {
        return output.getKey();
      }
      if (rank == POOLED_RANK && pooled == null) {
        pooled = output.getKey();
      }
    }
    if (pooled == null) {
      throw new IllegalArgumentException(
          "The model has no output of shape [batch, hidden] or [batch, tokens, hidden].");
    }
    return pooled;
  }

  /**
   * {@return the rank of a float tensor output, or {@code -1} if the output is missing or not a
   * float tensor}
   *
   * @param output The output to check, or {@code null}.
   */
  private int rank(final NodeInfo output) {
    if (output != null && output.getInfo() instanceof TensorInfo tensor
        && tensor.type == OnnxJavaType.FLOAT) {
      return tensor.getShape().length;
    }
    return -1;
  }

}
