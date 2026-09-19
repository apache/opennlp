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

import java.io.Serial;

/**
 * Thrown if a {@link TextEmbedder} fails to embed text, for example because the inference
 * backend reports an error.
 *
 * @since 3.0.0
 */
public class EmbeddingException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * @param message The detail message.
   * @param cause The underlying failure, or {@code null} if there is none.
   */
  public EmbeddingException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
