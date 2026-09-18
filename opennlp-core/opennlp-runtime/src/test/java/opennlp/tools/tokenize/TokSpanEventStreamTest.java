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

package opennlp.tools.tokenize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.model.Event;
import opennlp.tools.tokenize.lang.Factory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

/**
 * Tests for the {@link TokSpanEventStream} class.
 */
public class TokSpanEventStreamTest {

  private static final String QUOTED_SAMPLE = "\"<SPLIT>out<SPLIT>.<SPLIT>\"";

  /** The quoted sample, a token the ASCII default rejects, and one it accepts. */
  private static final String MIXED_SAMPLE = QUOTED_SAMPLE + " caf\u00E9 now";

  /** A token with an unpaired surrogate, which only a class over the surrogate block accepts. */
  private static final String SURROGATE_TOKEN = "ab\uD800";

  private static final Pattern PLANE_ZERO = Pattern.compile("^[A-\uFFFF]+$");

  /**
   * Tests the event stream for correctly generated outcomes.
   */
  @Test
  void testEventOutcomes() throws IOException {

    ObjectStream<String> sentenceStream = ObjectStreamUtils.createObjectStream(QUOTED_SAMPLE);

    ObjectStream<TokenSample> tokenSampleStream = new TokenSampleStream(sentenceStream);

    try (ObjectStream<Event> eventStream = new TokSpanEventStream(tokenSampleStream, false)) {

      Assertions.assertEquals(TokenizerME.SPLIT, eventStream.read().getOutcome());
      Assertions.assertEquals(TokenizerME.NO_SPLIT, eventStream.read().getOutcome());
      Assertions.assertEquals(TokenizerME.NO_SPLIT, eventStream.read().getOutcome());
      Assertions.assertEquals(TokenizerME.SPLIT, eventStream.read().getOutcome());
      Assertions.assertEquals(TokenizerME.SPLIT, eventStream.read().getOutcome());

      Assertions.assertNull(eventStream.read());
      Assertions.assertNull(eventStream.read());
    }
  }

  /**
   * A {@code null} pattern stands for {@link Factory#DEFAULT_ALPHANUMERIC}: with skipping on,
   * "now" yields no events while "café" still does, and the events are the ones the
   * default pattern gives.
   */
  @Test
  void testNullPatternMeansAsciiDefault() throws IOException {
    List<String> withNull = readEvents(MIXED_SAMPLE, true, null);
    Assertions.assertEquals(readEvents(MIXED_SAMPLE, true, Factory.DEFAULT_ALPHANUMERIC), withNull);
    List<String> withAccentedClass = readEvents(MIXED_SAMPLE, true, Pattern.compile("^[a-z\u00E9]+$"));
    Assertions.assertEquals(withNull.size() - eventsFor("caf\u00E9"), withAccentedClass.size());
    Assertions.assertEquals(withAccentedClass, withNull.subList(0, withAccentedClass.size()));
  }

  @Test
  void testSkippingLeavesOutAlphanumericTokens() throws IOException {
    List<String> kept = readEvents(MIXED_SAMPLE, false, Factory.DEFAULT_ALPHANUMERIC);
    List<String> skipped = readEvents(MIXED_SAMPLE, true, Factory.DEFAULT_ALPHANUMERIC);
    Assertions.assertEquals(kept.size() - eventsFor("now"), skipped.size());
    Assertions.assertEquals(skipped, kept.subList(0, skipped.size()));
  }

  /** With skipping off the pattern is not consulted, so a {@code null} pattern is accepted. */
  @Test
  void testPatternIsIgnoredWhenNotSkipping() throws IOException {
    List<String> withNull = readEvents(MIXED_SAMPLE, false, null);
    Assertions.assertFalse(withNull.isEmpty());
    Assertions.assertEquals(readEvents(MIXED_SAMPLE, false, PLANE_ZERO), withNull);
  }

  /**
   * A token with an unpaired surrogate is not alphanumeric under any class, so it yields
   * events under a class that covers the surrogate block as under the ASCII default, although
   * the regular expression accepts it.
   */
  @Test
  void testUnpairedSurrogateIsNeverAlphanumeric() throws IOException {
    String sample = QUOTED_SAMPLE + " " + SURROGATE_TOKEN;
    Assertions.assertTrue(PLANE_ZERO.matcher(SURROGATE_TOKEN).matches());
    List<String> underDefault = readEvents(sample, true, Factory.DEFAULT_ALPHANUMERIC);
    List<String> underPlaneZero = readEvents(sample, true, PLANE_ZERO);
    Assertions.assertEquals(underDefault, underPlaneZero);
    List<String> withoutToken = readEvents(QUOTED_SAMPLE, true, PLANE_ZERO);
    Assertions.assertEquals(withoutToken.size() + eventsFor(SURROGATE_TOKEN), underPlaneZero.size());
  }

  /** A token longer than one character yields one event per split position. */
  private int eventsFor(String token) {
    return token.length() - 1;
  }

  private List<String> readEvents(String sample, boolean skipAlphaNumerics, Pattern alphaNumeric)
      throws IOException {
    ObjectStream<String> sentenceStream = ObjectStreamUtils.createObjectStream(sample);
    ObjectStream<TokenSample> tokenSampleStream = new TokenSampleStream(sentenceStream);
    List<String> events = new ArrayList<>();
    try (ObjectStream<Event> eventStream = new TokSpanEventStream(tokenSampleStream,
        skipAlphaNumerics, alphaNumeric, new DefaultTokenContextGenerator())) {
      Event event;
      while ((event = eventStream.read()) != null) {
        events.add(event.toString());
      }
    }
    return events;
  }
}
