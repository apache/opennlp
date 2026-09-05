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
import java.util.List;

/**
 * The gold samples shared by the dependency parser tests.
 */
final class DependencyTestSamples {

  /** How often the distinct sentences are repeated in {@link #corpus()}. */
  private static final int REPETITIONS = 40;

  private DependencyTestSamples() {
    // This class only exposes static sample builders and is never instantiated.
  }

  /**
   * Builds one gold sample from its parallel arrays.
   *
   * @param tokens The sentence tokens. Must not be {@code null}.
   * @param tags The part-of-speech tags aligned with {@code tokens}.
   * @param heads The zero-based head per token, {@code -1} for the root.
   * @param relations The relation label per token.
   * @return The assembled sample. Never {@code null}.
   */
  static DependencySample sample(String[] tokens, String[] tags, int[] heads,
      String[] relations) {
    return new DependencySample(tokens, tags, DependencyGraph.of(heads, relations));
  }

  /**
   * Builds three repeated projective samples for deterministic parser tests.
   *
   * @return The training samples. Never {@code null}.
   */
  static List<DependencySample> corpus() {
    final List<DependencySample> distinct = List.of(
        sample(new String[] {"the", "dog", "barks"}, new String[] {"DT", "NN", "VBZ"},
            new int[] {1, 2, -1}, new String[] {"det", "nsubj", "root"}),
        sample(new String[] {"dogs", "bark"}, new String[] {"NNS", "VBP"},
            new int[] {1, -1}, new String[] {"nsubj", "root"}),
        sample(new String[] {"she", "eats", "fish"}, new String[] {"PRP", "VBZ", "NN"},
            new int[] {1, -1, 1}, new String[] {"nsubj", "root", "obj"}));
    final List<DependencySample> corpus = new ArrayList<>(REPETITIONS * distinct.size());
    for (int i = 0; i < REPETITIONS; i++) {
      corpus.addAll(distinct);
    }
    return corpus;
  }
}
