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

import org.junit.jupiter.api.Assertions;
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
  void nullTextThrowsIllegalArgumentException() {
    // The CharSequenceNormalizer contract: null is rejected, not passed through.
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(null));
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
  void punctuationPeelingFollowsUnicodeLetterAndNumberCategories() {
    // Characterization for the token guards: the peeling classes are the complement of
    // \p{L} and \p{N}, so Unicode punctuation peels like ASCII, and a combining mark
    // (category Mark) peels off as a suffix rather than staying attached to the core.
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    assertEquals("\u00ABquick\u00BB", norm(normalizer, "\u00ABquikc\u00BB"));
    assertEquals("quick\u0301", norm(normalizer, "quikc\u0301"));
    assertEquals("!!!", norm(normalizer, "!!!"));
  }

  @Test
  void numberAndUrlGuardsHoldAtTheirExactBoundaries() {
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    // Number-like: optional sign, digits with grouping/decimal marks, optional percent.
    assertEquals("+3,14%", norm(normalizer, "+3,14%"));
    assertEquals("-42%", norm(normalizer, "-42%"));
    assertEquals("1.2.3", norm(normalizer, "1.2.3"));
    // URL-like third alternative: a bare domain with a known TLD is never corrected.
    assertEquals("quikc.com", norm(normalizer, "quikc.com"));
    assertEquals("www.quikc.com/broen", norm(normalizer, "www.quikc.com/broen"));
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
  void emptyInputIsPassedThrough() {
    final var normalizer = new SpellCheckingCharSequenceNormalizer(symSpell);
    assertEquals("", norm(normalizer, ""));
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
    assertThrows(NullPointerException.class,
        () -> new SpellCheckingCharSequenceNormalizer((SymSpell) null));
  }
  private boolean numberLike(String core) {
    return new SpellCheckingCharSequenceNormalizer(symSpell).isNumberLike(core);
  }

  @Test
  void numberLikeRejectsShapesTheFormerRegexRejected() {
    // Reject-side pins for the scan structure: at most one trailing percent, a digit required,
    // no letters, no bare sign.
    Assertions.assertFalse(numberLike("5%%"));
    Assertions.assertFalse(numberLike("+%"));
    Assertions.assertFalse(numberLike("1,2a"));
    Assertions.assertFalse(numberLike("+"));
    Assertions.assertFalse(numberLike(""));
    Assertions.assertFalse(numberLike("%"));
    Assertions.assertFalse(numberLike("..,,"));
    Assertions.assertTrue(numberLike("+3,14%"));
    Assertions.assertTrue(numberLike("5%"));
  }

  @Test
  void numberLikeMatchesTheFormerRegexOverGeneratedTokens() {
    // Differential over the token alphabet of the former "[+-]?[\\d.,]*\\d[\\d.,]*%?" guard.
    final java.util.regex.Pattern former =
        java.util.regex.Pattern.compile("[+-]?[\\d.,]*\\d[\\d.,]*%?");
    final char[] alphabet = {'+', '-', '%', '.', ',', '0', '5', '9', 'a'};
    final java.util.Random random = new java.util.Random(42);
    for (int round = 0; round < 20_000; round++) {
      final int length = random.nextInt(7);
      final StringBuilder token = new StringBuilder();
      for (int i = 0; i < length; i++) {
        token.append(alphabet[random.nextInt(alphabet.length)]);
      }
      final String core = token.toString();
      Assertions.assertEquals(former.matcher(core).matches(),
          numberLike(core),
          () -> "core: " + core);
    }
  }
}
