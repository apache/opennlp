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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
 * graph accepts {@code input_ids}, optional {@code attention_mask}, and optional
 * {@code token_type_ids}. The encoder supplies only the inputs declared by the graph.
 * The pooled output is the mean of the rank-3 float {@code last_hidden_state} output over all
 * sequence positions. When that name is absent, the graph must have one rank-3 float output. The
 * attention mask is all ones because a batch is not padded (see {@link #encodeBatch(long[][])}).
 *
 * <p>Not thread-safe; a distillation drives one instance from a single thread. Close it to
 * release the native session.</p>
 */
final class OnnxTeacherEncoder implements AutoCloseable {

  /** The id-sequence input every transformer encoder graph declares. */
  private static final String INPUT_IDS = "input_ids";

  /** The optional attention-mask input. */
  private static final String ATTENTION_MASK = "attention_mask";

  /** The segment input the BERT-family graphs declare; fed all zeros. */
  private static final String TOKEN_TYPE_IDS = "token_type_ids";

  /** The rank of the last-hidden-state output: batch, position, hidden dimension. */
  private static final int HIDDEN_STATE_RANK = 3;

  /** The conventional name of a transformer encoder's last hidden state. */
  private static final String LAST_HIDDEN_STATE = "last_hidden_state";

  private final OrtEnvironment environment;
  private final OrtSession session;
  private final OnnxJavaType inputIdsType;
  private final OnnxJavaType attentionMaskType;
  private final OnnxJavaType tokenTypeIdsType;
  private final String hiddenStateOutput;
  private final AtomicBoolean closed = new AtomicBoolean();

  /** Holds the open session; created by {@link #load(Path)}. */
  private OnnxTeacherEncoder(OrtEnvironment environment, OrtSession session,
                             OnnxJavaType inputIdsType, OnnxJavaType attentionMaskType,
                             OnnxJavaType tokenTypeIdsType,
                             String hiddenStateOutput) {
    this.environment = environment;
    this.session = session;
    this.inputIdsType = inputIdsType;
    this.attentionMaskType = attentionMaskType;
    this.tokenTypeIdsType = tokenTypeIdsType;
    this.hiddenStateOutput = hiddenStateOutput;
  }

  /**
   * Loads a teacher's ONNX graph.
   *
   * @param onnxFile The ONNX file. Must not be {@code null} and must exist, must declare an
   *                 {@code input_ids} input, and must produce a named {@code last_hidden_state}
   *                 or one unambiguous rank-3 float tensor output.
   * @return The encoder.
   * @throws IllegalArgumentException Thrown if the file is missing, the graph has unsupported
   *     inputs, a sequence input has the wrong rank or element type, the hidden-state output is
   *     missing or ambiguous, or the runtime rejects the graph.
   */
  static OnnxTeacherEncoder load(Path onnxFile) {
    if (onnxFile == null) {
      throw new IllegalArgumentException("onnxFile must not be null");
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
    // Close the session if graph validation fails.
    try {
      if (!session.getInputNames().contains(INPUT_IDS)) {
        throw new IllegalArgumentException("ONNX graph " + onnxFile + " has no '" + INPUT_IDS
            + "' input; it does not look like a transformer encoder (inputs: "
            + session.getInputNames() + ")");
      }
      final Set<String> unsupportedInputs = new TreeSet<>(session.getInputNames());
      unsupportedInputs.removeAll(Set.of(INPUT_IDS, ATTENTION_MASK, TOKEN_TYPE_IDS));
      if (!unsupportedInputs.isEmpty()) {
        throw new IllegalArgumentException("ONNX graph " + onnxFile
            + " declares unsupported inputs: " + unsupportedInputs);
      }
      final Map<String, NodeInfo> inputInfo = session.getInputInfo();
      final OnnxJavaType inputIdsType = integerInputType(inputInfo, INPUT_IDS, onnxFile);
      final OnnxJavaType attentionMaskType = inputInfo.containsKey(ATTENTION_MASK)
          ? integerInputType(inputInfo, ATTENTION_MASK, onnxFile) : null;
      final OnnxJavaType tokenTypeIdsType = inputInfo.containsKey(TOKEN_TYPE_IDS)
          ? integerInputType(inputInfo, TOKEN_TYPE_IDS, onnxFile) : null;

      final Map<String, NodeInfo> outputInfo = session.getOutputInfo();
      final String hiddenStateOutput;
      if (outputInfo.containsKey(LAST_HIDDEN_STATE)) {
        if (!isHiddenState(outputInfo.get(LAST_HIDDEN_STATE))) {
          throw new IllegalArgumentException("ONNX graph " + onnxFile + " declares '"
              + LAST_HIDDEN_STATE + "', but it is not a rank-3 FLOAT tensor");
        }
        hiddenStateOutput = LAST_HIDDEN_STATE;
      } else {
        final List<String> candidates = new ArrayList<>();
        for (final Map.Entry<String, NodeInfo> output : outputInfo.entrySet()) {
          if (isHiddenState(output.getValue())) {
            candidates.add(output.getKey());
          }
        }
        if (candidates.isEmpty()) {
          throw new IllegalArgumentException("ONNX graph " + onnxFile + " has no rank-3 float "
              + "tensor output (a last hidden state) to pool (outputs: "
              + outputInfo.keySet() + ")");
        }
        if (candidates.size() > 1) {
          throw new IllegalArgumentException("ONNX graph " + onnxFile
              + " has multiple rank-3 FLOAT outputs and none is named '" + LAST_HIDDEN_STATE
              + "': " + candidates);
        }
        hiddenStateOutput = candidates.get(0);
      }
      return new OnnxTeacherEncoder(environment, session, inputIdsType, attentionMaskType,
          tokenTypeIdsType, hiddenStateOutput);
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
   * Reads and validates one integer sequence input.
   *
   * @param inputInfo The graph's input metadata.
   * @param name      The input name.
   * @param onnxFile  The graph file, for error messages.
   * @return The input's INT32 or INT64 element type.
   */
  private static OnnxJavaType integerInputType(Map<String, NodeInfo> inputInfo, String name,
                                                Path onnxFile) {
    final NodeInfo node = inputInfo.get(name);
    if (node == null || !(node.getInfo() instanceof TensorInfo tensorInfo)) {
      throw new IllegalArgumentException("ONNX graph " + onnxFile + " input '" + name
          + "' must be a tensor");
    }
    if (tensorInfo.getShape().length != 2) {
      throw new IllegalArgumentException("ONNX graph " + onnxFile + " input '" + name
          + "' must have rank 2, but has rank " + tensorInfo.getShape().length);
    }
    if (tensorInfo.type != OnnxJavaType.INT32 && tensorInfo.type != OnnxJavaType.INT64) {
      throw new IllegalArgumentException("ONNX graph " + onnxFile + " input '" + name
          + "' must be INT32 or INT64, but is " + tensorInfo.type);
    }
    return tensorInfo.type;
  }

  /**
   * Checks whether output metadata describes a last hidden state.
   *
   * @param node The output metadata.
   * @return {@code true} for a rank-three FLOAT tensor.
   */
  private static boolean isHiddenState(NodeInfo node) {
    return node.getInfo() instanceof TensorInfo tensorInfo
        && tensorInfo.type == OnnxJavaType.FLOAT
        && tensorInfo.getShape().length == HIDDEN_STATE_RANK;
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
   *              {@code null} or empty and must not contain a null or empty sequence.
   * @return The pooled vectors, {@code [batchSize][hiddenDimension]}.
   * @throws IllegalArgumentException Thrown if the batch is empty, contains a null or empty
   *     sequence, is ragged, or the runtime rejects the input.
   */
  float[][] encodeBatch(long[][] batch) {
    if (batch == null || batch.length == 0) {
      throw new IllegalArgumentException("batch must not be null or empty");
    }
    for (int i = 0; i < batch.length; i++) {
      if (batch[i] == null) {
        throw new IllegalArgumentException("batch[" + i + "] must not be null");
      }
      if (batch[i].length == 0) {
        throw new IllegalArgumentException("batch[" + i + "] must not be empty");
      }
    }
    final int sequenceLength = batch[0].length;
    for (final long[] sequence : batch) {
      if (sequence.length != sequenceLength) {
        throw new IllegalArgumentException("batch is ragged: sequence lengths differ");
      }
    }
    try {
      final Map<String, OnnxTensor> inputs = new HashMap<>();
      OnnxTensor mask = null;
      OnnxTensor tokenTypeIds = null;
      try (OnnxTensor inputIds = createIntegerTensor(batch, inputIdsType, INPUT_IDS)) {
        inputs.put(INPUT_IDS, inputIds);
        if (attentionMaskType != null) {
          final long[][] attentionMask = new long[batch.length][sequenceLength];
          for (final long[] row : attentionMask) {
            Arrays.fill(row, 1L);
          }
          mask = createIntegerTensor(attentionMask, attentionMaskType, ATTENTION_MASK);
          inputs.put(ATTENTION_MASK, mask);
        }
        if (tokenTypeIdsType != null) {
          tokenTypeIds = createIntegerTensor(new long[batch.length][sequenceLength],
              tokenTypeIdsType, TOKEN_TYPE_IDS);
          inputs.put(TOKEN_TYPE_IDS, tokenTypeIds);
        }
        try (OrtSession.Result result = session.run(inputs)) {
          final OnnxValue value = result.get(hiddenStateOutput)
              .orElseThrow(() -> new IllegalStateException("Output '" + hiddenStateOutput
                  + "' missing from the graph's results"));
          final float[][][] hidden = (float[][][]) value.getValue();
          validateOutputShape(hidden, batch.length, sequenceLength);
          final float[][] pooled = new float[batch.length][];
          for (int i = 0; i < batch.length; i++) {
            final float[][] states = hidden[i];
            final double[] sum = new double[states[0].length];
            for (final float[] state : states) {
              for (int d = 0; d < sum.length; d++) {
                sum[d] += state[d];
              }
            }
            final float[] mean = new float[sum.length];
            for (int d = 0; d < sum.length; d++) {
              final double meanValue = sum[d] / states.length;
              mean[d] = Double.isNaN(meanValue) ? 0 : (float) meanValue;
            }
            pooled[i] = mean;
          }
          return pooled;
        } finally {
          if (tokenTypeIds != null) {
            tokenTypeIds.close();
          }
          if (mask != null) {
            mask.close();
          }
        }
      }
    } catch (OrtException e) {
      throw new IllegalArgumentException("ONNX forward pass failed: " + e.getMessage(), e);
    }
  }

  /**
   * Creates an INT32 or INT64 tensor for a graph input.
   *
   * @param values The input values.
   * @param type   The element type declared by the graph.
   * @param name   The input name, for range errors.
   * @return The created tensor.
   * @throws IllegalArgumentException Thrown if an INT32 input value is outside its range.
   * @throws OrtException Thrown if ONNX Runtime cannot create the tensor.
   */
  private OnnxTensor createIntegerTensor(long[][] values, OnnxJavaType type, String name)
      throws OrtException {
    if (type == OnnxJavaType.INT64) {
      return OnnxTensor.createTensor(environment, values);
    }
    final int[][] converted = new int[values.length][];
    for (int row = 0; row < values.length; row++) {
      converted[row] = new int[values[row].length];
      for (int column = 0; column < values[row].length; column++) {
        final long value = values[row][column];
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
          throw new IllegalArgumentException(name + "[" + row + "][" + column + "] value "
              + value + " does not fit an INT32 tensor");
        }
        converted[row][column] = (int) value;
      }
    }
    return OnnxTensor.createTensor(environment, converted);
  }

  /**
   * Verifies that the graph preserved the input batch and sequence dimensions.
   *
   * @param hidden         The last hidden state.
   * @param batchSize      The input batch size.
   * @param sequenceLength The input sequence length.
   */
  private void validateOutputShape(float[][][] hidden, int batchSize, int sequenceLength) {
    if (hidden.length != batchSize) {
      throw new IllegalArgumentException("ONNX output '" + hiddenStateOutput
          + "' batch dimension " + hidden.length + " does not match input batch dimension "
          + batchSize);
    }
    int dimension = -1;
    for (int row = 0; row < hidden.length; row++) {
      final float[][] states = hidden[row];
      if (states.length != sequenceLength) {
        throw new IllegalArgumentException("ONNX output '" + hiddenStateOutput + "' row " + row
            + " has sequence dimension " + states.length + ", expected " + sequenceLength);
      }
      for (int position = 0; position < states.length; position++) {
        if (dimension < 0) {
          dimension = states[position].length;
          if (dimension == 0) {
            throw new IllegalArgumentException("ONNX output '" + hiddenStateOutput
                + "' has an empty hidden dimension");
          }
        } else if (states[position].length != dimension) {
          throw new IllegalArgumentException("ONNX output '" + hiddenStateOutput + "' row "
              + row + " position " + position + " has hidden dimension "
              + states[position].length + ", expected " + dimension);
        }
      }
    }
  }

  /**
   * Closes the native session; calling this more than once is a no-op after the first call.
   *
   * <p>The shared {@link OrtEnvironment} remains open because {@link
   * OrtEnvironment#getEnvironment()} returns a process-wide singleton. The atomic guard prevents
   * a second {@link OrtSession#close()} call.</p>
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
