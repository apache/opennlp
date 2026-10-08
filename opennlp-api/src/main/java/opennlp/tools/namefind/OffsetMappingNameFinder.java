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
 * <p>{@link #find(String[])} may report spans in the coordinates of the normalized text, which
 * differ from the caller's input when normalization changes the length.
 * {@link #findInOriginal(String[])} reports the same spans in original-input coordinates.</p>
 *
 * @since 3.0.0
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
