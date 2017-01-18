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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;

/**
 * This is the test class for {@link NameFinderEventStream}.
 */
public class NameFinderEventStreamTest {

  private static final String[] SENTENCE = {"Elise", "Wendel", "appreciated",
      "the", "hint", "and", "enjoyed", "a", "delicious", "traditional", "meal",
      "."};

  private static final NameContextGenerator CG = new DefaultNameContextGenerator(
          (AdaptiveFeatureGenerator[]) null);

  /**
   * Tests the correctly generated outcomes for a test sentence.
   */
  @Test
  public void testOutcomesForSingleTypeSentence() throws IOException {

    NameSample nameSample = new NameSample(SENTENCE,
        new Span[]{new Span(0, 2, "person")}, false);

    ObjectStream<Event> eventStream = new NameFinderEventStream(
        ObjectStreamUtils.createObjectStream(nameSample));

    Assert.assertEquals("person-" + NameFinderME.START, eventStream.read().getOutcome());
    Assert.assertEquals("person-" + NameFinderME.CONTINUE, eventStream.read().getOutcome());

    for (int i = 0; i < 10; i++) {
      Assert.assertEquals(NameFinderME.OTHER, eventStream.read().getOutcome());
    }

    Assert.assertNull(eventStream.read());
  }


  /**
   * Tests the correctly generated outcomes for a test sentence. If the Span
   * declares its type, passing the type to event stream has no effect
   */
  @Test
  public void testOutcomesTypeCantOverride() throws IOException {
    String type = "XYZ";

    NameSample nameSample = new NameSample(SENTENCE,
            new Span[] { new Span(0, 2, "person") }, false);

    ObjectStream<Event> eventStream = new NameFinderEventStream(
            ObjectStreamUtils.createObjectStream(nameSample), type, CG, null);

    String prefix = type + "-";
    Assert.assertEquals(prefix + NameFinderME.START, eventStream.read().getOutcome());
    Assert.assertEquals(prefix + NameFinderME.CONTINUE,
            eventStream.read().getOutcome());

    for (int i = 0; i < 10; i++) {
      Assert.assertEquals(NameFinderME.OTHER, eventStream.read().getOutcome());
    }

    Assert.assertNull(eventStream.read());
    eventStream.close();
  }

  /**
   * Tests the correctly generated outcomes for a test sentence. If the Span
   * does not declare its type and the user passed a type, use the type from
   * user
   */
  @Test
  public void testOutcomesWithType() throws IOException {
    String type = "XYZ";

    NameSample nameSample = new NameSample(SENTENCE,
            new Span[] { new Span(0, 2) }, false);

    ObjectStream<Event> eventStream = new NameFinderEventStream(
            ObjectStreamUtils.createObjectStream(nameSample), type, CG, null);

    String prefix = type + "-";
    Assert.assertEquals(prefix + NameFinderME.START, eventStream.read().getOutcome());
    Assert.assertEquals(prefix + NameFinderME.CONTINUE,
            eventStream.read().getOutcome());

    for (int i = 0; i < 10; i++) {
      Assert.assertEquals(NameFinderME.OTHER, eventStream.read().getOutcome());
    }

    Assert.assertNull(eventStream.read());
    eventStream.close();
  }

  /**
   * Tests the correctly generated outcomes for a test sentence. If the Span
   * does not declare its type and the user did not set a type, it will use
   * "default".
   */
  @Test
  public void testOutcomesTypeEmpty() throws IOException {

    NameSample nameSample = new NameSample(SENTENCE,
            new Span[] { new Span(0, 2) }, false);

    ObjectStream<Event> eventStream = new NameFinderEventStream(
            ObjectStreamUtils.createObjectStream(nameSample), null, CG, null);

    String prefix = "default-";
    Assert.assertEquals(prefix + NameFinderME.START, eventStream.read().getOutcome());
    Assert.assertEquals(prefix + NameFinderME.CONTINUE,
            eventStream.read().getOutcome());

    for (int i = 0; i < 10; i++) {
      Assert.assertEquals(NameFinderME.OTHER, eventStream.read().getOutcome());
    }

    Assert.assertNull(eventStream.read());
    eventStream.close();
  }
}
