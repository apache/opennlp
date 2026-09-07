/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

package opennlp.tools.depparse;

/**
 * Validates the token and tag arrays a parser receives. The checks match the ones a
 * {@link DependencySample} applies to its arrays; keeping a copy here avoids a call
 * into a package-private member of another module.
 */
final class ParserInput {

  /** Prevents construction of this utility class. */
  private ParserInput() {
  }

  /**
   * Checks that tokens and tags are present, non-empty, aligned, and free of nulls.
   *
   * @param tokens The tokens of one input.
   * @param tags The part-of-speech tag of each token.
   * @throws IllegalArgumentException Thrown if an array is {@code null} or empty, the
   *         lengths do not match, or an element is {@code null}.
   */
  static void check(String[] tokens, String[] tags) {
    if (tokens == null || tags == null) {
      throw new IllegalArgumentException("tokens and tags must not be null");
    }
    if (tokens.length == 0) {
      throw new IllegalArgumentException("tokens must not be empty");
    }
    if (tokens.length != tags.length) {
      throw new IllegalArgumentException("tokens and tags must have the same length: "
          + tokens.length + " != " + tags.length);
    }
    for (int i = 0; i < tokens.length; i++) {
      if (tokens[i] == null) {
        throw new IllegalArgumentException("token must not be null at index " + i);
      }
      if (tags[i] == null) {
        throw new IllegalArgumentException("tag must not be null at index " + i);
      }
    }
  }
}
