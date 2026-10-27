/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl.vectors;

import opennlp.tools.util.ParamChecks;

/**
 * Pooling, normalization, truncation and padding settings for {@link SentenceVectorsDL}.
 *
 * @param pooling How token vectors are pooled. Not used for a model with a
 *     {@code sentence_embedding} output. Must not be {@code null}.
 * @param normalize {@code true} to scale every vector to unit length.
 * @param maxLength The maximum number of tokens per input, including the classification and
 *     separator tokens. Longer input is truncated. Must be at least {@code 2}.
 * @param padding The padding strategy, or {@code null} for the device and model default:
 *     {@link SentenceVectorsDL#DEFAULT_GPU_PADDING} on a GPU for a token output if the
 *     vocabulary has a padding token, {@link SentenceVectorsDL#DEFAULT_CPU_PADDING} otherwise.
 *     An explicit padded strategy requires a padding token in the vocabulary.
 *
 * @since 3.0.0
 */
public record SentenceVectorsOptions(Pooling pooling, boolean normalize, int maxLength,
                                    PaddingStrategy padding) {

  /**
   * Creates vector settings after validating the pooling mode and maximum length.
   *
   * @throws IllegalArgumentException Thrown if {@code pooling} is {@code null} or
   *     {@code maxLength} is less than {@code 2}.
   */
  public SentenceVectorsOptions {
    ParamChecks.requireNonNullArg(pooling, "pooling");
    if (maxLength < 2) {
      throw new IllegalArgumentException("maxLength must be at least 2");
    }
  }

  /**
   * Creates settings for mean pooling, unit length scaling, a maximum of
   * {@value SentenceVectorsDL#DEFAULT_MAX_LENGTH} tokens and the device and model padding default.
   */
  public SentenceVectorsOptions() {
    this(Pooling.MEAN, true, SentenceVectorsDL.DEFAULT_MAX_LENGTH, null);
  }
}
