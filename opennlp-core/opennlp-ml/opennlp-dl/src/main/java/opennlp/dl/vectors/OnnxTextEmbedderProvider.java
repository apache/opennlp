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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import ai.onnxruntime.OrtException;

import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.embeddings.TextEmbedderProvider;

/**
 * Loads {@link SentenceVectorsDL} through the {@code onnx} provider. The required
 * {@code vocabulary} option names a vocabulary file; {@code lowerCase} accepts {@code true}
 * (the default) or {@code false}. Relative vocabulary paths resolve against the model's parent.
 * Construction does not initialize ONNX Runtime; {@link #load(Path, Map)} opens the session.
 *
 * @since 3.0.0
 */
public final class OnnxTextEmbedderProvider implements TextEmbedderProvider {

  private static final String VOCABULARY = "vocabulary";
  private static final String LOWER_CASE = "lowerCase";

  /** Creates a factory without opening a model. */
  public OnnxTextEmbedderProvider() {
  }

  /** {@inheritDoc} */
  @Override
  public String name() {
    return "onnx";
  }

  /** {@inheritDoc} */
  @Override
  public TextEmbedder load(Path model, Map<String, String> options) throws IOException {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    if (options == null) {
      throw new IllegalArgumentException("options must not be null");
    }
    for (Map.Entry<String, String> option : options.entrySet()) {
      if ((!VOCABULARY.equals(option.getKey()) && !LOWER_CASE.equals(option.getKey()))
          || option.getValue() == null) {
        throw new IllegalArgumentException("Invalid or unsupported option: " + option.getKey());
      }
    }
    if (!Files.isRegularFile(model)) {
      throw new IllegalArgumentException("model must be a regular file: " + model);
    }
    String vocabulary = options.get(VOCABULARY);
    if (vocabulary == null || vocabulary.isBlank()) {
      throw new IllegalArgumentException("vocabulary must not be null or blank");
    }
    String lowerCase = options.getOrDefault(LOWER_CASE, "true");
    if (!"true".equals(lowerCase) && !"false".equals(lowerCase)) {
      throw new IllegalArgumentException("lowerCase must be true or false");
    }
    Path vocabularyPath = Path.of(vocabulary);
    if (!vocabularyPath.isAbsolute()) {
      vocabularyPath = model.toAbsolutePath().getParent().resolve(vocabularyPath);
    }
    try {
      return new SentenceVectorsDL(model.toFile(), vocabularyPath.toFile(),
          Boolean.parseBoolean(lowerCase));
    } catch (OrtException e) {
      throw new IOException("Failed to load ONNX embedding model: " + model, e);
    }
  }
}
