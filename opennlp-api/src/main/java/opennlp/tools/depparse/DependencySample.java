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

import java.util.Arrays;
import java.util.Objects;

/**
 * One dependency-annotated sentence: tokens, their part-of-speech tags, and the gold
 * {@link DependencyGraph} over them. Used for training and evaluating a
 * {@link DependencyParser}.
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public class DependencySample {

  private final String[] tokens;
  private final String[] tags;
  private final DependencyGraph graph;

  /**
   * Initializes a {@link DependencySample}.
   *
   * @param tokens The tokens of the sentence. Must not be {@code null} or empty.
   * @param tags The part-of-speech tags aligned with {@code tokens}. Must not be
   *             {@code null} and must have the same length as {@code tokens}.
   * @param graph The dependency graph over the tokens. Must not be {@code null} and its
   *              {@link DependencyGraph#size()} must equal the number of tokens.
   * @throws IllegalArgumentException Thrown if any parameter is {@code null} or the
   *         lengths disagree.
   */
  public DependencySample(String[] tokens, String[] tags, DependencyGraph graph) {
    if (tokens == null || tags == null || graph == null) {
      throw new IllegalArgumentException("tokens, tags and graph must not be null");
    }
    if (tokens.length == 0) {
      throw new IllegalArgumentException("a sample needs at least one token");
    }
    if (tokens.length != tags.length || tokens.length != graph.size()) {
      throw new IllegalArgumentException("tokens, tags and graph must agree in length: "
          + tokens.length + ", " + tags.length + ", " + graph.size());
    }
    this.tokens = tokens.clone();
    this.tags = tags.clone();
    this.graph = graph;
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
   * @return The dependency graph over the tokens. Never {@code null}.
   */
  public DependencyGraph getGraph() {
    return graph;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DependencySample other)) {
      return false;
    }
    return Arrays.equals(tokens, other.tokens) && Arrays.equals(tags, other.tags)
        && graph.equals(other.graph);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(tokens), Arrays.hashCode(tags), graph);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < tokens.length; i++) {
      sb.append(i + 1).append('\t').append(tokens[i]).append('\t').append(tags[i])
          .append('\t').append(graph.headOf(i) + 1).append('\t').append(graph.relationOf(i))
          .append(System.lineSeparator());
    }
    return sb.toString();
  }
}
