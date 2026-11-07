/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.dl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.util.Span;

/**
 * Provides {@code tiny-vectors.onnx} (see {@code gen_tiny_vectors_model.py}) and a matching
 * vocabulary for tests that need an ONNX session but no trained model.
 */
final class TestModels {

  /** Maps every output index of {@code tiny-vectors.onnx} to a non-entity label. */
  static final Map<Integer, String> LABELS = Map.of(0, "O", 1, "O", 2, "O");

  /** Treats the whole input as one sentence. */
  static final SentenceDetector SINGLE_SENTENCE = new SentenceDetector() {
    @Override
    public String[] sentDetect(final CharSequence s) {
      return new String[] {s.toString()};
    }

    @Override
    public Span[] sentPosDetect(final CharSequence s) {
      return new Span[] {new Span(0, s.length())};
    }
  };

  private TestModels() {
  }

  /**
   * Copies {@code tiny-vectors.onnx} into a directory.
   *
   * @param dir The target directory.
   * @return The copied model file.
   * @throws IOException Thrown if the resource cannot be read or written.
   */
  static File model(final Path dir) throws IOException {
    return copy(dir, "/opennlp/dl/vectors/tiny-vectors.onnx", "tiny-vectors.onnx");
  }

  /**
   * Copies a plain-text BERT vocabulary into a directory.
   *
   * @param dir The target directory.
   * @return The copied vocabulary file.
   * @throws IOException Thrown if the resource cannot be read or written.
   */
  static File vocab(final Path dir) throws IOException {
    return copy(dir, "/opennlp/dl/vocab-plain.txt", "vocab.txt");
  }

  private static File copy(final Path dir, final String resource, final String name)
      throws IOException {
    final Path target = dir.resolve(name);
    try (InputStream in = Objects.requireNonNull(TestModels.class.getResourceAsStream(resource))) {
      Files.copy(in, target);
    }
    return target.toFile();
  }
}
