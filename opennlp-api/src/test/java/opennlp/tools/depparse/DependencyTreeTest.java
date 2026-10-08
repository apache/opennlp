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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the invariants and accessors of {@link DependencyTree} and {@link DependencyArc}.
 */
public class DependencyTreeTest {

  /** The three-token tree shared by the accessor tests. */
  private static DependencyTree sample() {
    return DependencyTree.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"});
  }

  @Test
  void testAccessors() {
    final DependencyTree tree = sample();
    assertEquals(3, tree.size());
    assertEquals(1, tree.headOf(0));
    assertEquals(2, tree.headOf(1));
    assertEquals(DependencyArc.ROOT_HEAD, tree.headOf(2));
    assertEquals("nsubj", tree.relationOf(1));
    assertEquals(2, tree.root());
  }

  @Test
  void testSerializationPreservesRoot() throws Exception {
    final DependencyTree tree = sample();
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(tree);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      final DependencyTree restored = (DependencyTree) in.readObject();
      assertEquals(tree, restored);
      assertEquals(tree.hashCode(), restored.hashCode());
      assertEquals(tree.root(), restored.root());
      assertEquals(DependencyArc.ROOT_HEAD, restored.headOf(restored.root()));
    }
  }

  /** The stream class carries the declared serial version UID, so the field is picked up. */
  @Test
  void testSerialVersionUid() {
    assertEquals(-3305194579961339787L,
        ObjectStreamClass.lookup(DependencyTree.class).getSerialVersionUID());
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
    assertNotEquals(sample(), DependencyTree.of(new int[] {1, 2, -1},
        new String[] {"amod", "nsubj", "root"}));
  }

  @Test
  void testInputArraysAreCopied() {
    final int[] heads = {1, -1};
    final String[] relations = {"nsubj", "root"};
    final DependencyTree tree = DependencyTree.of(heads, relations);
    heads[0] = 0;
    relations[0] = "det";
    assertEquals(1, tree.headOf(0));
    assertEquals("nsubj", tree.relationOf(0));
  }

  @Test
  void testNullArraysThrow() {
    assertEquals("heads must not be null", assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(null, new String[] {"root"})).getMessage());
    assertEquals("relations must not be null", assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {-1}, null)).getMessage());
  }

  @Test
  void testEmptyTreeThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[0], new String[0]));
  }

  @Test
  void testLengthMismatchThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {-1}, new String[] {"root", "nsubj"}));
  }

  @Test
  void testRootCountIsEnforced() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {1, 0}, new String[] {"a", "b"}));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {-1, -1}, new String[] {"root", "root"}));
  }

  @Test
  void testDisconnectedCycleThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {-1, 2, 1},
            new String[] {"root", "dep", "dep"}));
  }

  @Test
  void testSelfHeadThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {0, -1}, new String[] {"a", "root"}));
  }

  @Test
  void testOutOfRangeHeadThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {2, -1}, new String[] {"a", "root"}));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {-3, -1}, new String[] {"a", "root"}));
  }

  @Test
  void testBlankRelationThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {1, -1}, new String[] {" ", "root"}));
    // blankness follows the toolkit whitespace definition, which covers the no-break
    // space U+00A0 that the JDK predicate leaves out
    assertThrows(IllegalArgumentException.class,
        () -> DependencyTree.of(new int[] {1, -1}, new String[] {"\u00A0", "root"}));
    // and a label that only looks unusual is still content
    assertEquals("nmod:poss", DependencyTree.of(new int[] {1, -1},
        new String[] {"nmod:poss", "root"}).relationOf(0));
  }

  @Test
  void testIndexBoundsThrow() {
    final DependencyTree tree = sample();
    assertThrows(IllegalArgumentException.class, () -> tree.headOf(-1));
    assertThrows(IllegalArgumentException.class, () -> tree.relationOf(3));
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
