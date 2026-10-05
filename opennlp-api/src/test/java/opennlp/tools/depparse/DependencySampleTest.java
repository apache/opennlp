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
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.commons.Sample;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the invariants of {@link DependencySample}.
 */
public class DependencySampleTest {

  private static final String[] TOKENS = {"the", "dog", "barks"};
  private static final String[] TAGS = {"DT", "NN", "VBZ"};

  /** The tree matching {@link #TOKENS} and {@link #TAGS}. */
  private static DependencyTree tree() {
    return DependencyTree.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"});
  }

  @Test
  void testSampleContractAndSerialization() throws Exception {
    final DependencySample sample = new DependencySample(TOKENS, TAGS, tree());
    assertInstanceOf(Sample.class, sample);
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(sample);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      assertEquals(sample, in.readObject());
    }
  }

  @Test
  void testAccessors() {
    final DependencySample sample = new DependencySample(TOKENS, TAGS, tree());
    assertArrayEquals(TOKENS, sample.getTokens());
    assertArrayEquals(TAGS, sample.getTags());
    assertEquals(tree(), sample.getTree());
  }

  @Test
  void testEquals() {
    assertEquals(new DependencySample(TOKENS, TAGS, tree()),
        new DependencySample(TOKENS, TAGS, tree()));
  }

  @Test
  void testNullArgumentsThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(null, TAGS, tree()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(TOKENS, null, tree()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(TOKENS, TAGS, null));
  }

  @Test
  void testNullTokenOrTagThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(new String[] {"the", null, "barks"}, TAGS, tree()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(TOKENS, new String[] {"DT", null, "VBZ"}, tree()));
  }

  @Test
  void testLengthMismatchThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(new String[] {"one"}, TAGS, tree()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(TOKENS, new String[] {"DT"}, tree()));
  }

  @Test
  void testEmptySampleThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(new String[0], new String[0], tree()));
  }

  @Test
  void testNullEntriesThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(null, TAGS, tree()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(TOKENS, null, tree()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(new String[] {"the", null, "barks"}, TAGS, tree()));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(TOKENS, new String[] {"DT", null, "VBZ"}, tree()));
  }

  /**
   * Pins the message of every token and tag rejection of the constructor.
   *
   * @return Token and tag arrays with the expected message.
   */
  static Stream<Arguments> rejectedTokensAndTags() {
    return Stream.of(
        Arguments.of(null, null, "tokens must not be null"),
        Arguments.of(null, TAGS, "tokens must not be null"),
        Arguments.of(TOKENS, null, "tags must not be null"),
        Arguments.of(new String[0], new String[0], "tokens must not be empty"),
        Arguments.of(new String[0], TAGS, "tokens must not be empty"),
        Arguments.of(new String[] {"one"}, TAGS, "tokens and tags must have the same length: 1 != 3"),
        Arguments.of(TOKENS, new String[] {"DT"}, "tokens and tags must have the same length: 3 != 1"),
        Arguments.of(TOKENS, new String[0], "tokens and tags must have the same length: 3 != 0"),
        Arguments.of(new String[] {null, "dog", "barks"}, TAGS, "token must not be null at index 0"),
        Arguments.of(new String[] {"the", "dog", null}, TAGS, "token must not be null at index 2"),
        Arguments.of(TOKENS, new String[] {"DT", "NN", null}, "tag must not be null at index 2"),
        Arguments.of(new String[] {"the", null, "barks"}, new String[] {null, "NN", "VBZ"},
            "tag must not be null at index 0"));
  }

  /** Rejected token and tag arrays report the first violation in array order. */
  @ParameterizedTest(name = "{2}")
  @MethodSource("rejectedTokensAndTags")
  void testCheckTokensAndTagsMessages(String[] tokens, String[] tags, String message) {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(tokens, tags, tree()));
    assertEquals(message, exception.getMessage());
  }

  /** The validation checks presence only: empty, blank and supplementary-plane strings pass. */
  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"", " ", "\t", "\u00a0", "\uD835\uDFCF", "_"})
  void testCheckTokensAndTagsAcceptsAnyNonNullString(String value) {
    final String[] tokens = {value, "dog", value};
    final String[] tags = {value, value, "VBZ"};
    final DependencySample sample = new DependencySample(tokens, tags, tree());
    assertArrayEquals(tokens, sample.getTokens());
    assertArrayEquals(tags, sample.getTags());
  }

  /** A single-token sentence is the smallest accepted sample. */
  @Test
  void testSingleTokenSample() {
    final DependencySample sample = new DependencySample(new String[] {"Run"},
        new String[] {"VB"}, DependencyTree.of(new int[] {-1}, new String[] {"root"}));
    assertEquals(1, sample.getTokens().length);
    assertEquals("1\tRun\tVB\t0\troot" + System.lineSeparator(), sample.toString());
  }

  /** Long arrays are validated in one pass and pass when every entry is present. */
  @Test
  void testCheckTokensAndTagsAcceptsLongArrays() {
    final int length = 100_000;
    final String[] tokens = new String[length];
    final String[] tags = new String[length];
    Arrays.fill(tokens, "w");
    Arrays.fill(tags, "T");
    final int[] heads = new int[length];
    final String[] relations = new String[length];
    Arrays.fill(relations, "dep");
    for (int i = 1; i < length; i++) {
      heads[i] = i - 1;
    }
    heads[0] = DependencyArc.ROOT_HEAD;
    relations[0] = "root";
    final DependencyTree tree = DependencyTree.of(heads, relations);
    assertEquals(length, new DependencySample(tokens, tags, tree).getTokens().length);
    tags[length - 1] = null;
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(tokens, tags, tree));
    assertEquals("tag must not be null at index " + (length - 1), exception.getMessage());
  }

  /** The stream class carries the declared serial version UID, so the field is picked up. */
  @Test
  void testSerialVersionUid() {
    assertEquals(3889986411217462795L,
        ObjectStreamClass.lookup(DependencySample.class).getSerialVersionUID());
  }

  /** A deserialized sample keeps the hash code and the array contents of the original. */
  @Test
  void testDeserializedSampleKeepsHashCodeAndContents() throws Exception {
    final DependencySample sample = new DependencySample(TOKENS, TAGS, tree());
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(sample);
    }
    final DependencySample copy;
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (DependencySample) in.readObject();
    }
    assertEquals(sample.hashCode(), copy.hashCode());
    assertArrayEquals(TOKENS, copy.getTokens());
    assertArrayEquals(TAGS, copy.getTags());
    assertEquals(tree(), copy.getTree());
    assertEquals(sample.toString(), copy.toString());
  }

  /**
   * Samples that differ in one part.
   *
   * @return Samples that are not equal to the fixture sample.
   */
  static Stream<Arguments> differentSamples() {
    return Stream.of(
        Arguments.of("tokens", new DependencySample(new String[] {"a", "dog", "barks"}, TAGS, tree())),
        Arguments.of("tags", new DependencySample(TOKENS, new String[] {"DT", "NNS", "VBZ"}, tree())),
        Arguments.of("relation", new DependencySample(TOKENS, TAGS,
            DependencyTree.of(new int[] {1, 2, -1}, new String[] {"det", "obj", "root"}))),
        Arguments.of("head", new DependencySample(TOKENS, TAGS,
            DependencyTree.of(new int[] {2, 2, -1}, new String[] {"det", "nsubj", "root"}))));
  }

  /** The equals and hashCode contract: equal samples share a hash code, changed parts differ. */
  @ParameterizedTest(name = "different {0}")
  @MethodSource("differentSamples")
  void testEqualsAndHashCodeContract(String part, DependencySample other) {
    final DependencySample sample = new DependencySample(TOKENS, TAGS, tree());
    assertEquals(sample, sample);
    assertEquals(sample.hashCode(), new DependencySample(TOKENS, TAGS, tree()).hashCode());
    assertNotEquals(sample, other);
    assertNotEquals(sample, null);
    assertNotEquals(sample, TOKENS);
  }

  @Test
  void testInputArraysAreCopied() {
    final String[] tokens = TOKENS.clone();
    final DependencySample sample = new DependencySample(tokens, TAGS, tree());
    tokens[0] = "a";
    assertEquals("the", sample.getTokens()[0]);
  }
}
