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

package opennlp.tools.formats.conllu;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.cmdline.TerminateToolException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests the command line parameter mapping of {@link ConlluTagset}. */
public class ConlluTagsetTest {

  @ParameterizedTest
  @CsvSource({"u, U", "x, X"})
  void testKnownParameters(String parameter, ConlluTagset tagset) {
    assertEquals(tagset, ConlluTagset.fromParameter(parameter));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"U", "X", "upos", "invalid"})
  void testUnknownParametersAreRejected(String parameter) {
    assertThrows(IllegalArgumentException.class, () -> ConlluTagset.fromParameter(parameter));
  }

  /** The factory parameter reader accepts the same two values as the parameter reader. */
  @ParameterizedTest
  @CsvSource({"u, U", "x, X"})
  void testKnownFactoryParameters(String parameter, ConlluTagset tagset) {
    assertEquals(tagset, ConlluTagset.fromFactoryParameter(parameter));
  }

  /** An unknown factory parameter ends the tool with code -1 and the parameter reader's message. */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"U", "X", "upos", "xpos", " u", "u ", "invalid"})
  void testUnknownFactoryParametersTerminateTheTool(String parameter) {
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> ConlluTagset.fromFactoryParameter(parameter));
    assertEquals(-1, exception.getCode());
    assertEquals("Unknown tagset parameter: " + parameter, exception.getMessage());
  }
}
