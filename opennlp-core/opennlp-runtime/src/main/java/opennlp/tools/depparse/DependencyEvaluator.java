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

import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.Mean;

/**
 * Measures the quality of a {@link DependencyParser} against gold
 * {@link DependencySample samples} with the two standard scores: the unlabeled attachment
 * score (UAS, the fraction of tokens with the correct head) and the labeled attachment
 * score (LAS, the fraction of tokens with the correct head and relation label).
 *
 * @since 3.0.0
 */
public class DependencyEvaluator extends Evaluator<DependencySample> {

  private final DependencyParser parser;
  private final Mean uas = new Mean();
  private final Mean las = new Mean();

  /**
   * Initializes a {@link DependencyEvaluator}.
   *
   * @param parser The parser to evaluate. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code parser} is {@code null}.
   */
  public DependencyEvaluator(DependencyParser parser) {
    if (parser == null) {
      throw new IllegalArgumentException("parser must not be null");
    }
    this.parser = parser;
  }

  /**
   * Parses the sample's sentence and scores the prediction against the gold graph.
   *
   * @param reference The gold sample. Must not be {@code null}.
   * @return A {@link DependencySample} carrying the predicted graph. Never {@code null}.
   */
  @Override
  protected DependencySample processSample(DependencySample reference) {
    final DependencyGraph gold = reference.getGraph();
    final DependencyGraph predicted = parser.parse(reference.getTokens(), reference.getTags());
    for (int i = 0; i < gold.size(); i++) {
      final boolean headMatches = gold.headOf(i) == predicted.headOf(i);
      uas.add(headMatches ? 1 : 0);
      las.add(headMatches && gold.relationOf(i).equals(predicted.relationOf(i)) ? 1 : 0);
    }
    return new DependencySample(reference.getTokens(), reference.getTags(), predicted);
  }

  /**
   * @return The unlabeled attachment score over all evaluated tokens.
   */
  public double getUas() {
    return uas.mean();
  }

  /**
   * @return The labeled attachment score over all evaluated tokens.
   */
  public double getLas() {
    return las.mean();
  }

  /**
   * @return The number of tokens scored so far.
   */
  public long getWordCount() {
    return uas.count();
  }
}
