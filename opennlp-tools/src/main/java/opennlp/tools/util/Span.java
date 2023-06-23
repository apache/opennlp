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

import java.io.Serializable;
import java.util.Objects;

/**
 * Class for storing start and end integer offsets.
 *
 */
public class Span implements Comparable<Span>, Serializable {

  private static final long serialVersionUID = -7648780019844573507L;
  private final int start;
  private final int end;
  private final double prob; //default is 0
  private final String type;

  /**
   * Initializes a new {@link Span}. Sets the prob to {@code 0} as default.
   *
   * @param s The start position of a {@link Span}.
   *          Must be equal to or greater than {@code 0}.
   *          Must not be greater than {@code e}.
   * @param e The end position of a {@link Span}, which is {@code +1}
   *          more than the last element in the span.
   *          Must be equal to or greater than {@code 0}.
   * @param type the type of the span
   *             
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
  public Span(int s, int e, String type) {
    this(s, e, type, 0d);
  }

  /**
   * Initializes a new {@link Span}.
   *
   * @param s The start position of a {@link Span}.
   *          Must be equal to or greater than {@code 0}.
   *          Must not be greater than {@code e}.
   * @param e The end position of a {@link Span}, which is {@code +1}
   *          more than the last element in the span.
   *          Must be equal to or greater than {@code 0}.
   * @param type The type of the {@link Span}
   * @param prob The probability of the {@link Span}.
   *
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
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
   * Initializes a new {@link Span}. Sets the prob to {@code 0} as default.
   *
   * @param s The start position of a {@link Span}.
   *          Must be equal to or greater than {@code 0}.
   *          Must not be greater than {@code e}.
   * @param e The end position of a {@link Span}, which is {@code +1}
   *          more than the last element in the span.
   *          Must be equal to or greater than {@code 0}.
   *
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
  public Span(int s, int e) {
    this(s, e, null, 0d);
  }

  /**
   * Initializes a new {@link Span}. Sets the prob to {@code 0} as default.
   *
   * @param s The start position of a {@link Span}.
   *          Must be equal to or greater than {@code 0}.
   *          Must not be greater than {@code e}.
   * @param e The end position of a {@link Span}, which is {@code +1}
   *          more than the last element in the span.
   *          Must be equal to or greater than {@code 0}.
   * @param prob The probability of the {@link Span}
   *
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
  public Span(int s, int e, double prob) {
    this(s, e, null, prob);
  }

  /**
   * Initializes a new {@link Span} with an existing {@link Span} which is shifted by an
   * offset.
   *
   * @param span The existing {@link Span}.
   * @param offset The positive or negative shift offset.
   *               
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
  public Span(Span span, int offset) {
    this(span.start + offset, span.end + offset, span.getType(), span.getProb());
  }

  /**
   * Creates a new immutable {@link Span} based on an existing {@link Span},
   * where the existing {@link Span} did not include the probability.
   *
   * @param span The {@link Span} that has no prob or the prob is incorrect and
   *             a new {@link Span} must be generated.
   * @param prob The probability of the {@link Span}.
   *             
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
  public Span(Span span, double prob) {
    this(span.start, span.end, span.getType(), prob);
  }

  /**
   * @return Retrieves the start of a {@link Span}. Guaranteed to be greater than {@code 0}.
   */
  public int getStart() {
    return start;
  }

  /**
   * <b>Note:</b>
   * that the returned index is one past the actual end of the span in the
   * text, or the first element past the end of the span.
   *
   * @return Retrieves the end of a {@link Span}. Guaranteed to be greater than {@code 0}.
   */
  public int getEnd() {
    return end;
  }

  /**
   * @return Retrieves the type of a {@link Span} or {@code null} if not set.
   */
  public String getType() {
    return type;
  }

  /**
   * @return Returns the length of a {@link Span}. Guaranteed to be greater than {@code 0}.
   */
  public int length() {
    return end - start;
  }

  /**
   * Identical {@link Span spans} are considered to contain each other.
   *
   * @param s The {@link Span} to compare with this {@link Span}.
   *
   * @return {@code true} is the specified {{@link Span} s} is contained by this span,
   *         {@code false} otherwise.
   */
  public boolean contains(Span s) {
    return start <= s.getStart() && s.getEnd() <= end;
  }

  /**
   * An index with the value of end is considered outside the {@link Span}.
   *
   * @param index the index to test with this {@link Span}.
   *
   * @return {@code true} if the span contains this specified index, {@code false} otherwise.
   */
  public boolean contains(int index) {
    return start <= index && index < end;
  }

  /**
   * @param s The {@link Span} to compare with this span.
   *
   * @return {@code true} if the specified span starts with this span and is contained
   *         in this span, {@code false} otherwise
   */
  public boolean startsWith(Span s) {
    return getStart() == s.getStart() && contains(s);
  }

  /**
   * Checks if the specified {@link Span} intersects with this span.
   *
   * @param s The {@link Span} to compare with this span.
   *
   * @return {@code true} is the spans overlap, {@code false} otherwise.
   */
  public boolean intersects(Span s) {
    int sstart = s.getStart();
    //either s's start is in this or this' start is in s
    return this.contains(s) || s.contains(this)
            || getStart() <= sstart && sstart < getEnd()
            || sstart <= getStart() && getStart() < s.getEnd();
  }

  /**
   * Checks if the specified {@link Span} crosses this span.
   *
   * @param s The {@link Span} to compare with this span.
   *
   * @return {@code true} is the given {@link Span} overlaps this span and contains a
   *         non-overlapping section, {@code false} otherwise.
   */
  public boolean crosses(Span s) {
    int sstart = s.getStart();
    //either s's start is in this or this' start is in s
    return !this.contains(s) && !s.contains(this)
            && (getStart() <= sstart && sstart < getEnd()
            || sstart <= getStart() && getStart() < s.getEnd());
  }

  /**
   * @param text The {@link CharSequence text} to analyze.
   *
   * @return Retrieves the (sub)string covered by the current {@link Span} of the specified text.
   * 
   * @throws IllegalArgumentException Thrown if parameters violated a constraint.
   */
  public CharSequence getCoveredText(CharSequence text) {
    if (getEnd() > text.length()) {
      throw new IllegalArgumentException("The span " + this
              + " is outside the given text which has length " + text.length() + "!");
    }

    return text.subSequence(getStart(), getEnd());
  }

  /**
   * @param text The {@link CharSequence text} to analyze.
   *
   * @return A copy of this {@link Span} with leading and trailing white spaces removed,
   *         or the same object if already trimmed.
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
   * Compares the specified {@link Span} to the current span.
   *
   * @param s The {@link Span} instance to compare against.
   *          
   * @see Comparable#compareTo(Object) 
   */
  @Override
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

  @Override
  public int hashCode() {
    return Objects.hash(getStart(), getEnd(), getType());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (o instanceof Span s) {

      return getStart() == s.getStart() && getEnd() == s.getEnd()
          && Objects.equals(getType(), s.getType());
    }

    return false;
  }

  /**
   * @return A human-readable representation of this {@link Span}.
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
   * Converts an array of {@link Span spans} to an array of {@link String}.
   *
   * @param spans The array used as input.
   * @param s The {@link CharSequence} used to compute covered text.
   * @return The converted array of strings.
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

  /**
   * @return Retrieves the probability represented by a {@link Span}.
   */
  public double getProb() {
    return prob;
  }

}
