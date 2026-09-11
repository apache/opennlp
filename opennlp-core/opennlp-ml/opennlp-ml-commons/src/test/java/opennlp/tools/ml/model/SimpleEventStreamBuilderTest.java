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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStream;

public class SimpleEventStreamBuilderTest {

  @Test
  void testAddSplitsContextsOnAsciiWhitespaceRuns() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=he\t\tn1w=belongs   n2w=to \t po=other")
        .build()) {
      Event e = events.read();
      Assertions.assertEquals("other", e.getOutcome());
      Assertions.assertArrayEquals(
          new String[] {"w=he", "n1w=belongs", "n2w=to", "po=other"}, e.getContext());
      Assertions.assertNull(e.getValues());
      Assertions.assertNull(events.read());
    }
  }

  @Test
  void testAddDropsTrailingWhitespaceAndKeepsLeadingEmptyContext() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=he n1w=belongs  ")
        .add("other/ w=he")
        .build()) {
      Assertions.assertArrayEquals(new String[] {"w=he", "n1w=belongs"}, events.read().getContext());
      Assertions.assertArrayEquals(new String[] {"", "w=he"}, events.read().getContext());
    }
  }

  @Test
  void testAddWithValuesSplitsOnAsciiWhitespaceRuns() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=he;0.5\tn1w=belongs;0.4  n2w=to;0.3")
        .build()) {
      Event e = events.read();
      Assertions.assertArrayEquals(new String[] {"w=he", "n1w=belongs", "n2w=to"}, e.getContext());
      Assertions.assertArrayEquals(new float[] {0.5f, 0.4f, 0.3f}, e.getValues());
    }
  }

  @Test
  void testAddDoesNotSplitOnNonAsciiSpace() throws IOException {
    try (ObjectStream<Event> events = new SimpleEventStreamBuilder()
        .add("other/w=he n1w=belongs n2w=to")
        .build()) {
      Assertions.assertArrayEquals(
          new String[] {"w=he n1w=belongs", "n2w=to"}, events.read().getContext());
    }
  }

  @Test
  void testAddRejectsMissingSlash() {
    Assertions.assertThrows(RuntimeException.class,
        () -> new SimpleEventStreamBuilder().add("other w=he"));
  }
}
