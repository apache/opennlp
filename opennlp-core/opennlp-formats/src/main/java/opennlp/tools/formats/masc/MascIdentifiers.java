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

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.StringUtil;

/**
 * Shared handling of the identifier attributes in MASC annotation files. Node, region,
 * and named entity identifiers are a fixed text prefix followed by a number, as in
 * {@code penn-n7}; the parsers read the number and require the prefix.
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
   * Parses the number of an identifier that starts with {@code prefix} and continues with
   * one or more ASCII digits only.
   *
   * @param id The identifier, such as {@code penn-n7}.
   * @param prefix The expected prefix, such as {@link #PENN_TOKEN_ID_PREFIX}.
   * @return The number after the prefix.
   * @throws IllegalArgumentException If {@code id} is {@code null}, does not start with
   *         {@code prefix}, or is not followed by digits only.
   */
  static int parseId(String id, String prefix) {
    if (id == null || !id.startsWith(prefix) || id.length() == prefix.length()
        || StringUtil.endOfAsciiDigits(id, prefix.length()) != id.length()) {
      throw new IllegalArgumentException(
          "MASC identifier must be " + prefix + " followed by digits: " + id);
    }
    return Integer.parseInt(id, prefix.length(), id.length(), 10);
  }

  /**
   * Parses a whitespace separated list of identifiers, each as {@link #parseId(String, String)}
   * does.
   *
   * @param ids The identifiers, such as {@code seg-r1 seg-r2}.
   * @param prefix The expected prefix of each identifier.
   * @return The numbers in order.
   * @throws IllegalArgumentException If {@code ids} is {@code null}, names no identifier, or
   *         contains one that {@link #parseId(String, String)} rejects.
   */
  static int[] parseIds(String ids, String prefix) {
    if (ids == null) {
      throw new IllegalArgumentException("MASC identifier list must not be null");
    }
    String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(ids);
    if (tokens.length == 0) {
      throw new IllegalArgumentException("MASC identifier list must name at least one identifier");
    }
    int[] numbers = new int[tokens.length];
    for (int i = 0; i < tokens.length; i++) {
      numbers[i] = parseId(tokens[i], prefix);
    }
    return numbers;
  }
}
