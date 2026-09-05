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
import java.nio.file.Files;
import java.nio.file.Path;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.java.Experimental;

/**
 * Quantizes a static embedding model directory in place: reads the matrix and optional
 * per-token weights from the directory's {@code model.safetensors}, quantizes the matrix to the
 * requested bit width (see {@link QuantizedEmbeddingMatrix}), and writes
 * {@code model.quantized} next to it. Delete the safetensors before loading the quantized
 * deployment; a model directory containing both matrix files is ambiguous and is rejected.
 *
 * <p>The written file is verified by reading it back and measuring the mean cosine between the
 * original and reconstructed rows over a deterministic sample, so a completed run reports the
 * reconstruction quality actually on disk.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
public final class ModelQuantizer {

  // At most this many rows enter the verification sample, evenly strided so it is
  // deterministic and spans the row range.
  private static final int VERIFICATION_SAMPLE_CAP = 1024;

  /** Not instantiable. */
  private ModelQuantizer() {
  }

  /**
   * Statistics for a completed quantization.
   *
   * @param rowCount        The number of matrix rows.
   * @param dimension       The row width.
   * @param bits            The bit width per padded dimension.
   * @param hasWeights      Whether per-token pooling weights were included.
   * @param safetensorsBytes The size of the source safetensors file.
   * @param quantizedBytes  The size of the written quantized file.
   * @param sampledRows     The number of rows in the verification sample.
   * @param meanCosine      The mean cosine between original and reconstructed sampled rows;
   *                        {@code Double.NaN} when every sampled row was zero.
   */
  public record Result(int rowCount, int dimension, int bits, boolean hasWeights,
                       long safetensorsBytes, long quantizedBytes, int sampledRows,
                       double meanCosine) {
  }

  /**
   * Quantizes the model directory's matrix and writes {@code model.quantized}.
   *
   * @param modelDirectory The model directory. Must not be {@code null}, must be a directory,
   *                       and must hold a {@code model.safetensors}.
   * @param bits           The bit width, between {@link QuantizedEmbeddingMatrix#MIN_BITS} and
   *                       {@link QuantizedEmbeddingMatrix#MAX_BITS}.
   * @param seed           The rotation seed; the same matrix, bits, and seed write the same
   *                       file bytes.
   * @return Statistics for the written quantized matrix.
   * @throws IllegalArgumentException Thrown if an argument is invalid.
   * @throws InvalidFormatException Thrown if the directory has no safetensors file or its
   *     tensors do not have the required shapes.
   * @throws IOException Thrown if reading or writing fails.
   */
  public static Result quantize(Path modelDirectory, int bits, long seed) throws IOException {
    if (modelDirectory == null) {
      throw new IllegalArgumentException("ModelDirectory must not be null");
    }
    if (!Files.isDirectory(modelDirectory)) {
      throw new IllegalArgumentException(
          "Model directory does not exist or is not a directory: " + modelDirectory);
    }
    GaussianQuantizer.requireSupportedBits(bits);
    final Path safetensorsFile = modelDirectory.resolve(ModelFileNames.SAFETENSORS);
    if (!Files.isRegularFile(safetensorsFile)) {
      throw new InvalidFormatException("Model directory " + modelDirectory + " has no "
          + ModelFileNames.SAFETENSORS + " to quantize");
    }
    final SafetensorsFile tensors = SafetensorsFile.read(safetensorsFile);
    final boolean hasWeights = tensors.tensorNames()
        .contains(StaticEmbeddingModel.WEIGHTS_TENSOR_NAME);
    if (hasWeights && tensors.tensorInfo(StaticEmbeddingModel.WEIGHTS_TENSOR_NAME)
        .shape().length != 1) {
      throw new InvalidFormatException("Tensor '"
          + StaticEmbeddingModel.WEIGHTS_TENSOR_NAME + "' in " + safetensorsFile
          + " must be 1-D");
    }
    final String matrixName = tensors.singleMatrixTensorName();
    final TensorInfo matrixInfo = tensors.tensorInfo(matrixName);
    final int rowCount = matrixInfo.shape()[0];
    final int dimension = matrixInfo.shape()[1];
    final float[] matrix = tensors.readFloats(matrixName);
    float[] weights = null;
    if (hasWeights) {
      weights = tensors.readFloats(StaticEmbeddingModel.WEIGHTS_TENSOR_NAME);
      if (weights.length != rowCount) {
        throw new InvalidFormatException("Tensor '"
            + StaticEmbeddingModel.WEIGHTS_TENSOR_NAME + "' in " + safetensorsFile + " has "
            + weights.length + " elements but the matrix has " + rowCount + " rows");
      }
    }
    final Path quantizedFile = modelDirectory.resolve(ModelFileNames.QUANTIZED);
    QuantizedEmbeddingMatrix.quantize(matrix, rowCount, dimension, bits, seed)
        .withPoolingWeights(weights)
        .write(quantizedFile);
    // Verify what is actually on disk, not the in-memory object.
    final QuantizedEmbeddingMatrix written = QuantizedEmbeddingMatrix.read(quantizedFile);
    final int sampleCount = Math.min(rowCount, VERIFICATION_SAMPLE_CAP);
    int sampled = 0;
    int nonZero = 0;
    double cosineSum = 0;
    for (int sample = 0; sample < sampleCount; sample++) {
      final int row = sampleCount == 1 ? 0
          : (int) ((long) sample * (rowCount - 1) / (sampleCount - 1));
      sampled++;
      final double cosine = cosine(matrix, row * dimension, dimension, written.decodeRow(row));
      if (!Double.isNaN(cosine)) {
        nonZero++;
        cosineSum += cosine;
      }
    }
    return new Result(rowCount, dimension, bits, weights != null,
        Files.size(safetensorsFile), Files.size(quantizedFile), sampled,
        nonZero == 0 ? Double.NaN : cosineSum / nonZero);
  }

  /**
   * {@return the cosine between a matrix row and its reconstruction, or {@code Double.NaN} when
   * either has no direction} Also the shared fidelity measure of this package's tests.
   *
   * @param matrix    The flat row-major matrix.
   * @param base      The row's first index.
   * @param dimension The row width.
   * @param decoded   The reconstructed row.
   */
  static double cosine(float[] matrix, int base, int dimension, float[] decoded) {
    double dot = 0;
    double normASquared = 0;
    double normBSquared = 0;
    for (int d = 0; d < dimension; d++) {
      dot += (double) matrix[base + d] * decoded[d];
      normASquared += (double) matrix[base + d] * matrix[base + d];
      normBSquared += (double) decoded[d] * decoded[d];
    }
    final double denominator = Math.sqrt(normASquared) * Math.sqrt(normBSquared);
    if (denominator == 0) {
      return Double.NaN;
    }
    return Math.max(-1.0, Math.min(1.0, dot / denominator));
  }
}
