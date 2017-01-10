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

package opennlp.tools.util;

import java.util.Objects;

/**
 * Class for storing start and end integer offsets.
 *
 */
public class Span implements Comparable<Span> {

  private final int start;
  private final int end;
  private final double prob;//default is 0
  private final String type;

  /**
   * Initializes a new Span Object. Sets the prob to 0 as default.
   *
   * @param s start of span.
   * @param e end of span, which is +1 more than the last element in the span.
   * @param type the type of the span
   */
  public Span(int s, int e, String type) {

    if (s < 0) {
      throw new IllegalArgumentException("start index must be zero or greater: " + s);
    }
    if (e < 0) {
      throw new IllegalArgumentException("end index must be zero or greater: " + e);
    }
    if (s > e) {
      throw new IllegalArgumentException("start index must not be larger than end index: "
              + "start=" + s + ", end=" + e);
    }

    start = s;
    end = e;
    this.type = type;
    this.prob = 0d;
  }

  public Span(int s, int e, String type, double prob) {

    if (s < 0) {
      throw new IllegalArgumentException("start index must be zero or greater: " + s);
    }
    if (e < 0) {
      throw new IllegalArgumentException("end index must be zero or greater: " + e);
    }
    if (s > e) {
      throw new IllegalArgumentException("start index must not be larger than end index: "
              + "start=" + s + ", end=" + e);
    }

    start = s;
    end = e;
    this.prob = prob;
    this.type = type;
  }

  /**
   * Initializes a new Span Object. Sets the prob to 0 as default
   *
   * @param s start of span.
   * @param e end of span.
   */
  public Span(int s, int e) {
    this(s, e, null, 0d);
  }

  /**
   *
   * @param s the start of the span (the token index, not the char index)
   * @param e the end of the span (the token index, not the char index)
   * @param prob
   */
  public Span(int s, int e, double prob) {
    this(s, e, null, prob);
  }

  /**
   * Initializes a new Span object with an existing Span which is shifted by an
   * offset.
   *
   * @param span
   * @param offset
   */
  public Span(Span span, int offset) {
    this(span.start + offset, span.end + offset, span.getType(), span.getProb());
  }

  /**
   * Creates a new immutable span based on an existing span, where the existing span did not include the prob
   * @param span the span that has no prob or the prob is incorrect and a new Span must be generated
   * @param prob the probability of the span
   */
  public Span(Span span, double prob) {
    this(span.start, span.end, span.getType(), prob);
  }

  /**
   * Return the start of a span.
   *
   * @return the start of a span.
   *
   */
  public int getStart() {
    return start;
  }

  /**
   * Return the end of a span.
   *
   * Note: that the returned index is one past the actual end of the span in the
   * text, or the first element past the end of the span.
   *
   * @return the end of a span.
   *
   */
  public int getEnd() {
    return end;
  }

  /**
   * Retrieves the type of the span.
   *
   * @return the type or null if not set
   */
  public String getType() {
    return type;
  }

  /**
   * Returns the length of this span.
   *
   * @return the length of the span.
   */
  public int length() {
    return end - start;
  }

  /**
   * Returns true if the specified span is contained by this span. Identical
   * spans are considered to contain each other.
   *
   * @param s The span to compare with this span.
   *
   * @return true is the specified span is contained by this span; false otherwise.
   */
  public boolean contains(Span s) {
    return start <= s.getStart() && s.getEnd() <= end;
  }

  /**
   * Returns true if the specified index is contained inside this span. An index
   * with the value of end is considered outside the span.
   *
   * @param index the index to test with this span.
   *
   * @return true if the span contains this specified index; false otherwise.
   */
  public boolean contains(int index) {
    return start <= index && index < end;
  }

  /**
   * Returns true if the specified span is the begin of this span and the
   * specified span is contained in this span.
   *
   * @param s The span to compare with this span.
   *
   * @return true if the specified span starts with this span and is contained
   *     in this span; false otherwise
   */
  public boolean startsWith(Span s) {
    return getStart() == s.getStart() && contains(s);
  }

  /**
   * Returns true if the specified span intersects with this span.
   *
   * @param s The span to compare with this span.
   *
   * @return true is the spans overlap; false otherwise.
   */
  public boolean intersects(Span s) {
    int sstart = s.getStart();
    //either s's start is in this or this' start is in s
    return this.contains(s) || s.contains(this)
            || getStart() <= sstart && sstart < getEnd()
            || sstart <= getStart() && getStart() < s.getEnd();
  }

