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

import java.util.Random;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Principal component analysis by randomized SVD
 * (<a href="https://arxiv.org/abs/0909.4061">Halko, Martinsson, Tropp</a>), the approximation
 * Model2Vec's distillation performs with a dense LAPACK SVD through scikit-learn. A dense SVD of
 * a vocabulary-size matrix (250k rows for a multilingual teacher) is not practical in pure Java,
 * so the top components are found with a random range finder and {@value #POWER_ITERATIONS} power
 * iterations, which for the fast-decaying spectrum of transformer token embeddings recovers the
 * same subspace as the exact decomposition.
 *
 * <p>The column mean is subtracted before decomposition (the data matrix is modified in place),
 * and the signs of the components are fixed the way scikit-learn's full solver fixes them
 * ({@code svd_flip} with {@code u_based_decision=false}): each component's largest-magnitude
 * coordinate is positive, so two distillations of the same teacher produce directly comparable
 * vectors instead of mirror images.</p>
 *
 * <p>The heavy loops are row-parallel over the common fork/join pool; all accumulation is in
 * {@code double}.</p>
 */
final class RandomizedPca {

  /** Extra dimensions the range finder samples beyond the requested components. */
  private static final int OVERSAMPLING = 10;

  /** Power iterations sharpening the range finder toward the dominant subspace. */
  private static final int POWER_ITERATIONS = 8;

  /** Number of row blocks the parallel loops split the matrix into. */
  private static final int BLOCKS = 32;

  /** Jacobi eigensolver convergence, relative to the largest diagonal element. */
  private static final double JACOBI_EPSILON = 1e-12;

  /** Jacobi eigensolver sweep cap; convergence arrives long before this. */
  private static final int JACOBI_MAX_SWEEPS = 100;

  /** Floor on a squared singular value, so a rank-deficient direction divides by a non-zero. */
  private static final double MIN_SQUARED_SINGULAR_VALUE = 1e-12;

  /** CholeskyQR diagonal jitter, relative to the Gram matrix's average diagonal element. */
  private static final double JITTER_RATIO = 1e-12;

  /** Factor the jitter grows by after a failed factorization. */
  private static final double JITTER_ESCALATION = 1000;

  /** Number of jitter values tried before the factorization is given up on. */
  private static final int JITTER_ATTEMPTS = 5;

  /** Not instantiable. */
  private RandomizedPca() {
  }

  /** The outcome of a PCA: the projected data and how much variance the projection keeps. */
  record Result(float[] transformed, double explainedVarianceRatio) {
  }

  /**
   * Centers {@code data} and projects it onto its top {@code components} principal components.
   *
   * @param data       The row-major {@code rows x cols} matrix; centered in place.
   * @param rows       The number of rows.
   * @param cols       The number of columns (the original dimension).
   * @param components The number of principal components to keep; at most {@code cols} and less
   *                   than {@code rows}.
   * @param seed       The random seed of the range finder; a fixed seed makes the projection
   *                   deterministic.
   * @return The projected row-major {@code rows x components} matrix and the ratio of total
   *     variance it explains.
   * @throws IllegalArgumentException Thrown if the arguments are inconsistent, or if the data has
   *     no variance to decompose (every row is identical, or a value is not finite).
   */
  static Result fitTransform(float[] data, int rows, int cols, int components, long seed) {
    if (data == null) {
      throw new IllegalArgumentException("Data must not be null");
    }
    if (rows < 1 || cols < 1 || data.length != (long) rows * cols) {
      throw new IllegalArgumentException("Data has " + data.length + " elements, not " + rows
          + " x " + cols);
    }
    if (components < 1 || components > cols || components >= rows) {
      throw new IllegalArgumentException("Components must be in [1, " + Math.min(cols, rows - 1)
          + "], got " + components);
    }
    final double[] mean = columnMean(data, rows, cols);
    subtractMean(data, rows, cols, mean);
    final double totalVariance = totalVariance(data, rows, cols);
    if (!Double.isFinite(totalVariance) || totalVariance <= 0) {
      throw new IllegalArgumentException("Data has a total variance of " + totalVariance
          + "; there is no subspace to find. Every row is identical, or a value is not finite.");
    }
    final int sampleDimensions = Math.min(components + OVERSAMPLING, cols);
    final double[] omega = new double[cols * sampleDimensions];
    final Random random = new Random(seed);
    for (int i = 0; i < omega.length; i++) {
      omega[i] = random.nextGaussian();
    }
    double[] sample = multiplyDataByDense(data, rows, cols, omega, sampleDimensions);
    for (int iteration = 0; iteration < POWER_ITERATIONS; iteration++) {
      orthonormalizeInPlace(sample, rows, sampleDimensions);
      final double[] transposed = multiplyDataTransposedByDense(data, rows, cols, sample,
          sampleDimensions);
      sample = multiplyDataByDense(data, rows, cols, transposed, sampleDimensions);
    }
    orthonormalizeInPlace(sample, rows, sampleDimensions);
    // The small matrix B = Q'X holds the data's action on the found subspace; its right singular
    // vectors rotated back are the principal components.
    final double[] small = multiplyBasisTransposedByData(sample, rows, sampleDimensions,
        data, cols);
    final double[] gram = new double[sampleDimensions * sampleDimensions];
    for (int a = 0; a < sampleDimensions; a++) {
      for (int b = 0; b <= a; b++) {
        double sum = 0;
        for (int c = 0; c < cols; c++) {
          sum += small[a * cols + c] * small[b * cols + c];
        }
        gram[a * sampleDimensions + b] = sum;
        gram[b * sampleDimensions + a] = sum;
      }
    }
    final double[][] eigen = jacobiEigen(gram, sampleDimensions);
    final double[] eigenvalues = eigen[0];
    final double[] eigenvectors = eigen[1]; // row-major, column j is eigenvector j
    // Components in component-major layout: component j is eigenvector j of B's Gram matrix
    // mapped back through B and normalized by its singular value.
    final double[] componentsMajor = new double[components * cols];
    for (int j = 0; j < components; j++) {
      final double singularValue = Math.sqrt(Math.max(eigenvalues[j], MIN_SQUARED_SINGULAR_VALUE));
      for (int c = 0; c < cols; c++) {
        double sum = 0;
        for (int a = 0; a < sampleDimensions; a++) {
          sum += small[a * cols + c] * eigenvectors[a * sampleDimensions + j];
        }
        componentsMajor[j * cols + c] = sum / singularValue;
      }
      fixSign(componentsMajor, j * cols, cols);
    }
    final float[] transformed = project(data, rows, cols, componentsMajor, components);
    double keptVariance = 0;
    for (int j = 0; j < components; j++) {
      keptVariance += eigenvalues[j];
    }
    return new Result(transformed, keptVariance / totalVariance);
  }

  /**
   * Fixes a component's sign the way scikit-learn's {@code svd_flip} with
   * {@code u_based_decision=false} does: the largest-magnitude coordinate is made positive.
   *
   * @param componentMajor The component-major components array.
   * @param offset         The component's start offset.
   * @param length         The component's length.
   */
  private static void fixSign(double[] componentMajor, int offset, int length) {
    int maxIndex = 0;
    double maxAbs = 0;
    for (int c = 0; c < length; c++) {
      final double abs = Math.abs(componentMajor[offset + c]);
      if (abs > maxAbs) {
        maxAbs = abs;
        maxIndex = c;
      }
    }
    if (componentMajor[offset + maxIndex] < 0) {
      for (int c = 0; c < length; c++) {
        componentMajor[offset + c] = -componentMajor[offset + c];
      }
    }
  }

  /**
   * {@return the per-column means of the matrix, computed row-parallel}
   *
   * @param data The row-major matrix.
   * @param rows The number of rows.
   * @param cols The number of columns.
   */
  private static double[] columnMean(float[] data, int rows, int cols) {
    final double[][] partials = new double[BLOCKS][cols];
    forBlocks(rows, block -> {
      final double[] partial = partials[block];
      final int start = blockStart(rows, block);
      final int end = blockStart(rows, block + 1);
      for (int i = start; i < end; i++) {
        for (int c = 0; c < cols; c++) {
          partial[c] += data[i * cols + c];
        }
      }
    });
    final double[] mean = new double[cols];
    for (final double[] partial : partials) {
      for (int c = 0; c < cols; c++) {
        mean[c] += partial[c];
      }
    }
    for (int c = 0; c < cols; c++) {
      mean[c] /= rows;
    }
    return mean;
  }

  /**
   * Subtracts the per-column means from the matrix in place, row-parallel.
   *
   * @param data The row-major matrix.
   * @param rows The number of rows.
   * @param cols The number of columns.
   * @param mean The per-column means.
   */
  private static void subtractMean(float[] data, int rows, int cols, double[] mean) {
    forBlocks(rows, block -> {
      final int start = blockStart(rows, block);
      final int end = blockStart(rows, block + 1);
      for (int i = start; i < end; i++) {
        for (int c = 0; c < cols; c++) {
          final int index = i * cols + c;
          data[index] = (float) (data[index] - mean[c]);
        }
      }
    });
  }

  /**
   * {@return the total variance of the centered matrix (the squared Frobenius norm), row-parallel}
   *
   * @param data The centered row-major matrix.
   * @param rows The number of rows.
   * @param cols The number of columns.
   */
  private static double totalVariance(float[] data, int rows, int cols) {
    final double[] partials = new double[BLOCKS];
    forBlocks(rows, block -> {
      double sum = 0;
      final int start = blockStart(rows, block);
      final int end = blockStart(rows, block + 1);
      for (int i = start * cols; i < end * cols; i++) {
        sum += (double) data[i] * data[i];
      }
      partials[block] = sum;
    });
    double total = 0;
    for (final double partial : partials) {
      total += partial;
    }
    return total;
  }

  /**
   * {@return the product {@code data * dense} of the float data matrix with a dense double
   * matrix, row-parallel over the data}
   *
   * @param data  The row-major {@code rows x cols} float matrix.
   * @param rows  The number of rows.
   * @param cols  The number of columns.
   * @param dense The row-major {@code cols x width} double matrix.
   * @param width The number of columns of {@code dense}.
   */
  private static double[] multiplyDataByDense(float[] data, int rows, int cols, double[] dense,
                                              int width) {
    final double[] out = new double[rows * width];
    forBlocks(rows, block -> {
      final int start = blockStart(rows, block);
      final int end = blockStart(rows, block + 1);
      for (int i = start; i < end; i++) {
        final int rowBase = i * cols;
        final int outBase = i * width;
        for (int c = 0; c < cols; c++) {
          final float value = data[rowBase + c];
          if (value != 0) {
            final int denseBase = c * width;
            for (int j = 0; j < width; j++) {
              out[outBase + j] += value * dense[denseBase + j];
            }
          }
        }
      }
    });
    return out;
  }

  /**
   * {@return the product {@code data' * dense} of the transposed float data matrix with a dense
   * double matrix, row-parallel over the data with per-block partial results}
   *
   * @param data  The row-major {@code rows x cols} float matrix.
   * @param rows  The number of rows.
   * @param cols  The number of columns.
   * @param dense The row-major {@code rows x width} double matrix.
   * @param width The number of columns of {@code dense}.
   */
  private static double[] multiplyDataTransposedByDense(float[] data, int rows, int cols,
                                                        double[] dense, int width) {
    final double[][] partials = new double[BLOCKS][cols * width];
    forBlocks(rows, block -> {
      final double[] partial = partials[block];
      final int start = blockStart(rows, block);
      final int end = blockStart(rows, block + 1);
      for (int i = start; i < end; i++) {
        final int rowBase = i * cols;
        final int denseBase = i * width;
        for (int c = 0; c < cols; c++) {
          final float value = data[rowBase + c];
          if (value != 0) {
            final int outBase = c * width;
            for (int j = 0; j < width; j++) {
              partial[outBase + j] += value * dense[denseBase + j];
            }
          }
        }
      }
    });
    final double[] out = new double[cols * width];
    for (final double[] partial : partials) {
      for (int i = 0; i < out.length; i++) {
        out[i] += partial[i];
      }
    }
    return out;
  }

  /**
   * {@return the product {@code basis' * data} of the transposed orthonormal basis with the float
   * data matrix, row-parallel over the data with per-block partial results}
   *
   * @param basis  The row-major {@code rows x width} orthonormal basis.
   * @param rows   The number of rows.
   * @param width  The basis width.
   * @param data   The row-major {@code rows x cols} float matrix.
   * @param cols   The number of data columns.
   */
  private static double[] multiplyBasisTransposedByData(double[] basis, int rows, int width,
                                                        float[] data, int cols) {
    final double[][] partials = new double[BLOCKS][width * cols];
    forBlocks(rows, block -> {
      final double[] partial = partials[block];
      final int start = blockStart(rows, block);
      final int end = blockStart(rows, block + 1);
      for (int i = start; i < end; i++) {
        final int basisBase = i * width;
        final int rowBase = i * cols;
        for (int a = 0; a < width; a++) {
          final double value = basis[basisBase + a];
          final int outBase = a * cols;
          for (int c = 0; c < cols; c++) {
            partial[outBase + c] += value * data[rowBase + c];
          }
        }
      }
    });
    final double[] out = new double[width * cols];
    for (final double[] partial : partials) {
      for (int i = 0; i < out.length; i++) {
        out[i] += partial[i];
      }
    }
    return out;
  }

  /**
   * {@return the projection {@code data * components'} onto the component-major components,
   * row-parallel}
   *
   * @param data            The centered row-major {@code rows x cols} float matrix.
   * @param rows            The number of rows.
   * @param cols            The number of columns.
   * @param componentsMajor The row-major {@code components x cols} components.
   * @param components      The number of components.
   */
  private static float[] project(float[] data, int rows, int cols, double[] componentsMajor,
                                 int components) {
    final float[] out = new float[rows * components];
    forBlocks(rows, block -> {
      final int start = blockStart(rows, block);
      final int end = blockStart(rows, block + 1);
      for (int i = start; i < end; i++) {
        final int rowBase = i * cols;
        final int outBase = i * components;
        for (int j = 0; j < components; j++) {
          final int componentBase = j * cols;
          double sum = 0;
          for (int c = 0; c < cols; c++) {
            sum += data[rowBase + c] * componentsMajor[componentBase + c];
          }
          out[outBase + j] = (float) sum;
        }
      }
    });
    return out;
  }

  /**
   * Orthonormalizes the columns of the tall {@code rows x width} matrix in place by CholeskyQR:
   * the Cholesky factor of the Gram matrix triangular-solves the basis. A diagonal jitter relative
   * to the average pivot keeps the factorization alive when a power iteration has driven the
   * columns toward linear dependence.
   *
   * @param matrix The row-major tall matrix, orthonormalized in place.
   * @param rows   The number of rows.
   * @param width  The number of columns.
   */
  private static void orthonormalizeInPlace(double[] matrix, int rows, int width) {
    final double[][] partials = new double[BLOCKS][width * width];
    forBlocks(rows, block -> {
      final double[] partial = partials[block];
      final int start = blockStart(rows, block);
      final int end = blockStart(rows, block + 1);
      for (int i = start; i < end; i++) {
        final int base = i * width;
        for (int a = 0; a < width; a++) {
          final double value = matrix[base + a];
          final int gramBase = a * width;
          for (int b = 0; b <= a; b++) {
            partial[gramBase + b] += value * matrix[base + b];
          }
        }
      }
    });
    final double[] gram = new double[width * width];
    for (final double[] partial : partials) {
      for (int a = 0; a < width; a++) {
        for (int b = 0; b <= a; b++) {
          gram[a * width + b] += partial[a * width + b];
        }
      }
    }
    for (int a = 0; a < width; a++) {
      for (int b = 0; b < a; b++) {
        gram[b * width + a] = gram[a * width + b];
      }
    }
    double trace = 0;
    for (int a = 0; a < width; a++) {
      trace += gram[a * width + a];
    }
    // Relative to the average diagonal element, so the factorization is unchanged when the whole
    // matrix is rescaled; an absolute jitter would swamp a Gram matrix of small magnitude.
    double jitter = trace / width * JITTER_RATIO;
    double[] lower = null;
    for (int attempt = 0; attempt < JITTER_ATTEMPTS && lower == null; attempt++) {
      lower = cholesky(gram, width, jitter);
      jitter *= JITTER_ESCALATION;
    }
    if (lower == null) {
      throw new IllegalStateException("Gram matrix is not positive definite even with jitter; "
          + "the data columns are linearly dependent");
    }
    // Solve Q L' = Y row-wise. Transposed, that is L q' = y': a forward substitution against
    // the lower-triangular L.
    final double[] factor = lower;
    forBlocks(rows, block -> {
      final int start = blockStart(rows, block);
      final int end = blockStart(rows, block + 1);
      for (int i = start; i < end; i++) {
        final int base = i * width;
        for (int j = 0; j < width; j++) {
          double sum = matrix[base + j];
          for (int m = 0; m < j; m++) {
            sum -= factor[j * width + m] * matrix[base + m];
          }
          matrix[base + j] = sum / factor[j * width + j];
        }
      }
    });
  }

  /**
   * {@return the lower-triangular Cholesky factor of the symmetric positive-definite matrix, or
   * {@code null} when a pivot is not positive even after adding {@code jitter} to the diagonal}
   *
   * @param matrix The row-major symmetric matrix.
   * @param width  The matrix order.
   * @param jitter The value added to the diagonal before factoring.
   */
  private static double[] cholesky(double[] matrix, int width, double jitter) {
    final double[] lower = new double[width * width];
    for (int a = 0; a < width; a++) {
      for (int b = 0; b <= a; b++) {
        double sum = matrix[a * width + b];
        if (a == b) {
          sum += jitter;
        }
        for (int m = 0; m < b; m++) {
          sum -= lower[a * width + m] * lower[b * width + m];
        }
        if (a == b) {
          if (sum <= 0) {
            return null;
          }
          lower[a * width + a] = Math.sqrt(sum);
        } else {
          lower[a * width + b] = sum / lower[b * width + b];
        }
      }
    }
    return lower;
  }

  /**
   * {@return the eigenpairs of a small symmetric matrix by the cyclic Jacobi method, eigenvalues
   * descending; the returned array holds the eigenvalues at index 0 and the row-major eigenvector
   * matrix (column j is eigenvector j) at index 1}
   *
   * @param matrix The row-major symmetric matrix; not modified.
   * @param width  The matrix order.
   */
  private static double[][] jacobiEigen(double[] matrix, int width) {
    final double[] a = matrix.clone();
    final double[] eigenvectors = new double[width * width];
    for (int i = 0; i < width; i++) {
      eigenvectors[i * width + i] = 1;
    }
    for (int sweep = 0; sweep < JACOBI_MAX_SWEEPS; sweep++) {
      double offDiagonal = 0;
      double diagonal = 0;
      for (int p = 0; p < width; p++) {
        diagonal = Math.max(diagonal, Math.abs(a[p * width + p]));
        for (int q = p + 1; q < width; q++) {
          offDiagonal += a[p * width + q] * a[p * width + q];
        }
      }
      if (Math.sqrt(offDiagonal) <= JACOBI_EPSILON * Math.max(diagonal, JACOBI_EPSILON)) {
        break;
      }
      for (int p = 0; p < width; p++) {
        for (int q = p + 1; q < width; q++) {
          final double apq = a[p * width + q];
          if (Math.abs(apq) <= JACOBI_EPSILON * Math.max(diagonal, JACOBI_EPSILON)) {
            continue;
          }
          final double app = a[p * width + p];
          final double aqq = a[q * width + q];
          final double theta = (aqq - app) / (2 * apq);
          final double sign = theta >= 0 ? 1 : -1;
          final double t = sign / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
          final double cosine = 1 / Math.sqrt(t * t + 1);
          final double sine = t * cosine;
          for (int k = 0; k < width; k++) {
            final double akp = a[k * width + p];
            final double akq = a[k * width + q];
            a[k * width + p] = cosine * akp - sine * akq;
            a[k * width + q] = sine * akp + cosine * akq;
          }
          for (int k = 0; k < width; k++) {
            final double apk = a[p * width + k];
            final double aqk = a[q * width + k];
            a[p * width + k] = cosine * apk - sine * aqk;
            a[q * width + k] = sine * apk + cosine * aqk;
          }
          for (int k = 0; k < width; k++) {
            final double vkp = eigenvectors[k * width + p];
            final double vkq = eigenvectors[k * width + q];
            eigenvectors[k * width + p] = cosine * vkp - sine * vkq;
            eigenvectors[k * width + q] = sine * vkp + cosine * vkq;
          }
        }
      }
    }
    // Sort eigenpairs by eigenvalue, descending, with an insertion sort (the matrix is small).
    final double[] eigenvalues = new double[width];
    for (int j = 0; j < width; j++) {
      eigenvalues[j] = a[j * width + j];
    }
    for (int j = 1; j < width; j++) {
      int k = j;
      while (k > 0 && eigenvalues[k - 1] < eigenvalues[k]) {
        final double value = eigenvalues[k];
        eigenvalues[k] = eigenvalues[k - 1];
        eigenvalues[k - 1] = value;
        for (int i = 0; i < width; i++) {
          final double v = eigenvectors[i * width + k];
          eigenvectors[i * width + k] = eigenvectors[i * width + k - 1];
          eigenvectors[i * width + k - 1] = v;
        }
        k--;
      }
    }
    return new double[][] {eigenvalues, eigenvectors};
  }

  /**
   * Runs {@code action} for every row-block index in parallel over the common pool.
   *
   * @param rows   The total number of rows.
   * @param action Receives the block index, in {@code [0, BLOCKS)}.
   */
  private static void forBlocks(int rows, IntConsumer action) {
    IntStream.range(0, Math.min(BLOCKS, rows)).parallel().forEach(action);
  }

  /**
   * {@return the first row of a block}
   *
   * @param rows  The total number of rows.
   * @param block The block index; the effective block count yields the end sentinel.
   */
  private static int blockStart(int rows, int block) {
    return (int) ((long) rows * block / Math.min(BLOCKS, rows));
  }
}
