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
import java.nio.file.StandardCopyOption;

/**
 * Distills a sentence-transformer teacher into a static embedding table in the layout
 * {@link StaticEmbeddingModel#load(Path)} opens, reproducing
 * <a href="https://github.com/MinishLab/model2vec">Model2Vec</a>'s distillation in Java so no
 * Python environment is needed. The pipeline is Model2Vec's:
 *
 * <ol>
 *   <li>The teacher's vocabulary is cleaned (unused tokens and special added tokens other than
 *   the unknown and pad tokens are dropped, the rest keeps its id order) and every surviving
 *   token is run through the teacher's ONNX graph as {@code [bos, token, eos]}; the token's
 *   embedding is the mean of the last hidden states.</li>
 *   <li>The matrix is projected onto its top principal components (a randomized SVD standing in
 *   for scikit-learn's dense one; see {@link RandomizedPca}).</li>
 *   <li>Each row is scaled by its Zipf weight {@code sif / (sif + p)}, where {@code p} is the
 *   row's share of a Zipf distribution over the vocabulary and {@code sif} is
 *   {@value #SIF_COEFFICIENT}, Model2Vec's default.</li>
 *   <li>The result is written as {@code model.safetensors} (F32), the cleaned
 *   {@code tokenizer.json}, and a {@code config.json} with {@code "normalize": true}; a
 *   SentencePiece teacher's {@code .model} file is copied alongside. The directory is then
 *   completed and verified by {@link ModelAssembler}.</li>
 * </ol>
 *
 * <p>The teacher directory must hold {@code tokenizer.json} and {@code onnx/model.onnx} (the
 * ONNX export every sentence-transformer ships on the Hugging Face hub); a local
 * {@code tokenizer_config.json} supplies the pad token when present.</p>
 */
public final class ModelDistiller {

  /** Model2Vec's default SIF coefficient for the Zipf weighting. */
  static final double SIF_COEFFICIENT = 1e-4;

  /** The number of id sequences per forward-pass batch, Model2Vec's batch size. */
  private static final int BATCH_SIZE = 256;

  /** The fixed seed of the PCA range finder, so a distillation is reproducible. */
  private static final long PCA_SEED = 42;

  /** Not instantiable. */
  private ModelDistiller() {
  }

  /** Receives progress messages; the command-line tool prints them. */
  @FunctionalInterface
  public interface ProgressListener {

    /**
     * Reports a progress message.
     *
     * @param message The message.
     */
    void progress(String message);
  }

  /**
   * The outcome of a distillation: the family, size, and dimension of the verified model, plus
   * the variance the PCA kept.
   *
   * @param family                  {@code "WordPiece"} or {@code "SentencePiece"}.
   * @param vocabularySize          The number of rows in the distilled table.
   * @param teacherDimension        The teacher's hidden dimension.
   * @param dimension               The distilled table's dimension (after PCA).
   * @param explainedVarianceRatio  The share of the embedding variance the PCA kept.
   */
  public record Result(String family, int vocabularySize, int teacherDimension, int dimension,
                       double explainedVarianceRatio) {
  }

  /**
   * Distills a teacher into a model directory, resolving the teacher reference first: a local
   * directory is used as-is, a Hugging Face model id ({@code org/model}) is downloaded into a
   * local cache on first use.
   *
   * @param teacher         The teacher: a local directory or a Hugging Face model id. Must not
   *                        be {@code null}.
   * @param outputDirectory The model directory to write. Must not be {@code null}.
   * @param pcaDims         The number of principal components to keep.
   * @param listener        Receives progress lines; may be {@code null}.
   * @return The distillation result, read back from the verified directory.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or invalid, the
   *     teacher cannot be resolved, or the teacher cannot be run.
   * @throws IOException Thrown if reading or writing a file fails.
   */
  public static Result distill(String teacher, Path outputDirectory, int pcaDims,
                               ProgressListener listener) throws IOException {
    checkOutput(outputDirectory, pcaDims);
    return distill(HuggingFaceModelCache.resolve(teacher, listener), outputDirectory, pcaDims,
        listener);
  }

