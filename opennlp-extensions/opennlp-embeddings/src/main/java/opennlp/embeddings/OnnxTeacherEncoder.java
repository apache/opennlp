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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * Runs a teacher transformer over id sequences through its ONNX graph and mean-pools the last
 * hidden states, the forward pass
 * <a href="https://github.com/MinishLab/model2vec">Model2Vec</a>'s distillation performs per
 * vocabulary token. The
 * graph is fed exactly the inputs it declares: {@code input_ids} and {@code attention_mask} for
 * every model, plus a zero {@code token_type_ids} for the BERT-family graphs that ask for one.
 * The pooled output is the mean of the single rank-3 float output (the
 * {@code last_hidden_state}) over all of a sequence's positions; the attention mask is all ones,
 * because a batch is never padded (see {@link #encodeBatch(long[][])}).
 *
 * <p>Not thread-safe; a distillation drives one instance from a single thread. Close it to
 * release the native session.</p>
 */
final class OnnxTeacherEncoder implements AutoCloseable {

  /** The id-sequence input every transformer encoder graph declares. */
  private static final String INPUT_IDS = "input_ids";

  /** The attention-mask input every transformer encoder graph declares. */
  private static final String ATTENTION_MASK = "attention_mask";

  /** The segment input the BERT-family graphs declare; fed all zeros. */
  private static final String TOKEN_TYPE_IDS = "token_type_ids";

  /** The rank of the last-hidden-state output: batch, position, hidden dimension. */
  private static final int HIDDEN_STATE_RANK = 3;

  private final OrtEnvironment environment;
  private final OrtSession session;
  private final boolean wantsTokenTypeIds;
  private final String hiddenStateOutput;
  private final AtomicBoolean closed = new AtomicBoolean();

  /** Holds the open session; created by {@link #load(Path)}. */
  private OnnxTeacherEncoder(OrtEnvironment environment, OrtSession session,
                             boolean wantsTokenTypeIds, String hiddenStateOutput) {
    this.environment = environment;
    this.session = session;
    this.wantsTokenTypeIds = wantsTokenTypeIds;
    this.hiddenStateOutput = hiddenStateOutput;
  }

  /**
   * Loads a teacher's ONNX graph.
   *
   * @param onnxFile The ONNX file. Must not be {@code null} and must exist, must declare an
   *                 {@code input_ids} input, and must produce a rank-3 float tensor output; the
   *                 first such output is taken as the last hidden state and pooled.
   * @return The encoder.
   * @throws IllegalArgumentException Thrown if the file is missing, the graph has no
   *     {@code input_ids} input or no rank-3 float tensor output, or the runtime rejects the
   *     graph.
   */
  static OnnxTeacherEncoder load(Path onnxFile) {
    if (onnxFile == null) {
      throw new IllegalArgumentException("OnnxFile must not be null");
    }
    if (!Files.isRegularFile(onnxFile)) {
      throw new IllegalArgumentException("File does not exist or is not a regular file: "
          + onnxFile);
    }
    final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    final OrtSession session;
    try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
      session = environment.createSession(onnxFile.toString(), options);
    } catch (OrtException e) {
      throw new IllegalArgumentException("Failed to load ONNX graph " + onnxFile + ": "
          + e.getMessage(), e);
    }
    // The session is open from here on, so every exit below closes it: an inspection that
    // rejects the graph, or that fails outright, must not leak the native handle.
    try {
      if (!session.getInputNames().contains(INPUT_IDS)) {
        throw new IllegalArgumentException("ONNX graph " + onnxFile + " has no '" + INPUT_IDS
            + "' input; it does not look like a transformer encoder (inputs: "
            + session.getInputNames() + ")");
      }
      final boolean wantsTokenTypeIds = session.getInputNames().contains(TOKEN_TYPE_IDS);
      String hiddenStateOutput = null;
      for (final Map.Entry<String, NodeInfo> output : session.getOutputInfo().entrySet()) {
        if (output.getValue().getInfo() instanceof TensorInfo tensorInfo
            && tensorInfo.type == OnnxJavaType.FLOAT
            && tensorInfo.getShape().length == HIDDEN_STATE_RANK) {
          hiddenStateOutput = output.getKey();
          break;
        }
      }
      if (hiddenStateOutput == null) {
        throw new IllegalArgumentException("ONNX graph " + onnxFile + " has no rank-3 float "
            + "tensor output (a last hidden state) to pool (outputs: "
            + session.getOutputInfo().keySet() + ")");
      }
      return new OnnxTeacherEncoder(environment, session, wantsTokenTypeIds, hiddenStateOutput);
    } catch (OrtException e) {
      final IllegalArgumentException failure = new IllegalArgumentException(
          "Failed to inspect ONNX graph " + onnxFile + ": " + e.getMessage(), e);
      closeAfterFailure(session, failure);
      throw failure;
    } catch (RuntimeException e) {
      closeAfterFailure(session, e);
      throw e;
    }
  }

  /**
   * Closes a session on a failing load path, reporting a close failure as a suppressed exception
   * of the failure being thrown rather than in place of it.
   *
   * @param session The session to close.
   * @param failure The exception the caller is about to throw.
   */
  private static void closeAfterFailure(OrtSession session, RuntimeException failure) {
    try {
      session.close();
    } catch (OrtException e) {
      failure.addSuppressed(e);
    }
  }

  /**
   * Runs one batch of id sequences and mean-pools each sequence's hidden states. All sequences
   * in a batch must have the same length (the distillation wraps every vocabulary token in the
   * same bos/eos pair, so they do); the attention mask is all ones and no padding is needed.
   *
   * @param batch The id sequences, {@code [batchSize][sequenceLength]}. Must not be
   *              {@code null} or empty.
   * @return The pooled vectors, {@code [batchSize][hiddenDimension]}.
   * @throws IllegalArgumentException Thrown if the batch is empty or ragged, or the runtime
   *     rejects the input.
   */
  float[][] encodeBatch(long[][] batch) {
    if (batch == null || batch.length == 0) {
      throw new IllegalArgumentException("Batch must not be null or empty");
    }
    final int sequenceLength = batch[0].length;
    for (final long[] sequence : batch) {
      if (sequence.length != sequenceLength) {
        throw new IllegalArgumentException("Batch is ragged: sequence lengths differ");
      }
    }
    final long[][] attentionMask = new long[batch.length][sequenceLength];
    for (final long[] mask : attentionMask) {
      Arrays.fill(mask, 1L);
    }
    try {
      final Map<String, OnnxTensor> inputs = new HashMap<>();
      OnnxTensor tokenTypeIds = null;
      try (OnnxTensor inputIds = OnnxTensor.createTensor(environment, batch);
           OnnxTensor mask = OnnxTensor.createTensor(environment, attentionMask)) {
        inputs.put(INPUT_IDS, inputIds);
        inputs.put(ATTENTION_MASK, mask);
        if (wantsTokenTypeIds) {
          tokenTypeIds = OnnxTensor.createTensor(environment,
              new long[batch.length][sequenceLength]);
          inputs.put(TOKEN_TYPE_IDS, tokenTypeIds);
        }
        try (OrtSession.Result result = session.run(inputs)) {
          final OnnxValue value = result.get(hiddenStateOutput)
              .orElseThrow(() -> new IllegalStateException("Output '" + hiddenStateOutput
                  + "' missing from the graph's results"));
          final float[][][] hidden = (float[][][]) value.getValue();
          final float[][] pooled = new float[batch.length][];
          for (int i = 0; i < batch.length; i++) {
            final float[][] states = hidden[i];
            final float[] sum = new float[states[0].length];
            for (final float[] state : states) {
              for (int d = 0; d < sum.length; d++) {
                sum[d] += state[d];
              }
            }
            for (int d = 0; d < sum.length; d++) {
              sum[d] /= states.length;
              if (Float.isNaN(sum[d])) {
                sum[d] = 0;
              }
            }
            pooled[i] = sum;
          }
          return pooled;
        } finally {
          if (tokenTypeIds != null) {
            tokenTypeIds.close();
          }
        }
      }
    } catch (OrtException e) {
      throw new IllegalArgumentException("ONNX forward pass failed: " + e.getMessage(), e);
    }
  }

  /**
   * Closes the native session; calling this more than once is a no-op after the first call.
   *
   * <p>The {@link OrtEnvironment} is deliberately not closed: {@link
   * OrtEnvironment#getEnvironment()} returns a process-wide singleton shared with every other
   * ONNX component in the JVM, so closing it here would tear down an environment they still use.
   * {@link OrtSession#close()} rejects a second call, hence the guard.</p>
   */
  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      try {
        session.close();
      } catch (OrtException e) {
        // Closing a native resource must not mask a distillation result.
      }
    }
  }
}
