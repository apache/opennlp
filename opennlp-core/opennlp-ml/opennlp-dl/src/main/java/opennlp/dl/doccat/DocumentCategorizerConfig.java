/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl.doccat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import opennlp.dl.JsonScan;

public record DocumentCategorizerConfig(Map<String, String> id2label) {

  private static final String ID_TO_LABEL_KEY = "\"id2label\"";

  @Override
  public Map<String, String> id2label() {
    return Collections.unmodifiableMap(id2label);
  }

  /**
   * Reads the {@code id2label} object of a model configuration. The object is the text between
   * the opening brace after the first {@code "id2label"} key that has one and the first closing
   * brace after it, so a nested object is cut short there. Every string literal in it that is
   * followed by a colon and another string literal on the same line is an entry; the key is
   * taken up to the next quote, the value up to the next quote, neither honoring escapes.
   *
   * @param json The JSON text of the configuration.
   * @return The configuration, holding an empty map if no {@code id2label} object is present.
   */
  public static DocumentCategorizerConfig fromJson(String json) {
    Objects.requireNonNull(json, "json must not be null");

    final Map<String, String> id2label = new HashMap<>();
    final String id2labelContent = id2labelContent(json);
    if (id2labelContent != null) {
      putStringEntries(id2labelContent, id2label);
    }

    return new DocumentCategorizerConfig(id2label);
  }

  /**
   * Finds the text between the braces of the {@code id2label} object.
   *
   * @param json The JSON text of the configuration.
   * @return The text between the opening brace and the first closing brace after it, or
   *     {@code null} if no {@code "id2label"} key is followed by a colon, an opening brace,
   *     and a later closing brace.
   */
  private static String id2labelContent(String json) {
    int at = json.indexOf(ID_TO_LABEL_KEY);
    while (at >= 0) {
      final int brace = JsonScan.afterColon(json, at + ID_TO_LABEL_KEY.length());
      if (brace >= 0 && brace < json.length() && json.charAt(brace) == '{') {
        final int end = json.indexOf('}', brace + 1);
        if (end >= 0) {
          return json.substring(brace + 1, end);
        }
      }
      at = json.indexOf(ID_TO_LABEL_KEY, at + 1);
    }
    return null;
  }

  /**
   * Adds every string-to-string entry of the text to the map. A quote that does not open an
   * entry is skipped, and the scan resumes with the character after it.
   *
   * @param content The text between the braces of an object.
   * @param entries The map to add the entries to, a later key overwriting an earlier one.
   */
  private static void putStringEntries(String content, Map<String, String> entries) {
    int open = content.indexOf('"');
    while (open >= 0) {
      int next = open + 1;
      final int keyEnd = content.indexOf('"', open + 1);
      if (keyEnd > open + 1) {
        final int valueOpen = JsonScan.afterColon(content, keyEnd + 1);
        if (valueOpen >= 0 && valueOpen < content.length() && content.charAt(valueOpen) == '"') {
          final int valueEnd = JsonScan.closingQuoteOnLine(content, valueOpen);
          if (valueEnd >= 0) {
            entries.put(content.substring(open + 1, keyEnd),
                content.substring(valueOpen + 1, valueEnd));
            next = valueEnd + 1;
          }
        }
      }
      open = content.indexOf('"', next);
    }
  }
}
