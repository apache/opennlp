/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.wordnet;

import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;

import opennlp.tools.wordnet.LexicalKnowledgeBase;

/**
 * One lexicon in a WN-LMF lexical resource, with its identity, metadata, and independently
 * queryable knowledge base.
 *
 * <p>The {@link #metadata()} map contains every XML attribute on the {@code Lexicon} element
 * except {@code id}, {@code label}, {@code language}, and {@code version}. Keys are namespace-aware
 * {@link QName} values, so Dublin Core attributes do not collide with unqualified attributes.
 * The dependency list preserves the source order of WN-LMF {@code Requires} declarations. It is
 * descriptive metadata only; parsing does not resolve or load the referenced lexicons. The list
 * and map are immutable. Thread safety of the knowledge base depends on its implementation.</p>
 *
 * @param id            The WN-LMF lexicon id. Must not be {@code null} or empty.
 * @param label         The human-readable label. Must not be {@code null} or empty.
 * @param language      The BCP 47 language tag carried by the source. Must not be {@code null} or
 *                      empty.
 * @param version       The source's version string. Must not be {@code null} or empty.
 * @param metadata      The remaining Lexicon attributes. Must not be {@code null} and must not
 *                      contain null keys or values.
 * @param dependencies  The required lexicons in source order. Must not be {@code null} and must
 *                      not contain null elements.
 * @param knowledgeBase The independently queryable lexicon. Must not be {@code null}.
 * @since 3.0.0
 */
public record WnLmfLexicon(
    String id,
    String label,
    String language,
    String version,
    Map<QName, String> metadata,
    List<WnLmfDependency> dependencies,
    LexicalKnowledgeBase knowledgeBase) {

  /**
   * Creates a WN-LMF lexicon descriptor.
   *
   * @throws IllegalArgumentException Thrown if a component violates its documented constraint.
   */
  public WnLmfLexicon {
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("id must not be null or empty");
    }
    if (label == null || label.isEmpty()) {
      throw new IllegalArgumentException("label must not be null or empty");
    }
    if (language == null || language.isEmpty()) {
      throw new IllegalArgumentException("language must not be null or empty");
    }
    if (version == null || version.isEmpty()) {
      throw new IllegalArgumentException("version must not be null or empty");
    }
    if (metadata == null) {
      throw new IllegalArgumentException("metadata must not be null");
    }
    for (final Map.Entry<QName, String> entry : metadata.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        throw new IllegalArgumentException("metadata must not contain null keys or values");
      }
    }
    if (dependencies == null) {
      throw new IllegalArgumentException("dependencies must not be null");
    }
    for (final WnLmfDependency dependency : dependencies) {
      if (dependency == null) {
        throw new IllegalArgumentException("dependencies must not contain null");
      }
    }
    if (knowledgeBase == null) {
      throw new IllegalArgumentException("knowledgeBase must not be null");
    }
    metadata = Map.copyOf(metadata);
    dependencies = List.copyOf(dependencies);
  }
}
