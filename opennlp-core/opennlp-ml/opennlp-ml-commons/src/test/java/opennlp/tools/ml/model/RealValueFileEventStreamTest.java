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
import java.io.StringReader;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.ml.AbstractEventStreamTest;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that the textual event (input) format in {@link RealValueFileEventStream} is:
 * <br/>
 * {@code outcome context1 context2 context3 ...}
 * <p>
 * and is consistent with {@code RealBasicEventStream}. Moreover, the test checks that processing
 * given input works as expected.
 *
 * @see ObjectStream
 */
public class RealValueFileEventStreamTest extends AbstractEventStreamTest {

  @Override
  protected RealValueFileEventStream createEventStream(String input) throws IOException {
    return new RealValueFileEventStream(new StringReader(input));
  }

  /**
   * See: {@link AbstractEventStreamTest#EVENTS} for the input data.
   */
  @Test
  void testReadWithValidInput() throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream(EVENTS)) {
      Assertions.assertEquals("other [wc=ic=1.0 w&c=he,ic=2.0 n1wc=lc=3.0 n1w&c=belongs,lc=4.0 n2wc=lc=5.0]",
          eventStream.read().toString());
      Assertions.assertEquals("other [wc=lc=1.0 w&c=belongs,lc=2.0 p1wc=ic=3.0 p1w&c=he,ic=4.0 n1wc=lc=5.0]",
          eventStream.read().toString());
      Assertions.assertEquals("other [wc=lc=1.0 w&c=to,lc=2.0 p1wc=lc=3.0 p1w&c=belongs,lc=4.0 p2wc=ic=5.0]",
          eventStream.read().toString());
      Assertions.assertEquals("org-start [wc=ic=1.0 w&c=apache,ic=2.0 p1wc=lc=3.0 p1w&c=to,lc=4.0]",
          eventStream.read().toString());
      Assertions.assertEquals("org-cont [wc=ic=1.0 w&c=software,ic=2.0 p1wc=ic=3.0 p1w&c=apache,ic=4.0]",
          eventStream.read().toString());
      Assertions.assertEquals("org-cont [wc=ic=1.0 w&c=foundation,ic=2.0 p1wc=ic=3.0 p1w&c=software,ic=4.0]",
          eventStream.read().toString());
      Assertions.assertEquals("other [wc=other=1.0 w&c=.,other=2.0 p1wc=ic=3.0]",
          eventStream.read().toString());
      Assertions.assertNull(eventStream.read());
    }
  }

  @Test
  void testReadWithInvalidNegativeValues() throws IOException {
    try (RealValueFileEventStream eventStream = createEventStream(EVENTS_INVALID_NEGATIVE)) {
      eventStream.read();
      fail("Negative values should not be tolerated as input!");
    } catch (RuntimeException rte) {
      //noinspection StatementWithEmptyBody
      if (rte.getMessage().startsWith("Negative values are not allowed")) {
        // expected behviour
      } else {
        fail(rte);
      }
    }
  }

  @Test
  void testReset() throws IOException {
    try (RealValueFileEventStream feStream = createEventStream(EVENTS)) {
      feStream.reset();
      Assertions.fail("UnsupportedOperationException should be thrown");
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  void testReadSplitsContextsOnWhitespaceRuns() throws IOException {
    String input = "other wc=ic=1.0\t\tw&c=he,ic=2.0   n1wc=lc=3.0 \t \n"
        + "other wc=lc=1.0 w&c=belongs,lc=2.0\u00A0p1wc=ic=3.0\n"
        + "other   wc=lc=1.0  w&c=to,lc=2.0  \n";
    try (ObjectStream<Event> eventStream = createEventStream(input)) {
      Event e = eventStream.read();
      Assertions.assertArrayEquals(
          new String[] {"wc=ic", "w&c=he,ic", "n1wc=lc"}, e.getContext());
      Assertions.assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, e.getValues());
      e = eventStream.read();
      Assertions.assertArrayEquals(
          new String[] {"wc=lc", "w&c=belongs,lc=2.0\u00A0p1wc=ic"}, e.getContext());
      Assertions.assertArrayEquals(new float[] {1.0f, 3.0f}, e.getValues());
      // repeated runs produce no empty predicate
      e = eventStream.read();
      Assertions.assertArrayEquals(
          new String[] {"wc=lc", "w&c=to,lc"}, e.getContext());
      Assertions.assertArrayEquals(new float[] {1.0f, 2.0f}, e.getValues());
      Assertions.assertNull(eventStream.read());
    }
  }

  private static Stream<Arguments> fieldSeparators() {
    return Stream.of(
        // the fixed event separators, including runs and leading separators
        Arguments.of("other\twc=ic=1.0", "other", new String[] {"wc=ic"}),
        Arguments.of("other  \t wc=ic=1.0", "other", new String[] {"wc=ic"}),
        Arguments.of("other\fwc=ic=1.0", "other", new String[] {"wc=ic"}),
        Arguments.of("  other wc=ic=1.0", "other", new String[] {"wc=ic"}),
        Arguments.of("\tother wc=ic=1.0", "other", new String[] {"wc=ic"}),
        Arguments.of("other wc=ic=1.0\r", "other", new String[] {"wc=ic"}),
        // non-delimiter characters remain part of the outcome
        Arguments.of("other\u00A0wc=ic=1.0", "other\u00A0wc=ic=1.0", new String[0]),
        Arguments.of("other\u0085wc=ic=1.0", "other\u0085wc=ic=1.0", new String[0]),
        Arguments.of("other\u2028wc=ic=1.0", "other\u2028wc=ic=1.0", new String[0]),
        Arguments.of("other\u3000wc=ic=1.0", "other\u3000wc=ic=1.0", new String[0]),
        // file separator and zero width space do not, so they stay inside a field
        Arguments.of("other\u001Cwc=ic=1.0", "other\u001Cwc=ic=1.0", new String[0]),
        Arguments.of("other\u200Bwc=ic=1.0", "other\u200Bwc=ic=1.0", new String[0]),
        Arguments.of("other wc\u001C=ic=1.0", "other", new String[] {"wc\u001C=ic"}),
        // a supplementary character is one character of a field
        Arguments.of("\uD83D\uDE00 w=\uD83D\uDE00=1.0", "\uD83D\uDE00", new String[] {"w=\uD83D\uDE00"}));
  }

  /**
   * The fields use fixed event delimiters, independent of the whitespace mode.
   */
  @ParameterizedTest
  @MethodSource("fieldSeparators")
  void testFieldsUseEventDelimiters(String line, String outcome, String[] contexts)
      throws IOException {
    Event e = RealValueFileEventStream.parseEvent(line);
    Assertions.assertEquals(outcome, e.getOutcome());
    Assertions.assertArrayEquals(contexts, e.getContext());
  }

  @Test
  void testParseEventDoesNotDependOnTheSharedWhitespaceTokenizer() throws IOException {
    WhitespaceTokenizer.INSTANCE.setKeepNewLines(true);
    try {
      Event e = RealValueFileEventStream.parseEvent("other\nwc=ic=1.0\r\nn1wc=lc=2.0");
      Assertions.assertEquals("other", e.getOutcome());
      Assertions.assertArrayEquals(new String[] {"wc=ic", "n1wc=lc"}, e.getContext());
    } finally {
      WhitespaceTokenizer.INSTANCE.setKeepNewLines(false);
    }
  }

  @Test
  void testParseEventRejectsNull() {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> RealValueFileEventStream.parseEvent(null));
    Assertions.assertEquals("line must not be null", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"other", "other ", " other\t"})
  void testOutcomeOnlyLineHasNoContexts(String line) throws IOException {
    Event e = RealValueFileEventStream.parseEvent(line);
    Assertions.assertEquals("other", e.getOutcome());
    Assertions.assertEquals(0, e.getContext().length);
    Assertions.assertNull(e.getValues());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "\f", " \t\r\n\f", "\r"})
  void testLineWithoutOutcomeIsRejected(String line) {
    InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> RealValueFileEventStream.parseEvent(line));
    Assertions.assertEquals("An event line must start with an outcome: \"" + line + "\"", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"org/start", "a=b", "x;y", "B-ORG", "\u00E9tat", "\uD83D\uDE00"})
  void testOutcomeMayContainAnyNonWhitespace(String outcome) throws IOException {
    Event e = RealValueFileEventStream.parseEvent(outcome + " wc=ic=1.0");
    Assertions.assertEquals(outcome, e.getOutcome());
    Assertions.assertArrayEquals(new String[] {"wc=ic"}, e.getContext());
  }

  private static Stream<Arguments> contextValues() {
    return Stream.of(
        Arguments.of("w&c=he,ic=2.0", "w&c=he,ic", 2.0f),
        Arguments.of("a=b=c=3.5", "a=b=c", 3.5f),
        Arguments.of("wc=ic=1e2", "wc=ic", 100f),
        Arguments.of("wc=ic=1E-2", "wc=ic", 0.01f),
        Arguments.of("wc=ic=.5", "wc=ic", 0.5f),
        Arguments.of("wc=ic=+2", "wc=ic", 2f),
        Arguments.of("wc=ic=0", "wc=ic", 0f));
  }

  /** The value follows the last {@code =}, so a context name may contain the character. */
  @ParameterizedTest
  @MethodSource("contextValues")
  void testContextValueFollowsTheLastEqualsSign(String context, String name, float value)
      throws IOException {
    Event e = RealValueFileEventStream.parseEvent("other " + context);
    Assertions.assertArrayEquals(new String[] {name}, e.getContext());
    Assertions.assertArrayEquals(new float[] {value}, e.getValues());
  }

  @ParameterizedTest
  // no equals sign, an empty value after it, an equals sign in first position, text or a comma after it
  @ValueSource(strings = {"wc", "wc=", "=5", "wc=abc", "wc=1,5", "wc=ic=1.0.0"})
  void testContextWithoutANumberIsKeptWholeAndCountsOnce(String context) throws IOException {
    Event e = RealValueFileEventStream.parseEvent("other " + context);
    Assertions.assertArrayEquals(new String[] {context}, e.getContext());
    Assertions.assertNull(e.getValues());
  }

  @Test
  void testContextsWithAndWithoutValuesMix() throws IOException {
    Event e = RealValueFileEventStream.parseEvent("other wc=ic=2.0 wc n1wc=lc=0.5");
    Assertions.assertArrayEquals(new String[] {"wc=ic", "wc", "n1wc=lc"}, e.getContext());
    Assertions.assertArrayEquals(new float[] {2.0f, 1.0f, 0.5f}, e.getValues());
  }

  @ParameterizedTest
  @ValueSource(strings = {"wc=ic=-1", "wc=ic=-0.5", "wc=ic=-1e2", "wc=ic=-1E-2"})
  void testNegativeValueIsRejectedWithTheContextNamed(String context) {
    RuntimeException e = Assertions.assertThrows(RuntimeException.class,
        () -> RealValueFileEventStream.parseEvent("other " + context));
    Assertions.assertEquals("Negative values are not allowed: " + context, e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", "\r"})
  void testReadAcceptsEveryLineTerminator(String terminator) throws IOException {
    String input = "other wc=ic=1.0" + terminator + "other wc=lc=2.0" + terminator;
    try (ObjectStream<Event> eventStream = createEventStream(input)) {
      Assertions.assertArrayEquals(new String[] {"wc=ic"}, eventStream.read().getContext());
      Assertions.assertArrayEquals(new String[] {"wc=lc"}, eventStream.read().getContext());
      Assertions.assertNull(eventStream.read());
    }
  }

  @Test
  void testReadRejectsBlankLine() throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream("other wc=ic=1.0\n\nother wc=lc=1.0\n")) {
      Assertions.assertEquals("other", eventStream.read().getOutcome());
      Assertions.assertThrows(InvalidFormatException.class, eventStream::read);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"\u00A0", "\u0085", "\u2028", "\u2029", "\u3000", "\u2007",
      "\u202F", "\u200B", "\u000B", "\u001C", "\uD83D\uDE00", "e\u0301"})
  void testFeatureIdentityMatchesUnvaluedFormat(String text) throws IOException {
    String feature = "word=New" + text + "York";
    String line = FileEventStream.toLine(new Event("label" + text, new String[] {feature, "中文"}));
    try (FileEventStream plain = new FileEventStream(new StringReader(line));
         RealValueFileEventStream valued = createEventStream(line)) {
      Event expected = plain.read();
      Event actual = valued.read();
      Assertions.assertEquals(expected.getOutcome(), actual.getOutcome());
      Assertions.assertArrayEquals(new String[] {feature, "中文"}, actual.getContext());
      Assertions.assertNull(actual.getValues());
    }
    Event weighted = RealValueFileEventStream.parseEvent("label " + feature + "=2.5 中文=3");
    Assertions.assertArrayEquals(new String[] {feature, "中文"}, weighted.getContext());
    Assertions.assertArrayEquals(new float[] {2.5f, 3f}, weighted.getValues());
  }

  @Test
  void testOutcomeOnlyEventDoesNotEndTheStream() throws IOException {
    try (RealValueFileEventStream stream = createEventStream("first\nsecond word=中文\n")) {
      Assertions.assertEquals("first", stream.read().getOutcome());
      Event second = stream.read();
      Assertions.assertEquals("second", second.getOutcome());
      Assertions.assertArrayEquals(new String[] {"word=中文"}, second.getContext());
      Assertions.assertNull(stream.read());
    }
  }
}
