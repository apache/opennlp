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
package opennlp.tools.stemmer.light;

import java.util.EnumSet;
import java.util.Set;

/**
 * The written standards of Norwegian whose endings the {@link NorwegianLightStemmer} and
 * {@link NorwegianMinimalStemmer} remove. A stemmer can handle one standard or both.
 *
 * @since 3.0.0
 */
public enum NorwegianVariety {

  /** Bokmaal, the majority written standard. */
  BOKMAAL,

  /** Nynorsk, the minority written standard. */
  NYNORSK;

  /**
   * Flattens the varargs standard selection of the Norwegian stemmer constructors into a set.
   *
   * @param first The first standard; must not be null.
   * @param more  Further standards; must not be or contain null.
   * @return The set of selected standards.
   * @throws IllegalArgumentException Thrown if {@code first} or {@code more} is null,
   *         or {@code more} contains null.
   */
  static Set<NorwegianVariety> toSet(NorwegianVariety first, NorwegianVariety[] more) {
    if (first == null) {
      throw new IllegalArgumentException("first must not be null");
    }
    if (more == null) {
      throw new IllegalArgumentException("more must not be null");
    }
    final EnumSet<NorwegianVariety> varieties = EnumSet.of(first);
    for (final NorwegianVariety variety : more) {
      if (variety == null) {
        throw new IllegalArgumentException("more must not contain null");
      }
      varieties.add(variety);
    }
    return varieties;
  }
}
