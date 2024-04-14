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

package opennlp.tools.ml.maxent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.AbstractEventStreamTest;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.RealValueFileEventStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that the textual event (input) format in {@link RealBasicEventStream} is:
 * <br/>
 * {@code outcome context1 context2 context3 ...}
 * <p>
 * and is consistent with {@link RealValueFileEventStream}. Moreover, the test checks that processing
 * given input works as expected.
 * <p>
 * Reported in and adjusted via:
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-589">OPENNLP-589</a>.
 *
 * @see ObjectStream
 * @see RealValueFileEventStream
 */
public class RealBasicEventStreamTest extends AbstractEventStreamTest {

  @Override
  protected RealBasicEventStream createEventStream(String input) throws IOException {
    InputStreamFactory factory = () -> new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    ObjectStream<String> stream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
    return new RealBasicEventStream(stream);
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
    try (RealBasicEventStream eventStream = createEventStream(EVENTS_INVALID_NEGATIVE)) {
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
  void testReset() {
    try (RealBasicEventStream rbeStream = createEventStream(EVENTS)) {
      rbeStream.reset();
    } catch (IOException ioe) {
      fail(ioe.toString());
    }
  }

}
