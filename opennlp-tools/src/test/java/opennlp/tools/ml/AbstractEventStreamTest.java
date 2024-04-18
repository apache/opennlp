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

package opennlp.tools.ml;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.FileEventStream;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public abstract class AbstractEventStreamTest {

  protected static final String EVENTS_PLAIN =
          "other wc=ic w&c=he,ic n1wc=lc n1w&c=belongs,lc n2wc=lc\n" +
                  "other wc=lc w&c=belongs,lc p1wc=ic p1w&c=he,ic n1wc=lc\n" +
                  "other wc=lc w&c=to,lc p1wc=lc p1w&c=belongs,lc p2wc=ic\n" +
                  "org-start wc=ic w&c=apache,ic p1wc=lc p1w&c=to,lc\n" +
                  "org-cont wc=ic w&c=software,ic p1wc=ic p1w&c=apache,ic\n" +
                  "org-cont wc=ic w&c=foundation,ic p1wc=ic p1w&c=software,ic\n" +
                  "other wc=other w&c=.,other p1wc=ic\n";

  protected static final String EVENTS =
          "other wc=ic=1.0 w&c=he,ic=2.0 n1wc=lc=3.0 n1w&c=belongs,lc=4.0 n2wc=lc=5.0\n" +
                  "other wc=lc=1.0 w&c=belongs,lc=2.0 p1wc=ic=3.0 p1w&c=he,ic=4.0 n1wc=lc=5.0\n" +
                  "other wc=lc=1.0 w&c=to,lc=2.0 p1wc=lc=3.0 p1w&c=belongs,lc=4.0 p2wc=ic=5.0\n" +
                  "org-start wc=ic=1.0 w&c=apache,ic=2.0 p1wc=lc=3.0 p1w&c=to,lc=4.0\n" +
                  "org-cont wc=ic=1.0 w&c=software,ic=2.0 p1wc=ic=3.0 p1w&c=apache,ic=4.0\n" +
                  "org-cont wc=ic=1.0 w&c=foundation,ic=2.0 p1wc=ic=3.0 p1w&c=software,ic=4.0\n" +
                  "other wc=other=1.0 w&c=.,other=2.0 p1wc=ic=3.0\n";

  protected static final String EVENTS_INVALID_1 =
          "other wc=ic=1,0 w&c=he,ic=2,0 n1wc=lc=3,0 n1w&c=belongs,lc=4,0 n2wc=lc=5,0\n";

  protected static final String EVENTS_INVALID_2 =
          "other wc=ic=A w&c=he,ic=B n1wc=lc=C n1w&c=belongs,lc=D n2wc=lc=E\n";

  protected static final String EVENTS_INVALID_NEGATIVE =
          "other wc=ic=-1.0 w&c=he,ic=-2.0 n1wc=lc=-3.0 n1w&c=belongs,lc=-4.0 n2wc=lc=-5.0\n";

  protected abstract ObjectStream<Event> createEventStream(String input) throws IOException;

  @Test
  void testToLine() throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream(EVENTS_PLAIN)) {
      // just reading the first element here for format and platform checks
      Event e = eventStream.read();
      assertNotNull(e);
      assertNotNull(e.getOutcome());
      assertEquals("other wc=ic w&c=he,ic n1wc=lc n1w&c=belongs,lc n2wc=lc" + System.lineSeparator(),
              FileEventStream.toLine(e));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {EVENTS_INVALID_1, EVENTS_INVALID_2})
  void testReadWithInvalidRealValues(String input) throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream(input)) {
      Event e = eventStream.read();
      assertNotNull(e);
      assertNotNull(e.getOutcome());
      assertEquals("other", e.getOutcome());
      assertNotNull(e.getContext());
      assertEquals(5, e.getContext().length);
      assertNull(e.getValues()); // expected as float values where formatted incorrectly
    }
  }
}
