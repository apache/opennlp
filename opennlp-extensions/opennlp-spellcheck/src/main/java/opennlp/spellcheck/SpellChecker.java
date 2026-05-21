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

package opennlp.spellcheck;

import java.util.List;

/**
 * A spelling corrector that proposes {@link SuggestItem suggestions} for individual
 * terms and corrects whole sentences.
 *
 * <p>Implementations are expected to be thread-safe for concurrent {@code lookup} calls
 * once their dictionary has been fully populated <i>and safely published</i> to the reading
 * threads (e.g. stored in a {@code final} field, or otherwise guarded so a happens-before
 * edge exists between population and the first read). Population itself is not required to
 * be thread-safe.</p>
 */
public interface SpellChecker {

  /**
   * Looks up suggestions for a single {@code term} within {@code maxEditDistance}.
   *
   * @param term           the (possibly misspelled) term to correct; must not be {@code null}
   * @param verbosity      controls how many suggestions are returned
   * @param maxEditDistance the maximum edit distance to consider; must not be negative and
   *                       must not exceed {@link #maxEditDistance()}
   * @return the matching suggestions in natural order (best first); never {@code null}
   */
  List<SuggestItem> lookup(String term, Verbosity verbosity, int maxEditDistance);

  /**
   * @return the largest edit distance this checker can answer queries for (the configured
   *     maximum dictionary edit distance); a {@code maxEditDistance} argument to
   *     {@link #lookup(String, Verbosity, int)} must not exceed this value.
   */
  int maxEditDistance();

  /**
   * Convenience overload that uses {@link Verbosity#TOP} and the implementation's
   * configured maximum dictionary edit distance.
   *
   * @param term the (possibly misspelled) term to correct; must not be {@code null}
   * @return the matching suggestions in natural order (best first); never {@code null}
   */
  List<SuggestItem> lookup(String term);

  /**
   * Corrects a whole input string (a phrase or sentence), supporting word splits and
   * merges, and combining candidates using a bigram language model.
   *
   * @param input           the input phrase to correct; must not be {@code null}
   * @param maxEditDistance the maximum edit distance per token; must not be negative
   * @return a singleton list holding the best correction of the whole input; never {@code null}
   */
  List<SuggestItem> lookupCompound(String input, int maxEditDistance);
}
