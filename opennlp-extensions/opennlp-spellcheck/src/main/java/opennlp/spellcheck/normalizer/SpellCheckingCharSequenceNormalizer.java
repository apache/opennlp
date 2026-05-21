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

package opennlp.spellcheck.normalizer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import opennlp.spellcheck.SpellChecker;
import opennlp.spellcheck.SuggestItem;
import opennlp.spellcheck.Verbosity;
import opennlp.spellcheck.dictionary.SymSpellModel;
import opennlp.tools.util.normalizer.AggregateCharSequenceNormalizer;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;

/**
 * A {@link CharSequenceNormalizer} that corrects spelling in text using a
 * {@link SpellChecker} (typically a SymSpell engine).
 *
 * <p>The normalizer works in one of two {@linkplain Mode modes}:</p>
 * <ul>
 *   <li>{@link Mode#PER_TOKEN PER_TOKEN} (default) &ndash; the input is split into
 *       whitespace-delimited tokens and each token is corrected independently with
 *       {@link SpellChecker#lookup}. The original whitespace runs between tokens are
 *       preserved verbatim, so the shape of the line is kept. Tokens the dictionary
 *       already contains (best suggestion at edit distance {@code 0}) are left
 *       untouched.</li>
 *   <li>{@link Mode#COMPOUND COMPOUND} &ndash; the whole input is passed to
 *       {@link SpellChecker#lookupCompound}, which additionally repairs wrongly
 *       inserted or omitted spaces (word splits and merges). This collapses runs of
 *       whitespace to single spaces, as the compound corrector re-tokenizes the
 *       input.</li>
 * </ul>
 *
 * <p>Several guards keep the corrector from "fixing" tokens that should be left as
 * they are (configurable through the {@link Builder}):</p>
 * <ul>
 *   <li>tokens shorter than {@code minTokenLength} are skipped;</li>
 *   <li>numeric tokens are skipped ({@code skipNumbers}, on by default);</li>
 *   <li>URL- and email-like tokens are skipped ({@code skipUrls}, on by default);</li>
 *   <li>a token whose lower-cased form is already in the dictionary is never
 *       changed (the engine returns it at edit distance {@code 0}).</li>
 * </ul>
 *
 * <p><b>Casing.</b> Dictionaries are normally lower-cased, so lookups are performed on
 * the lower-cased token, and the original casing pattern is re-applied to the
 * correction: an all-upper token yields an all-upper correction, a leading-capital
 * token yields a leading-capital correction, otherwise the suggestion's own casing is
 * used. When no correction applies, the original token (including its casing and any
 * surrounding punctuation) is emitted unchanged.</p>
 *
 * <p>This normalizer composes cleanly inside an
 * {@link AggregateCharSequenceNormalizer}; place it after noise-removing normalizers
 * (URL, emoji, shrink) so it sees clean tokens.</p>
 *
 * <p><b>Serialization.</b> {@link CharSequenceNormalizer} is {@link java.io.Serializable},
 * but the backing {@link SpellChecker} usually is not; it is therefore held in a
 * {@code transient} field and is {@code null} after Java deserialization. A deserialized
 * instance is inert until a checker is re-attached: obtain a working copy with the same
 * settings via {@link #withSpellChecker(SpellChecker)} (this matches how the engine is
 * rebuilt from a model rather than Java-serialized). Calling {@link #normalize} on an
 * instance with no checker throws {@link IllegalStateException}.</p>
 */
