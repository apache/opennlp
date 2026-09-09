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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmarks model loading, embedding and nearest-neighbor queries.
 *
 * <p>The default {@code modelDir=synthetic} uses a generated 29,528 by 256 table without
 * downloads. Use {@code -p modelDir=/models/table} for a local model. Fixture creation is
 * outside the timed operations. Loading includes file access, decoding and model construction;
 * repeated loads can use the operating system's file cache. Add {@code -prof gc} for allocation
 * statistics.</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
@Threads(1)
public class StaticEmbeddingModelBenchmark {

  /** The synthetic-fixture selector; any other value is treated as a model directory path. */
  private static final String SYNTHETIC = "synthetic";

  private static final int VOCAB_SIZE = 29_528;
  private static final int DIMENSION = 256;

  private static final String[] REAL_WORDS = {
      "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog", "she", "told", "me", "he",
      "lived", "in", "wrote", "letter", "right", "away", "opennlp", "provides", "tools", "for",
      "language", "processing", "driver", "got", "badly", "injured", "by", "accident",
  };

  static final List<String> SENTENCES = List.of(
      "The quick brown fox jumps over the lazy dog.",
      "She told me he lived in Edinburgh.",
      "I wrote him a letter right away.",
      "OpenNLP provides tools for natural language processing.",
      "The driver got badly injured by the accident.");

  /** Model files shared by the benchmark threads. */
  @State(Scope.Benchmark)
  public static class ModelFiles {

    /**
     * The model to benchmark: {@code "synthetic"} for the built-in fixture, or a model directory
     * path. Override with {@code -p modelDir=dir1,dir2} to benchmark real tables.
     */
    @Param({SYNTHETIC})
    public String modelDir;

    Path directory;
    private Path tempDir;

    /**
     * Prepares a local model directory without loading the model.
     *
     * @throws IOException If fixture creation fails.
     */
    @Setup(Level.Trial)
    public void prepare() throws IOException {
      if (SYNTHETIC.equals(modelDir)) {
        tempDir = Files.createTempDirectory("opennlp-embeddings-jmh");
        directory = tempDir;
        writeVocab();
        writeSafetensors();
        Files.writeString(directory.resolve(ModelFileNames.CONFIG), "{\"normalize\":true}");
        Files.writeString(directory.resolve(ModelFileNames.TOKENIZER_CONFIG), "{\"do_lower_case\":true}");
      } else {
        directory = Path.of(modelDir);
      }
    }

    /**
     * Deletes generated fixtures, not user model files.
     *
     * @throws IOException If fixture deletion fails.
     */
    @TearDown(Level.Trial)
    public void cleanup() throws IOException {
      if (tempDir != null) {
        Files.deleteIfExists(tempDir.resolve(ModelFileNames.VOCABULARY));
        Files.deleteIfExists(tempDir.resolve(ModelFileNames.SAFETENSORS));
        Files.deleteIfExists(tempDir.resolve(ModelFileNames.CONFIG));
        Files.deleteIfExists(tempDir.resolve(ModelFileNames.TOKENIZER_CONFIG));
        Files.deleteIfExists(tempDir);
      }
    }

    /**
     * Writes WordPiece tokens for the generated table.
     *
     * @throws IOException If the file cannot be written.
     */
    private void writeVocab() throws IOException {
      final List<String> tokens = new ArrayList<>(VOCAB_SIZE);
      tokens.add("[CLS]");
      tokens.add("[SEP]");
      tokens.add("[UNK]");
      for (final String word : REAL_WORDS) {
        tokens.add(word);
      }
      while (tokens.size() < VOCAB_SIZE) {
        tokens.add("tok" + (tokens.size() - REAL_WORDS.length - 3));
      }
      Files.write(directory.resolve(ModelFileNames.VOCABULARY), tokens);
    }

    /**
     * Writes deterministic F32 embedding values.
     *
     * @throws IOException If the file cannot be written.
     */
    private void writeSafetensors() throws IOException {
      final Random random = new Random(42);
      final float[] values = new float[VOCAB_SIZE * DIMENSION];
      for (int i = 0; i < values.length; i++) {
        values[i] = (random.nextFloat() - 0.5f) * 2f;
      }
      SafetensorsTestFiles.write(directory.resolve(ModelFileNames.SAFETENSORS),
          new SafetensorsTestFiles.Tensor("embeddings", new int[] {VOCAB_SIZE, DIMENSION}, values));
    }
  }

  /** A loaded model shared by inference threads. */
  @State(Scope.Benchmark)
  public static class ModelState {

    StaticEmbeddingModel model;

    /**
     * Loads the model before inference timing starts.
     *
     * @param files The prepared model directory.
     * @throws IllegalArgumentException If the path is not a model directory.
     * @throws IOException If the model cannot be loaded.
     */
    @Setup(Level.Trial)
    public void load(ModelFiles files) throws IOException {
      model = StaticEmbeddingModel.load(files.directory);
    }
  }

  /**
   * Loads a new model instance per operation.
   *
   * @param files The prepared model directory.
   * @return The loaded model.
   * @throws IllegalArgumentException If the path is not a model directory.
   * @throws IOException If the model cannot be loaded.
   */
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public StaticEmbeddingModel load(ModelFiles files) throws IOException {
    return StaticEmbeddingModel.load(files.directory);
  }

  /**
   * Embeds a batch with throughput and allocation expressed per input text.
   *
   * @param state The loaded model.
   * @param blackhole Receives the output vectors.
   */
  @Benchmark
  @OperationsPerInvocation(5)
  public void embed(ModelState state, Blackhole blackhole) {
    for (final String sentence : SENTENCES) {
      blackhole.consume(state.model.embed(sentence));
    }
  }

  /**
   * Searches the model for 10 nearest tokens per operation.
   *
   * @param state The loaded model.
   * @param blackhole Receives the search results.
   */
  @Benchmark
  public void mostSimilarTop10(ModelState state, Blackhole blackhole) {
    blackhole.consume(state.model.mostSimilar(SENTENCES.get(0), 10));
  }

  /**
   * Runs the embedding benchmarks with JMH command-line options.
   *
   * @param args JMH options, such as {@code -t 1 -prof gc}.
   * @throws Exception If option parsing or benchmark execution fails.
   */
  public static void main(String[] args) throws Exception {
    final Options opt = new OptionsBuilder()
        .parent(new CommandLineOptions(args))
        .include(StaticEmbeddingModelBenchmark.class.getSimpleName())
        .shouldFailOnError(true)
        .build();
    new Runner(opt).run();
  }
}
