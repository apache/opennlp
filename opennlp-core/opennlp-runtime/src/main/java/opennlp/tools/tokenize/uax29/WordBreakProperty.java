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

  private static volatile Data data;

  private WordBreakProperty() {
  }

  // Immutable Word_Break tables: ordinal per BMP code point, plus supplementary ranges sorted by
  // start for binary search. Package-visible so a caller that looks up many code points in one pass
  // (WordSegmenter) can resolve this once and reuse it, instead of paying the volatile read behind
  // data() on every call.
  static final class Data {
    final byte[] bmp;
    final int[] supplementaryStart;
    final int[] supplementaryEnd;
    final byte[] supplementaryValue;

    Data(byte[] bmp, int[] start, int[] end, byte[] value) {
      this.bmp = bmp;
      this.supplementaryStart = start;
      this.supplementaryEnd = end;
      this.supplementaryValue = value;
    }
  }

  // Package-visible so a per-pass caller can resolve the table once (see the ordinalOf/of overloads
  // that take a resolved Data) rather than once per code point.
  static Data data() {
    Data d = data;
    if (d == null) {
      synchronized (WordBreakProperty.class) {
        d = data;
        if (d == null) {
          d = load();
          data = d;
        }
      }
    }
    return d;
  }

  private static Data load() {
    final byte[] bmp = new byte[0x10000];
    final List<int[]> supplementary = new ArrayList<>();
    try (InputStream in = WordBreakProperty.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing Word_Break data resource: " + RESOURCE);
      }
      parse(in, bmp, supplementary);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read Word_Break data resource " + RESOURCE, e);
    }
    supplementary.sort((a, b) -> Integer.compare(a[0], b[0]));
    final int[] start = new int[supplementary.size()];
    final int[] end = new int[supplementary.size()];
    final byte[] value = new byte[supplementary.size()];
    for (int i = 0; i < supplementary.size(); i++) {
      final int[] range = supplementary.get(i);
      start[i] = range[0];
      end[i] = range[1];
      value[i] = (byte) range[2];
    }
    return new Data(bmp, start, end, value);
  }

  // Package-visible so the malformed-data handling can be exercised without the bundled resource.
  static void parse(InputStream in, byte[] bmp, List<int[]> supplementary) throws IOException {
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
        if (semicolon < 0) {
          // A present-but-structurally-wrong line (no ';' to split code points from the value) is a
          // hard error naming the line, mirroring ExtendedPictographic, not an opaque substring throw.
          throw new IllegalStateException(
              "Malformed Word_Break data in " + RESOURCE + " (no ';'): " + content);
        }
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
        assign(start, end, ordinal, bmp, supplementary);
      }
    }
  }

  private static void assign(int start, int end, byte ordinal, byte[] bmp, List<int[]> supplementary) {
    final int bmpEnd = Math.min(end, 0xFFFF);
    if (start <= bmpEnd) {
      Arrays.fill(bmp, start, bmpEnd + 1, ordinal); // bulk fill the BMP portion of the range
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
    return of(data(), codePoint);
  }

  // Package-visible overload for a caller that already resolved Data once for a whole pass (see
  // ordinalOf(Data, int)), so it is not looked up again per code point.
  static WordBreak of(Data resolved, int codePoint) {
    return VALUES[ordinalOf(resolved, codePoint)];
  }

  /**
   * {@return the {@link WordBreak#ordinal() ordinal} of a code point's {@link WordBreak} value}
   * This is the allocation-free form of {@link #of(int)} for hot loops that work with ordinals.
   *
   * @param codePoint The code point. Values outside {@code [0, U+10FFFF]} return the ordinal of
   *     {@link WordBreak#OTHER}.
   */
  public static int ordinalOf(int codePoint) {
    return ordinalOf(data(), codePoint);
  }

  // Package-visible overload taking an already-resolved Data (see data()), so a caller that looks up
  // many code points in one pass (WordSegmenter) pays the volatile read behind data() once for the
  // whole pass rather than once per code point.
  static int ordinalOf(Data resolved, int codePoint) {
    if (codePoint >= 0 && codePoint <= 0xFFFF) {
      return resolved.bmp[codePoint] & 0xFF; // unsigned byte ordinal
    }
    return ordinalOfSupplementary(resolved, codePoint);
  }

  private static int ordinalOfSupplementary(Data d, int codePoint) {
    if (codePoint > 0xFFFF && codePoint <= Character.MAX_CODE_POINT) {
      int low = 0;
      int high = d.supplementaryStart.length - 1;
      while (low <= high) {
        final int mid = (low + high) >>> 1;
        if (codePoint < d.supplementaryStart[mid]) {
          high = mid - 1;
        } else if (codePoint > d.supplementaryEnd[mid]) {
          low = mid + 1;
        } else {
          return d.supplementaryValue[mid] & 0xFF; // unsigned byte ordinal
        }
      }
    }
    return WordBreak.OTHER.ordinal();
  }
}
