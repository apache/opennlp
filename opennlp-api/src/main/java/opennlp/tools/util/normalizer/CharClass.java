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
import java.util.List;
import java.util.function.IntFunction;

import opennlp.tools.util.Span;

/**
 * A configurable class of Unicode code points and the cursor based operations over it.
 *
 * <p>A {@code CharClass} pairs a {@link CodePointSet} of member code points with a single
 * canonical ASCII {@code replacement} code point. Whitespace and dashes are the two built-in
 * presets ({@link #whitespace()}, {@link #dashes()}); any other class is one more configured
 * instance with no new engine code.</p>
 *
 * <p>Every operation is a single forward pass that reads one code point
 * ({@link Character#codePointAt(CharSequence, int)}), tests membership in O(1), acts, and advances
 * by {@link Character#charCount(int)}. There is no regular expression, no {@link java.util.regex}
 * allocation, and no reliance on {@link Character#isWhitespace(int)} or
 * {@link Character#isSpaceChar(int)}, all of which disagree with the Unicode standard.</p>
 *
 * <p>Instances are immutable and thread-safe.</p>
 */
public final class CharClass {

  private static final CharClass WHITESPACE =
      new CharClass(CodePointSet.of(UnicodeWhitespace.codePoints()), 0x0020);
  private static final CharClass DASHES =
      new CharClass(CodePointSet.of(UnicodeDash.defaultDashCodePoints()), UnicodeDash.HYPHEN_MINUS);

  private final CodePointSet members;
  private final int replacement;

  private CharClass(CodePointSet members, int replacement) {
    this.members = members;
    this.replacement = replacement;
  }

  /**
   * Creates a class from a member set and a replacement code point.
   *
   * @param members The member code points.
   * @param replacement The canonical code point used by {@link #normalize(CharSequence)} and
   *     {@link #collapse(CharSequence)}.
   * @return The class.
   * @throws IllegalArgumentException Thrown if {@code members} is {@code null} or
   *     {@code replacement} is not a valid code point.
   */
  public static CharClass of(CodePointSet members, int replacement) {
    requireNonNullArg(members, "members");
    requireValidCodePoint(replacement);
    return new CharClass(members, replacement);
  }

  /** {@return the whitespace preset: the Unicode {@code White_Space} set, replacement {@code U+0020}} */
  public static CharClass whitespace() {
    return WHITESPACE;
  }

  /**
   * {@return the dash preset: the Unicode {@code Dash} set excluding the mathematical minus signs,
   * replacement {@code U+002D}}
   */
  public static CharClass dashes() {
    return DASHES;
  }

  /**
   * Returns a copy of this class whose member set is extended with {@code extra} (for example,
   * user-defined code points loaded from {@link CodePointSet#fromFile}).
   *
   * @param extra The additional member code points.
   * @return A new {@code CharClass}; this instance is unchanged.
   * @throws IllegalArgumentException Thrown if {@code extra} is {@code null}.
   */
  public CharClass withAdditional(CodePointSet extra) {
    requireNonNullArg(extra, "extra");
    return new CharClass(members.union(extra), replacement);
  }

  /** {@return the member code points of this class} */
  public CodePointSet members() {
    return members;
  }

  /** {@return the canonical replacement code point} */
  public int replacement() {
    return replacement;
  }

  /**
   * Tests membership.
   *
   * @param codePoint The code point to test.
   * @return {@code true} if the code point is a member of this class.
   */
  public boolean contains(int codePoint) {
    return members.contains(codePoint);
  }

