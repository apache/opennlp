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

package opennlp.tools.util;

import java.io.IOException;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests system-property parsing and entry-count checks for resource limits. */
public class ResourceLimitsTest {

  private static final String PROPERTY = "opennlp.test.resource.limit";

  /** Clears the test property after each test. */
  @AfterEach
  void clearProperty() {
    System.clearProperty(PROPERTY);
  }

  /** Verifies positive integer and long overrides. */
  @Test
  void testPositiveOverrides() {
    System.setProperty(PROPERTY, "1024");
    Assertions.assertEquals(1024, ResourceLimits.initLimit(PROPERTY, 7));
    Assertions.assertEquals(1024L, ResourceLimits.initLimit(PROPERTY, 7L));
  }

  /** Verifies that surrounding whitespace is trimmed before parsing. */
  @Test
  void testPaddedOverrideIsTrimmed() {
    System.setProperty(PROPERTY, " 123 ");
    Assertions.assertEquals(123, ResourceLimits.initLimit(PROPERTY, 7));
    Assertions.assertEquals(123L, ResourceLimits.initLimit(PROPERTY, 7L));
  }

  /** Verifies that a long override above the int range is read. */
  @Test
  void testLongOverrideAboveIntRange() {
    System.setProperty(PROPERTY, "4294967296");
    Assertions.assertEquals(4_294_967_296L, ResourceLimits.initLimit(PROPERTY, 7L));
  }

  /** Verifies that a {@code null} property name is rejected. */
  @Test
  void testNullPropertyIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceLimits.initLimit(null, 7L));
  }

  /** Verifies that an absent property uses the supplied defaults. */
  @Test
  void testAbsentPropertyUsesDefaults() {
    Assertions.assertEquals(7, ResourceLimits.initLimit(PROPERTY, 7));
    Assertions.assertEquals(7L, ResourceLimits.initLimit(PROPERTY, 7L));
  }

  /**
   * Verifies that an invalid property uses the supplied defaults.
   *
   * @param value The invalid property value.
   */
  @ParameterizedTest(name = "value {0} uses the default")
  @ValueSource(strings = {"", "  ", "abc", "-1", "0"})
  void testInvalidPropertyUsesDefaults(String value) {
    System.setProperty(PROPERTY, value);
    Assertions.assertEquals(7, ResourceLimits.initLimit(PROPERTY, 7));
    Assertions.assertEquals(7L, ResourceLimits.initLimit(PROPERTY, 7L));
  }

  /**
   * Supplies counts inside the accepted range, including both bounds.
   *
   * @return The accepted counts. Never {@code null}.
   */
  static LongStream acceptedCounts() {
    return LongStream.of(0L, 1L, ResourceLimits.MAX_ENTRIES);
  }

  /**
   * Supplies counts outside the accepted range on both sides.
   *
   * @return The rejected counts. Never {@code null}.
   */
  static LongStream rejectedCounts() {
    return LongStream.of(-1L, Integer.MIN_VALUE, ResourceLimits.MAX_ENTRIES + 1L,
        Integer.MAX_VALUE, Long.MAX_VALUE);
  }

  /**
   * Verifies that counts from zero up to {@link ResourceLimits#MAX_ENTRIES} are accepted.
   *
   * @param count The count to check.
   */
  @ParameterizedTest(name = "count {0} is accepted")
  @MethodSource("acceptedCounts")
  void testCountWithinLimitIsAccepted(long count) throws IOException {
    Assertions.assertDoesNotThrow(() -> ResourceLimits.requireWithinMaxEntries(count, "count"));
    ResourceLimits.requireWithinMaxEntries(count, "count", IOException::new);
  }

  /**
   * Verifies that negative counts and counts above {@link ResourceLimits#MAX_ENTRIES}
   * are rejected with an {@link IllegalArgumentException} naming the count and limit.
   *
   * @param count The count to check.
   */
  @ParameterizedTest(name = "count {0} is rejected")
  @MethodSource("rejectedCounts")
  void testCountOutsideLimitIsRejected(long count) {
    final IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceLimits.requireWithinMaxEntries(count, "Outcome count"));
    Assertions.assertEquals("Outcome count " + count + " exceeds safe limit of "
        + ResourceLimits.MAX_ENTRIES, e.getMessage());
  }

  /**
   * Verifies that a rejected count is reported through the supplied exception factory.
   *
   * @param count The count to check.
   */
  @ParameterizedTest(name = "count {0} is rejected as IOException")
  @MethodSource("rejectedCounts")
  void testCountOutsideLimitUsesExceptionFactory(long count) {
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ResourceLimits.requireWithinMaxEntries(count, "unigram count", IOException::new));
    Assertions.assertEquals("unigram count " + count + " exceeds safe limit of "
        + ResourceLimits.MAX_ENTRIES, e.getMessage());
  }

  /** Verifies that a {@code null} exception factory is rejected. */
  @Test
  void testNullExceptionFactoryIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceLimits.requireWithinMaxEntries(1L, "count", null));
  }

  /** Verifies that a {@code null} label is rejected by both overloads. */
  @Test
  void testNullLabelIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceLimits.requireWithinMaxEntries(1L, null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceLimits.requireWithinMaxEntries(1L, null, IOException::new));
  }
}
