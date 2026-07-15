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

package opennlp.tools.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import opennlp.tools.postag.POSTagger;

/**
 * Adapts a {@link POSTagger} to the document pipeline: reads {@link Layers#TOKENS} and
 * provides {@link Layers#POS_TAGS}, one tag annotation per token on the token's span.
 *
 * @since 3.0.0
 */
public class POSTaggerAnnotator implements DocumentAnnotator {

  private final POSTagger tagger;

  /**
   * Initializes the adapter.
   *
   * @param tagger The tagger to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code tagger} is {@code null}.
   */
  public POSTaggerAnnotator(POSTagger tagger) {
    if (tagger == null) {
      throw new IllegalArgumentException("tagger must not be null");
    }
    this.tagger = tagger;
  }

  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException("document lacks the required layer "
          + Layers.TOKENS);
    }
    final String[] words = new String[tokens.size()];
    for (int i = 0; i < words.length; i++) {
      words[i] = tokens.get(i).value();
    }
    final String[] tags = tagger.tag(words);
    final List<Annotation<String>> tagAnnotations = new ArrayList<>(tags.length);
    for (int i = 0; i < tags.length; i++) {
      tagAnnotations.add(new Annotation<>(tokens.get(i).span(), tags[i]));
    }
    return document.with(Layers.POS_TAGS, tagAnnotations);
  }

  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.TOKENS);
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(Layers.POS_TAGS);
  }
}
