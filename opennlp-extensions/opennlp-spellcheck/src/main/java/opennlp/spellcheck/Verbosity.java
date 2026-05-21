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

/**
 * Controls how many suggestions a {@link SpellChecker#lookup} call returns and
 * with how much effort they are gathered.
 *
 * <p>The semantics follow the SymSpell reference implementation.</p>
 */
public enum Verbosity {

  /**
   * Returns the single suggestion with the smallest edit distance; ties are
   * broken by the highest term frequency. Cheapest to compute.
   */
  TOP,

  /**
   * Returns all suggestions that share the smallest edit distance found,
   * ordered by descending frequency.
   */
  CLOSEST,

  /**
   * Returns every suggestion within the requested maximum edit distance,
   * ordered by ascending edit distance and then descending frequency.
   */
  ALL
}
