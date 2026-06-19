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
package opennlp.tools.util.normalizer;

import opennlp.tools.util.Span;

/**
 * One analyzed token: its character span in the source text, the original token text, and the
 * normalized form used for matching or indexing.
 *
 * <p>The span ties the normalized term back to the original text, so a search hit on
 * {@link #normalized()} can be highlighted against the source using {@link #span()} even though
 * the normalized form may differ in length (for example after diacritic folding).</p>
 *
 * @param span The character span of the token in the source text.
 * @param original The original token text.
 * @param normalized The normalized token text (the match/index form).
 */
public record AnalyzedToken(Span span, String original, String normalized) {
}