  /**
   * Returns true is the specified span crosses this span.
   *
   * @param s The span to compare with this span.
   *
   * @return true is the specified span overlaps this span and contains a
   *     non-overlapping section; false otherwise.
   */
  public boolean crosses(Span s) {
    int sstart = s.getStart();
    //either s's start is in this or this' start is in s
    return !this.contains(s) && !s.contains(this)
            && (getStart() <= sstart && sstart < getEnd()
            || sstart <= getStart() && getStart() < s.getEnd());
  }

  /**
   * Retrieves the string covered by the current span of the specified text.
   *
   * @param text
   *
   * @return the substring covered by the current span
   */
  public CharSequence getCoveredText(CharSequence text) {
    if (getEnd() > text.length()) {
      throw new IllegalArgumentException("The span " + toString()
              + " is outside the given text which has length " + text.length() + "!");
    }

    return text.subSequence(getStart(), getEnd());
  }

  /**
   * Return a copy of this span with leading and trailing white spaces removed.
   *
   * @param text
   *
   * @return the trimmed span or the same object if already trimmed
   */
  public Span trim(CharSequence text) {

    int newStartOffset = getStart();

    for (int i = getStart(); i < getEnd() && StringUtil.isWhitespace(text.charAt(i)); i++) {
      newStartOffset++;
    }

    int newEndOffset = getEnd();
    for (int i = getEnd(); i > getStart() && StringUtil.isWhitespace(text.charAt(i - 1)); i--) {
      newEndOffset--;
    }

    if (newStartOffset == getStart() && newEndOffset == getEnd()) {
      return this;
    } else if (newStartOffset > newEndOffset) {
      return new Span(getStart(), getStart(), getType());
    } else {
      return new Span(newStartOffset, newEndOffset, getType());
    }
  }

  /**
   * Compares the specified span to the current span.
   */
  public int compareTo(Span s) {
    if (getStart() < s.getStart()) {
      return -1;
    } else if (getStart() == s.getStart()) {
      if (getEnd() > s.getEnd()) {
        return -1;
      } else if (getEnd() < s.getEnd()) {
        return 1;
      } else {
        // compare the type
        if (getType() == null && s.getType() == null) {
          return 0;
        } else if (getType() != null && s.getType() != null) {
          // use type lexicography order
          return getType().compareTo(s.getType());
        } else if (getType() != null) {
          return -1;
        }
        return 1;
      }
    } else {
      return 1;
    }
  }

  /**
   * Generates a hash code of the current span.
   */
  @Override
  public int hashCode() {
    return Objects.hash(getStart(), getEnd(), getType());
  }

  /**
   * Checks if the specified span is equal to the current span.
   */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (o instanceof Span) {
      Span s = (Span) o;

      return getStart() == s.getStart() && getEnd() == s.getEnd()
          && Objects.equals(getType(), s.getType());
    }

    return false;
  }

  /**
   * Generates a human readable string.
   */
  @Override
  public String toString() {
    StringBuilder toStringBuffer = new StringBuilder(15);
    toStringBuffer.append("[");
    toStringBuffer.append(getStart());
    toStringBuffer.append("..");
    toStringBuffer.append(getEnd());
    toStringBuffer.append(")");
    if (getType() != null) {
      toStringBuffer.append(" ");
      toStringBuffer.append(getType());
    }

    return toStringBuffer.toString();
  }

  /**
   * Converts an array of {@link Span}s to an array of {@link String}s.
   *
   * @param spans
   * @param s
   * @return the strings
   */
  public static String[] spansToStrings(Span[] spans, CharSequence s) {
    String[] tokens = new String[spans.length];

    for (int si = 0, sl = spans.length; si < sl; si++) {
      tokens[si] = spans[si].getCoveredText(s).toString();
    }

    return tokens;
  }

  public static String[] spansToStrings(Span[] spans, String[] tokens) {
    String[] chunks = new String[spans.length];
    StringBuilder cb = new StringBuilder();
    for (int si = 0, sl = spans.length; si < sl; si++) {
      cb.setLength(0);
      for (int ti = spans[si].getStart(); ti < spans[si].getEnd(); ti++) {
        cb.append(tokens[ti]).append(" ");
      }
      chunks[si] = cb.substring(0, cb.length() - 1);
    }
    return chunks;
  }

  public double getProb() {
    return prob;
  }

}
