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

import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChecksumEventStreamTest {

  @Test
  void testCalculateChecksumEquality() throws IOException {
    ChecksumEventStream ces1 = new ChecksumEventStream(createEventStreamFull());
    ChecksumEventStream ces2 = new ChecksumEventStream(createEventStreamFull());
    consumeEventStream(ces1, 7);
    consumeEventStream(ces2, 7);
    
    long checksum1 = ces1.calculateChecksum();
    long checksum2 = ces2.calculateChecksum();
    assertTrue(checksum1 != 0);
    assertTrue(checksum2 != 0);
    assertEquals(checksum1, checksum2);
  }

  @Test
  void testCalculateChecksumMismatch() throws IOException {
    ChecksumEventStream ces1 = new ChecksumEventStream(createEventStreamFull());
    ChecksumEventStream ces2 = new ChecksumEventStream(createEventStreamPartial());
    consumeEventStream(ces1, 7);
    consumeEventStream(ces2, 2);

    long checksum1 = ces1.calculateChecksum();
    long checksum2 = ces2.calculateChecksum();
    assertTrue(checksum1 != 0);
    assertTrue(checksum2 != 0);
    assertNotEquals(checksum1, checksum2);
  }

  private ObjectStream<Event> createEventStreamFull() {
    // He belongs to <START:org> Apache Software Foundation <END> .
    return new SimpleEventStreamBuilder()
        .add("other/w=he n1w=belongs n2w=to po=other pow=other,He powf=other,ic ppo=other")
        .add("other/w=belongs p1w=he n1w=to n2w=apache po=other pow=other,belongs powf=other,lc ppo=other")
        .add("other/w=to p1w=belongs p2w=he n1w=apache n2w=software po=other pow=other,to" +
                " powf=other,lc ppo=other")
        .add("org-start/w=apache p1w=to p2w=belongs n1w=software n2w=foundation po=other pow=other,Apache" +
                " powf=other,ic ppo=other")
        .add("org-cont/w=software p1w=apache p2w=to n1w=foundation n2w=. po=org-start" +
                " pow=org-start,Software powf=org-start,ic ppo=other")
        .add("org-cont/w=foundation p1w=software p2w=apache n1w=. po=org-cont pow=org-cont,Foundation" +
                " powf=org-cont,ic ppo=org-start")
        .add("other/w=. p1w=foundation p2w=software po=org-cont pow=org-cont,. powf=org-cont,other" +
                " ppo=org-cont")
        .build();
  }

  private ObjectStream<Event> createEventStreamPartial() {
    // He .
    return new SimpleEventStreamBuilder()
        .add("other/w=he n1w=belongs n2w=to po=other pow=other,He powf=other,ic ppo=other")
        .add("other/w=. p1w=foundation p2w=software po=org-cont pow=org-cont,. powf=org-cont,other" +
                " ppo=org-cont")
        .build();
  }

  private void consumeEventStream(ObjectStream<Event> eventStream, int eventCount) throws IOException {
    for (int i = 0; i < eventCount; i++) {
      assertNotNull(eventStream.read());
    }
  }
}
