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
import java.util.Map;
import java.util.function.Predicate;

import opennlp.tools.util.StringUtil;

/** Non-recursive longest-match input and output conversions. */
final class HunspellConversion {

  /** An empty conversion table. */
  static final HunspellConversion NONE = new HunspellConversion(List.of());

  /**
   * A conversion with optional word-boundary requirements.
   *
   * @param from The input text.
   * @param to The output text.
   * @param initial Whether the match requires the start of the input.
   * @param terminal Whether the match requires the end of the input.
   */
  private record Rule(String from, String to, boolean initial, boolean terminal) { }

  private final List<Rule> rules;

  /**
   * Constructs an immutable table.
   *
   * @param rules The conversion definitions.
   */
  private HunspellConversion(List<Rule> rules) {
    this.rules = List.copyOf(rules);
  }

  /**
   * Parses an affix conversion table and validates the entry count.
   *
   * @param lines The affix fields indexed by source line.
   * @param directive The table name.
   * @return The conversion table.
   * @throws IOException If the table is malformed.
   */
  static HunspellConversion parse(String[][] lines, String directive) throws IOException {
    final List<Rule> rules = new ArrayList<>();
    final boolean replacement = "REP".equals(directive);
    final List<HunspellAffixTable.Entry> entries = HunspellAffixTable.read(lines, directive,
        3, replacement ? Integer.MAX_VALUE : 3);
    if (entries == null) {
      return NONE;
    }
    for (HunspellAffixTable.Entry entry : entries) {
      final String[] fields = entry.fields();
      final boolean initial = fields[1].startsWith(replacement ? "^" : "_");
      final boolean terminal = fields[1].endsWith(replacement ? "$" : "_");
      final int start = initial ? 1 : 0;
      final int end = fields[1].length() - (terminal ? 1 : 0);
      if (end <= start) {
        throw new IOException("empty " + directive + " pattern at line " + entry.line());
      }
      rules.add(new Rule(fields[1].substring(start, end),
          "0".equals(fields[2]) ? "" : fields[2].replace('_', ' '), initial, terminal));
    }
    return rules.isEmpty() ? NONE : new HunspellConversion(rules);
  }

  /**
   * Adds dictionary transliterations to the replacement table.
   *
   * @param entries The dictionary entries and their flags.
   * @param morphology The fields of each selected entry.
   * @return A table containing REP and ph: replacements.
   */
  HunspellConversion withPhoneticFields(Map<String, List<int[]>> entries,
      Map<int[], List<String>> morphology) {
    final List<Rule> extended = new ArrayList<>(rules);
    for (Map.Entry<String, List<int[]>> word : entries.entrySet()) {
      for (int[] flags : word.getValue()) {
        for (String field : morphology.getOrDefault(flags, List.of())) {
          if (!field.startsWith("ph:")) {
            continue;
          }
          String from = field.substring(3);
          String to = word.getKey();
          final int arrow = from.indexOf("->");
          if (arrow >= 0) {
            to = from.substring(arrow + 2);
            from = from.substring(0, arrow);
          } else if (from.endsWith("*")) {
            from = from.substring(0, from.length() - 1);
            if (!from.isEmpty() && !to.isEmpty()) {
              from = from.substring(0, from.offsetByCodePoints(from.length(), -1));
              to = to.substring(0, to.offsetByCodePoints(to.length(), -1));
            }
          }
          if (!from.isEmpty()) {
            extended.add(new Rule(from, to, false, false));
            final int first = Character.charCount(from.codePointAt(0));
            final String upper = StringUtil.toUpperCase(from.substring(0, first)) + from.substring(first);
            if (!from.equals(upper)) {
              extended.add(new Rule(upper, to, false, false));
            }
          }
        }
      }
    }
    return new HunspellConversion(extended);
  }

  /**
   * Replaces the longest matching pattern at each input position without rescanning
   * replacement text.
   *
   * @param input The input text.
   * @return The converted text, or the input when no conversion applies.
   */
  String apply(String input) {
    if (rules.isEmpty() || input.isEmpty()) {
      return input;
    }
    final StringBuilder output = new StringBuilder(input.length());
    boolean changed = false;
    for (int offset = 0; offset < input.length();) {
      Rule selected = null;
      for (Rule rule : rules) {
        if ((!rule.initial() || offset == 0)
            && (!rule.terminal() || offset + rule.from().length() == input.length())
            && input.startsWith(rule.from(), offset)
            && (selected == null || rule.from().length() > selected.from().length())) {
          selected = rule;
        }
      }
      if (selected != null) {
        output.append(selected.to());
        offset += selected.from().length();
        changed = true;
      } else {
        final int point = input.codePointAt(offset);
        output.appendCodePoint(point);
        offset += Character.charCount(point);
      }
    }
    return changed ? output.toString() : input;
  }

  /**
   * Tests individual replacements without combining separate corrections.
   *
   * @param input The candidate compound.
   * @param accepted Tests whether a replacement is a recognized non-compound word.
   * @return Whether a replacement satisfies the test.
   */
  boolean anyReplacement(String input, Predicate<String> accepted) {
    for (int offset = 0; offset < input.length();) {
      for (Rule rule : rules) {
        if ((!rule.initial() || offset == 0)
            && (!rule.terminal() || offset + rule.from().length() == input.length())
            && input.startsWith(rule.from(), offset)) {
          final String replacement = input.substring(0, offset) + rule.to()
              + input.substring(offset + rule.from().length());
          if (!replacement.equals(input) && accepted.test(replacement)) {
            return true;
          }
        }
      }
      offset += Character.charCount(input.codePointAt(offset));
    }
    return false;
  }
}