public class SpellCheckingCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 1L;

  /** The default minimum token length below which tokens are left untouched. */
  public static final int DEFAULT_MIN_TOKEN_LENGTH = 4;

  /** Matches tokens that are entirely digits, optionally with grouping/decimal marks. */
  private static final Pattern NUMBER_LIKE = Pattern.compile("[+-]?[\\d.,]*\\d[\\d.,]*%?");

  /** Matches URL- and email-like tokens that should never be spell-corrected. */
  private static final Pattern URL_LIKE = Pattern.compile(
      "(?:https?://|www\\.)\\S+"
          + "|[-+_.0-9A-Za-z]+@[-0-9A-Za-z]+\\.[-.0-9A-Za-z]+"
          + "|\\S+\\.(?:com|org|net|edu|gov|io)\\b\\S*");

  /** Leading non-letter/digit run kept verbatim around a token (e.g. opening quotes). */
  private static final Pattern LEADING_NON_WORD = Pattern.compile("^[^\\p{L}\\p{N}]+");

  /** Trailing non-letter/digit run kept verbatim around a token (e.g. punctuation). */
  private static final Pattern TRAILING_NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+$");

  /** The correction mode. */
  public enum Mode {
    /** Correct each whitespace-delimited token independently. */
    PER_TOKEN,
    /** Correct the whole input as a phrase, repairing space splits/merges. */
    COMPOUND
  }

  /** Held transiently: the engine is normally rebuilt from a model, not serialized. */
  private final transient SpellChecker spellChecker;
  private final Mode mode;
  private final int minTokenLength;
  private final int maxEditDistance;
  private final boolean skipNumbers;
  private final boolean skipUrls;

  /**
   * Creates a normalizer in {@link Mode#PER_TOKEN} mode with default guards from a
   * {@link SpellChecker}.
   *
   * @param spellChecker the engine used to correct tokens; must not be {@code null}
   */
  public SpellCheckingCharSequenceNormalizer(SpellChecker spellChecker) {
    this(builder(spellChecker));
  }

  /**
   * Creates a normalizer in {@link Mode#PER_TOKEN} mode with default guards from a
   * loaded {@link SymSpellModel} (uses the model's {@link SymSpellModel#getSymSpell()
   * engine}).
   *
   * @param model the loaded model whose engine is used; must not be {@code null}
   */
  public SpellCheckingCharSequenceNormalizer(SymSpellModel model) {
    this(Objects.requireNonNull(model, "model must not be null").getSymSpell());
  }

  private SpellCheckingCharSequenceNormalizer(Builder b) {
    this.spellChecker = Objects.requireNonNull(b.spellChecker, "spellChecker must not be null");
    this.mode = b.mode;
    this.minTokenLength = b.minTokenLength;
    // The engine throws if a query exceeds its configured maximum, so clamp to it; an
    // explicitly smaller requested distance is still honored.
    this.maxEditDistance = Math.min(b.maxEditDistance, this.spellChecker.maxEditDistance());
    this.skipNumbers = b.skipNumbers;
    this.skipUrls = b.skipUrls;
  }

  /**
   * @param spellChecker the engine to wrap; must not be {@code null}
   * @return a new {@link Builder} seeded with sensible defaults
   */
  public static Builder builder(SpellChecker spellChecker) {
    return new Builder(Objects.requireNonNull(spellChecker, "spellChecker must not be null"));
  }

  /**
   * @param model the loaded model whose engine to wrap; must not be {@code null}
   * @return a new {@link Builder} seeded with sensible defaults
   */
  public static Builder builder(SymSpellModel model) {
    return new Builder(Objects.requireNonNull(model, "model must not be null").getSymSpell());
  }

  /**
   * Returns a copy of this normalizer carrying the same settings but backed by the given
   * checker. This is the supported way to re-attach an engine to an instance restored by
   * Java deserialization (whose {@code transient} checker is {@code null}).
   *
   * @param checker the engine to attach; must not be {@code null}
   * @return a new, ready-to-use normalizer with this instance's settings
   */
  public SpellCheckingCharSequenceNormalizer withSpellChecker(SpellChecker checker) {
    return builder(checker)
        .mode(mode)
        .minTokenLength(minTokenLength)
        .maxEditDistance(maxEditDistance)
        .skipNumbers(skipNumbers)
        .skipUrls(skipUrls)
        .build();
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    if (spellChecker == null) {
      throw new IllegalStateException("no SpellChecker attached; this instance was likely "
          + "restored by Java deserialization. Re-attach one via withSpellChecker(...).");
    }
    if (text == null || text.isEmpty()) {
      return text;
    }
    final String input = text.toString();
    if (mode == Mode.COMPOUND) {
      return normalizeCompound(input);
    }
    return normalizePerToken(input);
  }

  private CharSequence normalizeCompound(String input) {
    if (input.isBlank()) {
      return input;
    }
    final List<SuggestItem> result = spellChecker.lookupCompound(input, maxEditDistance);
    if (result.isEmpty()) {
      return input;
    }
    final String corrected = result.get(0).term();
    return corrected.isEmpty() ? input : corrected;
  }

  private CharSequence normalizePerToken(String input) {
    final StringBuilder out = new StringBuilder(input.length());
    int i = 0;
    final int n = input.length();
    while (i < n) {
      // Copy a run of whitespace verbatim.
      if (Character.isWhitespace(input.charAt(i))) {
        final int start = i;
        while (i < n && Character.isWhitespace(input.charAt(i))) {
          i++;
        }
        out.append(input, start, i);
        continue;
      }
      // Take a non-whitespace token and correct it.
      final int start = i;
      while (i < n && !Character.isWhitespace(input.charAt(i))) {
        i++;
      }
      out.append(correctToken(input.substring(start, i)));
    }
    return out.toString();
  }

  /**
   * Corrects a single whitespace-free token, preserving surrounding punctuation and
   * the original casing pattern. Returns the token unchanged when no correction
   * applies.
   */
  private String correctToken(String token) {
    // Peel off leading/trailing punctuation so the core word is what we look up.
    final String prefix = match(LEADING_NON_WORD, token);
    final String suffix = token.length() > prefix.length()
        ? match(TRAILING_NON_WORD, token.substring(prefix.length())) : "";
    final String core = token.substring(prefix.length(), token.length() - suffix.length());

    if (!isCorrectable(core)) {
      return token;
    }

    final String lower = core.toLowerCase(Locale.ROOT);
    final List<SuggestItem> suggestions = spellChecker.lookup(lower, Verbosity.TOP, maxEditDistance);
    if (suggestions.isEmpty()) {
      return token;
    }
    final SuggestItem best = suggestions.get(0);
    // The dictionary already knows this word: leave the original casing alone.
    if (best.editDistance() == 0) {
      return token;
    }
    final String corrected = applyCasing(core, best.term());
    if (corrected.equals(core)) {
      return token;
    }
    return prefix + corrected + suffix;
  }

  /** @return {@code true} if {@code core} passes the configured length/number/URL guards. */
  private boolean isCorrectable(String core) {
    if (core.length() < minTokenLength) {
      return false;
    }
    if (skipUrls && URL_LIKE.matcher(core).matches()) {
      return false;
    }
    if (skipNumbers && NUMBER_LIKE.matcher(core).matches()) {
      return false;
    }
    // A token with no letters at all (pure symbols) cannot be a spelling error.
    boolean hasLetter = false;
    for (int k = 0; k < core.length(); k++) {
      if (Character.isLetter(core.charAt(k))) {
        hasLetter = true;
        break;
      }
    }
    return hasLetter;
  }

  /**
   * Re-applies the casing pattern of {@code original} to {@code corrected}: all-upper
   * stays all-upper, leading-capital stays leading-capital, otherwise the suggestion's
   * own casing (typically lower-case) is used.
   */
  private static String applyCasing(String original, String corrected) {
    if (corrected.isEmpty()) {
      return corrected;
    }
    if (isAllUpper(original)) {
      return corrected.toUpperCase(Locale.ROOT);
    }
    if (Character.isUpperCase(original.charAt(0))) {
      return Character.toUpperCase(corrected.charAt(0)) + corrected.substring(1);
    }
    return corrected;
  }

  private static boolean isAllUpper(String s) {
    boolean sawLetter = false;
    for (int k = 0; k < s.length(); k++) {
      final char c = s.charAt(k);
      if (Character.isLetter(c)) {
        sawLetter = true;
        if (!Character.isUpperCase(c)) {
          return false;
        }
      }
    }
    return sawLetter && s.length() > 1;
  }

  private static String match(Pattern pattern, String s) {
    final var m = pattern.matcher(s);
    return m.find() ? m.group() : "";
  }

  /** A mutable builder for {@link SpellCheckingCharSequenceNormalizer}. */
  public static final class Builder {

    private final SpellChecker spellChecker;
    private Mode mode = Mode.PER_TOKEN;
    private int minTokenLength = DEFAULT_MIN_TOKEN_LENGTH;
    private int maxEditDistance = 2;
    private boolean skipNumbers = true;
    private boolean skipUrls = true;

    private Builder(SpellChecker spellChecker) {
      this.spellChecker = spellChecker;
    }

    /**
     * @param value the correction mode; must not be {@code null}
     * @return this builder
     */
    public Builder mode(Mode value) {
      this.mode = Objects.requireNonNull(value, "mode must not be null");
      return this;
    }

    /**
     * @param value tokens shorter than this are left untouched; must be {@code >= 1}
     * @return this builder
     */
    public Builder minTokenLength(int value) {
      if (value < 1) {
        throw new IllegalArgumentException("minTokenLength must be >= 1: " + value);
      }
      this.minTokenLength = value;
      return this;
    }

    /**
     * @param value the maximum edit distance considered per token; must be {@code >= 0}
     * @return this builder
     */
    public Builder maxEditDistance(int value) {
      if (value < 0) {
        throw new IllegalArgumentException("maxEditDistance must be >= 0: " + value);
      }
      this.maxEditDistance = value;
      return this;
    }

    /**
     * @param value whether to skip purely numeric tokens (default {@code true})
     * @return this builder
     */
    public Builder skipNumbers(boolean value) {
      this.skipNumbers = value;
      return this;
    }

    /**
     * @param value whether to skip URL- and email-like tokens (default {@code true})
     * @return this builder
     */
    public Builder skipUrls(boolean value) {
      this.skipUrls = value;
      return this;
    }

    /** @return the configured normalizer. */
    public SpellCheckingCharSequenceNormalizer build() {
      return new SpellCheckingCharSequenceNormalizer(this);
    }
  }
}
