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

package opennlp.spellcheck.stream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.spellcheck.normalizer.SpellCheckingCharSequenceNormalizer;
import opennlp.spellcheck.symspell.SymSpell;
import opennlp.spellcheck.symspell.TinyDictionary;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SpellCorrectingTokenStreamTest {

  private SymSpell symSpell;

  @BeforeEach
  void setUp() throws Exception {
    symSpell = TinyDictionary.load();
  }

  /** A trivial in-memory token-line stream that supports reset(). */
  private static final class ListStream implements ObjectStream<String> {
    private final List<String> data;
    private int idx;

    ListStream(String... data) {
      this.data = new ArrayList<>(Arrays.asList(data));
    }

    @Override
    public String read() {
      return idx < data.size() ? data.get(idx++) : null;
    }

    @Override
    public void reset() {
      idx = 0;
    }
  }

  @Test
  void correctsTokensAndPreservesTokenCount() throws IOException {
    // minTokenLength(3) so the short typos ("teh", "fxo") are corrected too.
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).build();
    try (ObjectStream<String> stream = new SpellCorrectingTokenStream(
        new ListStream("teh quikc broen fxo"), normalizer, " ")) {
      final String corrected = stream.read();
      assertEquals("the quick brown fox", corrected);
      // Token count preserved (no split/merge).
      assertEquals(4, corrected.split(" ").length);
      assertNull(stream.read());
    }
  }

  @Test
  void correctsTokensWithDefaultGuards() throws IOException {
    try (ObjectStream<String> stream =
             new SpellCorrectingTokenStream(new ListStream("quikc broen wrold"), symSpell)) {
      assertEquals("quick brown world", stream.read());
      assertNull(stream.read());
    }
  }

  @Test
  void doesNotMergeTokensUnlikeCompound() throws IOException {
    // A single glued token must NOT be split apart by the token stream.
    try (ObjectStream<String> stream =
             new SpellCorrectingTokenStream(new ListStream("helloworld"), symSpell)) {
      final String corrected = stream.read();
      assertEquals(1, corrected.split(" ").length);
    }
  }

  @Test
  void preservesEmptyTokensFromDuplicateDelimiters() throws IOException {
    try (ObjectStream<String> stream =
             new SpellCorrectingTokenStream(new ListStream("quikc  wrold"), symSpell)) {
      // "quikc  wrold" splits to [quikc, "", wrold] -> kept aligned on re-join.
      assertEquals("quick  world", stream.read());
    }
  }

  @Test
  void honorsCustomDelimiter() throws IOException {
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell).build();
    try (ObjectStream<String> stream = new SpellCorrectingTokenStream(
        new ListStream("wrold\tquikc"), normalizer, "\t")) {
      assertEquals("world\tquick", stream.read());
    }
  }

  @Test
  void resetAndExhaustion() throws IOException {
    try (ObjectStream<String> stream =
             new SpellCorrectingTokenStream(new ListStream("wrold fox", "broen dog"), symSpell)) {
      assertEquals("world fox", stream.read());
      assertEquals("brown dog", stream.read());
      assertNull(stream.read());
      stream.reset();
      assertEquals("world fox", stream.read());
    }
  }
  @Test
  void leadingAndTrailingDelimitersPreserveEmptyEdgeTokens() throws IOException {
    // split(line, -1) kept empty edge tokens; the indexOf walk must re-join them unchanged.
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).build();
    try (ObjectStream<String> stream = new SpellCorrectingTokenStream(
        new ListStream(" teh quikc "), normalizer, " ")) {
      assertEquals(" the quick ", stream.read());
    }
  }

  @Test
  void multiCharacterDelimiterSplitsAndRejoinsLiterally() throws IOException {
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).build();
    try (ObjectStream<String> stream = new SpellCorrectingTokenStream(
        new ListStream("teh::quikc::broen"), normalizer, "::")) {
      assertEquals("the::quick::brown", stream.read());
    }
  }

  @Test
  void duplicateDelimitersAtTheEdgesSurviveTheRoundTrip() throws IOException {
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).build();
    try (ObjectStream<String> stream = new SpellCorrectingTokenStream(
        new ListStream("--teh--broen--"), normalizer, "--")) {
      assertEquals("--the--brown--", stream.read());
    }
  }
}
