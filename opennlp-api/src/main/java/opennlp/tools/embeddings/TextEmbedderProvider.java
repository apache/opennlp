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
package opennlp.tools.embeddings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Creates text embedders from model files or directories. Thread safety is implementation specific.
 *
 * @since 3.0.0
 */
public interface TextEmbedderProvider {

  /** @return The nonblank, case-sensitive identifier used to select this provider. */
  String name();

  /**
   * Loads an independently owned embedder. The caller closes the returned instance.
   *
   * @param model The model file or directory. Must not be {@code null}.
   * @param options Provider-specific options. Must not be {@code null} or contain null keys
   *                or values. Supported options and defaults are documented by the provider.
   * @return The loaded embedder. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument or option is invalid or unsupported.
   * @throws IOException Thrown if the model cannot be loaded.
   */
  TextEmbedder load(Path model, Map<String, String> options) throws IOException;
}
