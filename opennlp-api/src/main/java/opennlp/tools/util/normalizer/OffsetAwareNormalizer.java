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

/**
 * A {@link CharSequenceNormalizer} that can additionally report the {@link Alignment} from its
 * normalized output back to the input, so a span found in the normalized text maps to the exact
 * character offsets of the original.
 *
 * <p>Length-changing folds move offsets: collapsing a run of whitespace, folding a supplementary
 * dash to one ASCII hyphen, or stripping invisible controls all shift every later character. A rung
 * that performs such a fold over the cursor-based {@link CharClass} engine can record those edits
 * and expose them through {@link #normalizeAligned(CharSequence)}. A rung that delegates to
 * {@link java.text.Normalizer} (NFC/NFKC) or to a stemmer cannot report its edits, so it does not
 * implement this interface; that is a deliberate capability split rather than an oversight.</p>
 *
 * <p>{@code TextNormalizer.Builder.buildAligned()} composes a chain of these into a single
 * offset-aware pipeline whose {@link AlignedText} maps a match all the way back to the original
 * input. An interface-typed caller tests for the capability
 * ({@code normalizer instanceof OffsetAwareNormalizer}) instead of depending on a concrete rung,
 * the same plain {@code instanceof} pattern used by
 * {@code OffsetMappingNameFinder} (in the DL layer) rather than reflection.</p>
 */
public interface OffsetAwareNormalizer extends CharSequenceNormalizer {

  /**
   * Normalizes {@code text} and returns the result together with the {@link Alignment} back to the
   * input. The normalized text is identical to {@link #normalize(CharSequence)}: that is,
   * {@code normalizeAligned(text).normalized()} equals {@code normalize(text).toString()}.
   *
   * @param text The {@link CharSequence} to normalize.
   * @return The normalized text paired with its alignment to {@code text}.
   */
  AlignedText normalizeAligned(CharSequence text);
}
