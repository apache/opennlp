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

import java.util.Set;

/**
 * A pipeline step that reads layers from a {@link Document} and returns a new document
 * with its own layers added.
 *
 * <p>An annotator declares the layers it {@link #requires()} and {@link #provides()}, so
 * a {@link DocumentAnalyzer} can validate a pipeline before running it. Annotators are
 * usually thin adapters over an existing analysis component and should hold no per-call
 * state, so one instance can serve concurrent pipelines when the wrapped component
 * allows it.</p>
 *
 * @since 3.0.0
 */
public interface DocumentAnnotator {

  /**
   * Annotates a document.
   *
   * @param document The document to annotate. Must not be {@code null} and must contain
   *                 every layer named by {@link #requires()}.
   * @return A new {@link Document} carrying the layers named by {@link #provides()} in
   *         addition to the input layers. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null} or lacks
   *         a required layer.
   */
  Document annotate(Document document);

  /**
   * @return The keys of the layers this annotator reads. Never {@code null}.
   */
  default Set<LayerKey<?>> requires() {
    return Set.of();
  }

  /**
   * @return The keys of the layers this annotator adds. Never {@code null}.
   */
  Set<LayerKey<?>> provides();
}
