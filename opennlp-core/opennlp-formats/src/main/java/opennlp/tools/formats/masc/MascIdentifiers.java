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

package opennlp.tools.formats.masc;

/**
 * Shared handling of the identifier attributes in MASC annotation files. Node, region,
 * and named entity identifiers carry a fixed text prefix followed by a number; the parsers
 * remove the prefix before parsing the number.
 */
final class MascIdentifiers {

  /** The prefix of a named entity node identifier, as in {@code ne-n7}. */
  static final String NAMED_ENTITY_ID_PREFIX = "ne-n";

  /** The prefix of a Penn token node identifier, as in {@code penn-n7}. */
  static final String PENN_TOKEN_ID_PREFIX = "penn-n";

  /** The prefix of a segmentation region identifier, as in {@code seg-r7}. */
  static final String REGION_ID_PREFIX = "seg-r";

  private MascIdentifiers() {
  }

  /**
   * Removes the first occurrence of {@code literal} from {@code input}. Later occurrences
   * stay in place, and {@code input} is returned unchanged if it does not contain
   * {@code literal}. The search is a plain text comparison.
   *
   * @param input The text to search. Must not be {@code null}.
   * @param literal The text to remove. Must not be {@code null}.
   * @return {@code input} without its first occurrence of {@code literal}.
   */
  static String removeFirst(String input, String literal) {
    final int start = input.indexOf(literal);
    if (start < 0) {
      return input;
    }
    return input.substring(0, start) + input.substring(start + literal.length());
  }
}
