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
package opennlp.tools.util.normalizer;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@link CharSequenceNormalizer} contract held against every implementation in this
 * package: null input throws {@link IllegalArgumentException}, with no exceptions and no
 * legacy carve-outs.
 */
class CharSequenceNormalizerContractTest {

  static Stream<Arguments> allNormalizers() {
    return Stream.of(
        Arguments.of("accentFold", AccentFoldCharSequenceNormalizer.getInstance()),
        Arguments.of("aggregate",
            new AggregateCharSequenceNormalizer(NfcCharSequenceNormalizer.getInstance())),
        Arguments.of("caseFold", CaseFoldCharSequenceNormalizer.getInstance()),
        Arguments.of("confusableSkeleton", ConfusableSkeletonCharSequenceNormalizer.getInstance()),
        Arguments.of("emoji", EmojiCharSequenceNormalizer.getInstance()),
        Arguments.of("nfc", NfcCharSequenceNormalizer.getInstance()),
        Arguments.of("nfkc", NfkcCharSequenceNormalizer.getInstance()),
        Arguments.of("number", NumberCharSequenceNormalizer.getInstance()),
        Arguments.of("shrink", ShrinkCharSequenceNormalizer.getInstance()),
        Arguments.of("socialMedia", SocialMediaCharSequenceNormalizer.getInstance()),
        Arguments.of("url", UrlCharSequenceNormalizer.getInstance()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allNormalizers")
  void testNullTextThrowsIllegalArgumentException(String name,
                                                  CharSequenceNormalizer normalizer) {
    assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(null),
        name + " must reject null per the CharSequenceNormalizer contract");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allNormalizers")
  void testEmptyTextIsAccepted(String name, CharSequenceNormalizer normalizer) {
    assertEquals("", normalizer.normalize("").toString(),
        name + " must pass empty text through");
  }

  @Test
  void testNumberJavadocExample() {
    assertEquals("a b ",
        NumberCharSequenceNormalizer.getInstance().normalize("a1234b56").toString());
  }

  @Test
  void testShrinkJavadocExample() {
    assertEquals("cool",
        ShrinkCharSequenceNormalizer.getInstance().normalize("coooool").toString());
  }
}
