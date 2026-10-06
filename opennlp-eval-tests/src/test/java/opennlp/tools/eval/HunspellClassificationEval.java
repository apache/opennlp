/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

package opennlp.tools.eval;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.eval.HunspellCompatibilityEval.Outcome;
import opennlp.tools.eval.HunspellCompatibilityEval.Recorded;

/**
 * Checks the comparison classification of {@link HunspellCompatibilityEval} on synthetic
 * results, without the external dictionaries.
 */
class HunspellClassificationEval {

  private static final String WORD = "card";
  private static final Set<String> COMPLETE = Set.of(WORD);
  private static final Set<String> EMPTY = Set.of();
  private static final Set<String> IDENTITY = Set.of(HunspellCompatibilityEval.UNKNOWN);
  private static final Recorded ACCEPTED = new Recorded(true, COMPLETE);
  private static final Recorded ACCEPTED_EMPTY = new Recorded(true, EMPTY);
  private static final Recorded REJECTED = new Recorded(false, EMPTY);

  /**
   * Returns the synthetic comparisons and their expected classification.
   *
   * @return The word, the recorded outcome, the OpenNLP stems, the expected difference or
   *     {@code null}, and the expected classification.
   */
  private static Stream<Arguments> comparisons() {
    return Stream.of(
        Arguments.of(WORD, ACCEPTED, COMPLETE, null, Outcome.EXACT),
        Arguments.of(WORD, ACCEPTED_EMPTY, COMPLETE, COMPLETE, Outcome.EXPECTED_DIFFERENCE),
        Arguments.of(HunspellCompatibilityEval.UNKNOWN, REJECTED, IDENTITY, null, Outcome.IDENTITY_FALLBACK),
        Arguments.of(WORD, ACCEPTED, EMPTY, null, Outcome.UNEXPECTED),
        Arguments.of(WORD, ACCEPTED_EMPTY, COMPLETE, null, Outcome.UNEXPECTED),
        Arguments.of(WORD, REJECTED, Set.of("cards"), null, Outcome.UNEXPECTED),
        Arguments.of(WORD, ACCEPTED, COMPLETE, COMPLETE, Outcome.UNEXPECTED),
        Arguments.of(WORD, ACCEPTED_EMPTY, EMPTY, COMPLETE, Outcome.UNEXPECTED),
        Arguments.of(HunspellCompatibilityEval.UNKNOWN, ACCEPTED, IDENTITY, null, Outcome.UNEXPECTED));
  }

  /**
   * Checks the classification of one synthetic comparison.
   *
   * @param word The input.
   * @param reference The recorded Hunspell outcome.
   * @param stems The OpenNLP stems.
   * @param expected The recorded OpenNLP stems for a known difference, or {@code null}.
   * @param outcome The expected classification.
   */
  @ParameterizedTest
  @MethodSource("comparisons")
  void classifiesComparison(String word, Recorded reference, Set<String> stems,
                            Set<String> expected, Outcome outcome) {
    Assertions.assertEquals(outcome,
        HunspellCompatibilityEval.classify(word, reference, stems, expected));
  }
}
