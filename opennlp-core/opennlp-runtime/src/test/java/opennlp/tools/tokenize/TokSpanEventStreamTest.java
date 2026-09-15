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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.ml.model.Event;
import opennlp.tools.tokenize.lang.Factory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

/**
 * Tests for the {@link TokSpanEventStream} class.
 */
public class TokSpanEventStreamTest {

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

  /**
   * Tests that a {@code null} pattern stays usable with skipping disabled or enabled.
   * A {@code null} pattern never treats a token as alphanumeric, so both settings
   * must yield the same events as skipping disabled with the default pattern.
   *
   * @param skipAlphaNumerics Whether alphanumerics are skipped, or not.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testNullPatternIsPreserved(boolean skipAlphaNumerics) throws IOException {
    List<String> expected = readOutcomes(false, new Factory().getAlphanumeric(null));
    List<String> actual = readOutcomes(skipAlphaNumerics, null);
    Assertions.assertFalse(actual.isEmpty());
    Assertions.assertEquals(expected, actual);
  }

  private static List<String> readOutcomes(boolean skipAlphaNumerics, Pattern alphaNumeric)
      throws IOException {
    ObjectStream<String> sentenceStream =
        ObjectStreamUtils.createObjectStream("\"<SPLIT>out<SPLIT>.<SPLIT>\"");
    ObjectStream<TokenSample> tokenSampleStream = new TokenSampleStream(sentenceStream);
    List<String> outcomes = new ArrayList<>();
    try (ObjectStream<Event> eventStream = new TokSpanEventStream(tokenSampleStream,
        skipAlphaNumerics, alphaNumeric, new DefaultTokenContextGenerator())) {
      Event event;
      while ((event = eventStream.read()) != null) {
        outcomes.add(event.getOutcome());
      }
    }
    return outcomes;
  }
}