  /**
   * Splits text into the maximal runs of non-member code points, as character spans into the
   * original text. Runs of members are delimiters and produce no empty spans.
   *
   * @param text The text to split.
   * @return The token spans, in order.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public List<Span> splitSpans(CharSequence text) {
    requireNonNullArg(text, "text");
    final List<Span> spans = new ArrayList<>();
    final int length = text.length();
    int tokenStart = -1;
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (members.contains(codePoint)) {
        if (tokenStart >= 0) {
          spans.add(new Span(tokenStart, i));
          tokenStart = -1;
        }
      } else if (tokenStart < 0) {
        tokenStart = i;
      }
      i += Character.charCount(codePoint);
    }
    if (tokenStart >= 0) {
      spans.add(new Span(tokenStart, length));
    }
    return spans;
  }

  /**
   * Splits text into the maximal runs of non-member code points.
   *
   * @param text The text to split.
   * @return The tokens, in order, with no empty entries.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public String[] split(CharSequence text) {
    final List<Span> spans = splitSpans(text);
    final String[] tokens = new String[spans.size()];
    for (int i = 0; i < spans.size(); i++) {
      final Span span = spans.get(i);
      tokens[i] = text.subSequence(span.getStart(), span.getEnd()).toString();
    }
    return tokens;
  }

  /**
   * Replaces each member code point with the replacement, one for one.
   *
   * @param text The text to normalize.
   * @return The normalized text.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public String normalize(CharSequence text) {
    requireNonNullArg(text, "text");
    final StringBuilder out = new StringBuilder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      out.appendCodePoint(members.contains(codePoint) ? replacement : codePoint);
      i += Character.charCount(codePoint);
    }
    return out.toString();
  }

  /**
   * Collapses each maximal run of member code points to a single replacement.
   *
   * <p>Edges are not trimmed: a leading or trailing run becomes a single replacement, and a string
   * made up entirely of members collapses to one replacement (for whitespace, a single space, not
   * the empty string). Use {@link #trim(CharSequence)} to drop edge members, or collapse and then
   * trim to do both.</p>
   *
   * @param text The text to collapse.
   * @return The collapsed text.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public String collapse(CharSequence text) {
    requireNonNullArg(text, "text");
    final StringBuilder out = new StringBuilder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (members.contains(codePoint)) {
        out.appendCodePoint(replacement);
        i = skipRun(text, i);
      } else {
        out.appendCodePoint(codePoint);
        i += Character.charCount(codePoint);
      }
    }
    return out.toString();
  }

  /**
   * Collapses runs of members like {@link #collapse(CharSequence)}, but emits
   * {@code keepReplacement} instead of the usual replacement for any run that contains a code
   * point in {@code keep}. The whitespace "squish" that preserves a line break uses this with the
   * line-break code points as {@code keep} and {@code '\n'} as {@code keepReplacement}.
   *
   * @param text The text to collapse.
   * @param keep The member code points whose presence in a run preserves structure.
   * @param keepReplacement The replacement emitted for a run that contains a {@code keep} member.
   * @return The collapsed text.
   * @throws IllegalArgumentException Thrown if {@code text} or {@code keep} is {@code null}, or
   *     {@code keepReplacement} is not a valid code point.
   */
  public String collapsePreserving(CharSequence text, CodePointSet keep, int keepReplacement) {
    requireNonNullArg(text, "text");
    requireNonNullArg(keep, "keep");
    requireValidCodePoint(keepReplacement);
    final StringBuilder out = new StringBuilder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (members.contains(codePoint)) {
        boolean preserve = keep.contains(codePoint);
        int j = i + Character.charCount(codePoint);
        while (j < length) {
          final int next = Character.codePointAt(text, j);
          if (!members.contains(next)) {
            break;
          }
          preserve |= keep.contains(next);
          j += Character.charCount(next);
        }
        out.appendCodePoint(preserve ? keepReplacement : replacement);
        i = j;
      } else {
        out.appendCodePoint(codePoint);
        i += Character.charCount(codePoint);
      }
    }
    return out.toString();
  }

  /**
   * Removes leading and trailing member code points.
   *
   * @param text The text to trim.
   * @return The trimmed text.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public String trim(CharSequence text) {
    requireNonNullArg(text, "text");
    final int length = text.length();
    int start = 0;
    while (start < length) {
      final int codePoint = Character.codePointAt(text, start);
      if (!members.contains(codePoint)) {
        break;
      }
      start += Character.charCount(codePoint);
    }
    int end = length;
    while (end > start) {
      final int codePoint = Character.codePointBefore(text, end);
      if (!members.contains(codePoint)) {
        break;
      }
      end -= Character.charCount(codePoint);
    }
    return text.subSequence(start, end).toString();
  }

  /**
   * Removes every member code point.
   *
   * @param text The text to filter.
   * @return The text with all members removed.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public String removeAll(CharSequence text) {
    requireNonNullArg(text, "text");
    final StringBuilder out = new StringBuilder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (!members.contains(codePoint)) {
        out.appendCodePoint(codePoint);
      }
      i += Character.charCount(codePoint);
    }
    return out.toString();
  }

  /**
   * Like {@link #normalize(CharSequence)} but also produces the {@link Alignment} back to the
   * original text.
   *
   * @param text The text to normalize.
   * @return The normalized text and its alignment.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public AlignedText normalizeAligned(CharSequence text) {
    requireNonNullArg(text, "text");
    final StringBuilder out = new StringBuilder(text.length());
    final Alignment.Builder alignment = new Alignment.Builder();
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      final int charCount = Character.charCount(codePoint);
      if (members.contains(codePoint)) {
        out.appendCodePoint(replacement);
        alignment.replace(charCount, Character.charCount(replacement));
      } else {
        out.appendCodePoint(codePoint);
        alignment.equal(charCount);
      }
      i += charCount;
    }
    return new AlignedText(text, out.toString(), alignment.build(length));
  }

  /**
   * Like {@link #collapse(CharSequence)} but also produces the {@link Alignment} back to the
   * original text. Each collapsed run maps to the run's whole original extent.
   *
   * @param text The text to collapse.
   * @return The collapsed text and its alignment.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public AlignedText collapseAligned(CharSequence text) {
    requireNonNullArg(text, "text");
    final StringBuilder out = new StringBuilder(text.length());
    final Alignment.Builder alignment = new Alignment.Builder();
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (members.contains(codePoint)) {
        final int runEnd = skipRun(text, i);
        out.appendCodePoint(replacement);
        alignment.replace(runEnd - i, Character.charCount(replacement));
        i = runEnd;
      } else {
        final int charCount = Character.charCount(codePoint);
        out.appendCodePoint(codePoint);
        alignment.equal(charCount);
        i += charCount;
      }
    }
    return new AlignedText(text, out.toString(), alignment.build(length));
  }

  /**
   * Like {@link #collapsePreserving(CharSequence, CodePointSet, int)} but also produces the
   * {@link Alignment} back to the original text.
   *
   * @param text The text to collapse.
   * @param keep The member code points whose presence in a run preserves structure.
   * @param keepReplacement The replacement emitted for a run that contains a {@code keep} member.
   * @return The collapsed text and its alignment.
   * @throws IllegalArgumentException Thrown if {@code text} or {@code keep} is {@code null}, or
   *     {@code keepReplacement} is not a valid code point.
   */
  public AlignedText collapsePreservingAligned(CharSequence text, CodePointSet keep,
                                               int keepReplacement) {
    requireNonNullArg(text, "text");
    requireNonNullArg(keep, "keep");
    requireValidCodePoint(keepReplacement);
    final StringBuilder out = new StringBuilder(text.length());
    final Alignment.Builder alignment = new Alignment.Builder();
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (members.contains(codePoint)) {
        boolean preserve = keep.contains(codePoint);
        int j = i + Character.charCount(codePoint);
        while (j < length) {
          final int next = Character.codePointAt(text, j);
          if (!members.contains(next)) {
            break;
          }
          preserve |= keep.contains(next);
          j += Character.charCount(next);
        }
        final int emitted = preserve ? keepReplacement : replacement;
        out.appendCodePoint(emitted);
        alignment.replace(j - i, Character.charCount(emitted));
        i = j;
      } else {
        final int charCount = Character.charCount(codePoint);
        out.appendCodePoint(codePoint);
        alignment.equal(charCount);
        i += charCount;
      }
    }
    return new AlignedText(text, out.toString(), alignment.build(length));
  }

  /**
   * Like {@link #trim(CharSequence)} but also produces the {@link Alignment} back to the original
   * text. The trimmed leading and trailing members appear as deletions, so a span never reports
   * through them.
   *
   * @param text The text to trim.
   * @return The trimmed text and its alignment.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public AlignedText trimAligned(CharSequence text) {
    requireNonNullArg(text, "text");
    final int length = text.length();
    int start = 0;
    while (start < length) {
      final int codePoint = Character.codePointAt(text, start);
      if (!members.contains(codePoint)) {
        break;
      }
      start += Character.charCount(codePoint);
    }
    int end = length;
    while (end > start) {
      final int codePoint = Character.codePointBefore(text, end);
      if (!members.contains(codePoint)) {
        break;
      }
      end -= Character.charCount(codePoint);
    }
    final Alignment.Builder alignment = new Alignment.Builder();
    if (start > 0) {
      alignment.replace(start, 0);
    }
    alignment.equal(end - start);
    if (end < length) {
      alignment.replace(length - end, 0);
    }
    return new AlignedText(text, text.subSequence(start, end).toString(), alignment.build(length));
  }

  /**
   * Like {@link #removeAll(CharSequence)} but also produces the {@link Alignment} back to the
   * original text. Every removed member appears as a deletion, so a span never reports through one.
   *
   * @param text The text to filter.
   * @return The filtered text and its alignment.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public AlignedText removeAllAligned(CharSequence text) {
    requireNonNullArg(text, "text");
    final StringBuilder out = new StringBuilder(text.length());
    final Alignment.Builder alignment = new Alignment.Builder();
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      final int charCount = Character.charCount(codePoint);
      if (members.contains(codePoint)) {
        alignment.replace(charCount, 0);
      } else {
        out.appendCodePoint(codePoint);
        alignment.equal(charCount);
      }
      i += charCount;
    }
    return new AlignedText(text, out.toString(), alignment.build(length));
  }

  /**
   * Applies a per-code-point substitution: each code point for which {@code substitution} returns a
   * non-null string is replaced by that string, and the rest are copied through. This is the shared,
   * offset-changing cursor pass behind the expanding folds (ellipsis, German umlaut, digit), so each
   * of them supplies only a mapper rather than re-implementing the loop. No regular expression.
   *
   * @param text         The text to transform.
   * @param substitution The replacement for a code point, or {@code null} to copy it through.
   * @return The transformed text.
   * @throws IllegalArgumentException Thrown if {@code text} or {@code substitution} is {@code null}.
   */
  public static String substitute(CharSequence text, IntFunction<String> substitution) {
    requireNonNullArg(text, "text");
    requireNonNullArg(substitution, "substitution");
    final StringBuilder out = new StringBuilder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      final String replacement = substitution.apply(codePoint);
      if (replacement != null) {
        out.append(replacement);
      } else {
        out.appendCodePoint(codePoint);
      }
      i += Character.charCount(codePoint);
    }
    return out.toString();
  }

  /**
   * Like {@link #substitute(CharSequence, IntFunction)} but also produces the {@link Alignment} back
   * to the original text. Each replaced code point maps to its replacement string as one block.
   *
   * @param text         The text to transform.
   * @param substitution The replacement for a code point, or {@code null} to copy it through.
   * @return The transformed text and its alignment.
   * @throws IllegalArgumentException Thrown if {@code text} or {@code substitution} is {@code null}.
   */
  public static AlignedText substituteAligned(CharSequence text, IntFunction<String> substitution) {
    requireNonNullArg(text, "text");
    requireNonNullArg(substitution, "substitution");
    final StringBuilder out = new StringBuilder(text.length());
    final Alignment.Builder alignment = new Alignment.Builder();
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      final int charCount = Character.charCount(codePoint);
      final String replacement = substitution.apply(codePoint);
      if (replacement != null) {
        out.append(replacement);
        alignment.replace(charCount, replacement.length());
      } else {
        out.appendCodePoint(codePoint);
        alignment.equal(charCount);
      }
      i += charCount;
    }
    return new AlignedText(text, out.toString(), alignment.build(length));
  }

  // Returns the offset just past the maximal run of members starting at runStart.
  private int skipRun(CharSequence text, int runStart) {
    final int length = text.length();
    int i = runStart;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (!members.contains(codePoint)) {
        break;
      }
      i += Character.charCount(codePoint);
    }
    return i;
  }

  private static void requireValidCodePoint(int codePoint) {
    if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
      throw new IllegalArgumentException("Not a Unicode code point: " + codePoint);
    }
  }

  // Null parameters report IllegalArgumentException rather than requireNonNull's
  // NullPointerException, so an invalid parameter and an invalid code point surface through the
  // same exception type.
  private static <T> T requireNonNullArg(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException("The " + name + " must not be null.");
    }
    return value;
  }
}
