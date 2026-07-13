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
import opennlp.tools.util.normalizer.CodePoints.At;

/**
 * A configurable class of Unicode code points and the cursor based operations over it.
 *
 * <p>A {@code CharClass} pairs a {@link CodePointSet} of member code points with a single
 * canonical ASCII {@code replacement} code point. Whitespace and dashes are the two built-in
 * presets ({@link #whitespace()}, {@link #dashes()}); any other class is one more configured
 * instance with no new engine code.</p>
 *
 * <p>Every operation is a single forward cursor pass over the text: no regular expression and no
 * per-call allocation beyond the result.</p>
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
      final At cp = CodePoints.at(text, i);
      if (members.contains(cp.codePoint())) {
        if (tokenStart >= 0) {
          spans.add(new Span(tokenStart, i));
          tokenStart = -1;
        }
      } else if (tokenStart < 0) {
        tokenStart = i;
      }
      i = cp.nextIndex(i);
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
   * <p>When no code point of {@code text} is a member, the text is returned unchanged (as its
   * {@link CharSequence#toString() string form}) without copying.</p>
   *
   * @param text The text to normalize.
   * @return The normalized text.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public String normalize(CharSequence text) {
    requireNonNullArg(text, "text");
    final int length = text.length();
    final int first = firstMember(text);
    if (first == length) {
      return text.toString();
    }
    final StringBuilder out = new StringBuilder(length).append(text, 0, first);
    int i = first;
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
   * <p>When no code point of {@code text} is a member, the text is returned unchanged (as its
   * {@link CharSequence#toString() string form}) without copying.</p>
   *
   * @param text The text to collapse.
   * @return The collapsed text.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public String collapse(CharSequence text) {
    requireNonNullArg(text, "text");
    final int length = text.length();
    final int first = firstMember(text);
    if (first == length) {
      return text.toString();
    }
    final StringBuilder out = new StringBuilder(length).append(text, 0, first);
    int i = first;
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
      final At cp = CodePoints.at(text, i);
      if (members.contains(cp.codePoint())) {
        boolean preserve = keep.contains(cp.codePoint());
        int j = cp.nextIndex(i);
        while (j < length) {
          final At next = CodePoints.at(text, j);
          if (!members.contains(next.codePoint())) {
            break;
          }
          preserve |= keep.contains(next.codePoint());
          j = next.nextIndex(j);
        }
        out.appendCodePoint(preserve ? keepReplacement : replacement);
        i = j;
      } else {
        out.appendCodePoint(cp.codePoint());
        i = cp.nextIndex(i);
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
      final At cp = CodePoints.at(text, start);
      if (!members.contains(cp.codePoint())) {
        break;
      }
      start = cp.nextIndex(start);
    }
    int end = length;
    while (end > start) {
      final At cp = CodePoints.before(text, end);
      if (!members.contains(cp.codePoint())) {
        break;
      }
      end = cp.previousIndex(end);
    }
    return text.subSequence(start, end).toString();
  }

  /**
   * Removes every member code point.
   *
   * <p>When no code point of {@code text} is a member, the text is returned unchanged (as its
   * {@link CharSequence#toString() string form}) without copying.</p>
   *
   * @param text The text to filter.
   * @return The text with all members removed.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  public String removeAll(CharSequence text) {
    requireNonNullArg(text, "text");
    final int length = text.length();
    final int first = firstMember(text);
    if (first == length) {
      return text.toString();
    }
    final StringBuilder out = new StringBuilder(length).append(text, 0, first);
    int i = first;
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
    final Alignment.Builder alignment = new Alignment.Builder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final At cp = CodePoints.at(text, i);
      if (members.contains(cp.codePoint())) {
        out.appendCodePoint(replacement);
        alignment.replace(cp.charCount(), Character.charCount(replacement));
      } else {
        out.appendCodePoint(cp.codePoint());
        alignment.equal(cp.charCount());
      }
      i = cp.nextIndex(i);
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
    final Alignment.Builder alignment = new Alignment.Builder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final At cp = CodePoints.at(text, i);
      if (members.contains(cp.codePoint())) {
        final int runEnd = skipRun(text, i);
        out.appendCodePoint(replacement);
        alignment.replace(runEnd - i, Character.charCount(replacement));
        i = runEnd;
      } else {
        out.appendCodePoint(cp.codePoint());
        alignment.equal(cp.charCount());
        i = cp.nextIndex(i);
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
    final Alignment.Builder alignment = new Alignment.Builder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final At cp = CodePoints.at(text, i);
      if (members.contains(cp.codePoint())) {
        boolean preserve = keep.contains(cp.codePoint());
        int j = cp.nextIndex(i);
        while (j < length) {
          final At next = CodePoints.at(text, j);
          if (!members.contains(next.codePoint())) {
            break;
          }
          preserve |= keep.contains(next.codePoint());
          j = next.nextIndex(j);
        }
        final int emitted = preserve ? keepReplacement : replacement;
        out.appendCodePoint(emitted);
        alignment.replace(j - i, Character.charCount(emitted));
        i = j;
      } else {
        out.appendCodePoint(cp.codePoint());
        alignment.equal(cp.charCount());
        i = cp.nextIndex(i);
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
      final At cp = CodePoints.at(text, start);
      if (!members.contains(cp.codePoint())) {
        break;
      }
      start = cp.nextIndex(start);
    }
    int end = length;
    while (end > start) {
      final At cp = CodePoints.before(text, end);
      if (!members.contains(cp.codePoint())) {
        break;
      }
      end = cp.previousIndex(end);
    }
    final Alignment.Builder alignment = new Alignment.Builder(text.length());
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
    final Alignment.Builder alignment = new Alignment.Builder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final At cp = CodePoints.at(text, i);
      if (members.contains(cp.codePoint())) {
        alignment.replace(cp.charCount(), 0);
      } else {
        out.appendCodePoint(cp.codePoint());
        alignment.equal(cp.charCount());
      }
      i = cp.nextIndex(i);
    }
    return new AlignedText(text, out.toString(), alignment.build(length));
  }

  /**
   * Applies a per-code-point substitution: each code point for which {@code substitution} returns a
   * non-null string is replaced by that string, and the rest are copied through. This is the shared
   * cursor pass behind the expanding folds, with no regular expression.
   *
   * <p>When {@code substitution} returns {@code null} for every code point of {@code text}, the
   * text is returned unchanged (as its {@link CharSequence#toString() string form}) without
   * copying. The mapper is still applied exactly once per code point.</p>
   *
   * @param text         The text to transform.
   * @param substitution The replacement for a code point, or {@code null} to copy it through.
   * @return The transformed text.
   * @throws IllegalArgumentException Thrown if {@code text} or {@code substitution} is {@code null}.
   */
  public static String substitute(CharSequence text, IntFunction<String> substitution) {
    requireNonNullArg(text, "text");
    requireNonNullArg(substitution, "substitution");
    final int length = text.length();
    String firstReplacement = null;
    int first = 0;
    int firstEnd = 0;
    while (first < length) {
      final At cp = CodePoints.at(text, first);
      firstReplacement = substitution.apply(cp.codePoint());
      if (firstReplacement != null) {
        firstEnd = cp.nextIndex(first);
        break;
      }
      first = cp.nextIndex(first);
    }
    if (firstReplacement == null) {
      return text.toString();
    }
    final StringBuilder out =
        new StringBuilder(length).append(text, 0, first).append(firstReplacement);
    int i = firstEnd;
    while (i < length) {
      final At cp = CodePoints.at(text, i);
      final String replacement = substitution.apply(cp.codePoint());
      if (replacement != null) {
        out.append(replacement);
      } else {
        out.appendCodePoint(cp.codePoint());
      }
      i = cp.nextIndex(i);
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
    final Alignment.Builder alignment = new Alignment.Builder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final At cp = CodePoints.at(text, i);
      final String replacement = substitution.apply(cp.codePoint());
      if (replacement != null) {
        out.append(replacement);
        alignment.replace(cp.charCount(), replacement.length());
      } else {
        out.appendCodePoint(cp.codePoint());
        alignment.equal(cp.charCount());
      }
      i = cp.nextIndex(i);
    }
    return new AlignedText(text, out.toString(), alignment.build(length));
  }

  /**
   * Finds the index of the first member code point, so callers can return member-free text
   * uncopied.
   *
   * @param text The text to scan.
   * @return The index of the first member code point, or {@code text.length()} when the text
   *     contains no member.
   */
  private int firstMember(CharSequence text) {
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final At cp = CodePoints.at(text, i);
      if (members.contains(cp.codePoint())) {
        return i;
      }
      i = cp.nextIndex(i);
    }
    return length;
  }

  /**
   * Advances past a run of member code points.
   *
   * @param text The text to scan.
   * @param runStart The index where the member run starts.
   * @return The index of the first non-member code point at or after {@code runStart}, or
   *     {@code text.length()} when the run extends to the end of the text.
   */
  private int skipRun(CharSequence text, int runStart) {
    final int length = text.length();
    int i = runStart;
    while (i < length) {
      final At cp = CodePoints.at(text, i);
      if (!members.contains(cp.codePoint())) {
        break;
      }
      i = cp.nextIndex(i);
    }
    return i;
  }

  /**
   * Validates that {@code codePoint} is a Unicode code point.
   *
   * @param codePoint The value to validate.
   * @throws IllegalArgumentException Thrown if {@code codePoint} is negative or greater than
   *     {@link Character#MAX_CODE_POINT}.
   */
  private static void requireValidCodePoint(int codePoint) {
    if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
      throw new IllegalArgumentException("Not a Unicode code point: " + codePoint);
    }
  }

  /**
   * Validates that a parameter is not {@code null}.
   *
   * @param value The parameter value to validate.
   * @param name The parameter name used in the error message.
   * @param <T> The parameter type.
   * @return {@code value}, never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code value} is {@code null}.
   */
  private static <T> T requireNonNullArg(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException("The " + name + " must not be null.");
    }
    return value;
  }
}
