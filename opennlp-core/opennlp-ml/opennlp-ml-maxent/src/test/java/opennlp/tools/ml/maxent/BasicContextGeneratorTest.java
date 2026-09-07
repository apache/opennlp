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

package opennlp.tools.ml.maxent;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class BasicContextGeneratorTest {

  private static Stream<Arguments> contexts() {
    return Stream.of(
        Arguments.of(" ", "cp_1 cp_2 cp_3", new String[] {"cp_1", "cp_2", "cp_3"}),
        Arguments.of(" ", "single", new String[] {"single"}),
        Arguments.of(" ", "", new String[] {""}),
        // the separator is taken as written, not as a regular expression
        Arguments.of("|", "a|b|c", new String[] {"a", "b", "c"}),
        Arguments.of(".", "a.b", new String[] {"a", "b"}),
        Arguments.of("+", "a+b", new String[] {"a", "b"}),
        Arguments.of("(", "a(b", new String[] {"a", "b"}),
        Arguments.of("\\s", "a\\sb", new String[] {"a", "b"}),
        Arguments.of("::", "a::b::c", new String[] {"a", "b", "c"}),
        Arguments.of("::", "a:b", new String[] {"a:b"}),
        // String.split shape: leading empty element kept, trailing empty elements dropped
        Arguments.of(",", ",a,,b,,", new String[] {"", "a", "", "b"}),
        Arguments.of(",", ",,", new String[0]),
        Arguments.of(" ", "a b", new String[] {"a", "b"}),
        Arguments.of("😀", "a😀b", new String[] {"a", "b"}));
  }

  @ParameterizedTest
  @MethodSource("contexts")
  void testSplitsOnTheLiteralSeparator(String separator, String input, String[] expected) {
    Assertions.assertArrayEquals(expected, new BasicContextGenerator(separator).getContext(input));
  }

  @Test
  void testDefaultSeparatorIsSpace() {
    Assertions.assertArrayEquals(new String[] {"a", "b"}, new BasicContextGenerator().getContext("a b"));
    // a tab is not a separator by default
    Assertions.assertArrayEquals(new String[] {"a\tb"}, new BasicContextGenerator().getContext("a\tb"));
  }

  @ParameterizedTest
  @ValueSource(strings = {""})
  void testEmptySeparatorIsRejected(String separator) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BasicContextGenerator(separator));
  }

  @Test
  void testNullSeparatorIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BasicContextGenerator(null));
  }
}
