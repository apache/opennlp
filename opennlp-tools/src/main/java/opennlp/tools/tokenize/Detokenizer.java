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
 * A Detokenizer merges tokens back to their untokenized representation.
 *
 */
public interface Detokenizer {

  /**
   * This enum contains an operation for every token to merge the
   * tokens together to their detokenized form.
   */
  enum DetokenizationOperation {
    /**
     * The current token should be attached to the begin token on the right side.
     */
    MERGE_TO_RIGHT,

    /**
     * The current token should be attached to the string on the left side.
     */
    MERGE_TO_LEFT,

    /**
     * The current token should be attached to the string on the left side, as
     * well as to the begin token on the right side.
     */
    MERGE_BOTH,

    /**
     * Do not perform a merge operation for this token, but is possible that another
     * token can be attached to the left or right side of this one.
     */
    NO_OPERATION
  }

  /**
   * Detokenize the input tokens.
   *
   * @param tokens the tokens to detokenize.
   * @return the merge operations to detokenize the input tokens.
   */
  DetokenizationOperation[] detokenize(String tokens[]);

  /**
   * Detokenize the input tokens into a String. Tokens which
   * are connected without a space inbetween can be separated by
   * a split marker.
   *
   * @param tokens the token which should be concatenated
   * @param splitMarker the split marker or null
   *
   * @return the concatenated tokens
   */
  String detokenize(String tokens[], String splitMarker);
}
