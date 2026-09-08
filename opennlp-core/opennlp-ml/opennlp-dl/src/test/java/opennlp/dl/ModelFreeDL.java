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

package opennlp.dl;

import java.util.Map;

/**
 * An {@link AbstractDL} without an ONNX environment or session, so the shared encoder can be
 * exercised on a plain vocabulary. Only {@link #encode(CharSequence)} may be called; anything
 * reaching the absent session fails.
 */
final class ModelFreeDL extends AbstractDL {

  /**
   * Creates an encoder over the given vocabulary.
   *
   * @param vocab The token-to-id map; must not be {@code null} and must contain the special
   *     tokens the vocabulary's model family requires.
   * @param lowerCase {@code true} to lower case and strip accents, as for an uncased model.
   *
   * @throws IllegalArgumentException Thrown if {@code vocab} is {@code null} or lacks a
   *     required special token.
   */
  ModelFreeDL(Map<String, Integer> vocab, boolean lowerCase) {
    super(null, null, vocab, lowerCase);
  }

  /**
   * Encodes text through {@link AbstractDL#encodeTokens(CharSequence)}.
   *
   * @param text The text to encode; must not be {@code null}.
   * @return The model input arrays.
   *
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  Tokens encode(CharSequence text) {
    return encodeTokens(text);
  }
}
