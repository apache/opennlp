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
import java.util.List;

/**
 * The file names of a static embedding model directory, shared by
 * {@link StaticEmbeddingModel}'s loader and {@link ModelAssembler}. Every model directory has
 * one matrix file, either {@link #SAFETENSORS} or {@link #QUANTIZED}. A WordPiece directory also
 * has {@link #CONFIG}, {@link #VOCABULARY}, and {@link #TOKENIZER_CONFIG}; a Unigram directory has
 * {@link #CONFIG} and {@link #TOKENIZER_JSON}. Separate-file SentencePiece directories may additionally
 * have one of {@link #SENTENCEPIECE_MODELS}.
 *
 * <p>{@link #ONNX_MODEL} and {@link #ONNX_MODEL_DATA} name files of a <em>teacher</em> directory
 * rather than of a model directory; {@link ModelDistiller} and {@link HuggingFaceModelCache} share
 * them.</p>
 */
final class ModelFileNames {

  /** The safetensors file holding the embedding matrix and optional per-token weights. */
  static final String SAFETENSORS = "model.safetensors";

  /**
   * The quantized matrix file, written by the {@code QuantizeModel} tool. It contains the matrix
   * and any per-token weights itself, and is the directory's matrix source in place of
   * {@link #SAFETENSORS}, which a quantized deployment deletes.
   */
  static final String QUANTIZED = "model.quantized";

  /** The tokenizer description whose Unigram {@code model.vocab} order names the matrix rows. */
  static final String TOKENIZER_JSON = "tokenizer.json";

  /** The model configuration containing the {@code normalize} pooling switch. */
  static final String CONFIG = "config.json";

  /** The BERT-style vocabulary of a WordPiece model, one token per line in row order. */
  static final String VOCABULARY = "vocab.txt";

  /** The tokenizer configuration containing the WordPiece {@code do_lower_case} switch. */
  static final String TOKENIZER_CONFIG = "tokenizer_config.json";

  /** The optional term rows of the matrix, one normalized term per line in row order. */
  static final String TERMS = "terms.txt";

  /** The file names SentencePiece models ship their trained {@code .model} under, in try order. */
  static final List<String> SENTENCEPIECE_MODELS =
      List.of("sentencepiece.bpe.model", "spiece.model", "tokenizer.model");

  /** The ONNX graph of a teacher, relative to the teacher directory's root. */
  static final String ONNX_MODEL = "onnx/model.onnx";

  /** The external weights an ONNX export splits out of {@link #ONNX_MODEL}, if it splits them. */
  static final String ONNX_MODEL_DATA = "onnx/model.onnx_data";

  /** Not instantiable. */
  private ModelFileNames() {
  }

  /**
   * {@return the first of the given file names that exists as a regular file in the directory,
   * or {@code null} when none does}
   *
   * @param directory The directory to look in.
   * @param names     The file names to try, in order.
   */
  static Path firstRegularFile(Path directory, List<String> names) {
    for (final String name : names) {
      final Path file = directory.resolve(name);
      if (Files.isRegularFile(file)) {
        return file;
      }
    }
    return null;
  }
}
