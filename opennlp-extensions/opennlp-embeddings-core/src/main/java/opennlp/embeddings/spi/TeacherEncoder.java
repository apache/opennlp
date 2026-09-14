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

/**
 * Encodes prepared token sequences for static-model distillation.
 * Thread safety is implementation specific.
 *
 * @since 3.0.0
 */
public interface TeacherEncoder extends AutoCloseable {

  /**
   * Mean-pools the token hidden states for each sequence, without L2 normalization.
   *
   * @param batch Token ids, with shape {@code [batchSize][sequenceLength]}. Must not be null
   *              or empty, and must contain non-null, nonempty sequences of equal length.
   * @return One vector per input, in input order. The dimension must remain constant across
   *         calls. Neither the result nor any vector may be null.
   * @throws IllegalArgumentException Thrown if input is invalid or cannot be encoded.
   */
  float[][] encodeBatch(long[][] batch);

  /** Releases this encoder's resources. Repeated calls have no effect. */
  @Override
  void close();
}
