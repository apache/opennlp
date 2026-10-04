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

import java.util.List;

/**
 * How {@link SentenceVectorsDL#embedAll(List)} shapes the tensors of one inference when the texts
 * of a call differ in tokenized length.
 *
 * <p>A padded position is {@code 0} in the attention mask and {@link Pooling} reads only the
 * positions a row's own mask covers, so the vectors of all three strategies match up to
 * floating-point rounding, provided the model honors the attention mask. A
 * {@code sentence_embedding} output that ignores the mask changes with padding. The strategies
 * differ in tensor shape and in how many times the session runs.</p>
 *
 * @since 3.0.0
 */
public enum PaddingStrategy {

  /**
   * No padding. Texts are grouped by their exact tokenized length and each group runs on its own.
   * This is the default. On natural text most groups hold one or two texts.
   */
  EXACT_LENGTH,

  /**
   * Every row of a batch is padded to the longest row of that batch. Texts are ordered by length
   * before batching, so one long text does not pad out a batch of short ones. Requires a padding
   * token in the vocabulary.
   */
  LONGEST,

  /**
   * Every row is padded to the maximum length, so every inference has the same shape. Single
   * calls such as {@link SentenceVectorsDL#embed(CharSequence)} and
   * {@link SentenceVectorsDL#getVectors(String)} pad to the maximum length as well. Useful for a
   * model or accelerator that needs a fixed sequence length. Requires a padding token in the
   * vocabulary.
   */
  MAX_LENGTH
}
