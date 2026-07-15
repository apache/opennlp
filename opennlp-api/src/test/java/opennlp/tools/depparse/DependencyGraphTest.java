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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the invariants and accessors of {@link DependencyGraph} and {@link DependencyArc}.
 */
public class DependencyGraphTest {

  private static DependencyGraph sample() {
    return DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"});
  }

  @Test
  void testAccessors() {
    final DependencyGraph graph = sample();
    assertEquals(3, graph.size());
    assertEquals(1, graph.headOf(0));
    assertEquals(2, graph.headOf(1));
    assertEquals(DependencyArc.ROOT_HEAD, graph.headOf(2));
    assertEquals("nsubj", graph.relationOf(1));
    assertEquals(2, graph.root());
  }

  @Test
  void testArcsAreInTokenOrder() {
    final List<DependencyArc> arcs = sample().arcs();
    assertEquals(3, arcs.size());
    assertEquals(new DependencyArc(1, 0, "det"), arcs.get(0));
    assertEquals(new DependencyArc(2, 1, "nsubj"), arcs.get(1));
    assertEquals(new DependencyArc(DependencyArc.ROOT_HEAD, 2, "root"), arcs.get(2));
  }

  @Test
  void testEqualsAndHashCode() {
    assertEquals(sample(), sample());
    assertEquals(sample().hashCode(), sample().hashCode());
    assertNotEquals(sample(), DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"amod", "nsubj", "root"}));
  }

  @Test
  void testInputArraysAreCopied() {
    final int[] heads = {1, -1};
    final String[] relations = {"nsubj", "root"};
    final DependencyGraph graph = DependencyGraph.of(heads, relations);
    heads[0] = 0;
    relations[0] = "det";
    assertEquals(1, graph.headOf(0));
    assertEquals("nsubj", graph.relationOf(0));
  }

  @Test
  void testNullArraysThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(null, new String[] {"root"}));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {-1}, null));
  }

  @Test
  void testEmptyGraphThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[0], new String[0]));
  }

  @Test
  void testLengthMismatchThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {-1}, new String[] {"root", "nsubj"}));
  }

  @Test
  void testRootCountIsEnforced() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {1, 0}, new String[] {"a", "b"}));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {-1, -1}, new String[] {"root", "root"}));
  }

  @Test
  void testSelfHeadThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {0, -1}, new String[] {"a", "root"}));
  }

  @Test
  void testOutOfRangeHeadThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {2, -1}, new String[] {"a", "root"}));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {-3, -1}, new String[] {"a", "root"}));
  }

  @Test
  void testBlankRelationThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {1, -1}, new String[] {" ", "root"}));
  }

  @Test
  void testIndexBoundsThrow() {
    final DependencyGraph graph = sample();
    assertThrows(IllegalArgumentException.class, () -> graph.headOf(-1));
    assertThrows(IllegalArgumentException.class, () -> graph.relationOf(3));
  }

  @Test
  void testArcValidation() {
    assertThrows(IllegalArgumentException.class, () -> new DependencyArc(0, 0, "root"));
    assertThrows(IllegalArgumentException.class, () -> new DependencyArc(1, -1, "det"));
    assertThrows(IllegalArgumentException.class, () -> new DependencyArc(-2, 0, "det"));
    assertThrows(IllegalArgumentException.class, () -> new DependencyArc(1, 0, " "));
    assertThrows(IllegalArgumentException.class, () -> new DependencyArc(1, 0, null));
  }
}
