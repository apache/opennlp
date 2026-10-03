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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the contract of {@link ArgumentChecks#requireNonNullArg(Object, String)}: a non-null
 * argument is returned as is, and a {@code null} argument is rejected with an
 * {@link IllegalArgumentException} whose message names the parameter.
 */
public class ArgumentChecksTest {

  @Test
  void testNonNullArgumentIsReturnedUnchanged() {
    final Object value = new Object();
    assertSame(value, ArgumentChecks.requireNonNullArg(value, "value"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "text", "😀"})
  void testNonNullStringIsReturnedUnchanged(String value) {
    assertSame(value, ArgumentChecks.requireNonNullArg(value, "text"));
  }

  @Test
  void testEmptyArrayIsAccepted() {
    final String[] value = new String[0];
    assertSame(value, ArgumentChecks.requireNonNullArg(value, "tokens"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"text", "samples", "texts[1]", "😀"})
  void testNullArgumentIsRejectedWithParameterName(String name) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ArgumentChecks.requireNonNullArg(null, name));
    assertEquals(name + " must not be null", e.getMessage());
  }
}
