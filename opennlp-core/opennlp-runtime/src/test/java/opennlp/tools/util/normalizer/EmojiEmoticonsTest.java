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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmojiEmoticonsTest {

  private static EmojiEmoticons.Tables bundled() throws IOException {
    try (InputStream in = EmojiEmoticons.class.getResourceAsStream("emoji-emoticons.txt")) {
      return EmojiEmoticons.parse(in);
    }
  }

  private static Set<String> sources(Map<Integer, List<EmojiEmoticons.Mapping>> table) {
    final Set<String> sources = new HashSet<>();
    for (final List<EmojiEmoticons.Mapping> candidates : table.values()) {
      for (final EmojiEmoticons.Mapping mapping : candidates) {
        sources.add(mapping.source());
      }
    }
    return sources;
  }

  @Test
  void directionsCloseOverEachOther() throws IOException {
    // Every EMOJI target must be an EMOTICON source and every EMOTICON target an EMOJI source, so
    // folding one direction and then the other converges on a canonical form instead of producing
    // text the reverse table does not know. This is the audit the data file header promises.
    final EmojiEmoticons.Tables tables = bundled();
    final Set<String> emoticonSources = sources(tables.emoticonToEmoji().table());
    final Set<String> emojiSources = sources(tables.emojiToEmoticon().table());
    for (final List<EmojiEmoticons.Mapping> candidates : tables.emojiToEmoticon().table().values()) {
      for (final EmojiEmoticons.Mapping mapping : candidates) {
        assertTrue(emoticonSources.contains(mapping.target()),
            "EMOJI target has no EMOTICON row: " + mapping);
      }
    }
    for (final List<EmojiEmoticons.Mapping> candidates : tables.emoticonToEmoji().table().values()) {
      for (final EmojiEmoticons.Mapping mapping : candidates) {
        assertTrue(emojiSources.contains(mapping.target()),
            "EMOTICON target has no EMOJI row: " + mapping);
      }
    }
  }

  @Test
  void emoticonSourcesAreAsciiAndEmojiSourcesAreNot() throws IOException {
    // The whitespace-delimited boundary guard exists because emoticon sources are ordinary ASCII
    // that also occurs inside text; the emoji direction runs unguarded because its sources are
    // pictographs. These are the data invariants those design choices rest on.
    final EmojiEmoticons.Tables tables = bundled();
    for (final String source : sources(tables.emoticonToEmoji().table())) {
      source.chars().forEach(c ->
          assertTrue(c > 0x20 && c < 0x7F, "Non-ASCII-printable emoticon source: " + source));
    }
    for (final String source : sources(tables.emojiToEmoticon().table())) {
      assertTrue(source.codePointAt(0) > 0x7F, "ASCII-leading emoji source: " + source);
    }
  }

  @Test
  void bundledRowCountsAreAudited() throws IOException {
    // Locks the bundled row counts so a data edit trips this test for a conscious bump, the same
    // discipline as the CaseFolding.txt completeness audit.
    final EmojiEmoticons.Tables tables = bundled();
    assertEquals(35, sources(tables.emojiToEmoticon().table()).size());
    assertEquals(26, sources(tables.emoticonToEmoji().table()).size());
  }

  @Test
  void candidatesAreSortedLongestFirst() throws IOException {
    // The scan takes the first region match, so longest-match correctness rests on this ordering
    // in BOTH directions; the emoji table relies on it to try "2764 FE0F" before bare "2764".
    final EmojiEmoticons.Tables tables = bundled();
    for (final EmojiEmoticons.Direction direction
        : List.of(tables.emoticonToEmoji(), tables.emojiToEmoticon())) {
      for (final List<EmojiEmoticons.Mapping> candidates : direction.table().values()) {
        for (int i = 1; i < candidates.size(); i++) {
          assertTrue(
              candidates.get(i - 1).source().length() >= candidates.get(i).source().length(),
              "Candidates not longest-first: " + candidates);
        }
      }
    }
  }

  @Test
  void parseFailsLoudOnWrongFieldCount() {
    final String data = "1F642 ; 003A 0029 ; EMOJI ; UNSPECIFIED ; 17.0.0\n";
    assertThrows(IllegalArgumentException.class, () -> EmojiEmoticons.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void parseFailsLoudOnUnrecognizedFoldType() {
    final String data = "1F642 ; 003A 0029 ; SMILEY ; UNSPECIFIED ; 17.0.0 ; bad type\n";
    assertThrows(IllegalArgumentException.class, () -> EmojiEmoticons.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void parseFailsLoudOnMalformedHex() {
    final String data = "1F64X ; 003A 0029 ; EMOJI ; UNSPECIFIED ; 17.0.0 ; bad hex\n";
    assertThrows(IllegalArgumentException.class, () -> EmojiEmoticons.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void parseFailsLoudOnEmptySourceOrTarget() {
    final String empty = " ; 003A 0029 ; EMOJI ; UNSPECIFIED ; 17.0.0 ; empty source\n";
    assertThrows(IllegalArgumentException.class, () -> EmojiEmoticons.parse(
        new ByteArrayInputStream(empty.getBytes(StandardCharsets.UTF_8))));
    final String emptyTarget = "1F642 ; ; EMOJI ; UNSPECIFIED ; 17.0.0 ; empty target\n";
    assertThrows(IllegalArgumentException.class, () -> EmojiEmoticons.parse(
        new ByteArrayInputStream(emptyTarget.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void parseFailsLoudOnDuplicateSourceWithinADirection() {
    final String data = "1F642 ; 003A 0029 ; EMOJI ; UNSPECIFIED ; 17.0.0 ; first\n"
        + "1F642 ; 003A 0044 ; EMOJI ; UNSPECIFIED ; 17.0.0 ; duplicate source\n";
    assertThrows(IllegalArgumentException.class, () -> EmojiEmoticons.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void sameSourceInBothDirectionsIsNotADuplicate() throws IOException {
    // A sequence may legitimately be a source in one direction and a target in the other; only a
    // duplicate within one direction is ambiguous.
    final String data = "263A ; 003A 0029 ; EMOJI ; UNSPECIFIED ; 17.0.0 ; emoji row\n"
        + "003A 0029 ; 263A ; EMOTICON ; UNSPECIFIED ; 17.0.0 ; emoticon row\n";
    final EmojiEmoticons.Tables tables = EmojiEmoticons.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
    assertEquals(1, sources(tables.emojiToEmoticon().table()).size());
    assertEquals(1, sources(tables.emoticonToEmoji().table()).size());
  }

  @Test
  void commentAndBlankLinesAreSkipped() throws IOException {
    final String data = "# a comment line\n\n   \n"
        + "263A ; 003A 0029 ; EMOJI ; UNSPECIFIED ; 17.0.0 ; the only data row\n";
    final EmojiEmoticons.Tables tables = EmojiEmoticons.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
    assertEquals(1, sources(tables.emojiToEmoticon().table()).size());
    assertTrue(sources(tables.emoticonToEmoji().table()).isEmpty());
    assertFalse(sources(tables.emojiToEmoticon().table()).contains("#"));
  }
}
