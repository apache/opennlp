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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import opennlp.tools.ml.model.Event;
import opennlp.tools.tokenize.lang.Factory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.normalizer.CodePointSet;

/**
 * Tests for the {@link TokSpanEventStream} class.
 */
public class TokSpanEventStreamTest {

  private static final String SPLIT_SAMPLE = "ab<SPLIT>cd";
  private static final String NON_ASCII_SPLIT_SAMPLE = "\u00E4b<SPLIT>cd";
  private static final List<String> SPLIT_SAMPLE_OUTCOMES =
      List.of(TokenizerME.NO_SPLIT, TokenizerME.SPLIT, TokenizerME.NO_SPLIT);
  private static final TokenizerCharacterPolicy X_ONLY = TokenizerCharacterPolicy.of(
      CodePointSet.of('x'), CodePointSet.of(), CodePointSet.of());

  /**
   * Tests the event stream for correctly generated outcomes.
   */
  @Test
  void testEventOutcomes() throws IOException {

    ObjectStream<String> sentenceStream =
        ObjectStreamUtils.createObjectStream("\"<SPLIT>out<SPLIT>.<SPLIT>\"");

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

  @Test
  void testPolicyConstructorSkipsCandidatesThePolicyAccepts() throws IOException {
    Assertions.assertEquals(List.of(), outcomes(SPLIT_SAMPLE, true, TokenizerCharacterPolicy.ascii()));
  }

  @Test
  void testPolicyConstructorKeepsCandidatesThePolicyRejects() throws IOException {
    Assertions.assertEquals(SPLIT_SAMPLE_OUTCOMES, outcomes(SPLIT_SAMPLE, true, X_ONLY));
  }

  @Test
  void testPolicyIsIgnoredWhenAlphanumericsAreNotSkipped() throws IOException {
    Assertions.assertEquals(SPLIT_SAMPLE_OUTCOMES,
        outcomes(SPLIT_SAMPLE, false, TokenizerCharacterPolicy.ascii()));
  }

  @ParameterizedTest
  @CsvSource({
      SPLIT_SAMPLE + ", 0",
      NON_ASCII_SPLIT_SAMPLE + ", 3"})
  void testContextGeneratorConstructorUsesTheDefaultPolicy(String sample, int expectedEvents)
      throws IOException {
    ObjectStream<TokenSample> samples = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(sample));

    try (ObjectStream<Event> eventStream = new TokSpanEventStream(samples, true,
        new DefaultTokenContextGenerator())) {
      Assertions.assertEquals(expectedEvents, drain(eventStream).size());
    }
  }

  @Test
  void testDefaultPolicyIsTheFactoryDefault() throws IOException {
    Assertions.assertEquals(outcomes(NON_ASCII_SPLIT_SAMPLE, true, Factory.DEFAULT_CHARACTER_POLICY),
        outcomes(NON_ASCII_SPLIT_SAMPLE, true, TokenizerCharacterPolicy.ascii()));
  }

  @Test
  void testNullPolicyIsRejected() {
    ObjectStream<TokenSample> samples = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(SPLIT_SAMPLE));

    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new TokSpanEventStream(samples, true, (TokenizerCharacterPolicy) null,
            new DefaultTokenContextGenerator()));
    Assertions.assertEquals("characterPolicy must not be null", e.getMessage());
  }

  /**
   * The deprecated constructor with the ASCII pattern of earlier releases produces the events of
   * the ASCII policy.
   */
  @Test
  @SuppressWarnings("removal")
  void testDeprecatedPatternConstructorMatchesTheAsciiPolicy() throws IOException {
    ObjectStream<TokenSample> patternSamples = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(SPLIT_SAMPLE, NON_ASCII_SPLIT_SAMPLE));
    ObjectStream<TokenSample> policySamples = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(SPLIT_SAMPLE, NON_ASCII_SPLIT_SAMPLE));
    try (ObjectStream<Event> byPattern = new TokSpanEventStream(patternSamples, true,
            java.util.regex.Pattern.compile("^[A-Za-z0-9]+$"), new DefaultTokenContextGenerator());
         ObjectStream<Event> byPolicy = new TokSpanEventStream(policySamples, true,
            TokenizerCharacterPolicy.ascii(), new DefaultTokenContextGenerator())) {
      int events = 0;
      Event expected;
      while ((expected = byPolicy.read()) != null) {
        events++;
        Event actual = byPattern.read();
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(expected.getOutcome(), actual.getOutcome());
        Assertions.assertArrayEquals(expected.getContext(), actual.getContext());
      }
      Assertions.assertNull(byPattern.read());
      Assertions.assertTrue(events > 0, "the non-ASCII sample must produce events");
    }
  }

  private static List<String> outcomes(String sample, boolean skipAlphaNumerics,
      TokenizerCharacterPolicy policy) throws IOException {
    ObjectStream<TokenSample> samples = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(sample));

    try (ObjectStream<Event> eventStream = new TokSpanEventStream(samples, skipAlphaNumerics,
        policy, new DefaultTokenContextGenerator())) {
      List<String> outcomes = new ArrayList<>();
      for (Event event : drain(eventStream)) {
        outcomes.add(event.getOutcome());
      }
      return outcomes;
    }
  }

  private static List<Event> drain(ObjectStream<Event> eventStream) throws IOException {
    List<Event> events = new ArrayList<>();
    Event event;
    while ((event = eventStream.read()) != null) {
      events.add(event);
    }
    return events;
  }
}
