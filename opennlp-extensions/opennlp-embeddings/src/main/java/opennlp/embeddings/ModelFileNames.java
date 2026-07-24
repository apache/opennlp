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

import java.util.List;

/**
 * The file names of a static embedding model directory, shared by
 * {@link StaticEmbeddingModel}'s loader and {@link ModelAssembler}. A WordPiece directory holds
 * {@link #SAFETENSORS}, {@link #CONFIG}, {@link #VOCABULARY}, and {@link #TOKENIZER_CONFIG}; a
 * SentencePiece directory holds {@link #SAFETENSORS}, {@link #CONFIG}, {@link #TOKENIZER_JSON},
 * and one of {@link #SENTENCEPIECE_MODELS}.
 */
final class ModelFileNames {

  /** The safetensors file holding the embedding matrix and optional per-token weights. */
  static final String SAFETENSORS = "model.safetensors";

  /** The tokenizer description whose Unigram {@code model.vocab} order names the matrix rows. */
  static final String TOKENIZER_JSON = "tokenizer.json";

  /** The model configuration carrying the {@code normalize} pooling switch. */
  static final String CONFIG = "config.json";

  /** The BERT-style vocabulary of a WordPiece model, one token per line in row order. */
  static final String VOCABULARY = "vocab.txt";

  /** The tokenizer configuration carrying the WordPiece {@code do_lower_case} switch. */
  static final String TOKENIZER_CONFIG = "tokenizer_config.json";

  /** The file names SentencePiece models ship their trained {@code .model} under, in try order. */
  static final List<String> SENTENCEPIECE_MODELS =
      List.of("sentencepiece.bpe.model", "spiece.model", "tokenizer.model");

  /** Not instantiable. */
  private ModelFileNames() {
  }
}
