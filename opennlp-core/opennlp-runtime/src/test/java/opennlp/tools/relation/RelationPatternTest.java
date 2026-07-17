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

package opennlp.tools.relation;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.StringUtil;

/**
 * Verifies {@link RelationPattern} construction and path parsing, in particular the
 * whitespace handling of the step scan across ASCII and Unicode separator characters.
 */
public class RelationPatternTest {

  private static final char NBSP = (char) 0x00A0;
  private static final char NEL = (char) 0x0085;
  private static final char NNBSP = (char) 0x202F;
  private static final char FIGURE_SPACE = (char) 0x2007;
  private static final char IDEOGRAPHIC_SPACE = (char) 0x3000;
  private static final char FILE_SEPARATOR = (char) 0x001C;
  private static final char UNIT_SEPARATOR = (char) 0x001F;

  /** {@code Istanbul} with the Turkish capital I with dot above, U+0130, as its initial. */
  private static final String DOTTED_CAPITAL_ISTANBUL = "\u0130stanbul";

  /** {@code Istanbul} as the JDK lowercases U+0130: an i plus U+0307 combining dot above. */
  private static final String JDK_LOWERCASED_ISTANBUL = "i\u0307stanbul";

  /** The German {@code Strasse} spelled with U+00DF, the small sharp s. */
  private static final String SHARP_S_STRASSE = "stra\u00DFe";

