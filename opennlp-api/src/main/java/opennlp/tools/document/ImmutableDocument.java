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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.util.Span;

/**
 * The default {@link Document} implementation: an unmodifiable map from layer key to an
 * unmodifiable annotation list. Adding a layer copies the map, not the layers, so
 * documents grown from a common ancestor share their layer lists.
 */
final class ImmutableDocument implements Document {

  private final CharSequence text;
  private final Map<LayerKey<?>, List<Annotation<?>>> layers;

  private ImmutableDocument(CharSequence text, Map<LayerKey<?>, List<Annotation<?>>> layers) {
    this.text = text;
    this.layers = layers;
  }

  /**
   * Creates a document without any layers.
   *
   * @param text The original document text. Must not be {@code null}.
   * @return An empty {@link ImmutableDocument}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  static ImmutableDocument empty(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    return new ImmutableDocument(text, Collections.emptyMap());
  }

  @Override
  public CharSequence text() {
    return text;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> List<Annotation<T>> get(LayerKey<T> layer) {
    if (layer == null) {
      throw new IllegalArgumentException("layer must not be null");
    }
    final List<Annotation<?>> annotations = layers.get(layer);
    if (annotations == null) {
      return List.of();
    }
    // safe: with(LayerKey, List) verified every value against the key's type on insertion
    return (List<Annotation<T>>) (List<?>) annotations;
  }

  @Override
  public Set<LayerKey<?>> layers() {
    return Collections.unmodifiableSet(layers.keySet());
  }

  @Override
  public <T> Document with(LayerKey<T> layer, List<Annotation<T>> annotations) {
    if (layer == null || annotations == null) {
      throw new IllegalArgumentException("layer and annotations must not be null");
    }
    if (layers.containsKey(layer)) {
      throw new IllegalArgumentException("layer is already present: " + layer);
    }
    for (final Annotation<T> annotation : annotations) {
      if (annotation == null) {
        throw new IllegalArgumentException("annotations must not contain null: " + layer);
      }
      if (!layer.type().isInstance(annotation.value())) {
        throw new IllegalArgumentException("value of type "
            + annotation.value().getClass().getName() + " does not match layer " + layer);
      }
      final Span span = annotation.span();
      if (span.getEnd() > text.length()) {
        throw new IllegalArgumentException("span " + span + " exceeds the text length "
            + text.length() + " in layer " + layer);
      }
    }
    final Map<LayerKey<?>, List<Annotation<?>>> grown = new LinkedHashMap<>(layers);
    grown.put(layer, List.copyOf(annotations));
    return new ImmutableDocument(text, Collections.unmodifiableMap(grown));
  }
}
