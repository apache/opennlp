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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import opennlp.tools.util.StringUtil;

/**
 * An immutable dependency tree over one sentence: for every token, the index of its head
 * and the label of the relation to that head.
 *
 * <p>Token indices are zero-based positions in the sentence the graph was built for.
 * Exactly one token carries the head value {@link DependencyArc#ROOT_HEAD}, marking it as
 * the sentence root. Instances are immutable and safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public final class DependencyGraph {

  private final int[] heads;
  private final String[] relations;

  private DependencyGraph(int[] heads, String[] relations) {
    this.heads = heads;
    this.relations = relations;
  }

  /**
   * Creates a {@link DependencyGraph} from parallel head and relation arrays.
   *
   * @param heads For each token, the zero-based index of its head token, or
   *              {@link DependencyArc#ROOT_HEAD} for the sentence root. Must not be
   *              {@code null} or empty, every value must be a valid token index or
   *              {@link DependencyArc#ROOT_HEAD}, no token may head itself, and exactly
   *              one token must be the root.
   * @param relations For each token, the label of the relation to its head. Must not be
   *                  {@code null}, must have the same length as {@code heads}, and no
   *                  entry may be {@code null} or blank.
   * @return A validated {@link DependencyGraph}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if any of the above constraints is violated.
   */
  public static DependencyGraph of(int[] heads, String[] relations) {
    if (heads == null || relations == null) {
      throw new IllegalArgumentException("heads and relations must not be null");
    }
    if (heads.length == 0) {
      throw new IllegalArgumentException("a dependency graph needs at least one token");
    }
    if (heads.length != relations.length) {
      throw new IllegalArgumentException("heads and relations must have the same length: "
          + heads.length + " != " + relations.length);
    }
    int roots = 0;
    for (int i = 0; i < heads.length; i++) {
      if (heads[i] == DependencyArc.ROOT_HEAD) {
        roots++;
      } else if (heads[i] < 0 || heads[i] >= heads.length) {
        throw new IllegalArgumentException("head of token " + i
            + " is out of range: " + heads[i]);
      } else if (heads[i] == i) {
        throw new IllegalArgumentException("token " + i + " must not head itself");
      }
      if (relations[i] == null || StringUtil.isBlank(relations[i])) {
        throw new IllegalArgumentException("relation of token " + i + " must not be blank");
      }
    }
    if (roots != 1) {
      throw new IllegalArgumentException("expected exactly one root, found " + roots);
    }
    return new DependencyGraph(heads.clone(), relations.clone());
  }


  /**
   * @return The number of tokens the graph spans.
   */
  public int size() {
    return heads.length;
  }

  /**
   * Retrieves the head of a token.
   *
   * @param index The zero-based token index. Must be within {@code [0, size())}.
   * @return The zero-based index of the head token, or {@link DependencyArc#ROOT_HEAD}
   *         when the token is the sentence root.
   * @throws IllegalArgumentException Thrown if {@code index} is out of range.
   */
  public int headOf(int index) {
    checkIndex(index);
    return heads[index];
  }

  /**
   * Retrieves the relation label of a token.
   *
   * @param index The zero-based token index. Must be within {@code [0, size())}.
   * @return The label of the relation between the token and its head. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code index} is out of range.
   */
  public String relationOf(int index) {
    checkIndex(index);
    return relations[index];
  }

  /**
   * @return The zero-based index of the sentence root token.
   */
  public int root() {
    for (int i = 0; i < heads.length; i++) {
      if (heads[i] == DependencyArc.ROOT_HEAD) {
        return i;
      }
    }
    throw new IllegalStateException("graph invariant violated: no root present");
  }

  /**
   * @return All arcs of the graph in token order, one per token. Never {@code null}.
   */
  public List<DependencyArc> arcs() {
    final List<DependencyArc> arcs = new ArrayList<>(heads.length);
    for (int i = 0; i < heads.length; i++) {
      arcs.add(new DependencyArc(heads[i], i, relations[i]));
    }
    return Collections.unmodifiableList(arcs);
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= heads.length) {
      throw new IllegalArgumentException("token index out of range: " + index
          + ", size: " + heads.length);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DependencyGraph other)) {
      return false;
    }
    return Arrays.equals(heads, other.heads) && Arrays.equals(relations, other.relations);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(heads) + Arrays.hashCode(relations);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < heads.length; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(i).append("<-").append(heads[i]).append(':').append(relations[i]);
    }
    return sb.toString();
  }
}
