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

import opennlp.tools.util.Span;

/**
 * One annotation of a {@link Document}: a typed value anchored to a {@link Span} of the
 * document's original text, or a span-less value under a
 * {@link LayerKey.Scope#DOCUMENT document-scoped} key.
 *
 * <p>The span always refers to the text the document was created with, never to a
 * normalized or otherwise derived form, so any annotation can be highlighted in what the
 * caller supplied. Whether a span is present is decided by the layer key's scope, not
 * per annotation: the container rejects a span-less annotation under a positional key
 * and a spanned annotation under a document-scoped key. Annotations that need to
 * reference other annotations, for example a dependency arc naming its head token, do
 * so by the index of the target annotation within its layer, never by object
 * identity.</p>
 *
 * @param span The location of the annotation in the original text, or {@code null} for
 *             a value under a document-scoped key.
 * @param value The annotation value. Must not be {@code null}.
 * @param <T> The type of the annotation value.
 *
 * @since 3.0.0
 */
public record Annotation<T>(Span span, T value) {

  /**
   * Validates the annotation.
   *
   * @throws IllegalArgumentException Thrown if {@code value} is {@code null}.
   */
  public Annotation {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
  }

  /**
   * Creates a span-less annotation for a {@link LayerKey.Scope#DOCUMENT
   * document-scoped} layer.
   *
   * @param value The annotation value. Must not be {@code null}.
   * @param <T> The type of the annotation value.
   * @return An {@link Annotation} without a span. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code value} is {@code null}.
   */
  public static <T> Annotation<T> of(T value) {
    return new Annotation<>(null, value);
  }
}
