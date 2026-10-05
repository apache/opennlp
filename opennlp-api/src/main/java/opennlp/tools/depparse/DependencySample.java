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

import java.io.Serial;
import java.util.Arrays;
import java.util.Objects;

import opennlp.tools.commons.Sample;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.ArgumentChecks;

/**
 * One dependency-annotated sentence: tokens, their part-of-speech tags, and the gold
 * {@link DependencyTree} over them. Used for training and evaluating a
 * {@link DependencyParser}.
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @since 3.0.0
 */
@ThreadSafe
public final class DependencySample implements Sample {

  @Serial
  private static final long serialVersionUID = 3889986411217462795L;

  private final String[] tokens;
  private final String[] tags;
  private final DependencyTree tree;

  /**
   * Initializes a {@link DependencySample}.
   *
   * @param tokens The input tokens. Must not be {@code null} or empty and must
   *               not contain {@code null} entries.
   * @param tags The part-of-speech tags aligned with {@code tokens}. Must not be
   *             {@code null}, must have the same length as {@code tokens}, and must not
   *             contain {@code null} entries.
   * @param tree The dependency tree over the tokens. Must not be {@code null} and its
   *              {@link DependencyTree#size()} must equal the number of tokens.
   * @throws IllegalArgumentException Thrown if any parameter is {@code null},
   *         {@code tokens} is empty, {@code tokens} or {@code tags} contains a
   *         {@code null} entry, or the lengths of tokens, tags and tree disagree.
   */
  public DependencySample(String[] tokens, String[] tags, DependencyTree tree) {
    ArgumentChecks.requireNonNullArg(tree, "tree");
    checkTokensAndTags(tokens, tags);
    if (tokens.length != tree.size()) {
      throw new IllegalArgumentException("tokens, tags and tree must agree in length: "
          + tokens.length + ", " + tags.length + ", " + tree.size());
    }
    this.tokens = tokens.clone();
    this.tags = tags.clone();
    this.tree = tree;
  }

  /**
   * Validates the token and tag arrays of this sample.
   *
   * @param tokens The token array. Must not be {@code null} or empty and must not
   *               contain {@code null} entries.
   * @param tags The part-of-speech tags aligned with {@code tokens}. Must not be
   *             {@code null}, must have the same length as {@code tokens}, and must not
   *             contain {@code null} entries.
   * @throws IllegalArgumentException Thrown if an array is {@code null}, {@code tokens}
   *         is empty, the lengths do not match, or an entry is {@code null}.
   */
  private void checkTokensAndTags(String[] tokens, String[] tags) {
    ArgumentChecks.requireNonNullArg(tokens, "tokens");
    ArgumentChecks.requireNonNullArg(tags, "tags");
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

  /**
   * @return The tokens of the sentence. Never {@code null}.
   */
  public String[] getTokens() {
    return tokens.clone();
  }

  /**
   * @return The part-of-speech tags aligned with the tokens. Never {@code null}.
   */
  public String[] getTags() {
    return tags.clone();
  }

  /**
   * @return The dependency tree over the tokens. Never {@code null}.
   */
  public DependencyTree getTree() {
    return tree;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DependencySample other)) {
      return false;
    }
    return Arrays.equals(tokens, other.tokens) && Arrays.equals(tags, other.tags)
        && tree.equals(other.tree);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(tokens), Arrays.hashCode(tags), tree);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < tokens.length; i++) {
      sb.append(i + 1).append('\t').append(tokens[i]).append('\t').append(tags[i])
          .append('\t').append(tree.headOf(i) + 1).append('\t').append(tree.relationOf(i))
          .append(System.lineSeparator());
    }
    return sb.toString();
  }
}
