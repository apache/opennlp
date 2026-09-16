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

package opennlp.tools.stemmer.hunspell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Affix tables with entry counts and source locations for validation errors. */
final class HunspellAffixTable {

  /** Prevents construction. */
  private HunspellAffixTable() { }

  /**
   * An entry including the directive name.
   *
   * @param fields The tokenized entry.
   * @param line The one-based source line.
   */
  record Entry(String[] fields, int line) { }

  /**
   * Checks a table header, entry count, and field counts.
   *
   * @param lines The tokenized affix content.
   * @param tag The table directive.
   * @param minimumFields The minimum fields in an entry, including the directive.
   * @param maximumFields The maximum fields in an entry, including the directive.
   * @return The entries, or null if the file has no declaration for this table.
   * @throws IOException If the header, entries, or count is invalid.
   */
  static List<Entry> read(String[][] lines, String tag, int minimumFields, int maximumFields)
      throws IOException {
    final List<Entry> entries = new ArrayList<>();
    int count = -1;
    for (int i = 0; i < lines.length; i++) {
      final String[] fields = lines[i];
      if (fields.length == 0 || !tag.equals(fields[0])) {
        continue;
      }
      if (count < 0) {
        try {
          if (fields.length != 2) {
            throw new NumberFormatException();
          }
          count = Integer.parseInt(fields[1]);
          if (count < 0) {
            throw new NumberFormatException();
          }
        } catch (NumberFormatException e) {
          throw new IOException("invalid " + tag + " count at line " + (i + 1), e);
        }
      } else {
        if (fields.length < minimumFields || fields.length > maximumFields || entries.size() == count) {
          throw new IOException("invalid " + tag + " entry at line " + (i + 1));
        }
        entries.add(new Entry(fields, i + 1));
      }
    }
    if (count < 0) {
      return null;
    }
    if (entries.size() != count) {
      throw new IOException(tag + " header specifies " + count + " entries but found " + entries.size());
    }
    return entries;
  }
}
