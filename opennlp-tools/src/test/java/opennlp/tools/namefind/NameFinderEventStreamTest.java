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

package opennlp.tools.namefind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import junit.framework.Assert;
import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;

import org.junit.Test;

/**
 * This is the test class for {@link NameFinderEventStream}.
 */
public class NameFinderEventStreamTest{

  /**
   * Tests the correctly generated outcomes for a test sentence.
   */
  @Test
  public void testOutcomesForSingleTypeSentence() throws IOException {
    String sentence[] = {"Elise",
        "Wendel",
        "appreciated",
        "the",
        "hint",
        "and",
        "enjoyed",
        "a",
        "delicious",
        "traditional",
        "meal",
        "."};

    NameSample nameSample = new NameSample(sentence,
        new Span[]{new Span(0, 2, "person")}, false);

    ObjectStream<Event> eventStream = new NameFinderEventStream(
        ObjectStreamUtils.createObjectStream(nameSample));

    assertEquals("person-" + NameFinderME.START, eventStream.read().getOutcome());
    assertEquals("person-" + NameFinderME.CONTINUE, eventStream.read().getOutcome());

    for (int i = 0; i < 10; i++) {
      Assert.assertEquals(NameFinderME.OTHER, eventStream.read().getOutcome());
    }

    assertNull(eventStream.read());
  }
}
