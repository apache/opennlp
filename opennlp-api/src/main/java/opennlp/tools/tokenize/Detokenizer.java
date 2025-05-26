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

package opennlp.tools.tokenize;

/**
 * A {@link Detokenizer} merges tokens back to their detokenized representation.
 */
public interface Detokenizer {

  /**
   * This enum contains an operation for every token to merge the
   * tokens together to their detokenized form.
   */
  enum DetokenizationOperation {
    /**
     * The current token should be attached to the start token on the right side.
     */
    MERGE_TO_RIGHT,

    /**
     * The current token should be attached to the string on the left side.
     */
    MERGE_TO_LEFT,

    /**
     * The current token should be attached to the string on the left side, as
     * well as to the start token on the right side.
     */
    MERGE_BOTH,

    /**
     * Do not perform a merge operation for this token, but is possible that another
     * token can be attached to the left or right side of this one.
     */
    NO_OPERATION
  }

  /**
   * Detokenizes the collection of tokens.
   *
   * @param tokens The elements which should be detokenized.
   * @return The {@link DetokenizationOperation merge operations} to handle
   *         given {@code tokens}.
   */
  DetokenizationOperation[] detokenize(String[] tokens);

  /**
   * Detokenizes the input {@code tokens} into a String. Tokens which
   * are connected without a {@code whitespace} character in
   * between can be separated by a given {@code splitMarker}.
   *
   * @param tokens The elements which should be concatenated.
   * @param splitMarker The split marker or {@code null}.
   *
   * @return The concatenated tokens as a single string.
   */
  String detokenize(String[] tokens, String splitMarker);
}
