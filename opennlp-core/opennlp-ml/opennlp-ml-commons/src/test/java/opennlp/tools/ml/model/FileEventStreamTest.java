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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.AbstractEventStreamTest;
import opennlp.tools.util.ObjectStream;

public class FileEventStreamTest extends AbstractEventStreamTest {

  @Override
  protected FileEventStream createEventStream(String input) throws IOException {
    return new FileEventStream(new StringReader(input));
  }

  /**
   * See: {@link AbstractEventStreamTest#EVENTS_PLAIN} for the input data.
   */
  @Test
  void testReadWithValidInput() throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream(EVENTS_PLAIN)) {
      Assertions.assertEquals("other [wc=ic w&c=he,ic n1wc=lc n1w&c=belongs,lc n2wc=lc]",
          eventStream.read().toString());
      Assertions.assertEquals("other [wc=lc w&c=belongs,lc p1wc=ic p1w&c=he,ic n1wc=lc]",
          eventStream.read().toString());
      Assertions.assertEquals("other [wc=lc w&c=to,lc p1wc=lc p1w&c=belongs,lc p2wc=ic]",
          eventStream.read().toString());
      Assertions.assertEquals("org-start [wc=ic w&c=apache,ic p1wc=lc p1w&c=to,lc]",
          eventStream.read().toString());
      Assertions.assertEquals("org-cont [wc=ic w&c=software,ic p1wc=ic p1w&c=apache,ic]",
          eventStream.read().toString());
      Assertions.assertEquals("org-cont [wc=ic w&c=foundation,ic p1wc=ic p1w&c=software,ic]",
          eventStream.read().toString());
      Assertions.assertEquals("other [wc=other w&c=.,other p1wc=ic]",
          eventStream.read().toString());
      Assertions.assertNull(eventStream.read());
    }
  }
  
  @Test
  void testReset() throws IOException {
    try (FileEventStream feStream = createEventStream(EVENTS_PLAIN)) {
      feStream.reset();
      Assertions.fail("UnsupportedOperationException should be thrown");
    } catch (UnsupportedOperationException expected) {
    }
  }

}
