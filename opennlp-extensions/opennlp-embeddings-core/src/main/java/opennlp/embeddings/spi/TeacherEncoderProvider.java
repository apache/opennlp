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
package opennlp.embeddings.spi;

import java.nio.file.Path;

/**
 * Creates independently owned teacher encoders. Thread safety is implementation specific.
 *
 * @since 3.0.0
 */
public interface TeacherEncoderProvider {

  /** @return The nonblank, case-sensitive provider identifier. */
  String name();

  /**
   * Opens a teacher model. The caller closes the returned encoder.
   *
   * @param model The model file. Must not be {@code null}.
   * @return The encoder. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the model is invalid or cannot be loaded.
   */
  TeacherEncoder load(Path model);
}
