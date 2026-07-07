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

package opennlp.tools.doccat;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmojiFeatureGeneratorTest {

  private static String cp(int... codePoints) {
    final StringBuilder sb = new StringBuilder();
    for (final int codePoint : codePoints) {
      sb.appendCodePoint(codePoint);
    }
    return sb.toString();
  }

  @Test
  void annotatedTokensContributeDocumentFeatures() {
    final FeatureGenerator generator = new EmojiFeatureGenerator();
    final Collection<String> features = generator.extractFeatures(
        new String[] {"great", "pizza", cp(0x1F355), cp(0x1F600)}, null);
    assertEquals(List.of(
        "emojiSentiment=0", "emojiEntityType=FOOD", "emojiCategory=FOOD_AND_DRINK",
        "emojiSentiment=2", "emojiEntityType=FACE", "emojiCategory=SMILEYS_AND_EMOTION"),
        List.copyOf(features));
  }

  @Test
  void flagsContributeTheirRegion() {
    final FeatureGenerator generator = new EmojiFeatureGenerator();
    final Collection<String> features = generator.extractFeatures(
        new String[] {"match", cp(0x1F1EB, 0x1F1F7)}, null);
    assertTrue(features.contains("emojiRegion=FR"), "Missing region feature in: " + features);
    assertTrue(features.contains("emojiEntityType=FLAG"), "Missing type feature in: " + features);
  }

  @Test
  void plainDocumentsContributeNothing() {
    final FeatureGenerator generator = new EmojiFeatureGenerator();
    // Includes a lone regional indicator: bulk extraction must survive damaged text.
    final Collection<String> features = generator.extractFeatures(
        new String[] {"no", "emoji", "here", "", cp(0x1F1E9)}, null);
    assertTrue(features.isEmpty(), "Unexpected features: " + features);
  }

  @Test
  void argumentsAreValidated() {
    final FeatureGenerator generator = new EmojiFeatureGenerator();
    assertThrows(IllegalArgumentException.class, () -> generator.extractFeatures(null, null));
    assertThrows(IllegalArgumentException.class, () -> new EmojiFeatureGenerator(null));
  }

  @Test
  void optInThroughTheDoccatFactorySeam() {
    // The wiring is the existing DoccatFactory feature generator seam; the default factory keeps
    // only BagOfWords, so default behavior is unchanged and this factory is the opt-in.
    final DoccatFactory factory = new DoccatFactory(new FeatureGenerator[] {
        new BagOfWordsFeatureGenerator(), new EmojiFeatureGenerator()});
    assertEquals(2, factory.getFeatureGenerators().length);
    final DoccatFactory defaults = new DoccatFactory();
    assertEquals(1, defaults.getFeatureGenerators().length);
    assertEquals(BagOfWordsFeatureGenerator.class,
        defaults.getFeatureGenerators()[0].getClass());
  }
}
