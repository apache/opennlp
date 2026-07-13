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

package opennlp.spellcheck.normalizer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.spellcheck.symspell.SymSpell;
import opennlp.spellcheck.symspell.TinyDictionary;
import opennlp.tools.util.normalizer.AggregateCharSequenceNormalizer;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;
import opennlp.tools.util.normalizer.ShrinkCharSequenceNormalizer;
import opennlp.tools.util.normalizer.UrlCharSequenceNormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SpellCheckingCharSequenceNormalizerTest {

  private SymSpell symSpell;

  @BeforeEach
  void setUp() throws Exception {
    symSpell = TinyDictionary.load();
  }

  private static String norm(CharSequenceNormalizer n, String text) {
    return n.normalize(text).toString();
  }

  @Test
  void perTokenCorrectsTypos() {
    // Lower the min length so the 3-letter typos ("teh", "fxo") are also corrected.
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).build();
    assertEquals("the quick brown fox", norm(normalizer, "teh quikc broen fxo"));
  }

  @Test
  void perTokenCorrectsTyposWithDefaultMinLength() {
    // With the default minTokenLength (4) only the >=4-char typos are corrected.
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    assertEquals("quick brown world", norm(normalizer, "quikc broen wrold"));
  }

  @Test
  void perTokenLeavesKnownWordsUntouched() {
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    // Every token is already in the dictionary -> unchanged, including casing.
    assertEquals("the quick brown fox", norm(normalizer, "the quick brown fox"));
  }

  @Test
  void perTokenPreservesWhitespaceRuns() {
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    // "wrold" (5 chars) is corrected; the multiple-space / leading / trailing runs are kept.
    assertEquals("  quick   world  ", norm(normalizer, "  quikc   wrold  "));
  }

  @Test
  void perTokenPreservesSurroundingPunctuation() {
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    assertEquals("\"world\",", norm(normalizer, "\"wrold\","));
    assertEquals("(quick)", norm(normalizer, "(quikc)"));
  }

  @Test
  void perTokenPreservesCasing() {
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    assertEquals("World", norm(normalizer, "Wrold"));
    assertEquals("WORLD", norm(normalizer, "WROLD"));
    assertEquals("world", norm(normalizer, "wrold"));
  }

  @Test
  void minTokenLengthSkipsShortTokens() {
    final var lenient = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(1).build();
    final var strict = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(5).build();
    // "adn" (3 chars) -> "and" only when short tokens are allowed.
    assertEquals("and", norm(lenient, "adn"));
    assertEquals("adn", norm(strict, "adn"));
  }

  @Test
  void skipsNumbersAndUrls() {
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    assertEquals("12345", norm(normalizer, "12345"));
    assertEquals("3.14", norm(normalizer, "3.14"));
    assertEquals("http://example.com/teh", norm(normalizer, "http://example.com/teh"));
    assertEquals("user@example.com", norm(normalizer, "user@example.com"));
  }

  @Test
  void compoundModeRepairsSpaceMerges() {
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .mode(SpellCheckingCharSequenceNormalizer.Mode.COMPOUND).build();
    assertEquals("hello world", norm(normalizer, "helloworld"));
  }

  @Test
  void compoundModeCorrectsPhrase() {
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .mode(SpellCheckingCharSequenceNormalizer.Mode.COMPOUND).build();
    assertEquals("the quick brown fox", norm(normalizer, "teh quikc broen fxo"));
  }

  @Test
  void composesInsideAggregateNormalizer() {
    final var spell = new SpellCheckingCharSequenceNormalizer(symSpell);
    final CharSequenceNormalizer aggregate = new AggregateCharSequenceNormalizer(
        UrlCharSequenceNormalizer.getInstance(),
        ShrinkCharSequenceNormalizer.getInstance(),
        spell);
    // URL removed, repeated spaces shrunk, then typos corrected.
    // "quikc"/"broen" are >=4 chars (corrected); "fox" stays as it is already known.
    final String result = norm(aggregate, "quikc    broen http://example.com fox");
    assertEquals("quick brown fox", result);
  }

  @Test
  void emptyAndNullInputsArePassedThrough() {
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    assertEquals("", norm(normalizer, ""));
    org.junit.jupiter.api.Assertions.assertNull(normalizer.normalize(null));
  }

  @Test
  void requestedEditDistanceIsClampedToTheEngineMax() {
    // The tiny engine allows max distance 2; requesting 5 must clamp, not throw.
    assertEquals(2, symSpell.maxEditDistance());
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).maxEditDistance(5).build();
    assertEquals("the quick brown fox", norm(normalizer, "teh quikc broen fxo"));
  }

  @Test
  void deserializedInstanceIsInertUntilCheckerReattached() throws Exception {
    final var original = new SpellCheckingCharSequenceNormalizer(symSpell);

    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(original);
    }
    final SpellCheckingCharSequenceNormalizer restored;
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
      restored = (SpellCheckingCharSequenceNormalizer) ois.readObject();
    }

    // The transient checker is gone, so normalize must fail clearly rather than NPE.
    assertThrows(IllegalStateException.class, () -> restored.normalize("wrold"));

    // Re-attaching a checker yields a working copy that keeps the original settings.
    final var reattached = restored.withSpellChecker(symSpell);
    assertEquals("world", norm(reattached, "wrold"));
  }

  @Test
  void nullCheckerIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new SpellCheckingCharSequenceNormalizer((SymSpell) null));
  }

  @Test
  void perTokenTreatsNoBreakSpaceAsTokenBoundary() {
    // Since 3.0 PER_TOKEN boundaries use the Unicode White_Space set, which includes the
    // no-break spaces; both sides are corrected and the separator is copied verbatim
    // (Character.isWhitespace excluded the Zs no-break spaces).
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).build();
    assertEquals("the" + cp(0x00A0) + "fox",
        norm(normalizer, "teh" + cp(0x00A0) + "fxo"));
    assertEquals("the" + cp(0x202F) + "fox",
        norm(normalizer, "teh" + cp(0x202F) + "fxo"));
  }

  @Test
  void perTokenTreatsNextLineControlAsTokenBoundary() {
    // U+0085 NEL carries the Unicode White_Space property, so since 3.0 it separates
    // tokens (Character.isWhitespace excludes it).
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).build();
    assertEquals("the" + cp(0x0085) + "fox",
        norm(normalizer, "teh" + cp(0x0085) + "fxo"));
  }

  @Test
  void perTokenTreatsInformationSeparatorAsPartOfTheToken() {
    // The U+001C..U+001F information separators are not Unicode White_Space, so since 3.0
    // they no longer separate tokens; the joined token has no dictionary entry within
    // reach and passes through unchanged (Character.isWhitespace treated U+001C as
    // whitespace and corrected both sides).
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).build();
    String input = "teh" + cp(0x001C) + "fxo";
    assertEquals(input, norm(normalizer, input));
  }

  @Test
  void perTokenTreatsLineSeparatorAsTokenBoundary() {
    // U+2028 is whitespace under both Character.isWhitespace and Unicode White_Space.
    final var normalizer = SpellCheckingCharSequenceNormalizer.builder(symSpell)
        .minTokenLength(3).build();
    assertEquals("the" + cp(0x2028) + "fox",
        norm(normalizer, "teh" + cp(0x2028) + "fxo"));
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }
}
