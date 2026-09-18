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

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;

import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.embeddings.TextEmbedderProvider;
import opennlp.tools.util.ext.ProviderSpec;

/**
 * Provides a {@link SentenceVectorsDL} under the name {@value #NAME}. It supports a spec whose
 * location is a local file named {@code *.onnx}, in any letter case, and whose only options are
 * {@value #VOCABULARY_OPTION} and {@value #LOWER_CASE_OPTION}. The option values are checked by
 * {@link #create(ProviderSpec)}, which also initializes the ONNX Runtime.
 *
 * @since 3.0.0
 */
public final class OnnxTextEmbedderProvider implements TextEmbedderProvider {

  /** The name of this provider. */
  public static final String NAME = "onnx";

  /**
   * The required option that names the vocabulary file, resolved against the directory of the
   * model if it is relative.
   */
  public static final String VOCABULARY_OPTION = "vocabulary";

  /** The option that lower-cases the text, {@code true} by default or {@code false}. */
  public static final String LOWER_CASE_OPTION = "lowerCase";

  private static final String MODEL_SUFFIX = ".onnx";
  private static final String TRUE = "true";
  private static final String FALSE = "false";

  @Override
  public String name() {
    return NAME;
  }

  /**
   * {@inheritDoc}
   * Checks that the ONNX Runtime classes are present without initializing the runtime.
   */
  @Override
  public boolean isAvailable() {
    try {
      return OrtEnvironment.class.getName() != null;
    } catch (final LinkageError e) {
      return false;
    }
  }

  @Override
  public boolean supports(final ProviderSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    return spec.path().isPresent() && spec.locationEndsWith(MODEL_SUFFIX)
        && spec.hasOnlyOptions(VOCABULARY_OPTION, LOWER_CASE_OPTION);
  }

  @Override
  public TextEmbedder create(final ProviderSpec spec) throws IOException {
    if (spec == null || !supports(spec)) {
      throw new IllegalArgumentException("spec is not supported: " + spec);
    }
    final Path model = spec.path().orElseThrow();
    if (!Files.isRegularFile(model)) {
      throw new IllegalArgumentException("model must be a regular file: " + model);
    }
    final String vocabulary = spec.option(VOCABULARY_OPTION, null);
    if (vocabulary == null || vocabulary.isBlank()) {
      throw new IllegalArgumentException(VOCABULARY_OPTION + " must not be null or blank");
    }
    final String lowerCase = spec.option(LOWER_CASE_OPTION, TRUE);
    if (!TRUE.equals(lowerCase) && !FALSE.equals(lowerCase)) {
      throw new IllegalArgumentException(LOWER_CASE_OPTION + " must be true or false");
    }
    final Path vocabularyPath = model.toAbsolutePath().getParent().resolve(vocabulary);
    try {
      return new SentenceVectorsDL(model.toFile(), vocabularyPath.toFile(),
          Boolean.parseBoolean(lowerCase));
    } catch (final OrtException e) {
      throw new IOException("Cannot load the ONNX model " + model, e);
    }
  }
}
