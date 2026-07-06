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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.tokenize.uax29.WordTokenizer;
import opennlp.tools.util.Span;

/**
 * Builds {@link Term}s by segmenting text and applying a configured stack of normalization
 * {@link Dimension}s to each token. The analyzer is the configuration; each {@link Term} is the
 * layered result for one token, with the configured dimensions computed eagerly and any other
 * dimension computed lazily on first request.
 *
 * <p>Segmentation uses the Unicode {@linkplain WordTokenizer UAX&#160;#29 word tokenizer}, so the
 * input does not need to be pre-tokenized. The character-level dimensions ({@link Dimension#NFC}
 * through {@link Dimension#CONFUSABLE_FOLD}) have built-in defaults; {@link Dimension#STEM} and
 * {@link Dimension#LEMMA} are enabled by supplying a {@link Stemmer} or {@link Lemmatizer}.</p>
 *
 * <p>An instance is immutable and is thread-safe when its configured transforms are. The built-in
 * character normalizers are stateless, but the Snowball stemmers are not, so an analyzer configured
 * with a {@link Stemmer} (for example through {@code NormalizationProfile.matchingAnalyzer()}) should
 * not be shared across threads when {@link Dimension#STEM} is used. Build one with
 * {@link #builder()}.</p>
 */
public final class TermAnalyzer {

  private final List<Dimension> chain;
  private final Dimension finalDimension;
  private final EnumMap<Dimension, CharSequenceNormalizer> transforms;
  private final Stemmer stemmer;
  private final Lemmatizer lemmatizer;
  private final WordTokenizer tokenizer;

  private TermAnalyzer(Builder builder) {
    final List<Dimension> ordered = new ArrayList<>(builder.chain);
    Collections.sort(ordered); // pipeline order (enum declaration order)
    this.chain = List.copyOf(ordered);
    this.finalDimension = ordered.isEmpty() ? Dimension.ORIGINAL : ordered.get(ordered.size() - 1);
    // Only the per-analyzer overrides from the builder; the defaults live on Dimension itself.
    this.transforms = new EnumMap<>(builder.transforms);
    this.stemmer = builder.stemmer;
    this.lemmatizer = builder.lemmatizer;
    this.tokenizer = builder.tokenizer;
  }

  /**
   * {@return a new {@link Builder}} The builder starts with no dimensions enabled and the default
   * UAX&#160;#29 word tokenizer; enable dimensions and set a stemmer, lemmatizer, or tokenizer on it,
   * then call {@link Builder#build()}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Segments {@code text} with the UAX&#160;#29 word tokenizer and returns one {@link Term} per
   * word token, in order. The terms carry no part-of-speech tag, so {@link Dimension#LEMMA} cannot be
   * computed from this entry point: if a lemmatizer is configured, this method throws -- use
   * {@link #analyze(String[], String[])} when lemmas are needed.
   *
   * @param text The text to analyze. Must not be {@code null}.
   * @return The terms.
   * @throws IllegalArgumentException if {@code text} is {@code null}.
   * @throws IllegalStateException if {@link Dimension#LEMMA} is configured, because no
   *     part-of-speech tags are available from raw text.
   */
  public List<Term> analyze(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    if (chain.contains(Dimension.LEMMA)) {
      throw new IllegalStateException("Dimension LEMMA requires part-of-speech tags, which"
          + " analyze(CharSequence) cannot supply; use analyze(tokens, tags)");
    }
    final List<Span> spans = tokenizer.tokenizeSpans(text);
    final List<Term> terms = new ArrayList<>(spans.size());
    for (final Span span : spans) {
      terms.add(new Term(this, span.getCoveredText(text).toString(), span, null));
    }
    return terms;
  }

