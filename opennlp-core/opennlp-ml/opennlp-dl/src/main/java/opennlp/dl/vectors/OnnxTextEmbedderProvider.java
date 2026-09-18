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
import java.util.Locale;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;

import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.embeddings.TextEmbedderProvider;
import opennlp.tools.util.ext.ProviderSpec;

/**
 * Provides a {@link SentenceVectorsDL} under the name {@value #NAME}. It supports a spec whose
 * location is a local file named {@code *.onnx}, in any letter case, and whose only options are
 * {@value #VOCABULARY_OPTION}, {@value #LOWER_CASE_OPTION}, {@value #POOLING_OPTION},
 * {@value #NORMALIZE_OPTION} and {@value #MAX_LENGTH_OPTION}. The option values are checked by
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

  /** The option that selects the {@link Pooling}, {@code mean} by default or {@code cls}. */
  public static final String POOLING_OPTION = "pooling";

  /** The option that scales vectors to unit length, {@code true} by default or {@code false}. */
  public static final String NORMALIZE_OPTION = "normalize";

  /**
   * The option that sets the maximum number of tokens per input, at least {@code 2},
   * {@value SentenceVectorsDL#DEFAULT_MAX_LENGTH} by default.
   */
  public static final String MAX_LENGTH_OPTION = "maxLength";

  private static final String MODEL_SUFFIX = ".onnx";
  private static final String TRUE = "true";
  private static final String FALSE = "false";
  private static final String MEAN = Pooling.MEAN.name().toLowerCase(Locale.ROOT);
  private static final String CLS = Pooling.CLS.name().toLowerCase(Locale.ROOT);
  private static final int MIN_MAX_LENGTH = 2;

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
        && spec.hasOnlyOptions(VOCABULARY_OPTION, LOWER_CASE_OPTION, POOLING_OPTION,
            NORMALIZE_OPTION, MAX_LENGTH_OPTION);
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
    final boolean lowerCase = booleanOption(spec, LOWER_CASE_OPTION);
    final boolean normalize = booleanOption(spec, NORMALIZE_OPTION);
    final Pooling pooling = poolingOption(spec);
    final int maxLength = maxLengthOption(spec);
    final Path vocabularyPath = model.toAbsolutePath().getParent().resolve(vocabulary);
    try {
      return new SentenceVectorsDL(model.toFile(), vocabularyPath.toFile(), lowerCase, pooling,
          normalize, maxLength);
    } catch (final OrtException e) {
      throw new IOException("Cannot load the ONNX model " + model, e);
    } catch (final LinkageError e) {
      throw new IOException("Cannot initialize the ONNX Runtime", e);
    }
  }

  /**
   * Reads a boolean option that is {@code true} unless set to {@code false}.
   *
   * @param spec The spec to read.
   * @param name The name of the option.
   * @return The option value.
   * @throws IllegalArgumentException Thrown if the value is neither {@code true} nor
   *     {@code false}.
   */
  private boolean booleanOption(final ProviderSpec spec, final String name) {
    final String value = spec.option(name, TRUE);
    if (!TRUE.equals(value) && !FALSE.equals(value)) {
      throw new IllegalArgumentException(name + " must be true or false");
    }
    return TRUE.equals(value);
  }

  /**
   * Reads the {@value #POOLING_OPTION} option.
   *
   * @param spec The spec to read.
   * @return The pooling, {@link Pooling#MEAN} if the option is not set.
   * @throws IllegalArgumentException Thrown if the value is neither {@code mean} nor
   *     {@code cls}.
   */
  private Pooling poolingOption(final ProviderSpec spec) {
    final String value = spec.option(POOLING_OPTION, MEAN);
    if (MEAN.equals(value)) {
      return Pooling.MEAN;
    }
    if (CLS.equals(value)) {
      return Pooling.CLS;
    }
    throw new IllegalArgumentException(POOLING_OPTION + " must be " + MEAN + " or " + CLS);
  }

  /**
   * Reads the {@value #MAX_LENGTH_OPTION} option.
   *
   * @param spec The spec to read.
   * @return The maximum length, {@value SentenceVectorsDL#DEFAULT_MAX_LENGTH} if the option is
   *     not set.
   * @throws IllegalArgumentException Thrown if the value is not an integer of at least
   *     {@code 2}.
   */
  private int maxLengthOption(final ProviderSpec spec) {
    final String value = spec.option(MAX_LENGTH_OPTION, null);
    if (value == null) {
      return SentenceVectorsDL.DEFAULT_MAX_LENGTH;
    }
    try {
      final int maxLength = Integer.parseInt(value);
      if (maxLength >= MIN_MAX_LENGTH) {
        return maxLength;
      }
    } catch (final NumberFormatException e) {
      // reported below
    }
    throw new IllegalArgumentException(
        MAX_LENGTH_OPTION + " must be an integer of at least " + MIN_MAX_LENGTH);
  }
}
