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

import java.util.function.Predicate;

import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.Mean;

/**
 * Measures the quality of a {@link DependencyParser} against gold
 * {@link DependencySample samples} with the two standard scores: the unlabeled attachment
 * score (UAS, the fraction of tokens with the correct head) and the labeled attachment
 * score (LAS, the fraction of tokens with the correct head and relation label).
 *
 * <p>Both scores are also kept over the tokens that are not punctuation, because
 * treebank evaluations customarily leave punctuation out of the attachment scores. A
 * token counts as punctuation when its gold part-of-speech tag satisfies the predicate
 * given at construction; by default that is the universal tag
 * {@link #UNIVERSAL_PUNCTUATION_TAG}.</p>
 *
 * @since 3.0.0
 */
public class DependencyEvaluator extends Evaluator<DependencySample> {

  /**
   * The universal part-of-speech tag of punctuation, which the default constructor
   * excludes from the punctuation-free scores.
   */
  public static final String UNIVERSAL_PUNCTUATION_TAG = "PUNCT";

  private final DependencyParser parser;
  private final Predicate<String> punctuationTag;
  private final Mean uas = new Mean();
  private final Mean las = new Mean();
  private final Mean uasExcludingPunctuation = new Mean();
  private final Mean lasExcludingPunctuation = new Mean();

  /**
   * Initializes a {@link DependencyEvaluator} that treats tokens tagged
   * {@link #UNIVERSAL_PUNCTUATION_TAG} as punctuation.
   *
   * @param parser The parser to evaluate. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code parser} is {@code null}.
   */
  public DependencyEvaluator(DependencyParser parser) {
    this(parser, UNIVERSAL_PUNCTUATION_TAG::equals);
  }

  /**
   * Initializes a {@link DependencyEvaluator} with a custom notion of punctuation.
   *
   * @param parser The parser to evaluate. Must not be {@code null}.
   * @param punctuationTag Decides from a gold part-of-speech tag whether the token is
   *                       punctuation and therefore left out of the punctuation-free
   *                       scores. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public DependencyEvaluator(DependencyParser parser, Predicate<String> punctuationTag) {
    if (parser == null) {
      throw new IllegalArgumentException("parser must not be null");
    }
    if (punctuationTag == null) {
      throw new IllegalArgumentException("punctuationTag must not be null");
    }
    this.parser = parser;
    this.punctuationTag = punctuationTag;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The returned sample carries the predicted graph over the reference tokens, and
   * every token of the reference contributes to both scores.</p>
   */
  @Override
  protected DependencySample processSample(DependencySample reference) {
    final DependencyGraph gold = reference.getGraph();
    final String[] tags = reference.getTags();
    final DependencyGraph predicted = parser.parse(reference.getTokens(), tags);
    for (int i = 0; i < gold.size(); i++) {
      final boolean headMatches = gold.headOf(i) == predicted.headOf(i);
      final boolean labelMatches =
          headMatches && gold.relationOf(i).equals(predicted.relationOf(i));
      uas.add(headMatches ? 1 : 0);
      las.add(labelMatches ? 1 : 0);
      if (!punctuationTag.test(tags[i])) {
        uasExcludingPunctuation.add(headMatches ? 1 : 0);
        lasExcludingPunctuation.add(labelMatches ? 1 : 0);
      }
    }
    return new DependencySample(reference.getTokens(), tags, predicted);
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

  /**
   * @return The unlabeled attachment score over the evaluated tokens that are not
   *         punctuation.
   */
  public double getUasExcludingPunctuation() {
    return uasExcludingPunctuation.mean();
  }

  /**
   * @return The labeled attachment score over the evaluated tokens that are not
   *         punctuation.
   */
  public double getLasExcludingPunctuation() {
    return lasExcludingPunctuation.mean();
  }

  /**
   * @return The number of tokens scored so far that are not punctuation.
   */
  public long getWordCountExcludingPunctuation() {
    return uasExcludingPunctuation.count();
  }
}
