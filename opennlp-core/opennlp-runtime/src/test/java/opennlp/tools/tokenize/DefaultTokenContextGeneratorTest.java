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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link DefaultTokenContextGenerator} class.
 */
public class DefaultTokenContextGeneratorTest {

  /**
   * Model-stability pin: the {@code _ws} character-class feature is built on
   * {@code StringUtil.isWhitespace} (the union of {@code Character.isWhitespace} and the
   * {@code Zs} category) and must not migrate to the Unicode {@code White_Space} set, or the
   * generated feature strings would change for existing trained models. This pins the two
   * points where the predicates disagree: the {@code U+001C..U+001F} information separators
   * produce the {@code _ws} feature, the next line control {@code U+0085} does not.
   */
  @Test
  void testWhitespaceCharPredStaysOnTheLegacyPredicate() {
    TokenContextGenerator generator = new DefaultTokenContextGenerator();

    Assertions.assertTrue(hasWhitespacePred(generator, 0x00A0), "NBSP");
    Assertions.assertTrue(hasWhitespacePred(generator, 0x001C), "U+001C");
    Assertions.assertTrue(hasWhitespacePred(generator, 0x001F), "U+001F");
    Assertions.assertFalse(hasWhitespacePred(generator, 0x0085), "U+0085 NEL");
  }

  private static boolean hasWhitespacePred(TokenContextGenerator generator, int codePoint) {
    // The f1 predicates describe the character at the candidate split index (1).
    String token = "a" + new String(Character.toChars(codePoint)) + "b";
    String[] context = generator.getContext(token, 1);
    return Arrays.asList(context).contains("f1_ws");
  }
}
