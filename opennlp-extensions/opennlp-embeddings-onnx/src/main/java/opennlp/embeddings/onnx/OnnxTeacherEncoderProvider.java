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
package opennlp.embeddings.onnx;

import java.nio.file.Path;
import java.util.Locale;

import opennlp.embeddings.spi.TeacherEncoder;
import opennlp.embeddings.spi.TeacherEncoderProvider;

/**
 * Opens ONNX teacher sessions on demand. The provider name is {@code onnx}. It supports a
 * model file whose name ends with {@code .onnx} and is available when the ONNX Runtime classes
 * can be loaded; the runtime itself is initialized by {@link #load(Path)}, not by construction
 * or by the checks.
 *
 * @since 3.0.0
 */
public final class OnnxTeacherEncoderProvider implements TeacherEncoderProvider {

  private static final String NAME = "onnx";
  private static final String MODEL_SUFFIX = ".onnx";
  private static final String RUNTIME_CLASS = "ai.onnxruntime.OrtEnvironment";

  /** Creates a factory without opening a model. */
  public OnnxTeacherEncoderProvider() {
  }

  /** {@inheritDoc} */
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
      Class.forName(RUNTIME_CLASS, false, OnnxTeacherEncoderProvider.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   * A model file named {@code *.onnx}, in any letter case, is supported; the file itself is
   * checked by {@link #load(Path)}.
   */
  @Override
  public boolean supports(Path model) {
    return model != null && model.getFileName() != null
        && model.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(MODEL_SUFFIX);
  }

  /** {@inheritDoc} */
  @Override
  public TeacherEncoder load(Path model) {
    return OnnxTeacherEncoder.load(model);
  }
}
