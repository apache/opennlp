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

/**
 * Quantizes a static embedding model directory in place: reads the matrix and optional
 * per-token weights from the directory's {@code model.safetensors}, quantizes the matrix to the
 * requested bit width (see {@link QuantizedEmbeddingMatrix}), and writes
 * {@code model.quantized} next to it. {@code StaticEmbeddingModel.load} prefers the quantized
 * file from then on; the safetensors may be deleted for a slim deployment.
 *
 * <p>The written file is verified by reading it back and measuring the mean cosine between the
 * original and reconstructed rows over a deterministic sample, so a completed run reports the
 * reconstruction quality actually on disk.</p>
 */
public final class ModelQuantizer {

  // At most this many rows enter the verification sample, evenly strided so it is
  // deterministic and covers the whole table.
  private static final int VERIFICATION_SAMPLE_CAP = 1024;

  /** Not instantiable. */
  private ModelQuantizer() {
  }

  /**
   * What a quantization run produced and measured.
   *
   * @param rowCount        The number of matrix rows.
   * @param dimension       The row width.
   * @param bits            The bit width per padded dimension.
   * @param hasWeights      Whether per-token pooling weights were carried over.
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
   * @return What was produced and measured.
   * @throws IllegalArgumentException Thrown if an argument is invalid or the directory has no
   *     safetensors file.
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
    final Path safetensorsFile = modelDirectory.resolve(ModelFileNames.SAFETENSORS);
    if (!Files.isRegularFile(safetensorsFile)) {
      throw new IllegalArgumentException("Model directory " + modelDirectory + " has no "
          + ModelFileNames.SAFETENSORS + " to quantize");
    }
    final SafetensorsFile tensors = SafetensorsFile.read(safetensorsFile);
    final String matrixName = tensors.singleMatrixTensorName();
    final TensorInfo matrixInfo = tensors.tensorInfo(matrixName);
    final int rowCount = matrixInfo.shape()[0];
    final int dimension = matrixInfo.shape()[1];
    final float[] matrix = tensors.readFloats(matrixName);
    float[] weights = null;
    if (tensors.tensorNames().contains("weights")) {
      weights = tensors.readFloats("weights");
      if (weights.length != rowCount) {
        throw new IllegalArgumentException("Tensor 'weights' in " + safetensorsFile + " has "
            + weights.length + " elements but the matrix has " + rowCount + " rows");
      }
    }
    final Path quantizedFile = modelDirectory.resolve(ModelFileNames.QUANTIZED);
    QuantizedEmbeddingMatrix.quantize(matrix, rowCount, dimension, bits, seed)
        .withPoolingWeights(weights)
        .write(quantizedFile);
    // Verify what is actually on disk, not the in-memory object.
    final QuantizedEmbeddingMatrix written = QuantizedEmbeddingMatrix.read(quantizedFile);
    final int stride = Math.max(1, rowCount / VERIFICATION_SAMPLE_CAP);
    int sampled = 0;
    int nonZero = 0;
    double cosineSum = 0;
    for (int row = 0; row < rowCount; row += stride) {
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
   * either has no direction}
   *
   * @param matrix    The flat row-major matrix.
   * @param base      The row's first index.
   * @param dimension The row width.
   * @param decoded   The reconstructed row.
   */
  private static double cosine(float[] matrix, int base, int dimension, float[] decoded) {
    double dot = 0;
    double normASquared = 0;
    double normBSquared = 0;
    for (int d = 0; d < dimension; d++) {
      dot += (double) matrix[base + d] * decoded[d];
      normASquared += (double) matrix[base + d] * matrix[base + d];
      normBSquared += (double) decoded[d] * decoded[d];
    }
    final double denominator = Math.sqrt(normASquared) * Math.sqrt(normBSquared);
    return denominator == 0 ? Double.NaN : dot / denominator;
  }
}
