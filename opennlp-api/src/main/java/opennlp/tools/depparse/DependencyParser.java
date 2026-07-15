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

package opennlp.tools.depparse;

/**
 * The interface for dependency parsers, which assign every token of a sentence a syntactic
 * head and a relation label, forming a single-rooted tree over the sentence.
 *
 * <p>Dependency parsing complements the constituency {@link opennlp.tools.parser.Parser}:
 * where a constituency parse groups tokens into nested phrases, a dependency parse links
 * each token directly to the token it modifies. The result is a {@link DependencyGraph}
 * whose indices refer back to the input token array, so spans computed for those tokens
 * remain valid for the parse.</p>
 *
 * @see DependencyGraph
 * @since 3.0.0
 */
public interface DependencyParser {

  /**
   * Parses a sentence into a {@link DependencyGraph}.
   *
   * @param tokens The tokens of one sentence. Must not be {@code null} and must contain
   *               at least one token.
   * @param tags The part-of-speech tags aligned with {@code tokens}. Must not be
   *             {@code null} and must have the same length as {@code tokens}.
   * @return A {@link DependencyGraph} over the given tokens. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code tokens} or {@code tags} is
   *         {@code null}, empty, or of mismatched length.
   */
  DependencyGraph parse(String[] tokens, String[] tags);
}
