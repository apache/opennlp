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

package opennlp.tools.tokenize;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.WhitespaceMode;

/**
 * Tests for the {@link DefaultTokenContextGenerator} class.
 */
public class DefaultTokenContextGeneratorTest {

  private final DefaultTokenContextGenerator cg = new DefaultTokenContextGenerator();

  /**
   * Restores {@link WhitespaceMode} property resolution after each test, so no mode
   * state leaks.
   */
  @AfterEach
  void resetWhitespaceMode() {
    WhitespaceMode.reset();
  }

  /**
   * The {@code _ws} feature suffix (whitespace class; underscore is only a name separator,
   * as in {@code f1_ws}) follows the active {@link WhitespaceMode}. These features are part
   * of every trained tokenizer model, so this pins the default behavior at the code points
   * where the modes disagree: the next line control ({@code U+0085}) is classified as
   * whitespace, the information separator ({@code U+001C}) is not.
   */
  @Test
  void testNextLineControlProducesWhitespaceFeatureByDefault() {
    char nextLine = (char) 0x0085;
    List<String> preds = Arrays.asList(cg.getContext("a" + nextLine + "b", 1));
    Assertions.assertTrue(preds.contains("f1_ws"), preds.toString());

    char infoSeparator = (char) 0x001C;
    preds = Arrays.asList(cg.getContext("a" + infoSeparator + "b", 1));
    Assertions.assertFalse(preds.contains("f1_ws"), preds.toString());
  }

  /**
   * Under {@link WhitespaceMode#LEGACY} the classification flips: the information separator
   * ({@code U+001C}) is whitespace and the next line control ({@code U+0085}) is not,
   * reproducing the features of OpenNLP 1.x/2.x tokenizer models.
   */
  @Test
  void testInformationSeparatorProducesWhitespaceFeatureUnderLegacyMode() {
    WhitespaceMode.setActive(WhitespaceMode.LEGACY);

    char infoSeparator = (char) 0x001C;
    List<String> preds = Arrays.asList(cg.getContext("a" + infoSeparator + "b", 1));
    Assertions.assertTrue(preds.contains("f1_ws"), preds.toString());

    char nextLine = (char) 0x0085;
    preds = Arrays.asList(cg.getContext("a" + nextLine + "b", 1));
    Assertions.assertFalse(preds.contains("f1_ws"), preds.toString());
  }
}
