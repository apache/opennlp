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

package opennlp.tools.util.featuregen;

import java.util.List;

import opennlp.tools.util.normalizer.EmojiAnnotation;
import opennlp.tools.util.normalizer.EmojiAnnotator;

/**
 * Generates features from the emoji annotation layer for the token at the given index: the
 * project-authored coarse sentiment score ({@code emojiSentiment=2}), the entity type
 * ({@code emojiType=HEART}), the document category ({@code emojiCategory=SMILEYS_AND_EMOTION}),
 * and for flags the ISO 3166 region ({@code emojiRegion=DE}). For the name finder the entity
 * type behaves like gazetteer evidence: a flag token carries {@code emojiType=FLAG} and its
 * region without any dictionary.
 *
 * <p>Strictly opt-in: this generator only runs when a feature generation descriptor names
 * {@link EmojiAnnotationFeatureGeneratorFactory}, so default descriptors and existing models are
 * unchanged. A token that is not an annotated symbol contributes no features.</p>
 *
 * <p>Unlike most feature generators this one is stateless and thread-safe (when its annotator's
 * join is): it keeps no adaptive data.</p>
 *
 * @see EmojiAnnotator
 */
public class EmojiAnnotationFeatureGenerator implements AdaptiveFeatureGenerator {

  private final EmojiAnnotator annotator;

  /**
   * Instantiates a generator over the bundled and derived annotation layers.
   */
  public EmojiAnnotationFeatureGenerator() {
    this(new EmojiAnnotator());
  }

  /**
   * Instantiates a generator over a configured annotator, for example one with a gazetteer join.
   *
   * @param annotator The annotator to use. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code annotator} is {@code null}.
   */
  public EmojiAnnotationFeatureGenerator(EmojiAnnotator annotator) {
    if (annotator == null) {
      throw new IllegalArgumentException("Annotator must not be null");
    }
    this.annotator = annotator;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Adds one feature per present annotation attribute of the token at {@code index}.</p>
   */
  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
                             String[] previousOutcomes) {
    final String token = tokens[index];
    if (!EmojiAnnotator.isAnnotatableToken(token)) {
      return;
    }
    final EmojiAnnotation annotation = annotator.annotate(token).orElse(null);
    if (annotation == null) {
      return;
    }
    annotation.sentiment().ifPresent(
        score -> features.add(EmojiAnnotator.FEATURE_SENTIMENT_PREFIX + score));
    annotation.entityType().ifPresent(
        type -> features.add(EmojiAnnotator.FEATURE_TYPE_PREFIX + type));
    annotation.category().ifPresent(
        category -> features.add(EmojiAnnotator.FEATURE_CATEGORY_PREFIX + category));
    annotation.isoRegion().ifPresent(
        region -> features.add(EmojiAnnotator.FEATURE_REGION_PREFIX + region));
  }
}
