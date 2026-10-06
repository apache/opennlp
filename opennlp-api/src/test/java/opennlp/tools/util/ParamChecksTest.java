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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the contract of the {@link ParamChecks} helpers: a valid argument is returned as is,
 * and an invalid argument is rejected with an {@link IllegalArgumentException} whose message
 * names the parameter.
 */
public class ParamChecksTest {

  @Test
  void testNonNullArgumentIsReturnedUnchanged() {
    final Object value = new Object();
    assertSame(value, ParamChecks.requireNonNullArg(value, "value"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "text", "😀"})
  void testNonNullStringIsReturnedUnchanged(String value) {
    assertSame(value, ParamChecks.requireNonNullArg(value, "text"));
  }

  @Test
  void testEmptyArrayIsAccepted() {
    final String[] value = new String[0];
    assertSame(value, ParamChecks.requireNonNullArg(value, "tokens"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"text", "samples", "texts[1]", "😀"})
  void testNullArgumentIsRejectedWithParameterName(String name) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonNullArg(null, name));
    assertEquals(name + " must not be null", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "text", "😀"})
  void testNonEmptyCharSequenceIsReturnedUnchanged(String value) {
    assertSame(value, ParamChecks.requireNonEmpty(value, "text"));
    final StringBuilder builder = new StringBuilder(value);
    assertSame(builder, ParamChecks.requireNonEmpty(builder, "text"));
  }

  @Test
  void testNullCharSequenceIsRejectedAsEmpty() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonEmpty((String) null, "suffix"));
    assertEquals("suffix must not be null or empty", e.getMessage());
  }

  @Test
  void testEmptyCharSequenceIsRejected() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonEmpty(new StringBuilder(), "sep"));
    assertEquals("sep must not be null or empty", e.getMessage());
  }

  @Test
  void testNonEmptyCollectionIsReturnedUnchanged() {
    final List<String> list = List.of("a");
    assertSame(list, ParamChecks.requireNonEmpty(list, "spans"));
    final Set<Integer> set = Set.of(1, 2);
    assertSame(set, ParamChecks.requireNonEmpty(set, "spans"));
  }

  @Test
  void testNullCollectionIsRejectedAsEmpty() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonEmpty((List<String>) null, "spans"));
    assertEquals("spans must not be null or empty", e.getMessage());
  }

  @Test
  void testEmptyCollectionIsRejected() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonEmpty(new ArrayList<String>(), "spans"));
    assertEquals("spans must not be null or empty", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"text", " a ", "😀", "\u001C"})
  void testNonBlankStringIsReturnedUnchanged(String value) {
    assertSame(value, ParamChecks.requireNonBlank(value, "name"));
  }

  @Test
  void testNullStringIsRejectedAsBlank() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonBlank(null, "name"));
    assertEquals("name must not be null or blank", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", " \n\r ", "\u00A0", "\u2003", "\u3000", "\u0085"})
  void testBlankStringIsRejected(String value) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonBlank(value, "language"));
    assertEquals("language must not be null or blank", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
  void testNonNegativeIntIsReturnedUnchanged(int value) {
    assertEquals(value, ParamChecks.requireNonNegative(value, "id"));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, Integer.MIN_VALUE})
  void testNegativeIntIsRejected(int value) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonNegative(value, "id"));
    assertEquals("id must not be negative", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 1L, Long.MAX_VALUE})
  void testNonNegativeLongIsReturnedUnchanged(long value) {
    assertEquals(value, ParamChecks.requireNonNegative(value, "count"));
  }

  @ParameterizedTest
  @ValueSource(longs = {-1L, Long.MIN_VALUE})
  void testNegativeLongIsRejected(long value) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonNegative(value, "count"));
    assertEquals("count must not be negative", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, -0.0, 1.5, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN})
  void testNonNegativeDoubleIsReturnedUnchanged(double value) {
    assertEquals(value, ParamChecks.requireNonNegative(value, "weight"));
  }

  @ParameterizedTest
  @ValueSource(doubles = {-1.0, -Double.MIN_VALUE, Double.NEGATIVE_INFINITY})
  void testNegativeDoubleIsRejected(double value) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireNonNegative(value, "weight"));
    assertEquals("weight must not be negative", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, -1.5, Double.MAX_VALUE, -Double.MAX_VALUE})
  void testFiniteDoubleIsReturnedUnchanged(double value) {
    assertEquals(value, ParamChecks.requireFinite(value, "weight"));
  }

  @ParameterizedTest
  @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
  void testNonFiniteDoubleIsRejected(double value) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ParamChecks.requireFinite(value, "weight"));
    assertEquals("weight must be finite", e.getMessage());
  }
}
