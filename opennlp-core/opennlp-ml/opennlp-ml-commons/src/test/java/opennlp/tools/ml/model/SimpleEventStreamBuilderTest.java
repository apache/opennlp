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

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;

public class SimpleEventStreamBuilderTest {

  @Test
  void testAddSplitsContextsOnWhitespaceRuns() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=he\t\tn1w=belongs   n2w=to \t po=other")
        .build()) {
      Event e = events.read();
      Assertions.assertEquals("other", e.getOutcome());
      Assertions.assertArrayEquals(
          new String[] {"w=he", "n1w=belongs", "n2w=to", "po=other"}, e.getContext());
      Assertions.assertNull(e.getValues());
      Assertions.assertNull(events.read());
    }
  }

  @Test
  void testAddDropsLeadingAndTrailingWhitespace() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/  w=he n1w=belongs  ")
        .build()) {
      // leading, repeated, and trailing runs produce no empty predicate
      Assertions.assertArrayEquals(new String[] {"w=he", "n1w=belongs"}, events.read().getContext());
      Assertions.assertNull(events.read());
    }
  }

  @Test
  void testAddWithValuesSplitsOnWhitespaceRuns() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=he;0.5\tn1w=belongs;0.4  n2w=to;0.3")
        .build()) {
      Event e = events.read();
      Assertions.assertArrayEquals(new String[] {"w=he", "n1w=belongs", "n2w=to"}, e.getContext());
      Assertions.assertArrayEquals(new float[] {0.5f, 0.4f, 0.3f}, e.getValues());
    }
  }

  private static Stream<Arguments> contextSeparators() {
    return Stream.of(
        // Unicode whitespace outside the format's delimiters remains in the context
        Arguments.of("other/w=he\u00A0n1w=belongs n2w=to", new String[] {"w=he\u00A0n1w=belongs", "n2w=to"}),
        Arguments.of("other/w=he\u0085n1w=belongs", new String[] {"w=he\u0085n1w=belongs"}),
        Arguments.of("other/w=he\u2028n1w=belongs", new String[] {"w=he\u2028n1w=belongs"}),
        Arguments.of("other/w=he\u3000n1w=belongs", new String[] {"w=he\u3000n1w=belongs"}),
        // file separator and zero width space are not whitespace, they stay inside a context
        Arguments.of("other/w=he\u001Cn1w=belongs", new String[] {"w=he\u001Cn1w=belongs"}),
        Arguments.of("other/w=he\u200Bn1w=belongs", new String[] {"w=he\u200Bn1w=belongs"}),
        // a supplementary character is one character of a context
        Arguments.of("other/w=\uD83D\uDE00 n1w=x", new String[] {"w=\uD83D\uDE00", "n1w=x"}));
  }

  /**
   * The contexts use fixed event delimiters, independent of the whitespace mode.
   */
  @ParameterizedTest
  @MethodSource("contextSeparators")
  void testContextsUseEventDelimiters(String event, String[] contexts)
      throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder().add(event).build()) {
      Assertions.assertArrayEquals(contexts, events.read().getContext());
    }
  }

  @Test
  void testAddDoesNotDependOnTheSharedWhitespaceTokenizer() throws IOException {
    WhitespaceTokenizer.INSTANCE.setKeepNewLines(true);
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=he\nn1w=belongs\r\nn2w=to").build()) {
      Assertions.assertArrayEquals(
          new String[] {"w=he", "n1w=belongs", "n2w=to"}, events.read().getContext());
    } finally {
      WhitespaceTokenizer.INSTANCE.setKeepNewLines(false);
    }
  }

  @Test
  void testAddRejectsNull() {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SimpleEventStreamBuilder().add(null));
    Assertions.assertEquals("event must not be null", e.getMessage());
  }

  @Test
  void testAddKeepsASlashInsideAContext() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=1/2 n1w=a/b/c")
        .build()) {
      Event e = events.read();
      Assertions.assertEquals("other", e.getOutcome());
      Assertions.assertArrayEquals(new String[] {"w=1/2", "n1w=a/b/c"}, e.getContext());
    }
  }

  @Test
  void testOutcomeEndsAtTheFirstSlash() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder().add("a/b/w=x").build()) {
      Event e = events.read();
      Assertions.assertEquals("a", e.getOutcome());
      Assertions.assertArrayEquals(new String[] {"b/w=x"}, e.getContext());
    }
  }

  @Test
  void testValuedContextNameMayContainEqualsSigns() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=a=b;0.5 n=c;1e2 m=d;.25 k;0")
        .build()) {
      Event e = events.read();
      Assertions.assertArrayEquals(new String[] {"w=a=b", "n=c", "m=d", "k"}, e.getContext());
      Assertions.assertArrayEquals(new float[] {0.5f, 100f, 0.25f, 0f}, e.getValues());
    }
  }

  /** The first context tells whether the event has values; a later {@code ;} is then plain text. */
  @Test
  void testFirstContextWithoutValueMakesAllContextsPlain() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=he n1w=x;0.5")
        .build()) {
      Event e = events.read();
      Assertions.assertArrayEquals(new String[] {"w=he", "n1w=x;0.5"}, e.getContext());
      Assertions.assertNull(e.getValues());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"w=he;-0.5", "w=he;-1", "w=he;-1e2"})
  void testAddRejectsANegativeValueWithTheContextNamed(String context) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SimpleEventStreamBuilder().add("other/n=x;1 " + context));
    Assertions.assertEquals("Negative values are not allowed: " + context, e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"other/w=he;abc", "other/w=he;1,5", "other/w=he;0.5 n=x;-", "other/w=he;0x1"})
  void testAddRejectsAValueThatIsNotANumber(String event) {
    Assertions.assertThrows(NumberFormatException.class,
        () -> new SimpleEventStreamBuilder().add(event));
  }

  @ParameterizedTest
  // no slash, empty outcome, no contexts, blank contexts
  @ValueSource(strings = {"other w=he", "/w=he", "other/", "other/ \t "})
  void testAddRejectsMissingOutcomeOrContexts(String event) {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SimpleEventStreamBuilder().add(event));
  }

  @Test
  void testAddRejectsAContextWithoutValueWhenTheFirstHasOne() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SimpleEventStreamBuilder().add("other/w=he;0.5 n1w=belongs"));
  }

  @ParameterizedTest
  // no name before the separator, no value after it, a second separator
  @ValueSource(strings = {"other/;0.5", "other/w=he;0.5 ;0.4", "other/w=he;", "other/w=he;0.5 n1w=x;",
      "other/w=he;0.5;1", "other/w=he;;0.5"})
  void testAddRejectsAValuedContextThatIsNotNameAndValue(String event) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SimpleEventStreamBuilder().add(event));
    Assertions.assertTrue(e.getMessage().startsWith("format error of the event"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\u00A0", "\u0085", "\u2028", "\u2029", "\u3000", "\u2007",
      "\u202F", "\u200B", "\u000B", "\u001C", "\uD83D\uDE00", "e\u0301"})
  void testValuedFeaturesPreserveUnicode(String text) throws IOException {
    String feature = "word=New" + text + "York";
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("label/" + feature + ";2.5 中文;3").build()) {
      Event event = events.read();
      Assertions.assertArrayEquals(new String[] {feature, "中文"}, event.getContext());
      Assertions.assertArrayEquals(new float[] {2.5f, 3f}, event.getValues());
      Assertions.assertNull(events.read());
    }
  }
}
