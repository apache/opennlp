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
 * An offset-anchored annotation container: the original text of one document plus any
 * number of typed annotation layers over it.
 *
 * <p>A layer is a list of {@link Annotation annotations} identified by a
 * {@link LayerKey}. The container itself knows nothing about specific layers; every
 * analysis capability contributes its results as one more layer without any change to
 * this interface, which is what keeps new capabilities additive. All spans refer to
 * {@link #text()} as supplied, never to a derived form. A
 * {@link LayerKey.Scope#DOCUMENT document-scoped} layer carries whole-document values
 * without spans, for example a language id.</p>
 *
 * <p>A document is never modified in place: {@link #with(LayerKey, List)} leaves its
 * receiver untouched and returns a new document. Thread safety is implementation
 * specific.</p>
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
   * Creates an empty {@link Document} over a text. The returned document is immutable
   * and safe to share between threads: it captures the text's content at construction,
   * so later changes to a mutable {@code CharSequence} do not reach the document.
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

  /**
   * How {@link #merge(Document, DuplicateLayerPolicy)} treats a layer key that is
   * present on both documents.
   */
  enum DuplicateLayerPolicy {

    /** Reject any layer key present on both documents. */
    REJECT,

    /**
     * Keep one copy of a layer key present on both documents when the two layers are
     * structurally equal, for example when two parallel branches ran the same
     * tokenizer. Layers whose contents differ are rejected as with {@link #REJECT}.
     */
    KEEP_EQUAL
  }

  /**
   * Returns a new document combining this document's layers with another document's
   * layers over the same text, joining documents grown independently, for example by
   * pipelines that ran in parallel.
   *
   * @param other The document whose layers are added on top of this document's layers.
   *              Must not be {@code null}, must carry the same text content, and must
   *              not provide a layer this document already has.
   * @return A new {@link Document} carrying the layers of both documents. Never
   *         {@code null}; both source documents are left untouched.
   * @throws IllegalArgumentException Thrown if {@code other} is {@code null}, if its
   *         text content differs, or if a layer key is present on both documents; the
   *         exception names the offending key.
   */
  default Document merge(Document other) {
    return merge(other, DuplicateLayerPolicy.REJECT);
  }

  /**
   * Returns a new document combining this document's layers with another document's
   * layers over the same text, resolving duplicate layer keys with
   * {@code duplicateLayers}.
   *
   * @param other The document whose layers are added on top of this document's layers.
   *              Must not be {@code null} and must carry the same text content.
   * @param duplicateLayers How to treat a layer key present on both documents. Must
   *                        not be {@code null}.
   * @return A new {@link Document} carrying the layers of both documents. Never
   *         {@code null}; both source documents are left untouched.
   * @throws IllegalArgumentException Thrown if either argument is {@code null}, if the
   *         text content differs, or if a layer key is present on both documents and
   *         the policy does not keep it; the exception names the offending key.
   */
  default Document merge(Document other, DuplicateLayerPolicy duplicateLayers) {
    if (other == null) {
      throw new IllegalArgumentException("other must not be null");
    }
    if (duplicateLayers == null) {
      throw new IllegalArgumentException("duplicateLayers must not be null");
    }
    if (!text().toString().contentEquals(other.text())) {
      throw new IllegalArgumentException(
          "merge requires both documents to carry the same text");
    }
    Document merged = this;
    for (final LayerKey<?> layer : other.layers()) {
      if (duplicateLayers == DuplicateLayerPolicy.KEEP_EQUAL
          && merged.layers().contains(layer)
          && layersEqual(merged, layer, other)) {
        continue;
      }
      merged = addLayer(merged, layer, other);
    }
    return merged;
  }

  /**
   * @return Whether the two documents carry structurally equal contents for the layer.
   */
  private static <T> boolean layersEqual(Document first, LayerKey<T> layer, Document second) {
    return first.get(layer).equals(second.get(layer));
  }

  /**
   * Adds one layer of {@code from} to {@code base} through {@link #with(LayerKey, List)},
   * capturing the key's value type.
   */
  private static <T> Document addLayer(Document base, LayerKey<T> layer, Document from) {
    return base.with(layer, from.get(layer));
  }
}
