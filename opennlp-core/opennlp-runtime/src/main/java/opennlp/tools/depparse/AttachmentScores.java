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

import opennlp.tools.util.eval.Mean;

/**
 * Accumulates the unlabeled and labeled attachment scores over all tokens and over the
 * tokens that are not punctuation. {@link DependencyEvaluator} adds single tokens and
 * {@link DependencyCrossValidator} merges the totals of each fold.
 *
 * <p>Instances are not thread safe.</p>
 *
 * @since 3.0.0
 */
final class AttachmentScores {

  private final Mean uas = new Mean();
  private final Mean las = new Mean();
  private final Mean uasExcludingPunctuation = new Mean();
  private final Mean lasExcludingPunctuation = new Mean();

  /**
   * Adds the outcome of one token.
   *
   * @param headMatches Whether the predicted head equals the gold head.
   * @param labelMatches Whether the predicted head and relation both equal the gold ones.
   * @param punctuation Whether the token is punctuation and therefore left out of the
   *                    punctuation-free scores.
   */
  void add(boolean headMatches, boolean labelMatches, boolean punctuation) {
    final int head = headMatches ? 1 : 0;
    final int label = labelMatches ? 1 : 0;
    uas.add(head);
    las.add(label);
    if (!punctuation) {
      uasExcludingPunctuation.add(head);
      lasExcludingPunctuation.add(label);
    }
  }

  /**
   * Adds every token counted by another accumulator, weighted by its token counts.
   *
   * @param other The accumulator to merge. Must not be {@code null}.
   */
  void add(AttachmentScores other) {
    uas.add(other.uas.mean(), other.uas.count());
    las.add(other.las.mean(), other.las.count());
    uasExcludingPunctuation.add(other.uasExcludingPunctuation.mean(),
        other.uasExcludingPunctuation.count());
    lasExcludingPunctuation.add(other.lasExcludingPunctuation.mean(),
        other.lasExcludingPunctuation.count());
  }

  /**
   * @return The unlabeled attachment score over all tokens, or {@code 0} if none.
   */
  double getUas() {
    return uas.mean();
  }

  /**
   * @return The labeled attachment score over all tokens, or {@code 0} if none.
   */
  double getLas() {
    return las.mean();
  }

  /**
   * @return The number of tokens added.
   */
  long getWordCount() {
    return uas.count();
  }

  /**
   * @return The unlabeled attachment score over the tokens that are not punctuation, or
   *         {@code 0} if none.
   */
  double getUasExcludingPunctuation() {
    return uasExcludingPunctuation.mean();
  }

  /**
   * @return The labeled attachment score over the tokens that are not punctuation, or
   *         {@code 0} if none.
   */
  double getLasExcludingPunctuation() {
    return lasExcludingPunctuation.mean();
  }

  /**
   * @return The number of tokens added that are not punctuation.
   */
  long getWordCountExcludingPunctuation() {
    return uasExcludingPunctuation.count();
  }
}
