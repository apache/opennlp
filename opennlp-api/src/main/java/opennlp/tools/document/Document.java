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

import java.util.List;
import java.util.Set;

/**
 * An immutable, offset-anchored annotation container: the original text of one document
 * plus any number of typed annotation layers over it.
 *
 * <p>A layer is a list of {@link Annotation annotations} identified by a
 * {@link LayerKey}. The container itself knows nothing about specific layers; every
 * analysis capability contributes its results as one more layer without any change to
 * this interface, which is what keeps new capabilities additive. All spans refer to
 * {@link #text()} as supplied, never to a derived form. A
 * {@link LayerKey.Scope#DOCUMENT document-scoped} layer carries whole-document values
 * without spans, for example a language id.</p>
 *
 * <p>Documents are immutable: {@link #with(LayerKey, List)} returns a new document that
 * shares the unchanged layers. That immutability makes instances safe to share between threads.</p>
 *
 * <p>Three invariants make index-based references sound. A layer preserves its
 * insertion order, and the container never sorts or reorders it. A layer is immutable
 * once added: the returned lists reject modification and are detached from the
 * caller's input list. Providing a layer that already exists is rejected loudly: the
 * add is once-only, and the exception names the offending key. An annotation that
 * references another annotation by its index within a layer, for example a dependency
 * arc naming its head token, therefore stays valid for the lifetime of the
 * document.</p>
 *
 * @since 3.0.0
 */
public interface Document {

  /**
   * Creates an empty {@link Document} over a text.
   *
   * @param text The original document text. Must not be {@code null}.
   * @return A {@link Document} without any layers. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  static Document of(CharSequence text) {
    return ImmutableDocument.empty(text);
  }

  /**
   * @return The original text of the document. Never {@code null}.
   */
  CharSequence text();

  /**
   * Retrieves the annotations of one layer.
   *
   * @param layer The layer to read. Must not be {@code null}.
   * @param <T> The type of the layer's annotation values.
   * @return The layer's annotations in their layer order, or an empty list when the
   *         layer is absent. Never {@code null}; the list is unmodifiable.
   * @throws IllegalArgumentException Thrown if {@code layer} is {@code null}.
   */
  <T> List<Annotation<T>> get(LayerKey<T> layer);

  /**
   * @return The keys of all layers present on the document. Never {@code null}; the set
   *         is unmodifiable.
   */
  Set<LayerKey<?>> layers();

  /**
   * Returns a new document with one layer added.
   *
   * @param layer The key of the layer to add. Must not be {@code null} and must not
   *              already be present.
   * @param annotations The annotations of the layer. Must not be {@code null}, must not
   *                    contain {@code null}, and every value must be assignable to the
   *                    layer's type. Under a positional key every annotation must carry
   *                    a span within the text bounds; under a document-scoped key no
   *                    annotation may carry a span.
   * @param <T> The type of the layer's annotation values.
   * @return A new {@link Document} sharing this document's text and existing layers.
   *         Never {@code null}.
   * @throws IllegalArgumentException Thrown if any of the above constraints is violated.
   */
  <T> Document with(LayerKey<T> layer, List<Annotation<T>> annotations);
}