  /**
   * Distills a teacher into a model directory.
   *
   * @param teacherDirectory The teacher's directory, holding {@code tokenizer.json} and
   *                         {@code onnx/model.onnx}. Must not be {@code null} and must be a
   *                         directory.
   * @param outputDirectory  The model directory to write. Created when missing. The four files
   *                         a distillation produces are replaced, but files a previous run's
   *                         assembly derived ({@code vocab.txt}, {@code tokenizer_config.json})
   *                         are not, so distil into a fresh or emptied directory when the
   *                         vocabulary or the dimension changes. A failure part way through
   *                         leaves whatever was written so far. Must not be {@code null}.
   * @param pcaDims          The number of principal components to keep; clamped to the teacher's
   *                         hidden dimension, and skipped entirely when it would not reduce a
   *                         tiny vocabulary. Model2Vec's default (and the recommended value) is
   *                         256.
   * @param listener         Receives one progress line per distillation phase and one per
   *                         forward-pass batch; may be {@code null}.
   * @return The distillation result, read back from the verified directory.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or invalid, the
   *     teacher directory lacks its files, or the teacher cannot be run.
   * @throws IOException Thrown if reading or writing a file fails.
   */
  public static Result distill(Path teacherDirectory, Path outputDirectory, int pcaDims,
                               ProgressListener listener)
      throws IOException {
    if (teacherDirectory == null) {
      throw new IllegalArgumentException("TeacherDirectory must not be null");
    }
    if (!Files.isDirectory(teacherDirectory)) {
      throw new IllegalArgumentException("Teacher directory does not exist or is not a "
          + "directory: " + teacherDirectory);
    }
    checkOutput(outputDirectory, pcaDims);
    final Path onnxFile = teacherDirectory.resolve(ModelFileNames.ONNX_MODEL);
    if (!Files.isRegularFile(onnxFile)) {
      throw new IllegalArgumentException("Teacher directory " + teacherDirectory + " has no "
          + ModelFileNames.ONNX_MODEL + "; the distillation runs the teacher's ONNX export, which "
          + "sentence-transformers ship on the Hugging Face hub");
    }
    final TeacherTokenizer tokenizer = TeacherTokenizer.read(
        teacherDirectory.resolve(ModelFileNames.TOKENIZER_JSON),
        teacherDirectory.resolve(ModelFileNames.TOKENIZER_CONFIG));
    final int rows = tokenizer.vocabularySize();
    if (rows < 1) {
      throw new IllegalArgumentException("Teacher directory " + teacherDirectory + " has no "
          + "vocabulary token left after cleaning; there is nothing to distill");
    }

    report(listener, "Encoding " + rows + " vocabulary tokens of " + teacherDirectory
        + " through its ONNX graph");
    final float[] embeddings;
    final int teacherDimension;
    try (OnnxTeacherEncoder encoder = OnnxTeacherEncoder.load(onnxFile)) {
      float[][] first = encoder.encodeBatch(new long[][] {tokenizer.inputSequence(0)});
      teacherDimension = first[0].length;
      embeddings = new float[rows * teacherDimension];
      System.arraycopy(first[0], 0, embeddings, 0, teacherDimension);
      int row = 1;
      while (row < rows) {
        final int batchSize = Math.min(BATCH_SIZE, rows - row);
        final long[][] batch = new long[batchSize][];
        for (int b = 0; b < batchSize; b++) {
          batch[b] = tokenizer.inputSequence(row + b);
        }
        final float[][] pooled = encoder.encodeBatch(batch);
        for (int b = 0; b < batchSize; b++) {
          System.arraycopy(pooled[b], 0, embeddings, (row + b) * teacherDimension,
              teacherDimension);
        }
        row += batchSize;
        report(listener, "Encoded " + row + " / " + rows + " vocabulary tokens");
      }
    }
    nonFiniteToZero(embeddings);

    final int requested = Math.min(pcaDims, teacherDimension);
    final float[] transformed;
    final int components;
    double explainedVarianceRatio = 1.0;
    if (requested >= rows) {
      // A PCA with more components than rows is not a reduction; Model2Vec skips it with a
      // warning. Only reachable for toy vocabularies, which then keep the teacher's dimension.
      transformed = embeddings;
      components = teacherDimension;
    } else {
      report(listener, "Reducing " + rows + " x " + teacherDimension + " to " + requested
          + " principal components");
      final RandomizedPca.Result pca = RandomizedPca.fitTransform(embeddings, rows,
          teacherDimension, requested, PCA_SEED);
      transformed = pca.transformed();
      components = requested;
      explainedVarianceRatio = pca.explainedVarianceRatio();
    }
    final float[] weights = zipfWeights(rows, SIF_COEFFICIENT);
    for (int row = 0; row < rows; row++) {
      final int base = row * components;
      final float weight = weights[row];
      for (int d = 0; d < components; d++) {
        transformed[base + d] *= weight;
      }
    }

    report(listener, "Writing and verifying the model directory " + outputDirectory);
    Files.createDirectories(outputDirectory);
    SafetensorsWriter.writeMatrix(outputDirectory.resolve(ModelFileNames.SAFETENSORS), rows,
        components, transformed);
    tokenizer.writeCleaned(outputDirectory.resolve(ModelFileNames.TOKENIZER_JSON));
    Files.writeString(outputDirectory.resolve(ModelFileNames.CONFIG),
        configJson(teacherDirectory, pcaDims, components));
    copySentencePieceModel(teacherDirectory, outputDirectory);
    final ModelAssembler.Result assembled = ModelAssembler.assemble(outputDirectory);
    return new Result(assembled.family(), assembled.vocabularySize(), teacherDimension,
        assembled.dimension(), explainedVarianceRatio);
  }