  /**
   * Returns one {@link Term} per supplied token, attaching the matching part-of-speech tag so that
   * {@link Dimension#LEMMA} can be computed. The terms have no source span.
   *
   * @param tokens The tokens. Must not be {@code null} or contain {@code null} elements.
   * @param tags   The part-of-speech tag for each token; must be the same length as {@code tokens}
   *               and must not be {@code null}. A {@code null} tag is only acceptable when
   *               {@link Dimension#LEMMA} is not computed for that token.
   * @return The terms.
   * @throws IllegalArgumentException if {@code tokens} or {@code tags} is {@code null}, if they
   *     differ in length, or if {@code tokens} contains a {@code null} element.
   */
  public List<Term> analyze(String[] tokens, String[] tags) {
    if (tokens == null) {
      throw new IllegalArgumentException("tokens must not be null");
    }
    if (tags == null) {
      throw new IllegalArgumentException("tags must not be null");
    }
    if (tokens.length != tags.length) {
      throw new IllegalArgumentException(
          "tokens and tags must be the same length, got " + tokens.length + " and " + tags.length);
    }
    final List<Term> terms = new ArrayList<>(tokens.length);
    for (int i = 0; i < tokens.length; i++) {
      if (tokens[i] == null) {
        throw new IllegalArgumentException("tokens[" + i + "] is null");
      }
      terms.add(new Term(this, tokens[i], null, tags[i]));
    }
    return terms;
  }

  /**
   * {@return the configured dimensions that are computed eagerly, in pipeline order} The list
   * never includes {@link Dimension#ORIGINAL}, which is always present.
   */
  public List<Dimension> dimensions() {
    return chain;
  }

  /**
   * {@return the last configured dimension in pipeline order, or {@link Dimension#ORIGINAL} when
   * none are configured} This is the layer {@link Term#normalized()} reports.
   */
  Dimension finalDimension() {
    return finalDimension;
  }

  /**
   * Applies one dimension's transform to a single token value.
   *
   * @param dimension The dimension whose transform to apply.
   * @param input     The token value to transform.
   * @param posTag    The token's part-of-speech tag; only read by {@link Dimension#LEMMA} and may
   *                  be {@code null} otherwise.
   * @return The transformed value; never {@code null}.
   * @throws IllegalStateException if a token-level dimension was requested without the engine (or
   *     tag) it needs: {@link Dimension#STEM} without a {@link Stemmer}, {@link Dimension#LEMMA}
   *     without a {@link Lemmatizer} or without a tag, or a lemmatizer that returns no lemma. Also
   *     thrown for a character-level dimension with neither a default nor a configured normalizer.
   */
  String apply(Dimension dimension, String input, String posTag) {
    switch (dimension) {
      case ORIGINAL:
        return input;
      case STEM:
        if (stemmer == null) {
          throw new IllegalStateException(
              "Dimension STEM requires a Stemmer; configure it with builder().stem(...)");
        }
        return stemmer.stem(input).toString();
      case LEMMA:
        if (lemmatizer == null) {
          throw new IllegalStateException(
              "Dimension LEMMA requires a Lemmatizer; configure it with builder().lemmatize(...)");
        }
        if (posTag == null) {
          throw new IllegalStateException("Dimension LEMMA requires a part-of-speech tag, but the"
              + " tag for token '" + input + "' was null; use analyze(tokens, tags) with a"
              + " non-null tag per token");
        }
        final String[] lemmas = lemmatizer.lemmatize(new String[] {input}, new String[] {posTag});
        if (lemmas == null || lemmas.length == 0 || lemmas[0] == null) {
          // A contract-violating Lemmatizer must fail loud here: a null cached under LEMMA would
          // read as "absent" in Term.at's lazy cache and recompute through normalized() forever,
          // surfacing as a StackOverflowError far from the cause.
          throw new IllegalStateException(
              "The Lemmatizer returned no lemma for token '" + input + "'");
        }
        return lemmas[0];
      default:
        // A builder override wins; otherwise the dimension's own default normalizer.
        final CharSequenceNormalizer normalizer = transforms.containsKey(dimension)
            ? transforms.get(dimension) : dimension.defaultNormalizer();
        if (normalizer == null) {
          throw new IllegalStateException("Dimension " + dimension + " has no default normalizer; "
              + "configure it with builder().transform(" + dimension + ", ...)");
        }
        return normalizer.normalize(input).toString();
    }
  }

