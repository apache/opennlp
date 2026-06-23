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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the official Unicode {@code WordBreakTest.txt} conformance suite against
 * {@link WordSegmenter}. Each line marks boundaries with U+00F7 (division sign) and non-boundaries
 * with U+00D7 (multiplication sign) between code points.
 */
public class WordBoundaryConformanceTest {

  private static final int BOUNDARY = 0x00F7; // division sign

  @Test
  void testOfficialUnicodeWordBreakConformance() throws IOException {
    int total = 0;
    int passed = 0;
    final List<String> failures = new ArrayList<>();

    try (InputStream in = Objects.requireNonNull(
             WordBoundaryConformanceTest.class.getResourceAsStream("WordBreakTest.txt"),
             "Missing test resource: WordBreakTest.txt");
         BufferedReader reader =
             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String raw;
      int lineNumber = 0;
      while ((raw = reader.readLine()) != null) {
        lineNumber++;
        final int hash = raw.indexOf('#');
        final String content = (hash < 0 ? raw : raw.substring(0, hash)).strip();
        if (content.isEmpty()) {
          continue;
        }
        final String[] tokens = content.split("\\s+");

        final StringBuilder text = new StringBuilder();
        final List<Integer> expected = new ArrayList<>();
        expected.add(0); // tokens[0] is always a leading boundary marker.
        int offset = 0;
        for (int k = 1; k < tokens.length; k += 2) {
          final int codePoint = Integer.parseInt(tokens[k], 16);
          text.appendCodePoint(codePoint);
          offset += Character.charCount(codePoint);
          if (tokens[k + 1].codePointAt(0) == BOUNDARY) {
            expected.add(offset);
          }
        }

        final int[] actual = WordSegmenter.boundaries(text);
        final int[] expectedArray = expected.stream().mapToInt(Integer::intValue).toArray();
        total++;
        if (Arrays.equals(actual, expectedArray)) {
          passed++;
        } else if (failures.size() < 25) {
          failures.add("line " + lineNumber + ": " + content
              + "\n    expected=" + Arrays.toString(expectedArray)
              + "\n    actual  =" + Arrays.toString(actual));
        }
      }
    }

    final int passRate = total == 0 ? 0 : passed * 100 / total;
    assertTrue(total > 1900, "expected the full conformance suite to load, ran only " + total);
    assertTrue(failures.isEmpty(),
        "UAX#29 word-break conformance: " + passed + "/" + total + " (" + passRate
            + "%). First failures:\n" + String.join("\n", failures));
  }
}
