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

package opennlp.tools.formats.masc;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Shared handling of the identifier attributes in MASC annotation files. Node, region,
 * and named entity identifiers are a fixed text prefix followed by a number, as in
 * {@code penn-n7}; the parsers read the number and require the prefix. Attribute values
 * that list several items use XML whitespace: space, tab, carriage return, and line feed.
 * SAX normalizes literal whitespace in attributes but preserves character references, see
 * <a href="https://www.w3.org/TR/xml/#AVNormalize">XML 1.0, attribute-value
 * normalization</a>.
 */
final class MascIdentifiers {

  /** The prefix of a named entity node identifier, as in {@code ne-n7}. */
  static final String NAMED_ENTITY_ID_PREFIX = "ne-n";

  /** The prefix of a Penn token node identifier, as in {@code penn-n7}. */
  static final String PENN_TOKEN_ID_PREFIX = "penn-n";

  /** The prefix of a segmentation region identifier, as in {@code seg-r7}. */
  static final String REGION_ID_PREFIX = "seg-r";

  private MascIdentifiers() {
  }

  /**
   * Parses the number of an identifier that starts with {@code prefix} and continues with
   * one or more ASCII digits only.
   *
   * @param id The identifier, such as {@code penn-n7}.
   * @param prefix The expected prefix, such as {@link #PENN_TOKEN_ID_PREFIX}.
   * @return The number after the prefix.
   * @throws IllegalArgumentException If {@code id} is {@code null}, does not start with
   *         {@code prefix}, is not followed by digits only, or the number does not fit
   *         an {@code int}.
   */
  static int parseId(String id, String prefix) {
    if (id == null || !id.startsWith(prefix) || id.length() == prefix.length()
        || StringUtil.endOfAsciiDigits(id, prefix.length()) != id.length()) {
      throw new IllegalArgumentException(
          "MASC identifier must be " + prefix + " followed by digits: " + id);
    }
    try {
      return Integer.parseInt(id, prefix.length(), id.length(), 10);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("MASC identifier number does not fit an int: " + id, e);
    }
  }

  /**
   * Parses an XML whitespace separated list of identifiers as {@link #parseId(String, String)}
   * does. Leading, trailing, and repeated separators are ignored.
   *
   * @param ids The identifiers, such as {@code seg-r1 seg-r2}.
   * @param prefix The expected prefix of each identifier.
   * @return The numbers in order.
   * @throws IllegalArgumentException If {@code ids} is {@code null}, names no identifier, or
   *         contains one that {@link #parseId(String, String)} rejects.
   */
  static int[] parseIds(String ids, String prefix) {
    if (ids == null) {
      throw new IllegalArgumentException("MASC identifier list must not be null");
    }
    String[] items = splitOnXmlWhitespace(ids);
    if (items.length == 0) {
      throw new IllegalArgumentException("MASC identifier list must name at least one identifier");
    }
    int[] numbers = new int[items.length];
    for (int i = 0; i < items.length; i++) {
      numbers[i] = parseId(items[i], prefix);
    }
    return numbers;
  }

  /**
   * Splits an attribute value on runs of XML whitespace. Leading, trailing, and repeated separators
   * produce no empty item.
   *
   * @param value The attribute value. Must not be {@code null}.
   * @return The non-empty items in order; empty for a value without one.
   * @throws IllegalArgumentException If {@code value} is {@code null}.
   */
  static String[] splitOnXmlWhitespace(String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    List<String> items = new ArrayList<>();
    int start = -1;
    for (int i = 0; i <= value.length(); i++) {
      if (i == value.length() || isXmlWhitespace(value.charAt(i))) {
        if (start >= 0) {
          items.add(value.substring(start, i));
          start = -1;
        }
      } else if (start < 0) {
        start = i;
      }
    }
    return items.toArray(new String[0]);
  }

  /**
   * Parses the two ordered, nonnegative offsets of a contiguous text region.
   *
   * @param anchors The XML whitespace separated offsets.
   * @return The region span.
   * @throws IllegalArgumentException If the anchors are missing, malformed, out of range,
   *         negative, or reversed.
   */
  static Span parseAnchors(String anchors) {
    if (anchors == null) {
      throw new IllegalArgumentException("MASC region anchors must not be null");
    }
    String[] items = splitOnXmlWhitespace(anchors);
    if (items.length != 2) {
      throw new IllegalArgumentException("MASC region anchors must contain exactly two offsets: " + anchors);
    }
    try {
      return new Span(Integer.parseInt(items[0]), Integer.parseInt(items[1]));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid MASC region anchors: " + anchors, e);
    }
  }

  /** Returns whether a character belongs to the XML whitespace production S. */
  private static boolean isXmlWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
  }
}
