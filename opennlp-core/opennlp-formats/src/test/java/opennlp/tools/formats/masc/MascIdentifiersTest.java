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

package opennlp.tools.formats.masc;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MascIdentifiersTest {

  private static Stream<Arguments> removals() {
    return Stream.of(
        Arguments.of("ne-n7", MascIdentifiers.NAMED_ENTITY_ID_PREFIX, "7"),
        Arguments.of("penn-n12", MascIdentifiers.PENN_TOKEN_ID_PREFIX, "12"),
        Arguments.of("seg-r0", MascIdentifiers.REGION_ID_PREFIX, "0"),
        Arguments.of("xne-n7", MascIdentifiers.NAMED_ENTITY_ID_PREFIX, "x7"),
        Arguments.of("ne-nne-n7", MascIdentifiers.NAMED_ENTITY_ID_PREFIX, "ne-n7"),
        Arguments.of("penn-n7penn-n", MascIdentifiers.PENN_TOKEN_ID_PREFIX, "7penn-n"),
        Arguments.of("seg-r", MascIdentifiers.REGION_ID_PREFIX, ""),
        Arguments.of("7", MascIdentifiers.NAMED_ENTITY_ID_PREFIX, "7"),
        Arguments.of("", MascIdentifiers.NAMED_ENTITY_ID_PREFIX, ""),
        Arguments.of("NE-N7", MascIdentifiers.NAMED_ENTITY_ID_PREFIX, "NE-N7"),
        Arguments.of("ne\u2011n7", MascIdentifiers.NAMED_ENTITY_ID_PREFIX, "ne\u2011n7"),
        Arguments.of("\uD83D\uDE00ne-n1\uD83D\uDE00", MascIdentifiers.NAMED_ENTITY_ID_PREFIX,
            "\uD83D\uDE001\uD83D\uDE00"),
        Arguments.of("abc", "", "abc"));
  }

  @ParameterizedTest
  @MethodSource("removals")
  void testRemoveFirstRemovesOnlyTheFirstOccurrence(String input, String literal, String expected) {
    Assertions.assertEquals(expected, MascIdentifiers.removeFirst(input, literal));
    Assertions.assertEquals(input.replaceFirst(literal, ""), MascIdentifiers.removeFirst(input, literal));
  }
}
