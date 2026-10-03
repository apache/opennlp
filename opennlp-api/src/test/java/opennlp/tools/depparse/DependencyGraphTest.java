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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the invariants and accessors of {@link DependencyGraph} and {@link DependencyArc}.
 */
public class DependencyGraphTest {

  /** Serialized heads [1, -1] and relations [nsubj, root], without a cached root field. */
  private static final String GRAPH_WITHOUT_CACHED_ROOT =
      "rO0ABXNyACZvcGVubmxwLnRvb2xzLmRlcHBhcnNlLkRlcGVuZGVuY3lHcmFwaKMmtL0nhVuPAgAC"
          + "WwAFaGVhZHN0AAJbSVsACXJlbGF0aW9uc3QAE1tMamF2YS9sYW5nL1N0cmluZzt4cHVyAAJbSU26"
          + "YCZ26rKlAgAAeHAAAAACAAAAAf////91cgATW0xqYXZhLmxhbmcuU3RyaW5nO63SVufpHXtHAgAA"
          + "eHAAAAACdAAFbnN1Ymp0AARyb290";

  /** The three-token graph shared by the accessor tests. */
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
  void testSerializationPreservesRoot() throws Exception {
    final DependencyGraph graph = sample();
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(graph);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      final DependencyGraph restored = (DependencyGraph) in.readObject();
      assertEquals(graph, restored);
      assertEquals(graph.root(), restored.root());
      assertEquals(DependencyArc.ROOT_HEAD, restored.headOf(restored.root()));
    }
  }

  @Test
  void testSerializedGraphWithoutCachedRootIsRejected() throws Exception {
    final byte[] bytes = Base64.getDecoder().decode(GRAPH_WITHOUT_CACHED_ROOT);
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      assertThrows(InvalidClassException.class, in::readObject);
    }
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
    assertEquals("heads must not be null", assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(null, new String[] {"root"})).getMessage());
    assertEquals("relations must not be null", assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {-1}, null)).getMessage());
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
  void testDisconnectedCycleThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {-1, 2, 1},
            new String[] {"root", "dep", "dep"}));
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
    // blankness follows the toolkit whitespace definition, which covers the no-break
    // space U+00A0 that the JDK predicate leaves out
    assertThrows(IllegalArgumentException.class,
        () -> DependencyGraph.of(new int[] {1, -1}, new String[] {"\u00A0", "root"}));
    // and a label that only looks unusual is still content
    assertEquals("nmod:poss", DependencyGraph.of(new int[] {1, -1},
        new String[] {"nmod:poss", "root"}).relationOf(0));
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
    assertThrows(IllegalArgumentException.class, () -> new DependencyArc(1, 0, "\u00A0"));
    assertThrows(IllegalArgumentException.class, () -> new DependencyArc(1, 0, null));
    assertEquals("det", new DependencyArc(1, 0, "det").relation());
  }
}
