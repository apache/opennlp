/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;

/**
 * An ordered WN-LMF lexical resource containing one or more independently queryable lexicons.
 * Lexicon boundaries are preserved so equal lemmas in different languages never share a lookup
 * index by accident.
 *
 * @param lexicons The lexicons in document order. Must not be {@code null} or empty, must not
 *                 contain {@code null}, and ids must be unique.
 */
@ThreadSafe
public record WnLmfResource(List<WnLmfLexicon> lexicons) {

  /**
   * Creates a WN-LMF resource.
   *
   * @throws IllegalArgumentException Thrown if {@code lexicons} violates its documented
   *                                  constraint.
   */
  public WnLmfResource {
    if (lexicons == null || lexicons.isEmpty()) {
      throw new IllegalArgumentException("Lexicons must not be null or empty");
    }
    final Set<String> ids = new HashSet<>();
    for (final WnLmfLexicon lexicon : lexicons) {
      if (lexicon == null) {
        throw new IllegalArgumentException("Lexicons must not contain null");
      }
      if (!ids.add(lexicon.id())) {
        throw new IllegalArgumentException("Duplicate lexicon id " + lexicon.id());
      }
    }
    lexicons = List.copyOf(lexicons);
  }

  /**
   * Finds a lexicon by its WN-LMF id.
   *
   * @param id The exact lexicon id. Must not be {@code null}.
   * @return The lexicon, or empty when no lexicon carries the id.
   * @throws IllegalArgumentException Thrown if {@code id} is {@code null}.
   */
  public Optional<WnLmfLexicon> lexicon(String id) {
    if (id == null) {
      throw new IllegalArgumentException("Id must not be null");
    }
    for (final WnLmfLexicon lexicon : lexicons) {
      if (lexicon.id().equals(id)) {
        return Optional.of(lexicon);
      }
    }
    return Optional.empty();
  }
}
