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

package opennlp.tools.relation;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RelationPatternTest {

  private static final char NBSP = (char) 0x00A0;
  private static final char NEL = (char) 0x0085;
  private static final char NNBSP = (char) 0x202F;
  private static final char FIGURE_SPACE = (char) 0x2007;
  private static final char IDEOGRAPHIC_SPACE = (char) 0x3000;
  private static final char FILE_SEPARATOR = (char) 0x001C;
  private static final char UNIT_SEPARATOR = (char) 0x001F;

  @Test
  void testStepsSplitOnAsciiWhitespace() {
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj >obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "  <nsubj\t>obj  ", null).steps());
  }

  @Test
  void testStepsSplitOnEveryUnicodeWhitespace() {
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj" + NBSP + ">obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj" + NEL + ">obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj" + NNBSP + ">obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">obj"),
        new RelationPattern("t", "<nsubj" + FIGURE_SPACE + ">obj", null).steps());
    Assertions.assertEquals(List.of("<nsubj", ">nmod", ">case"),
        new RelationPattern("t",
            NBSP + "<nsubj" + IDEOGRAPHIC_SPACE + ">nmod" + NNBSP + ">case" + NEL,
            null).steps());
  }

  @Test
  void testInformationSeparatorsStayInsideALabel() {
    Assertions.assertEquals(List.of("<ns" + FILE_SEPARATOR + "ubj"),
        new RelationPattern("t", "<ns" + FILE_SEPARATOR + "ubj", null).steps());
    Assertions.assertEquals(List.of(">ab" + UNIT_SEPARATOR + "cd"),
        new RelationPattern("t", ">ab" + UNIT_SEPARATOR + "cd", null).steps());
  }

  @Test
  void testUpStepsMustPrecedeDownStepsAcrossAllSeparators() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", ">obj <nsubj", null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", ">obj" + NBSP + "<nsubj", null));
  }
}
