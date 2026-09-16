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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.StringUtil;

/** Tests relation path parsing and trigger validation. */
public class RelationPatternTest {

  private static final char NBSP = (char) 0x00A0;
  private static final char NEL = (char) 0x0085;
  private static final char NNBSP = (char) 0x202F;
  private static final char FIGURE_SPACE = (char) 0x2007;
  private static final char IDEOGRAPHIC_SPACE = (char) 0x3000;
  private static final char FILE_SEPARATOR = (char) 0x001C;
  private static final char UNIT_SEPARATOR = (char) 0x001F;

  private static final String DOTTED_CAPITAL_ISTANBUL = "\u0130stanbul";

  private static final String JDK_LOWERCASED_ISTANBUL = "i\u0307stanbul";

  private static final String SHARP_S_STRASSE = "stra\u00DFe";

  @Test
  void testStepsSplitOnAsciiWhitespace() {
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj >obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "  <nsubj\t>obj  ", null).steps());
  }

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

  @ParameterizedTest
  @ValueSource(chars = {NEL, FILE_SEPARATOR, UNIT_SEPARATOR})
  void testSplittingFollowsTheProjectWhitespacePredicate(char divergent) {
    final String path = "<nsubj" + divergent + ">obj";
    if (StringUtil.isWhitespace(divergent)) {
      Assertions.assertEquals(List.of("<nsubj", ">obj"),
          new RelationPattern("t", path, null).steps());
    } else {
      Assertions.assertThrows(IllegalArgumentException.class,
          () -> new RelationPattern("t", path, null));
    }
  }

  @Test
  void testUpStepsMustPrecedeDownStepsAcrossAllSeparators() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", ">obj <nsubj", null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", ">obj" + NBSP + "<nsubj", null));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t\n"})
  void testEmptyPathIsRejected(String path) {
    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RelationPattern("t", path, null));
    Assertions.assertEquals("path must not be null or blank", e.getMessage());
  }

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

  @Test
  void testDirectionMarkerInsideLabelIsRejected() {
    final IllegalArgumentException missingSeparator = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj>obj", null));
    Assertions.assertEquals("not a valid path step: <nsubj>obj",
        missingSeparator.getMessage());

    final IllegalArgumentException repeatedMarker = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", "<<nsubj", null));
    Assertions.assertEquals("not a valid path step: <<nsubj", repeatedMarker.getMessage());
  }

  @Test
  void testUpAfterDownReportsThePath() {
    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", ">obj <nsubj", null));
    Assertions.assertEquals("up steps must come before down steps: >obj <nsubj",
        e.getMessage());
  }

  @Test
  void testNonLowercasedTriggerIsRejected() {
    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj >obj", "Founded"));
    Assertions.assertEquals("trigger must be lowercased, but was: Founded", e.getMessage());
    Assertions.assertEquals("founded",
        new RelationPattern("t", "<nsubj >obj", "founded").trigger());
  }

  @ParameterizedTest
  @ValueSource(strings = {"founded", "Founded", DOTTED_CAPITAL_ISTANBUL, "istanbul",
      JDK_LOWERCASED_ISTANBUL, "STRASSE", SHARP_S_STRASSE, "\uD801\uDC00", "\uD801\uDC28"})
  void testTriggerCheckFollowsTheProjectCaseMapping(String candidate) {
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

  @Test
  void testNoBreakSpaceOnlyValuesAreRejectedAsBlank() {
    final IllegalArgumentException path = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", String.valueOf(NBSP), null));
    Assertions.assertEquals("path must not be null or blank", path.getMessage());

    final IllegalArgumentException type = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern(String.valueOf(NBSP), "<nsubj", null));
    Assertions.assertEquals("type must not be null or blank", type.getMessage());
  }

  @Test
  void testTriggerContainingWhitespaceIsRejected() {
    final IllegalArgumentException spaced = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj", "new york"));
    Assertions.assertEquals("trigger must not contain whitespace, since it is matched"
        + " against a single token: new york", spaced.getMessage());

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj", " founded"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj", "founded" + NBSP));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj", NBSP + "founded"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj", String.valueOf(NBSP)));
  }
}
