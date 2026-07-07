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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.normalizer.EmojiAnnotation;
import opennlp.tools.util.normalizer.EmojiAnnotator;

/**
 * Generates document features from the emoji annotation layer: for every annotated token the
 * project-authored coarse sentiment score ({@code emojiSentiment=2}), the entity type
 * ({@code emojiEntityType=HEART}), the document category hint
 * ({@code emojiCategory=SMILEYS_AND_EMOTION}), and for flags the ISO 3166 region
 * ({@code emojiRegion=DE}). Tokens that are not annotated symbols contribute nothing.
 *
 * <p>Strictly opt-in: pass it alongside the defaults, for example
 * {@code new DoccatFactory(new FeatureGenerator[] {new BagOfWordsFeatureGenerator(),
 * new EmojiFeatureGenerator()})}; the default factory configuration is unchanged. The no-argument
 * constructor is required so a trained model can re-instantiate the generator from its
 * manifest.</p>
 *
 * @see EmojiAnnotator
 */
public class EmojiFeatureGenerator implements FeatureGenerator {

  private static final String SENTIMENT_PREFIX = "emojiSentiment=";
  private static final String ENTITY_TYPE_PREFIX = "emojiEntityType=";
  private static final String CATEGORY_PREFIX = "emojiCategory=";
  private static final String REGION_PREFIX = "emojiRegion=";

  private final EmojiAnnotator annotator;

  /**
   * Instantiates a generator over the bundled and derived annotation layers.
   */
  public EmojiFeatureGenerator() {
    this(new EmojiAnnotator());
  }

  /**
   * Instantiates a generator over a configured annotator, for example one with a gazetteer join.
   *
   * @param annotator The annotator to use. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code annotator} is {@code null}.
   */
  public EmojiFeatureGenerator(EmojiAnnotator annotator) {
    if (annotator == null) {
      throw new IllegalArgumentException("Annotator must not be null");
    }
    this.annotator = annotator;
  }

  @Override
  public Collection<String> extractFeatures(String[] text, Map<String, Object> extraInformation) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    final List<String> features = new ArrayList<>();
    for (final String token : text) {
      // Every annotatable symbol starts beyond ASCII (audited in EmojiAnnotationsTest), so
      // ordinary tokens exit here without touching the annotation layer.
      if (token.isEmpty() || token.charAt(0) < 0x80) {
        continue;
      }
      final EmojiAnnotation annotation = annotator.annotate(token).orElse(null);
      if (annotation == null) {
        continue;
      }
      annotation.sentiment().ifPresent(score -> features.add(SENTIMENT_PREFIX + score));
      annotation.entityType().ifPresent(type -> features.add(ENTITY_TYPE_PREFIX + type));
      annotation.category().ifPresent(category -> features.add(CATEGORY_PREFIX + category));
      annotation.isoRegion().ifPresent(region -> features.add(REGION_PREFIX + region));
    }
    return features;
  }
}
