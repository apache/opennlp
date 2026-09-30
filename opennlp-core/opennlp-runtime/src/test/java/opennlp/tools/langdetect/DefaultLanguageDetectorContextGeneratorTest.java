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

package opennlp.tools.langdetect;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.CompatibilityMode;


public class DefaultLanguageDetectorContextGeneratorTest {

  @AfterEach
  void resetMode() {
    CompatibilityMode.reset();
  }

  private static Set<String> defaultChainFeatures(String doc) {
    LanguageDetectorContextGenerator cg = new LanguageDetectorFactory().getContextGenerator();
    Set<String> features = new HashSet<>();
    for (CharSequence feature : cg.getContext(doc)) {
      features.add(feature.toString());
    }
    return features;
  }

  @Test
  void extractContext() {
    String doc = "abcde fghijk";

    DefaultLanguageDetectorContextGenerator cg = new DefaultLanguageDetectorContextGenerator(1, 3);

    Collection<CharSequence> features = Arrays.asList(cg.getContext(doc));

    Assertions.assertEquals(33, features.size());
    Assertions.assertTrue(features.contains(CharBuffer.wrap("ab")));
    Assertions.assertTrue(features.contains(CharBuffer.wrap("abc")));
    Assertions.assertTrue(features.contains(CharBuffer.wrap("e f")));
    Assertions.assertTrue(features.contains(CharBuffer.wrap(" fg")));
  }

  /**
   * The default chain removes complete emoji only: hyphens, fullwidth letters and a symbol
   * with text presentation reach the n-grams, and a BMP emoji such as U+231A does not.
   */
  @Test
  void defaultChainKeepsHyphensAndFullwidthTextAndRemovesEmoji() {
    CompatibilityMode.setActive(CompatibilityMode.CURRENT);
    Set<String> features = defaultChainFeatures(
        "well-known \uD83D\uDE00\uD83D\uDE00 x\uFF21y a\u231Ab \u2764 caf\u00E9");

    Assertions.assertTrue(features.contains("l-k"));
    Assertions.assertTrue(features.contains("n x"));
    Assertions.assertTrue(features.contains("x\uFF41y"));
    Assertions.assertTrue(features.contains("a b"));
    Assertions.assertTrue(features.contains(" \u2764 "));
    Assertions.assertTrue(features.contains("caf"));
    Assertions.assertFalse(features.contains("l k"));
    Assertions.assertFalse(features.contains("\uD83D\uDE00"));
  }

  /**
   * Under LEGACY the chain produces the features of the 1.x/2.x releases: hyphens, everything
   * from U+E000 to U+FFFF and supplementary characters are gone, and U+231A stays.
   */
  @Test
  void defaultChainReproducesLegacyFeaturesInLegacyMode() {
    CompatibilityMode.setActive(CompatibilityMode.LEGACY);
    Set<String> features = defaultChainFeatures(
        "well-known \uD83D\uDE00\uD83D\uDE00 x\uFF21y a\u231Ab \u2764 caf\u00E9");

    Assertions.assertTrue(features.contains("l k"));
    Assertions.assertTrue(features.contains("n x"));
    Assertions.assertTrue(features.contains("x y"));
    Assertions.assertTrue(features.contains("a\u231Ab"));
    Assertions.assertTrue(features.contains(" \u2764 "));
    Assertions.assertTrue(features.contains("caf"));
    Assertions.assertFalse(features.contains("l-k"));
    Assertions.assertFalse(features.contains("x\uFF41y"));
    Assertions.assertFalse(features.contains("\uD83D\uDE00"));
  }
}
