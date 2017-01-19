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

package opennlp.tools.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.model.Event;

/**
 * Tests for the {@link AbstractEventStream} class.
 */
public class AbstractEventStreamTest {

  /**
   * Checks if the {@link AbstractEventStream} behavior is correctly
   * if the {@link AbstractEventStream#createEvents(Object)} method
   * return iterators with events and empty iterators.
   */
  @Test
  public void testStandardCase() throws IOException {

    List<RESULT> samples = new ArrayList<>();
    samples.add(RESULT.EVENTS);
    samples.add(RESULT.EMPTY);
    samples.add(RESULT.EVENTS);

    TestEventStream eventStream = new TestEventStream(new CollectionObjectStream<>(samples));

    int eventCounter = 0;

    while (eventStream.read() != null) {
      eventCounter++;
    }

    Assert.assertEquals(2, eventCounter);
  }

  /**
   * Checks if the {@link AbstractEventStream} behavior is correctly
   * if the {@link AbstractEventStream#createEvents(Object)} method
   * only returns empty iterators.
   */
  @Test
  public void testEmtpyEventStream() throws IOException {
    List<RESULT> samples = new ArrayList<>();
    samples.add(RESULT.EMPTY);

    TestEventStream eventStream = new TestEventStream(new CollectionObjectStream<>(samples));
    Assert.assertNull(eventStream.read());

    // now check if it can handle multiple empty event iterators
    samples.add(RESULT.EMPTY);
    samples.add(RESULT.EMPTY);

    eventStream = new TestEventStream(new CollectionObjectStream<>(samples));
    Assert.assertNull(eventStream.read());
  }

  private enum RESULT {
    EVENTS,
    EMPTY
  }

  /**
   * This class extends the {@link AbstractEventStream} to help
   * testing the {@link AbstractEventStream#hasNext()}
   * and {@link AbstractEventStream#next()} methods.
   */
  class TestEventStream extends AbstractEventStream<RESULT> {


    public TestEventStream(ObjectStream<RESULT> samples) {
      super(samples);
    }

    /**
     * Creates {@link Iterator}s for testing.
     *
     * @param sample parameter to specify the output
     * @return it returns an {@link Iterator} which contains one
     * {@link Event} object if the sample parameter equals
     * {@link RESULT#EVENTS} or an empty {@link Iterator} if the sample
     * parameter equals {@link RESULT#EMPTY}.
     */
    @Override
    protected Iterator<Event> createEvents(RESULT sample) {

      if (RESULT.EVENTS.equals(sample)) {
        List<Event> events = new ArrayList<>();
        events.add(new Event("test", new String[] {"f1", "f2"}));

        return events.iterator();
      } else if (RESULT.EMPTY.equals(sample)) {
        List<Event> emptyList = Collections.emptyList();
        return emptyList.iterator();
      } else {
        // throws runtime exception, execution stops here
        Assert.fail();

        return null;
      }
    }

  }
}