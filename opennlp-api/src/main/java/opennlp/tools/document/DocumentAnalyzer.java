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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a fixed sequence of {@link DocumentAnnotator annotators} over a text, producing
 * one {@link Document} that carries every step's layers.
 *
 * <p>The pipeline is validated at build time: every annotator's required layers must be
 * provided by an earlier annotator, and no two annotators may provide the same layer, so
 * a misordered or conflicting pipeline fails when it is assembled rather than midway
 * through a document. The analyzer holds no per-call state; it is as thread-safe as the
 * annotators it is built from.</p>
 *
 * @since 3.0.0
 */
public final class DocumentAnalyzer {

  private final List<DocumentAnnotator> annotators;

  private DocumentAnalyzer(List<DocumentAnnotator> annotators) {
    this.annotators = annotators;
  }

  /**
   * @return A new {@link Builder}. Never {@code null}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Analyzes a text by running every annotator in order.
   *
   * @param text The original document text. Must not be {@code null}.
   * @return The annotated {@link Document}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public Document analyze(CharSequence text) {
    Document document = Document.of(text);
    for (final DocumentAnnotator annotator : annotators) {
      document = annotator.annotate(document);
    }
    return document;
  }

  /**
   * Assembles a {@link DocumentAnalyzer} from annotators in execution order.
   */
  public static final class Builder {

    private final List<DocumentAnnotator> annotators = new ArrayList<>();

    private Builder() {
    }

    /**
     * Appends an annotator to the pipeline.
     *
     * @param annotator The annotator to run after the ones already added. Must not be
     *                  {@code null}.
     * @return This {@link Builder}. Never {@code null}.
     * @throws IllegalArgumentException Thrown if {@code annotator} is {@code null}.
     */
    public Builder add(DocumentAnnotator annotator) {
      if (annotator == null) {
        throw new IllegalArgumentException("annotator must not be null");
      }
      annotators.add(annotator);
      return this;
    }

    /**
     * Validates the pipeline and builds the analyzer.
     *
     * @return A {@link DocumentAnalyzer}. Never {@code null}.
     * @throws IllegalArgumentException Thrown if the pipeline is empty, an annotator
     *         requires a layer no earlier annotator provides, or two annotators provide
     *         the same layer.
     */
    public DocumentAnalyzer build() {
      if (annotators.isEmpty()) {
        throw new IllegalArgumentException("a pipeline needs at least one annotator");
      }
      final Map<LayerKey<?>, Integer> providers = new HashMap<>();
      for (int position = 0; position < annotators.size(); position++) {
        final DocumentAnnotator annotator = annotators.get(position);
        for (final LayerKey<?> required : annotator.requires()) {
          if (!providers.containsKey(required)) {
            throw new IllegalArgumentException("annotator " + annotator
                + " requires layer " + required + ", which no earlier annotator provides");
          }
        }
        for (final LayerKey<?> provided : annotator.provides()) {
          final Integer earlier = providers.putIfAbsent(provided, position);
          if (earlier != null) {
            throw new IllegalArgumentException("annotators at positions " + earlier
                + " and " + position + " both provide layer " + provided);
          }
        }
      }
      return new DocumentAnalyzer(List.copyOf(annotators));
    }
  }
}
