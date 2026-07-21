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

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import opennlp.embeddings.StaticEmbeddingModel;

/**
 * The JVM half of the parity and speed comparison (see run.sh). Loads the static table, writes
 * one vector per input sentence for the parity check, then measures single-thread embed
 * throughput with the same fixed-duration, warmup-discarded methodology the Python side uses.
 *
 * <p>Args: modelDir sentencesFile vectorsOut warmupSeconds measureSeconds</p>
 */
public final class EmbedBenchM3 {

  /** Not instantiable. */
  private EmbedBenchM3() {
  }

  /**
   * Runs the parity dump and the single-thread throughput measurement.
   *
   * @param args modelDir, sentencesFile, vectorsOut, warmupSeconds, measureSeconds.
   * @throws Exception Thrown if a file cannot be read or written.
   */
  public static void main(String[] args) throws Exception {
    final Path modelDir = Path.of(args[0]);
    final List<String> sentences = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)
        .stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    final Path vectorsOut = Path.of(args[2]);
    final int warmupSeconds = Integer.parseInt(args[3]);
    final int measureSeconds = Integer.parseInt(args[4]);

    final long loadStart = System.nanoTime();
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(modelDir);
    final double loadMs = (System.nanoTime() - loadStart) / 1e6;

    // One vector per sentence, so the Python side can diff them for parity.
    try (BufferedWriter writer = Files.newBufferedWriter(vectorsOut, StandardCharsets.UTF_8)) {
      for (final String sentence : sentences) {
        final float[] vector = model.embed(sentence);
        final StringBuilder line = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
          if (i > 0) {
            line.append(' ');
          }
          line.append(Float.toString(vector[i]));
        }
        writer.write(line.toString());
        writer.newLine();
      }
    }

    final long warmupEnd = System.nanoTime() + warmupSeconds * 1_000_000_000L;
    int index = 0;
    while (System.nanoTime() < warmupEnd) {
      model.embed(sentences.get(index++ % sentences.size()));
    }

    long embedded = 0;
    final long measureStart = System.nanoTime();
    final long measureEnd = measureStart + measureSeconds * 1_000_000_000L;
    index = 0;
    while (System.nanoTime() < measureEnd) {
      model.embed(sentences.get(index++ % sentences.size()));
      embedded++;
    }
    final double seconds = (System.nanoTime() - measureStart) / 1e9;

    System.out.printf("JVM     load %.0f ms  |  %,.0f texts/s single-thread  (%d embeds in %.1fs)%n",
        loadMs, embedded / seconds, embedded, seconds);
  }
}
