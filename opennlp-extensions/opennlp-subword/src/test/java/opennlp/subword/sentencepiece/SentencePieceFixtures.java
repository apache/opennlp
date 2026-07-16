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
package opennlp.subword.sentencepiece;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.tokenize.SubwordPiece;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared support for the tab-separated parity fixture files produced by the
 * {@code gen_fixtures.py} and {@code gen_real_fixtures.py} scripts in the test resources: one
 * line per input, holding the input, the expected piece count, four columns per expected piece
 * (content, id, start, end), and the expected normalized form.
 */
final class SentencePieceFixtures {

  private SentencePieceFixtures() {
  }

  /**
   * One parsed fixture line: an input with the piece sequence and normalized form the reference
   * implementation produced for it.
   *
   * @param input      The text to encode.
   * @param pieces     The expected pieces with ids and original-text spans, in text order.
   * @param normalized The expected normalized form of {@code input}.
   */
  record Fixture(String input, List<SubwordPiece> pieces, String normalized) {
  }

  /**
   * Reads all fixture lines from a reader.
   *
   * @param reader The reader positioned at the start of a fixture file; must not be null.
   * @return The parsed fixtures in file order.
   * @throws IOException Thrown if the reader fails.
   */
  static List<Fixture> read(BufferedReader reader) throws IOException {
    final List<Fixture> fixtures = new ArrayList<>();
    String line;
    while ((line = reader.readLine()) != null) {
      final String[] cols = line.split("\t", -1);
      final String input = unescape(cols[0]);
      final int count = Integer.parseInt(cols[1]);
      final List<SubwordPiece> pieces = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        pieces.add(new SubwordPiece(unescape(cols[2 + i * 4]),
            Integer.parseInt(cols[3 + i * 4]), Integer.parseInt(cols[4 + i * 4]),
            Integer.parseInt(cols[5 + i * 4])));
      }
      fixtures.add(new Fixture(input, pieces, unescape(cols[2 + count * 4])));
    }
    return fixtures;
  }

  /**
   * Asserts that a tokenizer reproduces one fixture exactly: the piece sequence with ids and
   * spans, and the normalized form.
   *
   * @param tokenizer The tokenizer under test.
   * @param fixture   The expected encoding.
   * @param context   A prefix for failure messages that identifies the model and input.
   */
  static void assertFixture(SentencePieceTokenizer tokenizer, Fixture fixture, String context) {
    final List<SubwordPiece> actual = tokenizer.encode(fixture.input());
    assertEquals(fixture.pieces().size(), actual.size(),
        context + " piece count; got " + actual);
    for (int i = 0; i < actual.size(); i++) {
      final SubwordPiece expected = fixture.pieces().get(i);
      final SubwordPiece got = actual.get(i);
      assertEquals(expected.piece(), got.piece(), context + " piece " + i);
      assertEquals(expected.id(), got.id(), context + " id of piece " + i);
      assertEquals(expected.start(), got.start(), context + " start of piece " + i);
      assertEquals(expected.end(), got.end(), context + " end of piece " + i);
    }
    assertEquals(fixture.normalized(), tokenizer.normalize(fixture.input()).toString(),
        context + " normalized form");
  }

  /**
   * Reverses the fixture files' escaping of tab, newline, carriage return, and backslash.
   *
   * @param s The escaped column content.
   * @return The unescaped text.
   * @throws IllegalArgumentException Thrown if an unknown escape sequence occurs.
   */
  static String unescape(String s) {
    final StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        i++;
        switch (s.charAt(i)) {
          case 't' -> out.append('\t');
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case '\\' -> out.append('\\');
          default -> throw new IllegalArgumentException("bad escape in fixture: " + s);
        }
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
