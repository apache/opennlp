/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.document;

import opennlp.tools.util.ParamChecks;

/**
 * The argument and duplicate layer checks shared by the
 * {@link Document#merge(Document, Document.DuplicateLayerPolicy) merge} implementations.
 */
final class DocumentMerges {

  private DocumentMerges() {
  }

  /**
   * Checks the arguments of a merge.
   *
   * @param document The document merged into.
   * @param other The document whose layers are added.
   * @param duplicateLayers How to treat a layer key present on both documents.
   * @throws IllegalArgumentException Thrown if {@code other} or {@code duplicateLayers} is
   *         {@code null}, or if the two documents carry different text content.
   */
  static void checkMergeable(Document document, Document other,
                             Document.DuplicateLayerPolicy duplicateLayers) {
    ParamChecks.requireNonNullArg(other, "other");
    ParamChecks.requireNonNullArg(duplicateLayers, "duplicateLayers");
    if (!document.text().toString().contentEquals(other.text())) {
      throw new IllegalArgumentException(
          "merge requires both documents to carry the same text");
    }
  }

  /**
   * Checks that a layer present on both documents of a merge can be kept as it is.
   *
   * @param base The document merged into, which carries {@code layer}.
   * @param layer The layer key present on both documents.
   * @param other The document whose layers are added, which carries {@code layer}.
   * @param duplicateLayers How to treat a layer key present on both documents.
   * @throws IllegalArgumentException Thrown if {@code duplicateLayers} is not
   *         {@link Document.DuplicateLayerPolicy#KEEP_EQUAL}, or if the two documents carry
   *         different contents for {@code layer}; the exception names the key.
   */
  static void checkDuplicateLayer(Document base, LayerKey<?> layer, Document other,
                                  Document.DuplicateLayerPolicy duplicateLayers) {
    if (duplicateLayers != Document.DuplicateLayerPolicy.KEEP_EQUAL) {
      throw new IllegalArgumentException("layer is already present: " + layer);
    }
    if (!base.get(layer).equals(other.get(layer))) {
      throw new IllegalArgumentException(
          "layer is present on both documents with differing contents: " + layer);
    }
  }
}
