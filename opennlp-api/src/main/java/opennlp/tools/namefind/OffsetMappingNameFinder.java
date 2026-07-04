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
package opennlp.tools.namefind;

import opennlp.tools.util.Span;

/**
 * A {@link TokenNameFinder} that can additionally report detected spans in the character coordinates
 * of the original input, mapping back through any text normalization applied before detection.
 *
 * <p>An implementation that normalizes input before detection (for example an ONNX model that folds
 * Unicode whitespace or dashes) returns spans from {@link #find(String[])} in the coordinates of the
 * normalized text, which no longer line up with the caller's input when a fold changes the length.
 * {@link #findInOriginal(String[])} maps those spans back to original-input coordinates. This is a
 * separate capability interface rather than a method on {@link TokenNameFinder} because the classic
 * contract reports token-index spans, for which an original-character mapping is not meaningful; an
 * interface-typed caller tests for the capability ({@code finder instanceof OffsetMappingNameFinder})
 * instead of depending on a concrete implementation.</p>
 */
public interface OffsetMappingNameFinder extends TokenNameFinder {

  /**
   * Finds names and returns their {@link Span spans} in the character coordinates of the original
   * input, regardless of any normalization applied before detection.
   *
   * @param tokens The tokens to search.
   * @return The detected spans, in original-input character coordinates.
   * @throws IllegalArgumentException Thrown if {@code tokens} is {@code null} or contains a
   *     {@code null} token.
   */
  Span[] findInOriginal(String[] tokens);
}
