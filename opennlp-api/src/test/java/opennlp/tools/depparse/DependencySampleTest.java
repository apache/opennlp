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

package opennlp.tools.depparse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the invariants of {@link DependencySample}.
 */
public class DependencySampleTest {

  private static final String[] TOKENS = {"the", "dog", "barks"};
  private static final String[] TAGS = {"DT", "NN", "VBZ"};

  private static DependencyGraph graph() {
    return DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"});
  }

  @Test
  void testAccessors() {
    final DependencySample sample = new DependencySample(TOKENS, TAGS, graph());
    assertArrayEquals(TOKENS, sample.getTokens());
    assertArrayEquals(TAGS, sample.getTags());
    assertEquals(graph(), sample.getGraph());
  }

  @Test
  void testEquals() {
    assertEquals(new DependencySample(TOKENS, TAGS, graph()),
        new DependencySample(TOKENS, TAGS, graph()));
  }

  @Test
  void testNullArgumentsThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(null, TAGS, graph()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(TOKENS, null, graph()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(TOKENS, TAGS, null));
  }

  @Test
  void testLengthMismatchThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(new String[] {"one"}, TAGS, graph()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(TOKENS, new String[] {"DT"}, graph()));
  }

  @Test
  void testInputArraysAreCopied() {
    final String[] tokens = TOKENS.clone();
    final DependencySample sample = new DependencySample(tokens, TAGS, graph());
    tokens[0] = "a";
    assertEquals("the", sample.getTokens()[0]);
  }
}
