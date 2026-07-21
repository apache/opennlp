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
import java.util.Collections;
import java.util.List;

import opennlp.tools.util.normalizer.EmojiAnnotator;

/**
 * A {@link SentimentContextGenerator} that adds emoji annotation features to the default
 * token context: for every annotated token the project-authored coarse sentiment score
 * ({@code emojiSentiment=2}), the entity type ({@code emojiType=HEART}), the category
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

  private final EmojiAnnotator annotator;

  /**
   * Instantiates a context generator over the bundled and derived annotation layers.
   */
  public EmojiSentimentContextGenerator() {
    this(new EmojiAnnotator());
  }

  /**
   * Instantiates a context generator over a configured annotator, for example one with a
   * gazetteer join.
   *
   * @param annotator The annotator to use. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code annotator} is {@code null}.
   */
  public EmojiSentimentContextGenerator(EmojiAnnotator annotator) {
    if (annotator == null) {
      throw new IllegalArgumentException("Annotator must not be null");
    }
    this.annotator = annotator;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Appends the emoji annotation features of every annotated token to the default token
   * context.</p>
   *
   * @throws IllegalArgumentException if {@code text} is {@code null}.
   */
  @Override
  public String[] getContext(String[] text) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    final List<String> context = new ArrayList<>(text.length);
    Collections.addAll(context, super.getContext(text));
    for (final String token : text) {
      annotator.collectFeatures(token, context);
    }
    return context.toArray(new String[0]);
  }
}
