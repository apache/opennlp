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
package opennlp.tools.tokenize.uax29;

import opennlp.tools.util.Span;

/**
 * A word token produced by {@link WordTokenizer}: the character {@link Span} into the source text
 * together with its {@link WordType}.
 *
 * @param span The character offsets of the token in the source text.
 * @param type The token category.
 */
public record WordToken(Span span, WordType type) {

  /**
   * Returns the covered text of this token.
   *
   * @param source The text this token was produced from.
   * @return The covered text.
   */
  public CharSequence text(CharSequence source) {
    return span.getCoveredText(source);
  }
}
