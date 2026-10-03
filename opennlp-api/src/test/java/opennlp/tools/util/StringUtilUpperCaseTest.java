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
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks code-point uppercase conversion, including malformed UTF-16. */
class StringUtilUpperCaseTest {

  @ParameterizedTest
  @CsvSource({"𐐨,𐐀", "a𐐨z,A𐐀Z", "straße,STRAßE", "😀a,😀A"})
  void mapsCodePointsWithoutExpandingCharacters(String input, String expected) {
    assertEquals(expected, StringUtil.toUpperCase(input));
  }

  @Test
  void preservesUnpairedSurrogates() {
    assertEquals("\uD801X\uDC28", StringUtil.toUpperCase("\uD801x\uDC28"));
  }

  @Test
  void acceptsMutableInputWithoutChangingIt() {
    StringBuilder input = new StringBuilder("𐐨a");
    assertEquals("𐐀A", StringUtil.toUpperCase(input));
    assertEquals("𐐨a", input.toString());
    assertEquals("", StringUtil.toUpperCase(new StringBuilder()));
  }
}
