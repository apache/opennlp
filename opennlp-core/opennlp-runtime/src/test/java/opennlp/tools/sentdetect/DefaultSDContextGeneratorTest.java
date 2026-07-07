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

package opennlp.tools.sentdetect;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.sentdetect.lang.Factory;

public class DefaultSDContextGeneratorTest {

  @Test
  void testGetContext() {
    SDContextGenerator sdContextGenerator =
        new DefaultSDContextGenerator(Collections.emptySet(), Factory.defaultEosCharacters);

    String[] context = sdContextGenerator.getContext(
        "Mr. Smith joined RONDHUIT Inc. as a manager of sales department.", 2);
    Assertions.assertArrayEquals("sn/eos=./x=Mr/2/xcap/v=/s=/n=Smith/ncap".split("/"), context);

    context = sdContextGenerator.getContext(
        "Mr. Smith joined RONDHUIT Inc. as a manager of sales department.", 29);
    Assertions.assertArrayEquals("sn/eos=./x=Inc/3/xcap/v=RONDHUIT/vcap/s=/n=as".split("/"), context);
  }

  @Test
  void testGetContextWithAbbreviations() {
    SDContextGenerator sdContextGenerator =
        new DefaultSDContextGenerator(new HashSet<>(Arrays.asList("Mr./Inc.".split("/"))),
            Factory.defaultEosCharacters);

    String[] context = sdContextGenerator.getContext(
        "Mr. Smith joined RONDHUIT Inc. as a manager of sales department.", 2);
    Assertions.assertArrayEquals("sn/eos=./x=Mr/2/xcap/xabbrev/v=/s=/n=Smith/ncap".split("/"), context);

    context = sdContextGenerator.getContext(
        "Mr. Smith joined RONDHUIT Inc. as a manager of sales department.", 29);
    Assertions.assertArrayEquals("sn/eos=./x=Inc/3/xcap/xabbrev/v=RONDHUIT/vcap/s=/n=as".split("/"), context);
  }

  /**
   * Model-stability pin: the whitespace features ({@code sp}, {@code sn}) are built on
   * {@code StringUtil.isWhitespace} (the union of {@code Character.isWhitespace} and the
   * {@code Zs} category) and must not migrate to the Unicode {@code White_Space} set, or the
   * generated feature strings would change for existing trained models. This pins the two
   * points where the predicates disagree: the {@code U+001C..U+001F} information separators
   * produce the whitespace feature, the next line control {@code U+0085} does not.
   */
  @Test
  void testWhitespaceFeaturesStayOnTheLegacyPredicate() {
    SDContextGenerator generator =
        new DefaultSDContextGenerator(Collections.emptySet(), Factory.defaultEosCharacters);

    Assertions.assertTrue(hasSpaceNextFeature(generator, cp(0x00A0)), "NBSP");
    Assertions.assertTrue(hasSpaceNextFeature(generator, cp(0x001C)), "U+001C");
    Assertions.assertTrue(hasSpaceNextFeature(generator, cp(0x001F)), "U+001F");
    Assertions.assertFalse(hasSpaceNextFeature(generator, cp(0x0085)), "U+0085 NEL");
  }

  private static boolean hasSpaceNextFeature(SDContextGenerator generator, String separator) {
    // The eos character is at index 3; "sn" fires when the character after it is whitespace.
    String[] context = generator.getContext("Foo." + separator + "Bar", 3);
    return Arrays.asList(context).contains("sn");
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }
}