  /** A builder for {@link TermAnalyzer}. */
  public static final class Builder {

    private final EnumSet<Dimension> chain = EnumSet.noneOf(Dimension.class);
    private final EnumMap<Dimension, CharSequenceNormalizer> transforms =
        new EnumMap<>(Dimension.class);
    private Stemmer stemmer;
    private Lemmatizer lemmatizer;
    private WordTokenizer tokenizer = new WordTokenizer();

    private Builder() {
    }

    /**
     * Enables {@link Dimension#NFC}.
     *
     * @return this builder
     */
    public Builder nfc() {
      chain.add(Dimension.NFC);
      return this;
    }

    /**
     * Enables {@link Dimension#NFKC}.
     *
     * @return this builder
     */
    public Builder nfkc() {
      chain.add(Dimension.NFKC);
      return this;
    }

    /**
     * Enables {@link Dimension#WHITESPACE}.
     *
     * @return this builder
     */
    public Builder whitespace() {
      chain.add(Dimension.WHITESPACE);
      return this;
    }

    /**
     * Enables {@link Dimension#WHITESPACE} with a specific normalizer, choosing the fold target and
     * behavior. For a custom class and target use a {@link CharClass} method reference, for example
     * {@code whitespace(CharClass.of(members, replacement)::collapse)}.
     *
     * @param normalizer The whitespace normalizer to use. Must not be {@code null}.
     * @return this builder
     * @throws IllegalArgumentException if {@code normalizer} is {@code null}.
     */
    public Builder whitespace(CharSequenceNormalizer normalizer) {
      return transform(Dimension.WHITESPACE, normalizer);
    }

    /**
     * Enables {@link Dimension#DASH}.
     *
     * @return this builder
     */
    public Builder dash() {
      chain.add(Dimension.DASH);
      return this;
    }

    /**
     * Enables {@link Dimension#DASH} with a specific normalizer (a custom dash set or target).
     *
     * @param normalizer The dash normalizer to use. Must not be {@code null}.
     * @return this builder
     * @throws IllegalArgumentException if {@code normalizer} is {@code null}.
     */
    public Builder dash(CharSequenceNormalizer normalizer) {
      return transform(Dimension.DASH, normalizer);
    }

    /**
     * Enables {@link Dimension#CASE_FOLD}.
     *
     * @return this builder
     */
    public Builder caseFold() {
      chain.add(Dimension.CASE_FOLD);
      return this;
    }

    /**
     * Enables {@link Dimension#CASE_FOLD} using the given locale's case rules (for example Turkish
     * dotted/dotless i), instead of the default {@link Locale#ROOT}.
     *
     * @param locale The locale whose case rules to apply. Must not be {@code null}.
     * @return this builder
     * @throws IllegalArgumentException if {@code locale} is {@code null}.
     */
    public Builder caseFold(Locale locale) {
      if (locale == null) {
        throw new IllegalArgumentException("locale must not be null");
      }
      return transform(Dimension.CASE_FOLD, CaseFoldCharSequenceNormalizer.getInstance(locale));
    }

    /**
     * Enables {@link Dimension#ACCENT_FOLD}.
     *
     * @return this builder
     */
    public Builder accentFold() {
      chain.add(Dimension.ACCENT_FOLD);
      return this;
    }

    /**
     * Enables {@link Dimension#ACCENT_FOLD} restricted to a specific set of scripts, instead of the
     * default Latin/Greek/Cyrillic.
     *
     * @param foldScripts       The scripts whose diacritics to fold. Must not be {@code null} or
     *                          contain {@code null} elements.
     * @param foldStrokeLetters Whether to also fold stroke letters such as o-slash and l-stroke.
     * @return this builder
     * @throws IllegalArgumentException if {@code foldScripts} is {@code null} or contains a
     *     {@code null} element.
     */
    public Builder accentFold(Set<Character.UnicodeScript> foldScripts, boolean foldStrokeLetters) {
      if (foldScripts == null) {
        throw new IllegalArgumentException("foldScripts must not be null");
      }
      return transform(Dimension.ACCENT_FOLD,
          new AccentFoldCharSequenceNormalizer(foldScripts, foldStrokeLetters));
    }