  /**
   * Verifies splitting on ASCII blanks and tabs, including runs of separators at the
   * start and end of the path, which never produce empty steps.
   */
  @Test
  void testStepsSplitOnAsciiWhitespace() {
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj >obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "  <nsubj\t>obj  ", null).steps());
  }

  /**
   * Verifies splitting on the Unicode space separators: the no-break space, the narrow
   * no-break space, the figure space, and the ideographic space delimit steps exactly
   * like an ASCII blank, alone as well as combined in one path.
   */
  @Test
  void testStepsSplitOnUnicodeSpaceSeparators() {
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj" + NBSP + ">obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj" + NNBSP + ">obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj" + FIGURE_SPACE + ">obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">nmod", ">case"),
        new RelationPattern("t",
            NBSP + "<nsubj" + IDEOGRAPHIC_SPACE + ">nmod" + NNBSP + ">case" + NBSP,
            null).steps());
  }

  /**
   * Verifies that splitting follows {@link StringUtil#isWhitespace(char)} exactly for
   * the characters whose classification differs between whitespace definitions: the
   * next line control U+0085 and the information separators U+001C and U+001F. The
   * expected step list is derived from the predicate itself, so this test asserts the
   * contract, splitting wherever the project predicate sees whitespace, rather than
   * pinning any one character table, and it stays correct when the predicate's
   * character set changes.
   */
  @Test
  void testSplittingFollowsTheProjectWhitespacePredicate() {
    for (final char divergent : new char[] {NEL, FILE_SEPARATOR, UNIT_SEPARATOR}) {
      final List<String> expected = StringUtil.isWhitespace(divergent)
          ? List.of("<nsubj", ">obj")
          : List.of("<nsubj" + divergent + ">obj");
      Assertions.assertEquals(expected,
          new RelationPattern("t", "<nsubj" + divergent + ">obj", null).steps(),
          "split behavior for U+" + String.format("%04X", (int) divergent)
              + " must follow StringUtil.isWhitespace");
    }
  }

  /**
   * Verifies that the step order rule is enforced regardless of which whitespace
   * character separates the steps.
   */
  @Test
  void testUpStepsMustPrecedeDownStepsAcrossAllSeparators() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", ">obj <nsubj", null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", ">obj" + NBSP + "<nsubj", null));
  }

  /**
   * Verifies that {@code null}, empty, and blank paths are all rejected at construction
   * with the exact same message.
   */
  @Test
  void testEmptyPathFailsLoudWithExactMessage() {
    for (final String path : new String[] {null, "", " ", "\t\n"}) {
      final IllegalArgumentException e = Assertions.assertThrows(
          IllegalArgumentException.class, () -> new RelationPattern("t", path, null));
      Assertions.assertEquals("path must not be null or blank", e.getMessage());
    }
  }

  /**
   * Verifies that a direction marker separated from its label forms an empty step and is
   * rejected with a message naming the offending step, wherever it occurs in the path.
   */
  @Test
  void testDirectionMarkerAloneIsRejectedAsEmptyStep() {
    final IllegalArgumentException first = Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RelationPattern("t", "< nsubj", null));
    Assertions.assertEquals("not a valid path step: <", first.getMessage());

    final IllegalArgumentException later = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj > obj", null));
    Assertions.assertEquals("not a valid path step: >", later.getMessage());
  }

  /**
   * Verifies the exact message of the step order check: a path with an up step after a
   * down step is rejected and the message quotes the whole path.
   */
  @Test
  void testUpAfterDownReportsTheWholePath() {
    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", ">obj <nsubj", null));
    Assertions.assertEquals("up steps must come before down steps: >obj <nsubj",
        e.getMessage());
  }

  /**
   * Verifies that leading and trailing no-break spaces (U+00A0, written as Unicode
   * escapes in the source) are separators like any other whitespace and never produce
   * empty steps.
   */
  @Test
  void testLeadingAndTrailingNoBreakSpacesAreIgnored() {
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "\u00A0<nsubj\u00A0>obj\u00A0", null).steps());
  }

  /**
   * Verifies that a trigger which is not already lowercased is rejected at construction
   * with a message naming the offending trigger. Such a trigger could never equal the
   * lowercased pivot form the annotator compares it against, so the pattern would match
   * nothing without any error; the check turns that silent dead rule into a failure at
   * the boundary where the user can fix it.
   */
  @Test
  void testNonLowercasedTriggerFailsLoudWithExactMessage() {
    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj >obj", "Founded"));
    Assertions.assertEquals("trigger must be lowercased, but was: Founded", e.getMessage());
    Assertions.assertEquals("founded",
        new RelationPattern("t", "<nsubj >obj", "founded").trigger());
  }

  /**
   * Verifies that the trigger check follows {@link StringUtil#toLowerCase(CharSequence)},
   * the same mapping {@link RelationAnnotator} applies to the pivot form: a trigger is
   * accepted exactly when that mapping leaves it unchanged. The expectation is derived
   * from the mapping itself rather than pinned per character, so this test asserts the
   * contract that construction accepts exactly the forms a pivot can lowercase to, and it
   * stays correct when the mapping's character set changes. The candidates include
   * {@code Istanbul} written with the Turkish capital I with dot above (U+0130), whose
   * JDK lowercasing expands beyond the per code point mapping, and the German sharp s,
   * which is already lowercase while its uppercase form expands.
   */
  @Test
  void testTriggerCheckFollowsTheProjectCaseMapping() {
    // the Deseret pair is supplementary-plane: capital long I, U+10400, lowercases to
    // U+10428, so acceptance must follow the mapping across surrogate pairs as well
    for (final String candidate : new String[] {"founded", "Founded",
        DOTTED_CAPITAL_ISTANBUL, "istanbul", JDK_LOWERCASED_ISTANBUL, "STRASSE",
        SHARP_S_STRASSE, "\uD801\uDC00", "\uD801\uDC28"}) {
      final String message = "trigger acceptance for " + candidate
          + " must follow StringUtil.toLowerCase";
      if (StringUtil.toLowerCase(candidate).equals(candidate)) {
        Assertions.assertEquals(candidate,
            new RelationPattern("t", "<nsubj", candidate).trigger(), message);
      } else {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new RelationPattern("t", "<nsubj", candidate), message);
      }
    }
  }

  /**
   * Verifies that blankness is judged by the same whitespace definition the step scan
   * separates on: a path holding only a no-break space would split into zero steps and
   * could never match, so it is rejected at construction instead of building a dead
   * rule, and the same holds for a type spelled from nothing but no-break spaces.
   */
  @Test
  void testNoBreakSpaceOnlyValuesAreRejectedAsBlank() {
    final IllegalArgumentException path = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", "\u00A0", null));
    Assertions.assertEquals("path must not be null or blank", path.getMessage());

    final IllegalArgumentException type = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("\u00A0", "<nsubj", null));
    Assertions.assertEquals("type must not be null or blank", type.getMessage());
  }

  /**
   * Verifies that a trigger containing whitespace is rejected at construction: the
   * trigger is compared against a single pivot token, which the tokenizer guarantees
   * carries no whitespace, so a multi-word or padded trigger is a rule that could
   * never match. The whitespace scan follows the project predicate, so a no-break
   * space inside or around the trigger is caught the same way as a plain space.
   */
  @Test
  void testTriggerContainingWhitespaceIsRejected() {
    final IllegalArgumentException spaced = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj", "new york"));
    Assertions.assertEquals("trigger must not contain whitespace, since it is matched"
        + " against a single token: new york", spaced.getMessage());

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj", "founded\u00A0"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj", "\u00A0"));
  }
}
