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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <p>This class is thread-safe and may be shared across threads: up to
 * {@link InferenceOptions#getMaxConcurrentInferences()} inferences run at once, each holding a
 * buffer of the pool only while it runs, a caller beyond the limit waits for one to finish, and
 * the underlying {@link OrtSession} supports concurrent execution. Interrupting a waiting caller
 * throws an {@link EmbeddingException} with the interrupt flag restored.
 * This thread-safety guarantee applies until {@link #close()} is called; callers must not race
 * {@code close()} with inference methods.</p>
 *
 * <p>{@link #embedAll(List)} batches its texts according to the configured
 * {@link PaddingStrategy}. By default it does not pad on the CPU and runs one inference per
 * distinct tokenized length. On a GPU it pads to the longest text of a batch, which runs texts of
 * mixed lengths together, except for a model with a {@code sentence_embedding} output, which is
 * not padded by default because such a model may ignore the attention mask. No inference exceeds
 * 16384 token positions, except that a single text whose encoding is wider runs as an inference
 * of its own.</p>
 *
 * <p>The input and output tensors of an inference live in one direct buffer. The instance keeps
 * one such buffer per concurrent inference for reuse until {@link #close()}. A buffer is
 * allocated when more inferences overlap than before or when a slot needs to grow, and stays
 * as large as the largest inference it has held. The pool can therefore hold the concurrency
 * limit times the largest inference's direct memory: a token output at the
 * {@value #MAX_BATCH_TOKEN_POSITIONS} token
 * position bound with a hidden dimension of 1024 amounts to about 67 MB per buffer. With the
 * default of one inference per available processor, 64 concurrent inferences can retain about
 * 4 GiB. Set {@link InferenceOptions#setMaxConcurrentInferences(int)} to fit the direct memory
 * budget; the default does not account for {@code -XX:MaxDirectMemorySize}. Replaced buffers
 * await garbage collection, so growing a slot temporarily needs memory for both buffers.</p>
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

  /** The {@link PaddingStrategy} on the CPU, or on a GPU for a pooled output, if none is given. */
  public static final PaddingStrategy DEFAULT_CPU_PADDING = PaddingStrategy.EXACT_LENGTH;

  /**
   * The {@link PaddingStrategy} on a GPU if none is given and the model has no pooled output, so
   * padding is masked out of the pooling, and the vocabulary has a padding token. A GPU runs one
   * wide inference faster than many narrow ones.
   */
  public static final PaddingStrategy DEFAULT_GPU_PADDING = PaddingStrategy.LONGEST;

  private static final String SENTENCE_EMBEDDING = "sentence_embedding";
  private static final int POOLED_RANK = 2;
  private static final int TOKEN_RANK = 3;

  private final Pooling pooling;
  private final boolean normalize;
  private final int maxLength;
  private final String outputName;
  private final boolean pooledOutput;
  private final int dimension;
  private final PaddingStrategy padding;
  private final long padTokenId;

  /**
   * The permits of the inferences that may run at once. An inference holds a permit while it
   * holds a buffer, so the pool never holds more buffers than there are permits.
   */
  private final Semaphore inferencePermits;

  /** The direct buffers an inference may reuse, one per permit at most. Emptied on {@link #close()}. */
  private final BlockingQueue<ByteBuffer> spareBuffers;

  /** The direct buffers this instance has allocated, for tests. */
  private final AtomicInteger allocatedBuffers = new AtomicInteger();

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

    this(model, vocabulary, lowerCase,
        new SentenceVectorsOptions(pooling, normalize, maxLength, PaddingStrategy.AUTO),
        new InferenceOptions());

  }

  /**
   * Instantiates a sentence vector generator with vector settings and ONNX session settings.
   * With {@link PaddingStrategy#MAX_LENGTH}, a probe for an unknown output dimension also runs
   * at the configured maximum length and can fail if it exceeds the model's position limit.
   *
   * @param model The file name of a sentence vectors ONNX model.
   * @param vocabulary The file name of the vocabulary file for the model.
   * @param lowerCase {@code true} for uncased models, {@code false} for cased models. A lower case
   *     setting in {@code inferenceOptions} takes precedence.
   * @param vectorOptions The pooling, normalization, truncation and padding settings. Must not
   *     be {@code null}. {@link PaddingStrategy#AUTO} selects the device and model default.
   * @param inferenceOptions The ONNX session settings. Must not be {@code null}.
   *
   * @throws IllegalArgumentException Thrown if {@code vectorOptions} or {@code inferenceOptions}
   *     is {@code null}, if the inference options contain invalid split settings, a negative GPU
   *     device id, a negative intra-op thread count, or a negative concurrency limit, if the
   *     model has no output of shape {@code [batch, hidden]} or {@code [batch, tokens, hidden]},
   *     or if padding is requested without a vocabulary padding token.
   * @throws OrtException Thrown if the model cannot be loaded, the output dimension probe fails,
   *     or the requested GPU is unavailable. ONNX Runtime does not fall back to the CPU.
   * @throws IOException Thrown if the model or vocabulary cannot be read.
   * @throws opennlp.tools.util.InvalidFormatException Thrown if a JSON vocabulary is malformed,
   *     has an unsupported layout, or disagrees with the configured lower casing.
   *
   * @since 3.0.0
   */
  public SentenceVectorsDL(final File model, final File vocabulary, final boolean lowerCase,
      final SentenceVectorsOptions vectorOptions, final InferenceOptions inferenceOptions)
      throws OrtException, IOException {

    super(model, vocabulary,
        sessionOptions(validateConstructorArguments(inferenceOptions, vectorOptions)),
        resolveLowerCase(inferenceOptions, lowerCase));
    try {
      final int concurrency = inferenceOptions.getMaxConcurrentInferences() > 0
          ? inferenceOptions.getMaxConcurrentInferences()
          : Runtime.getRuntime().availableProcessors();
      this.inferencePermits = new Semaphore(concurrency);
      this.spareBuffers = new ArrayBlockingQueue<>(concurrency);
      this.pooling = vectorOptions.pooling();
      this.normalize = vectorOptions.normalize();
      this.maxLength = vectorOptions.maxLength();
      final Map<String, NodeInfo> outputs = session.getOutputInfo();
      this.outputName = selectOutput(outputs);
      final long[] shape = ((TensorInfo) outputs.get(outputName).getInfo()).getShape();
      this.pooledOutput = shape.length == POOLED_RANK;
      this.padding = vectorOptions.padding() == PaddingStrategy.AUTO
          ? devicePadding(inferenceOptions.isGpu()) : vectorOptions.padding();
      this.padTokenId = this.padding == PaddingStrategy.EXACT_LENGTH ? 0 : padTokenId();
      final long declared = shape[shape.length - 1];
      this.dimension = declared > 0 && declared <= Integer.MAX_VALUE
          ? (int) declared : measureDimension();
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
   * Checks the arguments of a public constructor that the base class does not check.
   *
   * @param inferenceOptions The inference options.
   * @param vectorOptions The vector options.
   * @return {@code inferenceOptions}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or the concurrency
   *     limit is negative.
   */
  private static InferenceOptions validateConstructorArguments(
      final InferenceOptions inferenceOptions, final SentenceVectorsOptions vectorOptions) {
    ParamChecks.requireNonNullArg(vectorOptions, "vectorOptions");
    ParamChecks.requireNonNullArg(inferenceOptions, "inferenceOptions");
    ParamChecks.requireNonNegative(inferenceOptions.getMaxConcurrentInferences(),
        "maxConcurrentInferences");
    return inferenceOptions;
  }

  /**
   * {@return the padding selected by {@link PaddingStrategy#AUTO}:
   * {@link #DEFAULT_GPU_PADDING} on a GPU for a token output if the vocabulary has a padding
   * token, {@link #DEFAULT_CPU_PADDING} otherwise} A model with a pooled output is not padded by
   * default because it may ignore the attention mask, in which case padding changes the vectors.
   *
   * @param gpu {@code true} if the session runs on a GPU.
   */
  PaddingStrategy devicePadding(final boolean gpu) {
    return gpu && !pooledOutput && hasPadToken() ? DEFAULT_GPU_PADDING : DEFAULT_CPU_PADDING;
  }

  /**
   * Generates vectors given a sentence.
   * 
   * @param sentence The input sentence.
   * @return The sentence vector.
   *
   * @throws IllegalArgumentException Thrown if {@code sentence} is {@code null} or if the
   *     inference does not fit in one direct buffer.
   * @throws EmbeddingException Thrown if interrupted while waiting for an inference permit.
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
   * <p>The inputs are tokenized up front and batched according to the configured
   * {@link PaddingStrategy}, so that no inference exceeds 16384 token positions; a single text
   * whose encoding is wider runs alone. Without padding, every row is computed from the tensors
   * its single-input call would have used. A padded position is {@code 0} in the attention mask,
   * so vector {@code i} matches what {@link #embed(CharSequence)} returns for input {@code i} up
   * to floating-point rounding, provided the model honors the attention mask. A
   * {@code sentence_embedding} output that ignores the mask changes with padding.</p>
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
   * Splits the indices of a call into the batches that run as single inferences. No batch
   * exceeds {@value #MAX_BATCH_TOKEN_POSITIONS} token positions, but each has at least one row, so
   * a row wider than the bound runs alone.
   *
   * <p>Under {@link PaddingStrategy#EXACT_LENGTH} a batch contains indices of one tokenized length,
   * in call order. Under {@link PaddingStrategy#MAX_LENGTH} every row is the maximum length wide,
   * so batches are consecutive indices in call order. Under {@link PaddingStrategy#LONGEST} the
   * indices are ordered by tokenized length and cut where the next row would exceed the cap.</p>
   *
   * @param encoded The encodings of the call, in call order. An encoding contains at least
   *     the start and end tokens, so no length is zero.
   * @return The batches, each a list of indices into {@code encoded}.
   */
  List<List<Integer>> batches(final Tokens[] encoded) {
    final List<List<Integer>> batches = new ArrayList<>();
    if (padding == PaddingStrategy.EXACT_LENGTH) {
      final Map<Integer, List<Integer>> byLength = new LinkedHashMap<>();
      for (int i = 0; i < encoded.length; i++) {
        byLength.computeIfAbsent(encoded[i].ids().length, length -> new ArrayList<>()).add(i);
      }
      for (final Map.Entry<Integer, List<Integer>> group : byLength.entrySet()) {
        addCapped(batches, group.getValue(), group.getKey());
      }
      return batches;
    }
    final List<Integer> order = new ArrayList<>(encoded.length);
    for (int i = 0; i < encoded.length; i++) {
      order.add(i);
    }
    if (padding == PaddingStrategy.MAX_LENGTH) {
      addCapped(batches, order, maxLength);
      return batches;
    }
    order.sort(Comparator.comparingInt(i -> encoded[i].ids().length));
    int from = 0;
    for (int i = 0; i < order.size(); i++) {
      // Sorted ascending, so the row being added is the longest of its batch.
      final long width = encoded[order.get(i)].ids().length;
      if (i > from && (i - from + 1) * width > MAX_BATCH_TOKEN_POSITIONS) {
        batches.add(order.subList(from, i));
        from = i;
      }
    }
    if (from < order.size()) {
      batches.add(order.subList(from, order.size()));
    }
    return batches;
  }

  /**
   * Adds indices that all run at one row width as consecutive batches of at most
   * {@value #MAX_BATCH_TOKEN_POSITIONS} token positions, and at least one row, so a row wider
   * than the bound runs alone.
   *
   * @param batches The batches to add to.
   * @param indices The indices to split, in the order they run.
   * @param width The row width they run at.
   */
  private static void addCapped(final List<List<Integer>> batches, final List<Integer> indices,
      final int width) {
    final int rows = Math.max(1, MAX_BATCH_TOKEN_POSITIONS / width);
    for (int from = 0; from < indices.size(); from += rows) {
      batches.add(indices.subList(from, Math.min(from + rows, indices.size())));
    }
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
   * Drops the pooled direct buffers and closes the ONNX session. An {@link OrtException} from the
   * session is rethrown as the cause of an {@link UncheckedIOException}.
   */
  @Override
  public void close() {
    spareBuffers.clear();
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
   * Runs one inference over a batch of encodings and returns one sentence vector per encoding, in
   * order. Rows shorter than the batch width are padded: {@code input_ids} with the padding
   * token id, {@code attention_mask} and {@code token_type_ids} with {@code 0}. The input and
   * output tensors share one direct buffer, which ONNX Runtime reads and writes in place, so no
   * tensor is copied through the Java heap. The buffer comes from the pool of this instance when
   * one is big enough and returns to the pool afterwards, so calls that fit the available slots
   * allocate no new direct memory. When the concurrency limit is reached, a further call waits
   * for a running inference to finish.
   *
   * @param batch The encodings, of any lengths.
   * @return The sentence vectors.
   * @throws IllegalArgumentException Thrown if the inference needs more direct buffer memory than
   *     one buffer can hold.
   * @throws OrtException Thrown if an error occurs during inference.
   * @throws EmbeddingException Thrown if interrupted while waiting for an inference permit.
   */
  private float[][] run(final Tokens[] batch) throws OrtException {

    final int length = width(batch);
    final int bytes = bufferBytes(batch.length, length, dimension, pooledOutput);
    try {
      inferencePermits.acquire();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EmbeddingException("Interrupted while waiting for an inference permit.", e);
    }
    try {
      return run(batch, length, bytes);
    } finally {
      inferencePermits.release();
    }

  }

  /**
   * {@return the row width of one inference} It is the maximum length under
   * {@link PaddingStrategy#MAX_LENGTH}, otherwise the longest row of the batch.
   *
   * @param batch The encodings of one inference, at least one.
   */
  private int width(final Tokens[] batch) {
    if (padding == PaddingStrategy.MAX_LENGTH) {
      return maxLength;
    }
    int longest = 0;
    for (final Tokens row : batch) {
      longest = Math.max(longest, row.ids().length);
    }
    return longest;
  }

  /**
   * Runs one inference while holding a permit, in a buffer taken from the pool.
   *
   * @param batch The encodings of the inference, of any lengths.
   * @param length The row width of the inference.
   * @param bytes The direct buffer bytes of the inference.
   * @return The sentence vectors.
   * @throws OrtException Thrown if an error occurs during inference.
   */
  private float[][] run(final Tokens[] batch, final int length, final int bytes)
      throws OrtException {

    final ByteBuffer buffer = takeBuffer(bytes);
    final int inputBytes = batch.length * length * Long.BYTES;
    final int outputBytes = bytes - 3 * inputBytes;
    try {
      final LongBuffer ids = slice(buffer, 0, inputBytes).asLongBuffer();
      final LongBuffer mask = slice(buffer, inputBytes, inputBytes).asLongBuffer();
      final LongBuffer types = slice(buffer, 2 * inputBytes, inputBytes).asLongBuffer();
      for (final Tokens tokens : batch) {
        final int rowLength = tokens.ids().length;
        ids.put(tokens.ids());
        mask.put(tokens.mask());
        types.put(tokens.types());
        // The buffer is reused, so the padding positions are written, not left over.
        for (int i = rowLength; i < length; i++) {
          ids.put(padTokenId);
          mask.put(0L);
          types.put(0L);
        }
      }
      ids.rewind();
      mask.rewind();
      types.rewind();
      final FloatBuffer values = slice(buffer, 3 * inputBytes, outputBytes).asFloatBuffer();

      final Map<String, OnnxTensor> inputs = inputs(ids, mask, types,
          new long[] {batch.length, length});
      try (OnnxTensor output = OnnxTensor.createTensor(env, values, pooledOutput
              ? new long[] {batch.length, dimension} : new long[] {batch.length, length, dimension});
           OrtSession.Result ignored = session.run(inputs, Map.of(outputName, output))) {
        return vectors(batch, length, values);
      } finally {
        inputs.values().forEach(OnnxTensor::close);
      }
    } finally {
      releaseBuffer(buffer);
    }

  }

  /**
   * {@return the hidden dimension of the model, measured with one inference of the empty text}
   * Used when the model metadata does not declare the dimension; ONNX Runtime allocates the
   * output of this inference, since its size is not known yet. The probe runs at the width
   * {@link #width(Tokens[])} gives the encoding, as an inference of {@link #run(Tokens[])} does.
   *
   * @throws OrtException Thrown if an error occurs during inference.
   */
  private int measureDimension() throws OrtException {
    final Tokens tokens = encodeTokens("");
    final int length = width(new Tokens[] {tokens});
    final long[] ids = new long[length];
    final long[] mask = new long[length];
    final long[] types = new long[length];
    final int rowLength = tokens.ids().length;
    System.arraycopy(tokens.ids(), 0, ids, 0, rowLength);
    System.arraycopy(tokens.mask(), 0, mask, 0, rowLength);
    System.arraycopy(tokens.types(), 0, types, 0, rowLength);
    // mask and types stay 0 past the row; only the ids need the padding token id.
    Arrays.fill(ids, rowLength, length, padTokenId);
    final Map<String, OnnxTensor> inputs = inputs(LongBuffer.wrap(ids),
        LongBuffer.wrap(mask), LongBuffer.wrap(types), new long[] {1, length});
    try (OrtSession.Result result = session.run(inputs, Set.of(outputName))) {
      final long[] shape = ((TensorInfo) result.get(0).getInfo()).getShape();
      return (int) shape[shape.length - 1];
    } finally {
      inputs.values().forEach(OnnxTensor::close);
    }
  }

  /**
   * {@return the three input tensors of an inference over the given token ids, attention mask and
   * token type ids}
   *
   * @param ids The token ids.
   * @param mask The attention mask.
   * @param types The token type ids.
   * @param shape The shape of each input, {@code [rows, length]}.
   * @throws OrtException Thrown if a tensor cannot be created; the tensors created before are
   *     closed.
   */
  private Map<String, OnnxTensor> inputs(final LongBuffer ids, final LongBuffer mask,
      final LongBuffer types, final long[] shape) throws OrtException {
    final Map<String, OnnxTensor> inputs = new HashMap<>();
    try {
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, ids, shape));
      inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env, mask, shape));
      inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env, types, shape));
      return inputs;
    } catch (final OrtException | RuntimeException e) {
      inputs.values().forEach(OnnxTensor::close);
      throw e;
    }
  }

  /** {@return the number of direct buffers this instance has allocated} */
  int allocatedBuffers() {
    return allocatedBuffers.get();
  }

  /**
   * {@return the direct buffer bytes of one inference over {@code rows} encodings of the given
   * tokenized length} The buffer holds the three inputs of {@code rows * length} longs each and
   * the output, which holds {@code rows} vectors of the hidden dimension, or
   * {@code rows * length} of them for a token output.
   *
   * @param rows The number of encodings in the inference.
   * @param length The tokenized length of every encoding.
   * @param dimension The hidden dimension of the model.
   * @param pooledOutput Whether the model output is pooled to one vector per encoding.
   * @throws IllegalArgumentException Thrown if the bytes exceed the capacity of one direct buffer.
   */
  static int bufferBytes(final int rows, final int length, final int dimension,
      final boolean pooledOutput) {
    final long positions = (long) rows * length;
    final long bytes;
    try {
      final long inputBytes = Math.multiplyExact(Math.multiplyExact(positions, 3), Long.BYTES);
      final long outputPositions = pooledOutput ? rows : positions;
      final long outputBytes = Math.multiplyExact(
          Math.multiplyExact(outputPositions, dimension), (long) Float.BYTES);
      bytes = Math.addExact(inputBytes, outputBytes);
    } catch (final ArithmeticException e) {
      // The true size exceeds long, so it certainly exceeds one direct buffer.
      throw new IllegalArgumentException("The inference needs more direct buffer memory than the "
          + Integer.MAX_VALUE + " bytes of one buffer hold. Reduce maxLength.", e);
    }
    if (bytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("The inference needs " + bytes
          + " bytes of direct buffer memory, which exceeds the " + Integer.MAX_VALUE
          + " bytes of one buffer. Reduce maxLength.");
    }
    return (int) bytes;
  }

  /**
   * {@return a direct buffer that holds at least {@code bytes} bytes} The caller holds an
   * inference permit. One buffer is taken from the pool and replaced if too small. The other
   * slots remain available for reuse. An empty pool requires a new buffer.
   *
   * @param bytes The number of bytes the inference needs.
   */
  private ByteBuffer takeBuffer(final int bytes) {
    final ByteBuffer buffer = spareBuffers.poll();
    if (buffer != null && buffer.capacity() >= bytes) {
      return buffer;
    }
    allocatedBuffers.incrementAndGet();
    return ByteBuffer.allocateDirect(bytes);
  }

  /**
   * Returns a buffer to the pool of this instance. The pool has room, since it holds no more
   * buffers than there are permits.
   *
   * @param buffer The buffer an inference is done with.
   */
  private void releaseBuffer(final ByteBuffer buffer) {
    spareBuffers.offer(buffer);
  }

  /**
   * {@return {@code bytes} bytes of {@code buffer} from {@code offset} on, in native byte order,
   * the order ONNX Runtime reads and writes}
   *
   * @param buffer The buffer to slice.
   * @param offset The index of the first byte.
   * @param bytes The number of bytes.
   */
  private ByteBuffer slice(final ByteBuffer buffer, final int offset, final int bytes) {
    return buffer.slice(offset, bytes).order(ByteOrder.nativeOrder());
  }

  /**
   * Reads one sentence vector per encoding from the output of an inference.
   *
   * @param batch The encodings of the inference.
   * @param length The row length of the inference.
   * @param values The output, row after row.
   * @return The sentence vectors.
   */
  private float[][] vectors(final Tokens[] batch, final int length, final FloatBuffer values) {
    final float[][] vectors = new float[batch.length][];
    for (int b = 0; b < batch.length; b++) {
      if (pooledOutput) {
        vectors[b] = new float[dimension];
        values.get(b * dimension, vectors[b]);
      } else {
        vectors[b] = pool(values, b * length * dimension, batch[b].mask());
      }
      if (normalize) {
        scaleToUnitLength(vectors[b]);
      }
    }
    return vectors;
  }

  /**
   * Pools the token vectors of one input into its sentence vector.
   *
   * @param values The token vectors of the whole batch, row after row.
   * @param offset The index of the first value of the input in {@code values}.
   * @param mask The attention mask of the input.
   * @return A new array holding the sentence vector.
   */
  private float[] pool(final FloatBuffer values, final int offset, final long[] mask) {
    final float[] sum = new float[dimension];
    if (pooling == Pooling.CLS) {
      values.get(offset, sum);
      return sum;
    }
    int count = 0;
    for (int t = 0; t < mask.length; t++) {
      if (mask[t] != 0) {
        final int start = offset + t * dimension;
        for (int d = 0; d < dimension; d++) {
          sum[d] += values.get(start + d);
        }
        count++;
      }
    }
    for (int d = 0; d < dimension; d++) {
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
