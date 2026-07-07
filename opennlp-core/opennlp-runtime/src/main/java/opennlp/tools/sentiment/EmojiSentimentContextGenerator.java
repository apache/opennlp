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

package opennlp.tools.sentiment;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.normalizer.EmojiAnnotation;
import opennlp.tools.util.normalizer.EmojiAnnotator;

/**
 * A {@link SentimentContextGenerator} that adds emoji annotation features to the default
 * token context: for every annotated token the project-authored coarse sentiment score
 * ({@code emojiSentiment=2}), the entity type ({@code emojiEntityType=HEART}), the category
 * ({@code emojiCategory=SMILEYS_AND_EMOTION}), and for flags the region ({@code emojiRegion=DE}).
 * A crying-face or heart pictograph thereby becomes direct evidence for the sentiment model
 * instead of an opaque token.
 *
 * <p>Strictly opt-in through the {@link SentimentFactory} seam: train with
 * {@link EmojiSentimentFactory} (or any factory whose
 * {@link SentimentFactory#createContextGenerator()} returns this class) and the same context is
 * regenerated at prediction time; the default factory and existing models are unchanged.</p>
 *
 * @see EmojiSentimentFactory
 * @see EmojiAnnotator
 */
public class EmojiSentimentContextGenerator extends SentimentContextGenerator {

  private static final String SENTIMENT_PREFIX = "emojiSentiment=";
  private static final String ENTITY_TYPE_PREFIX = "emojiEntityType=";
  private static final String CATEGORY_PREFIX = "emojiCategory=";
  private static final String REGION_PREFIX = "emojiRegion=";

  private final EmojiAnnotator annotator = new EmojiAnnotator();

  @Override
  public String[] getContext(String[] text) {
    final List<String> context = new ArrayList<>(text.length);
    for (final String token : text) {
      context.add(token); // the default context: every token is a feature
    }
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
      annotation.sentiment().ifPresent(score -> context.add(SENTIMENT_PREFIX + score));
      annotation.entityType().ifPresent(type -> context.add(ENTITY_TYPE_PREFIX + type));
      annotation.category().ifPresent(category -> context.add(CATEGORY_PREFIX + category));
      annotation.isoRegion().ifPresent(region -> context.add(REGION_PREFIX + region));
    }
    return context.toArray(new String[0]);
  }
}
