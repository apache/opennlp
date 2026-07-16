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
 * <p>Several guards, all configurable through the {@link Builder}, keep the corrector from
 * "fixing" tokens that should be left as they are: tokens shorter than {@code minTokenLength}
 * are skipped, numeric tokens are skipped when {@code skipNumbers} is set (on by default),
 * URL- and email-like tokens are skipped when {@code skipUrls} is set (on by default), and a
 * token whose lower-cased form is already in the dictionary is never changed.</p>
 *
 * <p><b>Casing.</b> Lookups are performed on the lower-cased token and the original casing
 * pattern is re-applied to the correction: an all-upper token yields an all-upper correction,
 * a leading-capital token yields a leading-capital correction, otherwise the suggestion's own
 * casing is used. When no correction applies, the original token is emitted unchanged.</p>
 *
 * <p>This normalizer composes cleanly inside an
 * {@link AggregateCharSequenceNormalizer}; place it after noise-removing normalizers
 * (URL, emoji, shrink) so it sees clean tokens.</p>
 *
 * <p><b>Serialization.</b> The settings fields are serialized, but the backing
 * {@link SpellChecker} is held in a {@code transient} field and is {@code null} after Java
 * deserialization; re-attach a checker with {@link #withSpellChecker(SpellChecker)} to obtain
 * a working copy with the same settings. Calling {@link #normalize} on an instance with no
 * checker throws {@link IllegalStateException}.</p>
 */
public class SpellCheckingCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 488960282194566688L;

  /** The default minimum token length below which tokens are left untouched. */
  public static final int DEFAULT_MIN_TOKEN_LENGTH = 4;

  /** Matches URL- and email-like tokens that should never be spell-corrected. */
  private static final Pattern URL_LIKE = Pattern.compile(
      "(?:https?://|www\\.)\\S+"
          + "|[-+_.0-9A-Za-z]+@[-0-9A-Za-z]+\\.[-.0-9A-Za-z]+"
          + "|\\S+\\.(?:com|org|net|edu|gov|io)\\b\\S*");

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
    this(builder(model));
  }

  private SpellCheckingCharSequenceNormalizer(Builder b) {
    if (b.spellChecker == null) {
      throw new IllegalArgumentException("spellChecker must not be null");
    }
    this.spellChecker = b.spellChecker;
    this.mode = b.mode;
    this.minTokenLength = b.minTokenLength;
    // Clamp to the engine's configured maximum; a smaller requested distance is still honored.
    this.maxEditDistance = Math.min(b.maxEditDistance, this.spellChecker.maxEditDistance());
    this.skipNumbers = b.skipNumbers;
    this.skipUrls = b.skipUrls;
  }

  /**
   * @param spellChecker the engine to wrap; must not be {@code null}
   * @return a new {@link Builder} seeded with sensible defaults
   */
  public static Builder builder(SpellChecker spellChecker) {
    if (spellChecker == null) {
      throw new IllegalArgumentException("spellChecker must not be null");
    }
    return new Builder(spellChecker);
  }

  /**
   * @param model the loaded model whose engine to wrap; must not be {@code null}
   * @return a new {@link Builder} seeded with sensible defaults
   */
  public static Builder builder(SymSpellModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    return new Builder(model.getSymSpell());
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

  /** {@inheritDoc} */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (spellChecker == null) {
      throw new IllegalStateException("no SpellChecker attached; this instance was likely "
          + "restored by Java deserialization. Re-attach one via withSpellChecker(...).");
    }
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    if (text.isEmpty()) {
      return text;
    }
    final String input = text.toString();
    if (mode == Mode.COMPOUND) {
      return normalizeCompound(input);
    }
    return normalizePerToken(input);
  }

  /**
   * Corrects the whole input as a phrase with {@link SpellChecker#lookupCompound}, repairing
   * wrongly inserted or omitted spaces. Returns the input unchanged when it is blank or the
   * engine offers no correction.
   *
   * @param input The text to correct; never null.
   * @return The corrected phrase, or the input itself when nothing changed.
   */
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

  /**
   * Corrects each whitespace-delimited token independently, copying the whitespace runs
   * between tokens verbatim so the shape of the line is kept.
   *
   * @param input The text to correct; never null.
   * @return The text with each token corrected.
   */
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
    final int coreStart = leadingNonWordLength(token);
    final int coreEnd = token.length() - trailingNonWordLength(token, coreStart);
    final String prefix = token.substring(0, coreStart);
    final String suffix = token.substring(coreEnd);
    final String core = token.substring(coreStart, coreEnd);

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
    if (skipNumbers && isNumberLike(core)) {
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

  /** {@return whether {@code s} has more than one character and every letter in it is upper-case} */
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

  /**
   * {@return the length of the leading run of non-letter, non-number code points of
   * {@code token}}
   *
   * @param token The token to scan; never null.
   */
  private int leadingNonWordLength(String token) {
    int i = 0;
    while (i < token.length()) {
      final int codePoint = token.codePointAt(i);
      if (isLetterOrNumber(codePoint)) {
        break;
      }
      i += Character.charCount(codePoint);
    }
    return i;
  }

  /**
   * {@return the length of the trailing run of non-letter, non-number code points of
   * {@code token}, looking only behind {@code from}}
   *
   * @param token The token to scan; never null.
   * @param from  The index the backward scan must not cross.
   */
  private int trailingNonWordLength(String token, int from) {
    int end = token.length();
    while (end > from) {
      final int codePoint = token.codePointBefore(end);
      if (isLetterOrNumber(codePoint)) {
        break;
      }
      end -= Character.charCount(codePoint);
    }
    return token.length() - end;
  }

  /**
   * {@return whether {@code codePoint} is a letter or a number} Numbers cover the decimal,
   * letter, and other number categories, not only the decimal digits.
   *
   * @param codePoint The code point to classify.
   */
  private boolean isLetterOrNumber(int codePoint) {
    if (Character.isLetter(codePoint)) {
      return true;
    }
    final int type = Character.getType(codePoint);
    return type == Character.DECIMAL_DIGIT_NUMBER
        || type == Character.LETTER_NUMBER
        || type == Character.OTHER_NUMBER;
  }

  /**
   * {@return whether {@code core} looks like a number: an optional sign, then digits with
   * optional grouping or decimal marks containing at least one ASCII digit, then an optional
   * trailing percent sign} Package private so the differential test can drive the
   * classification directly.
   *
   * @param core The token to classify; never null.
   */
  boolean isNumberLike(String core) {
    int start = 0;
    if (start < core.length() && (core.charAt(start) == '+' || core.charAt(start) == '-')) {
      start++;
    }
    int end = core.length();
    if (end > start && core.charAt(end - 1) == '%') {
      end--;
    }
    if (start >= end) {
      return false;
    }
    boolean sawDigit = false;
    for (int i = start; i < end; i++) {
      final char c = core.charAt(i);
      if (c >= '0' && c <= '9') {
        sawDigit = true;
      } else if (c != '.' && c != ',') {
        return false;
      }
    }
    return sawDigit;
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
      if (value == null) {
        throw new IllegalArgumentException("mode must not be null");
      }
      this.mode = value;
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
