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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnicodeEmojiSequencesParseTest {

  private static final String WELL_FORMED = "# EmojiSequences.txt\n"
      + "# Version: 17.0\n"
      + "S;1F600\n"
      + "S;1F44D 1F3FD\n"
      + "\n"
      + "C;1F3FB..1F3FF\n"
      + "C;200D\n";

  private static InputStream in(String data) {
    return new ByteArrayInputStream(data.getBytes(StandardCharsets.US_ASCII));
  }

  private static String cp(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  @Test
  void parseReadsWellFormedRecords() throws Exception {
    final UnicodeEmojiSequences sequences = UnicodeEmojiSequences.parse(in(WELL_FORMED));
    final String text = "a" + cp(0x1F600) + cp(0x1F44D, 0x1F3FD) + "b";
    final UnicodeEmojiSequences.Candidate candidate = sequences.candidateAt(text, 1);
    assertNotNull(candidate);
    assertTrue(candidate.valid());
    assertEquals(text.length() - 1, candidate.end());
    assertNull(sequences.candidateAt(text, 0));
    // The component range makes a stray modifier after a sequence a malformed candidate.
    final UnicodeEmojiSequences.Candidate malformed =
        sequences.candidateAt(cp(0x1F600, 0x1F3FB), 0);
    assertNotNull(malformed);
    assertFalse(malformed.valid());
  }

  @Test
  void parseFailsLoudOnMalformedCodePoint() {
    final String data = "S;1F600\nS;1F60G\nC;200D\n";
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> UnicodeEmojiSequences.parse(in(data)));
    assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  @Test
  void parseFailsLoudOnMalformedRange() {
    final String data = "S;1F600\nC;1F3FB..\n";
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> UnicodeEmojiSequences.parse(in(data)));
    assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  @Test
  void parseFailsLoudOnUnknownRecord() {
    // A non-comment, non-blank line with another prefix is a wrong or corrupted file. It must
    // not be skipped silently, which would give a quietly incomplete matcher.
    final String data = "S;1F600\nX;1F601\nC;200D\n";
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> UnicodeEmojiSequences.parse(in(data)));
    assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  @Test
  void parseRejectsDataWithoutSequences() {
    final String data = "# header only\nC;200D\n";
    assertThrows(IllegalArgumentException.class, () -> UnicodeEmojiSequences.parse(in(data)));
  }

  @Test
  void parseRejectsDataWithoutComponentRanges() {
    final String data = "# header only\nS;1F600\n";
    assertThrows(IllegalArgumentException.class, () -> UnicodeEmojiSequences.parse(in(data)));
  }
}
