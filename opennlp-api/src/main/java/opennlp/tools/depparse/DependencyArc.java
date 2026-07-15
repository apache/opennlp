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
 * One labeled edge of a {@link DependencyGraph}: the token at {@code dependent} is governed
 * by the token at {@code head} under the given {@code relation}.
 *
 * <p>Indices are zero-based positions in the token array the graph was built over. A
 * {@code head} of {@link #ROOT_HEAD} marks the dependent as the sentence root, which is
 * attached to the artificial root node rather than to another token.</p>
 *
 * @param head The zero-based index of the governing token, or {@link #ROOT_HEAD} when the
 *             dependent is the sentence root.
 * @param dependent The zero-based index of the governed token.
 * @param relation The dependency relation label, for example {@code nsubj}.
 *
 * @since 3.0.0
 */
public record DependencyArc(int head, int dependent, String relation) {

  /**
   * The {@code head} value marking an arc from the artificial root node.
   */
  public static final int ROOT_HEAD = -1;

  /**
   * Validates the arc invariants.
   *
   * @throws IllegalArgumentException Thrown if {@code dependent} is negative, {@code head}
   *         is less than {@link #ROOT_HEAD}, the arc is a self-loop, or {@code relation}
   *         is {@code null} or blank.
   */
  public DependencyArc {
    if (dependent < 0) {
      throw new IllegalArgumentException("dependent must not be negative: " + dependent);
    }
    if (head < ROOT_HEAD) {
      throw new IllegalArgumentException("head must be a token index or ROOT_HEAD: " + head);
    }
    if (head == dependent) {
      throw new IllegalArgumentException("arc must not be a self-loop: " + head);
    }
    if (relation == null || relation.isBlank()) {
      throw new IllegalArgumentException("relation must not be null or blank");
    }
  }
}
