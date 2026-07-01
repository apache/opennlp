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
import java.util.BitSet;

/**
 * Tests the Unicode {@code Extended_Pictographic} property of a code point.
 *
 * <p>This is the one extra property the word boundary algorithm needs (rule WB3c), to keep emoji
 * zero-width-joiner sequences together. The data is loaded once from the {@code emoji-data.txt}
 * derived resource of the Unicode Character Database and stored in a {@link BitSet}, so membership
 * is an O(1) bit test.</p>
 */
public final class ExtendedPictographic {

  private static final String RESOURCE = "ExtendedPictographic.txt";

  private static volatile BitSet members;

  private ExtendedPictographic() {
  }

  // Package-visible so a per-pass caller can resolve the set once (see is(BitSet, int)) rather than
  // once per code point.
  static BitSet members() {
    BitSet set = members;
    if (set == null) {
      synchronized (ExtendedPictographic.class) {
        set = members;
        if (set == null) {
          set = load();
          members = set;
        }
      }
    }
    return set;
  }

  private static BitSet load() {
    final BitSet set = new BitSet();
    try (InputStream in = ExtendedPictographic.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing Extended_Pictographic data resource: " + RESOURCE);
      }
      parse(in, set);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Unable to read Extended_Pictographic data resource " + RESOURCE, e);
    }
    return set;
  }

  // Package-visible so the malformed-data handling can be exercised without the bundled resource.
  static void parse(InputStream in, BitSet set) throws IOException {
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        final int hash = line.indexOf('#');
        final String content = (hash < 0 ? line : line.substring(0, hash)).strip();
        if (content.isEmpty()) {
          continue;
        }
        // Only the code-point column is needed; the property value after ';' is implicit (this is a
        // filtered single-property file), so a line with no ';' is taken whole -- unlike
        // WordBreakProperty, whose value column is required.
        final int semicolon = content.indexOf(';');
        final String codePoints = (semicolon < 0 ? content : content.substring(0, semicolon)).strip();
        try {
          final int dots = codePoints.indexOf("..");
          if (dots < 0) {
            set.set(Integer.parseInt(codePoints, 16));
          } else {
            final int start = Integer.parseInt(codePoints.substring(0, dots), 16);
            final int end = Integer.parseInt(codePoints.substring(dots + 2), 16);
            set.set(start, end + 1);
          }
        } catch (NumberFormatException e) {
          // Fail loud naming the bad line, the same way the sibling loaders do.
          throw new IllegalArgumentException(
              "Malformed Extended_Pictographic data in " + RESOURCE + ": " + content, e);
        }
      }
    }
  }

  /**
   * {@return whether a code point has the {@code Extended_Pictographic} property}
   *
   * @param codePoint The code point. Values outside {@code [0, U+10FFFF]} return {@code false}.
   */
  public static boolean is(int codePoint) {
    return is(members(), codePoint);
  }

  // Package-visible overload taking an already-resolved BitSet, so a caller that tests many code
  // points in one pass (WordSegmenter, WordType) pays the volatile read behind members() once for the
  // whole pass rather than once per code point.
  static boolean is(BitSet resolved, int codePoint) {
    return codePoint >= 0 && codePoint <= Character.MAX_CODE_POINT && resolved.get(codePoint);
  }
}
