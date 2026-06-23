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
package opennlp.tools.tokenize.uax29;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Looks up the Unicode {@link WordBreak Word_Break} property of a code point.
 *
 * <p>The data is loaded once from the {@code WordBreakProperty.txt} resource of the Unicode
 * Character Database (parsed with simple cursor scanning, no regular expression). Lookup is O(1)
 * for the Basic Multilingual Plane (a direct array index) and O(log n) for supplementary code
 * points (a binary search over a small sorted range table), so it imposes no per-character
 * allocation on the word boundary algorithm.</p>
 */
public final class WordBreakProperty {

  private static final String RESOURCE = "WordBreakProperty.txt";

  private static final WordBreak[] VALUES = WordBreak.values();

  // Word_Break value ordinal for each BMP code point; the default 0 is WordBreak.OTHER.
  private static final byte[] BMP = new byte[0x10000];

  // Supplementary ranges (above the BMP), sorted by start for binary search.
  private static final int[] SUPPLEMENTARY_START;
  private static final int[] SUPPLEMENTARY_END;
  private static final byte[] SUPPLEMENTARY_VALUE;

  static {
    final List<int[]> supplementary = new ArrayList<>();
    try (InputStream in = WordBreakProperty.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing Word_Break data resource: " + RESOURCE);
      }
      load(in, supplementary);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read Word_Break data resource " + RESOURCE, e);
    }
    supplementary.sort((a, b) -> Integer.compare(a[0], b[0]));
    SUPPLEMENTARY_START = new int[supplementary.size()];
    SUPPLEMENTARY_END = new int[supplementary.size()];
    SUPPLEMENTARY_VALUE = new byte[supplementary.size()];
    for (int i = 0; i < supplementary.size(); i++) {
      final int[] range = supplementary.get(i);
      SUPPLEMENTARY_START[i] = range[0];
      SUPPLEMENTARY_END[i] = range[1];
      SUPPLEMENTARY_VALUE[i] = (byte) range[2];
    }
  }

  private WordBreakProperty() {
  }

  private static void load(InputStream in, List<int[]> supplementary) throws IOException {
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        final int hash = line.indexOf('#');
        final String content = (hash < 0 ? line : line.substring(0, hash)).strip();
        if (content.isEmpty()) {
          continue;
        }
        final int semicolon = content.indexOf(';');
        final String codePoints = content.substring(0, semicolon).strip();
        final String value = content.substring(semicolon + 1).strip();
        final byte ordinal = (byte) WordBreak.fromPropertyName(value).ordinal();

        final int dots = codePoints.indexOf("..");
        final int start;
        final int end;
        if (dots < 0) {
          start = Integer.parseInt(codePoints, 16);
          end = start;
        } else {
          start = Integer.parseInt(codePoints.substring(0, dots), 16);
          end = Integer.parseInt(codePoints.substring(dots + 2), 16);
        }
        assign(start, end, ordinal, supplementary);
      }
    }
  }

  private static void assign(int start, int end, byte ordinal, List<int[]> supplementary) {
    final int bmpEnd = Math.min(end, 0xFFFF);
    if (start <= bmpEnd) {
      Arrays.fill(BMP, start, bmpEnd + 1, ordinal); // bulk fill the BMP portion of the range
    }
    if (end > 0xFFFF) {
      supplementary.add(new int[] {Math.max(start, 0x10000), end, ordinal});
    }
  }

  /**
   * {@return the {@link WordBreak} value of a code point}
   *
   * @param codePoint The code point. Values outside {@code [0, U+10FFFF]} return
   *     {@link WordBreak#OTHER}.
   */
  public static WordBreak of(int codePoint) {
    return VALUES[ordinalOf(codePoint)];
  }

  /**
   * {@return the {@link WordBreak#ordinal() ordinal} of a code point's {@link WordBreak} value}
   * This is the allocation-free form of {@link #of(int)} for hot loops that work with ordinals.
   *
   * @param codePoint The code point. Values outside {@code [0, U+10FFFF]} return the ordinal of
   *     {@link WordBreak#OTHER}.
   */
  public static int ordinalOf(int codePoint) {
    if (codePoint >= 0 && codePoint <= 0xFFFF) {
      return BMP[codePoint] & 0xFF; // unsigned: ordinals are stored as bytes, guard sign extension
    }
    return ordinalOfSupplementary(codePoint);
  }

  private static int ordinalOfSupplementary(int codePoint) {
    if (codePoint > 0xFFFF && codePoint <= Character.MAX_CODE_POINT) {
      int low = 0;
      int high = SUPPLEMENTARY_START.length - 1;
      while (low <= high) {
        final int mid = (low + high) >>> 1;
        if (codePoint < SUPPLEMENTARY_START[mid]) {
          high = mid - 1;
        } else if (codePoint > SUPPLEMENTARY_END[mid]) {
          low = mid + 1;
        } else {
          return SUPPLEMENTARY_VALUE[mid] & 0xFF; // unsigned, as in the BMP path
        }
      }
    }
    return WordBreak.OTHER.ordinal();
  }
}
