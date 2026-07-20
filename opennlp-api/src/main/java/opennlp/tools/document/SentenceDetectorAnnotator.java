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

import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.util.Span;

/**
 * Adapts a {@link SentenceDetector} to the document pipeline: provides
 * {@link Layers#SENTENCES} from the document text.
 *
 * <p>The wrapped detector stays the primary API for single-task use; this adapter calls
 * it like any other caller would.</p>
 *
 * @since 3.0.0
 */
public class SentenceDetectorAnnotator implements DocumentAnnotator {

  private final SentenceDetector detector;

  /**
   * Initializes the adapter.
   *
   * @param detector The sentence detector to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code detector} is {@code null}.
   */
  public SentenceDetectorAnnotator(SentenceDetector detector) {
    if (detector == null) {
      throw new IllegalArgumentException("detector must not be null");
    }
    this.detector = detector;
  }

  /**
   * Detects sentences over the document text and adds the {@link Layers#SENTENCES}
   * layer, each sentence annotated with its covered text on its span.
   *
   * @param document The document to annotate. Must not be {@code null}.
   * @return A new {@link Document} with the {@link Layers#SENTENCES} layer added.
   *         Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null} or
   *         already carries the {@link Layers#SENTENCES} layer.
   */
  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    final CharSequence text = document.text();
    final List<Annotation<String>> sentences = new ArrayList<>();
    for (final Span span : detector.sentPosDetect(text)) {
      sentences.add(new Annotation<>(span, span.getCoveredText(text).toString()));
    }
    return document.with(Layers.SENTENCES, sentences);
  }

  /** {@inheritDoc} */
  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(Layers.SENTENCES);
  }
}
