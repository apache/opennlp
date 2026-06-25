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

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TermAnalyzerTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void testNoDimensionsLeavesTokenUnchanged() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().build();
    final Term term = analyzer.analyze("Hello").get(0);
    assertEquals("Hello", term.original());
    assertEquals("Hello", term.normalized());
    assertEquals("Hello", term.peel());
    assertEquals(List.of(), analyzer.dimensions());
  }

  @Test
  void testChainAppliesInCanonicalOrderRegardlessOfBuilderOrder() {
    // accentFold added before caseFold, but the canonical order is caseFold then accentFold.
    final TermAnalyzer analyzer = TermAnalyzer.builder().accentFold().caseFold().build();
    assertEquals(List.of(Dimension.CASE_FOLD, Dimension.ACCENT_FOLD), analyzer.dimensions());
    final String input = "CAF" + cp(0x00C9); // CAFE with capital acute E
    final Term term = analyzer.analyze(input).get(0);
    assertEquals(input, term.original());
    assertEquals("cafe", term.normalized());
    assertEquals("caf" + cp(0x00E9), term.peel()); // before accent folding: lower-case, acute kept
  }

  @Test
  void testStemIsTheTopLayer() {
    final TermAnalyzer analyzer =
        TermAnalyzer.builder().caseFold().stem(new PorterStemmer()).build();
    final Term term = analyzer.analyze("Running").get(0);
    assertEquals("running", term.peel()); // case-folded form, before stemming
    assertEquals("run", term.normalized());
    assertEquals("run", term.at(Dimension.STEM));
  }

  @Test
  void testUnconfiguredCharDimensionComputedLazily() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().build();
    final Term term = analyzer.analyze("HELLO").get(0);
    assertEquals("HELLO", term.normalized());
    assertEquals("hello", term.at(Dimension.CASE_FOLD)); // lazily added on top of the final form
  }

  @Test
  void testStemDimensionWithoutStemmerFailsLoudly() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().caseFold().build();
    final Term term = analyzer.analyze("running").get(0);
    assertThrows(IllegalStateException.class, () -> term.at(Dimension.STEM));
  }

  @Test
  void testLemmaWithoutLemmatizerFailsLoudly() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().build();
    final Term term = analyzer.analyze("running").get(0);
    assertThrows(IllegalStateException.class, () -> term.at(Dimension.LEMMA));
  }

  @Test
  void testAnalyzeTextProducesSpans() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().caseFold().build();
    final List<Term> terms = analyzer.analyze("The Cats");
    assertEquals(2, terms.size());
    assertEquals("The", terms.get(0).original());
    assertEquals("the", terms.get(0).normalized());
    assertEquals(new Span(0, 3), terms.get(0).span());
    assertEquals("Cats", terms.get(1).original());
    assertEquals(new Span(4, 8), terms.get(1).span());
  }

  @Test
  void testAnalyzeTokensHasNoSpan() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().caseFold().build();
    final List<Term> terms = analyzer.analyze(new String[] {"Cats"}, new String[] {"NNS"});
    assertNull(terms.get(0).span());
    assertEquals("cats", terms.get(0).normalized());
  }

  @Test
  void testAnalyzeTokensRejectsLengthMismatch() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().build();
    assertThrows(IllegalArgumentException.class,
        () -> analyzer.analyze(new String[] {"a", "b"}, new String[] {"X"}));
  }

  @Test
  void testTransformRejectsNonCharacterDimension() {
    assertThrows(IllegalArgumentException.class, () -> TermAnalyzer.builder()
        .transform(Dimension.STEM, CaseFoldCharSequenceNormalizer.getInstance()));
  }

  @Test
  void testLemmaWithLemmatizerAndTag() {
    final Lemmatizer lemmatizer = new Lemmatizer() {
      @Override
      public String[] lemmatize(String[] tokens, String[] tags) {
        return new String[] {"be"};
      }

      @Override
      public List<List<String>> lemmatize(List<String> tokens, List<String> tags) {
        return List.of(List.of("be"));
      }
    };
    final TermAnalyzer analyzer =
        TermAnalyzer.builder().caseFold().lemmatize(lemmatizer).build();
    final Term term = analyzer.analyze(new String[] {"was"}, new String[] {"VBD"}).get(0);
    assertEquals("be", term.normalized());
  }

  @Test
  void testAnalyzeCharSequenceFailsLoudlyWhenLemmaConfigured() {
    // analyze(CharSequence) has no POS tags, so a configured LEMMA layer cannot be satisfied; it
    // fails loud rather than silently dropping the layer. Callers needing lemmas use analyze(tokens,
    // tags).
    final Lemmatizer lemmatizer = new Lemmatizer() {
      @Override
      public String[] lemmatize(String[] tokens, String[] tags) {
        return tokens.clone();
      }

      @Override
      public List<List<String>> lemmatize(List<String> tokens, List<String> tags) {
        return List.of(tokens);
      }
    };
    final TermAnalyzer analyzer = TermAnalyzer.builder().lemmatize(lemmatizer).build();
    final IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> analyzer.analyze("running"));
    assertTrue(e.getMessage().contains("part-of-speech"), e.getMessage());
  }

  @Test
  void testConfusableFoldComposesWithCaseFold() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().caseFold().confusableFold().build();
    final String spoof = "P" + cp(0x0430) + "yp" + cp(0x0430) + "l"; // Paypal with Cyrillic a's
    assertEquals(Confusables.skeleton("paypal"), analyzer.analyze(spoof).get(0).normalized());
  }

  @Test
  void testAtIsMemoized() {
    final TermAnalyzer analyzer = TermAnalyzer.builder().build();
    final Term term = analyzer.analyze("HELLO").get(0);
    final String first = term.at(Dimension.CASE_FOLD);
    assertSame(first, term.at(Dimension.CASE_FOLD));
  }

  @Test
  void testWhitespaceTargetIsConfigurable() {
    final CharClass lineFold = CharClass.of(CodePointSet.of('\n', '\t'), '\n');
    final TermAnalyzer analyzer = TermAnalyzer.builder().whitespace(lineFold::collapse).build();
    final Term term = analyzer.analyze(new String[] {"a\n\n\tb"}, new String[] {"X"}).get(0);
    assertEquals("a\nb", term.normalized());
  }

  @Test
  void testCaseFoldLocaleAppliesTurkishRules() {
    final TermAnalyzer analyzer =
        TermAnalyzer.builder().caseFold(Locale.forLanguageTag("tr")).build();
    assertEquals(cp(0x0131), analyzer.analyze("I").get(0).normalized()); // dotless lowercase i
  }

  @Test
  void testAccentFoldScopeFoldsLatin() {
    final TermAnalyzer analyzer = TermAnalyzer.builder()
        .accentFold(Set.of(Character.UnicodeScript.LATIN), false).build();
    assertEquals("cafe", analyzer.analyze("caf" + cp(0x00E9)).get(0).normalized()); // cafe + acute
  }

  @Test
  void testMaxTokenLengthChopsTokens() {
    final List<Term> terms = TermAnalyzer.builder().maxTokenLength(3).build().analyze("abcdefg");
    assertEquals(3, terms.size());
    assertEquals("abc", terms.get(0).original());
    assertEquals("def", terms.get(1).original());
    assertEquals("g", terms.get(2).original());
  }

  @Test
  void testAnalyzeEmptyTextProducesNoTerms() {
    assertEquals(List.of(), TermAnalyzer.builder().caseFold().build().analyze(""));
  }

  @Test
  void testWhitespaceOnlyInputHasNoWordTerms() {
    assertEquals(List.of(), TermAnalyzer.builder().build().analyze("   \t  "));
  }

  @Test
  void testAtDimensionBelowFinalIsAppliedOnTop() {
    // Final dimension is STEM; asking for NFC applies it on top of the stem (documented behavior).
    final TermAnalyzer analyzer =
        TermAnalyzer.builder().caseFold().stem(new PorterStemmer()).build();
    final Term term = analyzer.analyze("Running").get(0);
    assertEquals("run", term.normalized());
    assertEquals("run", term.at(Dimension.NFC));
  }
}