    /**
     * Enables {@link Dimension#CONFUSABLE_FOLD}.
     *
     * @return this builder
     */
    public Builder confusableFold() {
      chain.add(Dimension.CONFUSABLE_FOLD);
      return this;
    }

    /**
     * Enables a character-level dimension with a specific normalizer, overriding its default (for
     * example a locale-specific case fold for a language profile).
     *
     * @param dimension  The character-level dimension to enable. Must not be {@code null}.
     * @param normalizer The normalizer to use for it. Must not be {@code null}.
     * @return this builder
     * @throws IllegalArgumentException if {@code dimension} or {@code normalizer} is {@code null},
     *     or if {@code dimension} is {@link Dimension#ORIGINAL}, {@link Dimension#STEM}, or
     *     {@link Dimension#LEMMA}.
     */
    public Builder transform(Dimension dimension, CharSequenceNormalizer normalizer) {
      if (dimension == null) {
        throw new IllegalArgumentException("dimension must not be null");
      }
      if (normalizer == null) {
        throw new IllegalArgumentException("normalizer must not be null");
      }
      if (dimension == Dimension.ORIGINAL || dimension == Dimension.STEM
          || dimension == Dimension.LEMMA) {
        throw new IllegalArgumentException(
            "transform(...) only applies to character-level dimensions, not " + dimension);
      }
      transforms.put(dimension, normalizer);
      chain.add(dimension);
      return this;
    }

    /**
     * Enables {@link Dimension#STEM} through the given stemmer.
     *
     * @param value The stemmer. Must not be {@code null}.
     * @return this builder
     * @throws IllegalArgumentException if {@code value} is {@code null}.
     */
    public Builder stem(Stemmer value) {
      if (value == null) {
        throw new IllegalArgumentException("stemmer must not be null");
      }
      this.stemmer = value;
      chain.add(Dimension.STEM);
      return this;
    }

    /**
     * Enables {@link Dimension#LEMMA} through the given lemmatizer.
     *
     * @param value The lemmatizer. Must not be {@code null}.
     * @return this builder
     * @throws IllegalArgumentException if {@code value} is {@code null}.
     */
    public Builder lemmatize(Lemmatizer value) {
      if (value == null) {
        throw new IllegalArgumentException("lemmatizer must not be null");
      }
      this.lemmatizer = value;
      chain.add(Dimension.LEMMA);
      return this;
    }

    /**
     * Sets the tokenizer used by {@link TermAnalyzer#analyze(CharSequence)}.
     *
     * @param value The tokenizer. Must not be {@code null}.
     * @return this builder
     * @throws IllegalArgumentException if {@code value} is {@code null}.
     */
    public Builder tokenizer(WordTokenizer value) {
      if (value == null) {
        throw new IllegalArgumentException("tokenizer must not be null");
      }
      this.tokenizer = value;
      return this;
    }

    /**
     * Sets the maximum token length of the tokenizer used by
     * {@link TermAnalyzer#analyze(CharSequence)}. Convenience for
     * {@code tokenizer(new WordTokenizer(maxTokenLength))}.
     *
     * @param maxTokenLength The maximum number of characters in a token. Must be at least
     *                       {@code 1}.
     * @return this builder
     * @throws IllegalArgumentException if {@code maxTokenLength} is less than {@code 1}.
     */
    public Builder maxTokenLength(int maxTokenLength) {
      this.tokenizer = new WordTokenizer(maxTokenLength);
      return this;
    }

    /**
     * {@return a new {@link TermAnalyzer} with this configuration}
     */
    public TermAnalyzer build() {
      return new TermAnalyzer(this);
    }
  }
}