  /**
   * Validates the arguments that do not depend on the teacher, so that a distillation naming a
   * hub teacher fails before it downloads anything.
   *
   * @param outputDirectory The model directory to write.
   * @param pcaDims         The number of principal components to keep.
   * @throws IllegalArgumentException Thrown if the directory is {@code null} or {@code pcaDims}
   *     is below 1.
   */
  private static void checkOutput(Path outputDirectory, int pcaDims) {
    if (outputDirectory == null) {
      throw new IllegalArgumentException("OutputDirectory must not be null");
    }
    if (pcaDims < 1) {
      throw new IllegalArgumentException("PcaDims must be at least 1, got " + pcaDims);
    }
  }

  /**
   * Reports one progress line, if anyone is listening.
   *
   * @param listener The listener; may be {@code null}.
   * @param message  The message.
   */
  private static void report(ProgressListener listener, String message) {
    if (listener != null) {
      listener.progress(message);
    }
  }

  /**
   * {@return Model2Vec's Zipf weights: row {@code i} gets {@code sif / (sif + p_i)} with
   * {@code p_i = (1 / (i + 2)) / sum_j (1 / (j + 2))}, a SIF weighting under the assumption that
   * vocabulary order approximates frequency order (Zipf's law)}
   *
   * @param rows           The number of rows.
   * @param sifCoefficient The SIF coefficient.
   */
  static float[] zipfWeights(int rows, double sifCoefficient) {
    double harmonicSum = 0;
    for (int j = 2; j <= rows + 1; j++) {
      harmonicSum += 1.0 / j;
    }
    final float[] weights = new float[rows];
    for (int i = 0; i < rows; i++) {
      final double probability = 1.0 / (i + 2) / harmonicSum;
      weights[i] = (float) (sifCoefficient / (sifCoefficient + probability));
    }
    return weights;
  }

  /**
   * Replaces non-finite values with zero, the guard against a teacher emitting a NaN or infinite
   * hidden state. Model2Vec applies numpy's {@code nan_to_num} here, which maps an infinity to the
   * largest finite float; zero is used instead because an infinity of that magnitude still leaves
   * the principal component analysis with nothing but that one row.
   *
   * @param values The matrix, modified in place.
   */
  private static void nonFiniteToZero(float[] values) {
    for (int i = 0; i < values.length; i++) {
      if (!Float.isFinite(values[i])) {
        values[i] = 0;
      }
    }
  }

  /**
   * {@return the {@code config.json} of the distilled model, mirroring the fields Model2Vec
   * writes; the loader reads only {@code normalize}}
   *
   * @param teacherDirectory The teacher's directory, for the name.
   * @param pcaDims          The requested PCA dimension.
   * @param components       The effective PCA dimension.
   */
  private static String configJson(Path teacherDirectory, int pcaDims, int components) {
    final Path name = teacherDirectory.getFileName();
    return "{\n"
        + "  \"model_type\": \"model2vec\",\n"
        + "  \"architectures\": [\"StaticModel\"],\n"
        + "  \"tokenizer_name\": \"" + (name == null ? teacherDirectory : name) + "\",\n"
        + "  \"apply_pca\": " + pcaDims + ",\n"
        + "  \"sif_coefficient\": " + SIF_COEFFICIENT + ",\n"
        + "  \"hidden_dim\": " + components + ",\n"
        + "  \"seq_length\": 1000000,\n"
        + "  \"normalize\": true,\n"
        + "  \"pooling\": \"mean\",\n"
        + "  \"embedding_dtype\": \"float32\"\n"
        + "}\n";
  }

  /**
   * Copies the teacher's trained SentencePiece {@code .model} file into the model directory when
   * the teacher has one; the distillation cannot fabricate it and the loader needs it for the
   * SentencePiece layout.
   *
   * @param teacherDirectory The teacher's directory.
   * @param outputDirectory  The model directory.
   * @throws IOException Thrown if copying fails.
   */
  private static void copySentencePieceModel(Path teacherDirectory, Path outputDirectory)
      throws IOException {
    for (final String name : ModelFileNames.SENTENCEPIECE_MODELS) {
      final Path source = teacherDirectory.resolve(name);
      if (Files.isRegularFile(source)) {
        Files.copy(source, outputDirectory.resolve(name),
            StandardCopyOption.REPLACE_EXISTING);
        return;
      }
    }
  }
}
