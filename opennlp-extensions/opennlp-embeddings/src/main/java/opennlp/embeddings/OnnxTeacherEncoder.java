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

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * Runs a teacher transformer over id sequences through its ONNX graph and mean-pools the last
 * hidden states, the forward pass Model2Vec's distillation performs per vocabulary token. The
 * graph is fed exactly the inputs it declares: {@code input_ids} and {@code attention_mask} for
 * every model, plus a zero {@code token_type_ids} for the BERT-family graphs that ask for one.
 * The pooled output is the mask-weighted mean of the single rank-3 float output (the
 * {@code last_hidden_state}), over the non-padding positions only.
 *
 * <p>Not thread-safe; a distillation drives one instance from a single thread. Close it to
 * release the native session.</p>
 */
final class OnnxTeacherEncoder implements AutoCloseable {

  private final OrtEnvironment environment;
  private final OrtSession session;
  private final boolean wantsTokenTypeIds;
  private final String hiddenStateOutput;

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
   *                 {@code input_ids} input, and must produce exactly the rank-3 float
   *                 last-hidden-state output this encoder pools.
   * @return The encoder.
   * @throws IllegalArgumentException Thrown if the file is missing, the graph has no
   *     {@code input_ids} input or no rank-3 float output, or the runtime rejects the graph.
   */
  static OnnxTeacherEncoder load(Path onnxFile) {
    if (onnxFile == null) {
      throw new IllegalArgumentException("OnnxFile must not be null");
    }
    if (!Files.isRegularFile(onnxFile)) {
      throw new IllegalArgumentException("File does not exist or is not a regular file: "
          + onnxFile);
    }
    try {
      final OrtEnvironment environment = OrtEnvironment.getEnvironment();
      final OrtSession session = environment.createSession(onnxFile.toString(),
          new OrtSession.SessionOptions());
      if (!session.getInputNames().contains("input_ids")) {
        session.close();
        throw new IllegalArgumentException("ONNX graph " + onnxFile + " has no 'input_ids' "
            + "input; it does not look like a transformer encoder (inputs: "
            + session.getInputNames() + ")");
      }
      final boolean wantsTokenTypeIds = session.getInputNames().contains("token_type_ids");
      String hiddenStateOutput = null;
      for (final Map.Entry<String, NodeInfo> output : session.getOutputInfo().entrySet()) {
        if (output.getValue().getInfo() instanceof TensorInfo tensorInfo
            && tensorInfo.getShape().length == 3) {
          hiddenStateOutput = output.getKey();
          break;
        }
      }
      if (hiddenStateOutput == null) {
        session.close();
        throw new IllegalArgumentException("ONNX graph " + onnxFile + " has no rank-3 tensor "
            + "output (a last hidden state) to pool (outputs: "
            + session.getOutputInfo().keySet() + ")");
      }
      return new OnnxTeacherEncoder(environment, session, wantsTokenTypeIds, hiddenStateOutput);
    } catch (OrtException e) {
      throw new IllegalArgumentException("Failed to load ONNX graph " + onnxFile + ": "
          + e.getMessage(), e);
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
        inputs.put("input_ids", inputIds);
        inputs.put("attention_mask", mask);
        if (wantsTokenTypeIds) {
          tokenTypeIds = OnnxTensor.createTensor(environment,
              new long[batch.length][sequenceLength]);
          inputs.put("token_type_ids", tokenTypeIds);
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

  /** Closes the native session. */
  @Override
  public void close() {
    try {
      session.close();
      environment.close();
    } catch (OrtException e) {
      // Closing a native resource must not mask a distillation result.
    }
  }
}
