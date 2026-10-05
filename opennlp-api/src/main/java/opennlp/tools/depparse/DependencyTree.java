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

import java.io.InvalidObjectException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.ArgumentChecks;
import opennlp.tools.util.StringUtil;

/**
 * An immutable dependency tree over one sentence: for every token, the index of its head
 * and the label of the relation to that head.
 *
 * <p>Token indices are zero-based positions in the sentence the tree was built for.
 * Exactly one token carries the head value {@link DependencyArc#ROOT_HEAD}, marking it as
 * the sentence root. Instances are immutable and safe to share between threads.</p>
 *
 * @since 3.0.0
 */
@ThreadSafe
public final class DependencyTree implements Serializable {

  @Serial
  private static final long serialVersionUID = -3305194579961339787L;

  /** Traversal state of a token whose head chain has not been followed yet. */
  private static final byte UNVISITED = 0;

  /** Traversal state of a token on the head chain currently being followed. */
  private static final byte VISITING = 1;

  /** Traversal state of a token whose head chain is known to reach the root. */
  private static final byte VISITED = 2;

  private final int[] heads;
  private final String[] relations;
  private final int root;
  private final int hash;

  /**
   * Wraps already validated arrays; instances are created through {@link #of}.
   *
   * @param heads The validated head array, owned by the new instance.
   * @param relations The validated relation array, owned by the new instance.
   * @param root The index of the single token whose head is {@link DependencyArc#ROOT_HEAD}.
   */
  private DependencyTree(int[] heads, String[] relations, int root) {
    this.heads = heads;
    this.relations = relations;
    this.root = root;
    this.hash = 31 * Arrays.hashCode(heads) + Arrays.hashCode(relations);
  }

  /**
   * Creates a {@link DependencyTree} from parallel head and relation arrays.
   *
   * @param heads For each token, the zero-based index of its head token, or
   *              {@link DependencyArc#ROOT_HEAD} for the sentence root. Must not be
   *              {@code null} or empty, every value must be a valid token index or
   *              {@link DependencyArc#ROOT_HEAD}, no token may head itself, and exactly
   *              one token must be the root.
   * @param relations For each token, the label of the relation to its head. Must not be
   *                  {@code null}, must have the same length as {@code heads}, and no
   *                  entry may be {@code null} or blank.
   * @return A validated {@link DependencyTree}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if any of the above constraints is violated.
   */
  public static DependencyTree of(int[] heads, String[] relations) {
    ArgumentChecks.requireNonNullArg(heads, "heads");
    ArgumentChecks.requireNonNullArg(relations, "relations");
    if (heads.length == 0) {
      throw new IllegalArgumentException("a dependency tree needs at least one token");
    }
    if (heads.length != relations.length) {
      throw new IllegalArgumentException("heads and relations must have the same length: "
          + heads.length + " != " + relations.length);
    }
    int roots = 0;
    int root = DependencyArc.ROOT_HEAD;
    for (int i = 0; i < heads.length; i++) {
      if (heads[i] == DependencyArc.ROOT_HEAD) {
        roots++;
        root = i;
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
    checkAcyclic(heads);
    return new DependencyTree(heads.clone(), relations.clone(), root);
  }

  /**
   * Rejects a cycle that is disconnected from the single root by following every token's
   * head chain until it reaches the root or a token already known to reach it.
   *
   * @param heads The head array, already checked for range, self-heads, and root count.
   * @throws IllegalArgumentException Thrown if a head chain returns to a token on that
   *         same chain.
   */
  private static void checkAcyclic(int[] heads) {
    final byte[] states = new byte[heads.length];
    for (int start = 0; start < heads.length; start++) {
      int current = start;
      while (current != DependencyArc.ROOT_HEAD && states[current] == UNVISITED) {
        states[current] = VISITING;
        current = heads[current];
      }
      if (current != DependencyArc.ROOT_HEAD && states[current] == VISITING) {
        throw new IllegalArgumentException(
            "dependency tree contains a cycle at token " + current);
      }
      current = start;
      while (current != DependencyArc.ROOT_HEAD && states[current] == VISITING) {
        states[current] = VISITED;
        current = heads[current];
      }
    }
  }

  /**
   * @return The number of tokens the tree spans.
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
    return root;
  }

  /**
   * @return All arcs of the tree in token order, one per token, as a new unmodifiable
   *     list allocated on every call. Never {@code null}.
   */
  public List<DependencyArc> arcs() {
    final List<DependencyArc> arcs = new ArrayList<>(heads.length);
    for (int i = 0; i < heads.length; i++) {
      arcs.add(new DependencyArc(heads[i], i, relations[i]));
    }
    return Collections.unmodifiableList(arcs);
  }

  /**
   * Rejects a token index outside {@code [0, size())}.
   *
   * @param index The zero-based token index to check.
   * @throws IllegalArgumentException Thrown if {@code index} is out of range.
   */
  private void checkIndex(int index) {
    if (index < 0 || index >= heads.length) {
      throw new IllegalArgumentException("token index out of range: " + index
          + ", size: " + heads.length);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DependencyTree other)) {
      return false;
    }
    return hash == other.hash && Arrays.equals(heads, other.heads)
        && Arrays.equals(relations, other.relations);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return hash;
  }

  /**
   * Replaces a deserialized instance by a validated one, so the arrays and the cached hash
   * read from a stream pass the same checks as {@link #of(int[], String[])}.
   *
   * @return A validated tree with the same heads and relations.
   * @throws InvalidObjectException Thrown if the stream does not hold a valid tree.
   */
  @Serial
  private Object readResolve() throws InvalidObjectException {
    try {
      return of(heads, relations);
    } catch (IllegalArgumentException e) {
      final InvalidObjectException invalid = new InvalidObjectException(e.getMessage());
      invalid.initCause(e);
      throw invalid;
    }
  }

  /**
   * {@inheritDoc}
   */
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
