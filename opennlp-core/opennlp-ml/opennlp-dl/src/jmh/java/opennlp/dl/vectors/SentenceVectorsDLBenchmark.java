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
package opennlp.dl.vectors;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import opennlp.dl.InferenceOptions;
import opennlp.tools.tokenize.WordpieceEncoder;

/**
 * Times {@link SentenceVectorsDL} on real sentence-transformers models, one call of
 * {@code callSize} texts per operation, on the CPU or a CUDA device.
 *
 * <p>The texts are 4 to 40 words drawn with a fixed seed from a small word list, so a call
 * mixes tokenized lengths the way natural text does. Every operation reports the average time
 * per call and its percentiles, and through {@link Counts} the time per text and per token.
 * Run with {@code -prof gc} for the heap bytes allocated per call and with
 * {@code -prof opennlp.dl.vectors.NativeMemoryProfiler} for the direct buffers and the resident
 * set size.</p>
 *
 * <p>Each {@code model} is a directory under {@code modelRoot} that holds {@code model.onnx} and
 * {@code vocab.txt}. The defaults are {@code all-MiniLM-L6-v2} (384 dimensions) and
 * {@code e5-large-v2} (1024 dimensions) under {@code ~/.opennlp/onnx}.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class SentenceVectorsDLBenchmark {

  private static final String[] WORDS = {"the", "model", "reads", "a", "short", "sentence",
      "and", "returns", "one", "vector", "for", "search", "over", "news", "articles", "with",
      "many", "different", "lengths", "while", "batching", "keeps", "throughput", "high",
      "on", "both", "devices", "today", "tomorrow", "city", "council", "approved", "budget"};

  private static final int MIN_WORDS = 4;

  private static final int MAX_WORDS = 40;

  private static final long SEED = 42;

  /** The directory that holds the model directories; empty for {@code ~/.opennlp/onnx}. */
  @Param({""})
  public String modelRoot;

  /** The model directory under {@link #modelRoot}, one small and one large dimension. */
  @Param({"all-MiniLM-L6-v2", "e5-large-v2"})
  public String model;

  /** {@code cpu} or {@code gpu} (CUDA device 0). */
  @Param({"cpu"})
  public String device;

  /** The number of texts in one call. */
  @Param({"1", "8", "32", "128"})
  public int callSize;

  private SentenceVectorsDL vectors;

  private List<String> texts;

  private int tokens;

  /**
   * The texts and tokens embedded per iteration. JMH reports them next to the calls, so the time
   * per call also shows as time per text and time per token.
   */
  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.OPERATIONS)
  public static class Counts {

    /** The texts embedded in this iteration. */
    public long texts;

    /** The token positions embedded in this iteration, {@code [CLS]} and {@code [SEP]} included. */
    public long tokens;

    /** Starts the iteration at zero. */
    @Setup(Level.Iteration)
    public void reset() {
      texts = 0;
      tokens = 0;
    }
  }

  /**
   * Opens the session and builds the texts of one call.
   *
   * @throws IllegalStateException Thrown if the model directory has no {@code model.onnx}.
   * @throws Exception Thrown if the model cannot be loaded or the device is not usable.
   */
  @Setup(Level.Trial)
  public void setUp() throws Exception {
    final File root = modelRoot.isEmpty()
        ? new File(System.getProperty("user.home"), ".opennlp/onnx") : new File(modelRoot);
    final File dir = new File(root, model);
    if (!new File(dir, "model.onnx").isFile()) {
      throw new IllegalStateException("No model.onnx in " + dir.getPath()
          + "; set -p modelRoot=<dir> to the directory that holds the model directories.");
    }
    final InferenceOptions options = new InferenceOptions();
    options.setGpu("gpu".equals(device));
    vectors = new SentenceVectorsDL(new File(dir, "model.onnx"), new File(dir, "vocab.txt"),
        true, Pooling.MEAN, true, SentenceVectorsDL.DEFAULT_MAX_LENGTH, options);
    final Random random = new Random(SEED);
    texts = new ArrayList<>(callSize);
    for (int i = 0; i < callSize; i++) {
      final int words = MIN_WORDS + random.nextInt(MAX_WORDS - MIN_WORDS + 1);
      final StringBuilder text = new StringBuilder();
      for (int w = 0; w < words; w++) {
        if (w > 0) {
          text.append(' ');
        }
        text.append(WORDS[random.nextInt(WORDS.length)]);
      }
      texts.add(text.toString());
    }
    tokens = countTokens(new File(dir, "vocab.txt"));
  }

  /**
   * {@return the token positions of one call, as {@link SentenceVectorsDL} encodes the texts}
   *
   * @param vocab The vocabulary of the model.
   * @throws IOException Thrown if the vocabulary cannot be read.
   */
  private int countTokens(final File vocab) throws IOException {
    final WordpieceEncoder encoder = new WordpieceEncoder(
        Files.readAllLines(vocab.toPath(), StandardCharsets.UTF_8), true);
    int count = 0;
    for (final String text : texts) {
      count += Math.min(encoder.encode(text).size(), SentenceVectorsDL.DEFAULT_MAX_LENGTH);
    }
    return count;
  }

  /** Closes the session. */
  @TearDown(Level.Trial)
  public void tearDown() {
    vectors.close();
  }

  /**
   * Embeds the call one text at a time, as a caller without {@code embedAll} would.
   *
   * @param counts Counts the texts and tokens.
   * @param blackhole Consumes the vectors.
   */
  @Benchmark
  public void embedEach(final Counts counts, final Blackhole blackhole) {
    for (final String text : texts) {
      blackhole.consume(vectors.embed(text));
    }
    counts.texts += callSize;
    counts.tokens += tokens;
  }

  /**
   * Embeds the call with one {@code embedAll}.
   *
   * @param counts Counts the texts and tokens.
   * @return The vectors.
   */
  @Benchmark
  public float[][] embedAll(final Counts counts) {
    counts.texts += callSize;
    counts.tokens += tokens;
    return vectors.embedAll(texts);
  }
}
