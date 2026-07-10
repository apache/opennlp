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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import opennlp.embeddings.StaticEmbeddingModel.Casing;
import opennlp.embeddings.StaticEmbeddingModel.Normalization;

/**
 * JMH benchmark for {@link StaticEmbeddingModel}, the raw-lookup-throughput number the module's
 * design doc calls for before any "faster than Python" claim is made (a concurrent gRPC-traffic
 * comparison against a Python baseline is a separate, later benchmark; this one is the JVM-only
 * baseline). The fixture is sized to match {@code minishlab/potion-base-8M} (29,528 vocabulary
 * rows, 256 dimensions), synthesized rather than downloaded so the benchmark has no network
 * dependency, but seeded with real English words so the benchmark sentences tokenize into actual
 * vocabulary hits rather than degenerating into all-unknown-token lookups.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
public class StaticEmbeddingModelBenchmark {

  // Matches minishlab/potion-base-8M's config.json (hidden_dim) and its reported total
  // parameter count (7,559,168 / 256), verified against the real model repo, not guessed.
  private static final int VOCAB_SIZE = 29_528;
  private static final int DIMENSION = 256;

  private static final String[] REAL_WORDS = {
      "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog", "she", "told", "me", "he",
      "lived", "in", "wrote", "letter", "right", "away", "opennlp", "provides", "tools", "for",
      "language", "processing", "driver", "got", "badly", "injured", "by", "accident",
  };

  private static final String[] SENTENCES = {
      "The quick brown fox jumps over the lazy dog.",
      "She told me he lived in Edinburgh.",
      "I wrote him a letter right away.",
      "OpenNLP provides tools for natural language processing.",
      "The driver got badly injured by the accident.",
  };

  @State(Scope.Benchmark)
  public static class ModelState {

    StaticEmbeddingModel model;
    private Path tempDir;

    @Setup(Level.Trial)
    public void load() throws IOException {
      tempDir = Files.createTempDirectory("opennlp-embeddings-jmh");
      final Path vocabFile = writeVocab(tempDir);
      final Path safetensorsFile = writeSafetensors(tempDir);
      model = StaticEmbeddingModel.load(vocabFile, safetensorsFile,
            Casing.UNCASED, Normalization.L2);
    }

    @TearDown(Level.Trial)
    public void cleanup() throws IOException {
      Files.deleteIfExists(tempDir.resolve("vocab.txt"));
      Files.deleteIfExists(tempDir.resolve("model.safetensors"));
      Files.deleteIfExists(tempDir);
    }

    private static Path writeVocab(Path dir) throws IOException {
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
      final Path file = dir.resolve("vocab.txt");
      Files.write(file, tokens);
      return file;
    }

    private static Path writeSafetensors(Path dir) throws IOException {
      final Random random = new Random(42);
      final ByteBuffer buffer = ByteBuffer.allocate(VOCAB_SIZE * DIMENSION * 4)
          .order(ByteOrder.LITTLE_ENDIAN);
      for (int i = 0; i < VOCAB_SIZE * DIMENSION; i++) {
        buffer.putFloat((random.nextFloat() - 0.5f) * 2f);
      }
      final byte[] data = buffer.array();
      final String header = "{\"embeddings\":{\"dtype\":\"F32\",\"shape\":[" + VOCAB_SIZE + ","
          + DIMENSION + "],\"data_offsets\":[0," + data.length + "]}}";
      final byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
          .putLong(headerBytes.length).array());
      out.write(headerBytes);
      out.write(data);
      final Path file = dir.resolve("model.safetensors");
      Files.write(file, out.toByteArray());
      return file;
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void embed(ModelState state, Blackhole blackhole) {
    for (final String sentence : SENTENCES) {
      blackhole.consume(state.model.embed(sentence));
    }
  }

  @Benchmark
  @Threads(Threads.MAX)
  public void mostSimilarTop10(ModelState state, Blackhole blackhole) {
    blackhole.consume(state.model.mostSimilar(SENTENCES[0], 10));
  }

  /**
   * Quick local iteration only: {@code forks(0)} disables JVM fork isolation (unlike
   * {@code mvn} with the {@code jmh} profile). Use the Maven-invoked configuration for
   * publishable numbers.
   */
  public static void main(String[] args) throws Exception {
    final Options opt = new OptionsBuilder()
        .include(StaticEmbeddingModelBenchmark.class.getSimpleName())
        .forks(0)
        .warmupIterations(3)
        .measurementIterations(5)
        .build();
    new Runner(opt).run();
  }
}
