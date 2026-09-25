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
package opennlp.tools.ml.model;

import java.util.ArrayList;
import java.util.List;

/** Field delimiters for the textual event formats, shared with {@link FileEventStream}. */
final class EventFields {

  private EventFields() {
  }

  /**
   * Splits on runs of space, tab, carriage return, line feed, and form feed. Other
   * characters remain in the fields, and no empty fields are produced.
   *
   * @param text The event text, already checked for {@code null} by the caller.
   * @return The fields in order.
   */
  static String[] split(String text) {
    final List<String> fields = new ArrayList<>();
    int start = -1;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || isSeparator(text.charAt(i))) {
        if (start >= 0) {
          fields.add(text.substring(start, i));
          start = -1;
        }
      } else if (start < 0) {
        start = i;
      }
    }
    return fields.toArray(new String[0]);
  }

  /**
   * Tests the fixed delimiter set used by {@link FileEventStream}.
   *
   * @param c The character to test.
   * @return Whether the character separates event fields.
   */
  private static boolean isSeparator(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f';
  }
}
