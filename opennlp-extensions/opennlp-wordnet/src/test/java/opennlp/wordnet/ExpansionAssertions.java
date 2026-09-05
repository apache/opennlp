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
package opennlp.wordnet;

import java.util.List;

import opennlp.wordnet.LexicalExpander.Expansion;

/**
 * Shared lookup helpers for tests that assert on {@link LexicalExpander} output.
 */
final class ExpansionAssertions {

  /** Not instantiable. */
  private ExpansionAssertions() {
  }

  /**
   * Finds the first expansion of a term in an expansion list.
   *
   * @param expansions The expansions to search. Must not be {@code null}.
   * @param term       The exact term to find. Must not be {@code null}.
   * @return The first expansion whose term equals {@code term}, or {@code null} when absent.
   */
  static Expansion find(List<Expansion> expansions, String term) {
    return expansions.stream().filter(e -> e.term().equals(term)).findFirst().orElse(null);
  }
}
