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
 * A layer of the {@link Term} normalization stack, in increasing order of aggressiveness. A
 * {@link TermAnalyzer} applies a configured prefix of these to each token; the declaration order is
 * the canonical pipeline order, because the transforms do not commute (case folding then accent
 * folding differs from the reverse for Turkish dotted/dotless i and the German eszett).
 *
 * <p>{@link #ORIGINAL} is the source token and is always present. The character-level dimensions
 * have a default transform and can therefore be requested from any term. {@link #STEM} and
 * {@link #LEMMA} are token-level and require a {@link opennlp.tools.stemmer.Stemmer} or
 * {@link opennlp.tools.lemmatizer.Lemmatizer} to be configured on the analyzer; {@link #LEMMA} also
 * requires a part-of-speech tag.</p>
 */
public enum Dimension {

  /** The original token text, the canonical source of truth. */
  ORIGINAL,

  /** Unicode canonical composition (NFC); lossless under canonical equivalence. */
  NFC,

  /** Unicode compatibility composition (NFKC); lossy (for example superscripts to digits). */
  NFKC,

  /** Unicode whitespace folded to ASCII spaces. */
  WHITESPACE,

  /** Unicode dashes folded to the ASCII hyphen-minus. */
  DASH,

  /** Case folding; lossy and locale sensitive. */
  CASE_FOLD,

  /** Diacritic and accent folding; lossy, script gated, and language-wrong for some languages. */
  ACCENT_FOLD,

  /** Confusable (homoglyph) skeleton folding per UTS #39; lossy, for matching only. */
  CONFUSABLE_FOLD,

  /** Stemming through a configured {@link opennlp.tools.stemmer.Stemmer}. */
  STEM,

  /** Lemmatization through a configured {@link opennlp.tools.lemmatizer.Lemmatizer}. */
  LEMMA
}
