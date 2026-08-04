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

package opennlp.tools.sentdetect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;

/**
 * Proves that the bounded-window abbreviation veto of
 * {@link SentenceDetectorME#isAcceptableBreak(CharSequence, int, int)} decides exactly what the
 * previous full-text scan decided.
 * <p>
 * {@link LegacyAbbreviationSentenceDetectorME} holds that previous implementation verbatim and
 * is the oracle here. Agreement is asserted twice over: for every single {@code (fromIndex,
 * candidateIndex)} pair of a set of hand-written and seeded random texts, and for the sentence
 * spans a full {@link SentenceDetectorME#sentPosDetect(CharSequence)} run produces on a corpus.
 */
public class SentenceDetectorMEAbbreviationEquivalenceTest extends AbstractSentenceDetectorTest {

  private static SentenceModel sentdetectModel;

  /**
   * A model without an abbreviation dictionary, the shape every sentence model published by the
   * project has. Detectors built from it never enter the veto at all.
   */
  private static SentenceModel modelWithoutAbbreviations;
  private static String corpus;

  @BeforeAll
  public static void prepareResources() throws IOException {
    final SentenceDetectorFactory factory = new SentenceDetectorFactory(
        "eng", true, loadAbbDictionary(Locale.ENGLISH), null);
    sentdetectModel = train(factory, Locale.ENGLISH);
    Assertions.assertNotNull(sentdetectModel);
    modelWithoutAbbreviations = train(
        new SentenceDetectorFactory("eng", true, null, null), Locale.ENGLISH);
    Assertions.assertNull(modelWithoutAbbreviations.getAbbreviations());
    corpus = readCorpus();
    Assertions.assertTrue(corpus.length() > 10_000, "corpus too small to be meaningful");
  }

  /**
   * The pre-rewrite implementation, the oracle every comparison below is made against.
   *
   * @see LegacyAbbreviationSentenceDetectorME#decide
   */
  private static boolean legacyIsAcceptableBreak(Dictionary abbDict, CharSequence s,
      int fromIndex, int candidateIndex) {
    return LegacyAbbreviationSentenceDetectorME.decide(abbDict, s, fromIndex, candidateIndex);
  }

  /*
   * ------------------------------------------------------------------------------------------
   * Differential harness.
   * ------------------------------------------------------------------------------------------
   */

  private static Dictionary dictionaryOf(boolean caseSensitive, String... entries) {
    final Dictionary dict = new Dictionary(caseSensitive);
    for (String entry : entries) {
      dict.put(new StringList(entry));
    }
    return dict;
  }

  /**
   * Compares old and new for every {@code 0 <= fromIndex <= candidateIndex < text.length()},
   * that is over the whole contract domain of the method.
   *
   * @return The number of decisions compared.
   */
  private static long assertAgreesEverywhere(Dictionary dict, String text) {
    final SentenceDetectorME detector = new SentenceDetectorME(sentdetectModel, dict);
    long compared = 0;
    for (int fromIndex = 0; fromIndex < text.length(); fromIndex++) {
      for (int candidateIndex = fromIndex; candidateIndex < text.length(); candidateIndex++) {
        final boolean expected = legacyIsAcceptableBreak(dict, text, fromIndex, candidateIndex);
        final boolean actual = detector.isAcceptableBreak(text, fromIndex, candidateIndex);
        if (expected != actual) {
          Assertions.fail(String.format(Locale.ROOT,
              "disagreement at fromIndex=%d candidateIndex=%d: legacy=%b, new=%b, text=<%s>",
              fromIndex, candidateIndex, expected, actual, text));
        }
        compared++;
      }
    }
    return compared;
  }

  /**
   * As {@link #assertAgreesEverywhere}, and additionally requires that the oracle vetoes at
   * least once, so a case cannot pass by never reaching the interesting branch.
   */
  private static long assertAgreesEverywhereAndVetoes(Dictionary dict, String text) {
    final long compared = assertAgreesEverywhere(dict, text);
    int vetoes = 0;
    for (int fromIndex = 0; fromIndex < text.length(); fromIndex++) {
      for (int candidateIndex = fromIndex; candidateIndex < text.length(); candidateIndex++) {
        if (!legacyIsAcceptableBreak(dict, text, fromIndex, candidateIndex)) {
          vetoes++;
        }
      }
    }
    Assertions.assertTrue(vetoes > 0,
        "the oracle never vetoes here, so the case proves nothing: " + text);
    return compared;
  }

  /*
   * ------------------------------------------------------------------------------------------
   * Edge cases.
   * ------------------------------------------------------------------------------------------
   */

  @Test
  void testAbbreviationAtDocumentStart() {
    final Dictionary dict = dictionaryOf(true, "Mr.", "Dr.");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict, "Mr. Smith left.") > 0);
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict, "Dr.") > 0);
  }

  @Test
  void testAbbreviationAtDocumentEnd() {
    final Dictionary dict = dictionaryOf(true, "etc.", "Mr.");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict, "Bring apples, pears, etc.") > 0);
    // The entry reaches exactly the last index, and one that reaches past the end.
    Assertions.assertTrue(assertAgreesEverywhere(dict, "Bring apples, pears, etc") > 0);
  }

  @Test
  void testOverlappingCandidates() {
    // "U.S." and "S." overlap, and "..." makes several candidate ends adjacent.
    final Dictionary dict = dictionaryOf(true, "U.S.", "S.", "e.g.", "g.");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict, "The U.S. and e.g. Spain...") > 0);
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict, "U.S.S.R. is gone.") > 0);
  }

  @Test
  void testEmptyDictionary() {
    final Dictionary dict = dictionaryOf(true);
    Assertions.assertTrue(assertAgreesEverywhere(dict, "A sentence. Another one.") > 0);
    final Dictionary insensitive = dictionaryOf(false);
    Assertions.assertTrue(assertAgreesEverywhere(insensitive, "A sentence. Another one.") > 0);
  }

  @Test
  void testNullDictionaryAlwaysAccepts() {
    final SentenceDetectorME detector = new SentenceDetectorME(sentdetectModel, (Dictionary) null);
    final String text = "Mr. Smith left. Dr. Jones stayed.";
    for (int fromIndex = 0; fromIndex < text.length(); fromIndex++) {
      for (int candidateIndex = fromIndex; candidateIndex < text.length(); candidateIndex++) {
        Assertions.assertTrue(detector.isAcceptableBreak(text, fromIndex, candidateIndex));
      }
    }
  }

  @Test
  void testDictionaryEntryNotPresentInText() {
    final Dictionary dict = dictionaryOf(true, "Zzz.", "Qqq.", "Mr.");
    Assertions.assertTrue(assertAgreesEverywhere(dict, "Nothing here matches at all.") > 0);
  }

  @Test
  void testEntryLongerThanText() {
    final Dictionary dict = dictionaryOf(true, "averyveryverylongabbreviation.");
    Assertions.assertTrue(assertAgreesEverywhere(dict, "short.") > 0);
  }

  @Test
  void testMultiCharacterAndUnicodeAbbreviations() {
    final Dictionary dict = dictionaryOf(true, "z.B.", "Abb.", "Straße.", "№.", "ç.");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict,
        "Siehe z.B. Abb. 3 in der Straße. Und №. 7 sowie ç. hier.") > 0);
  }

  @Test
  void testSupplementaryCodePointsInTextAndDictionary() {
    // U+1D400 MATHEMATICAL BOLD CAPITAL A and U+10400 DESERET CAPITAL LONG I, the latter has a
    // lower case mapping, so it exercises the case-folded window on a surrogate pair.
    final String bold = new String(Character.toChars(0x1D400));
    final String deseret = new String(Character.toChars(0x10400));
    final String deseretLower = new String(Character.toChars(0x10428));
    final Dictionary sensitive = dictionaryOf(true, bold + ".", deseret + ".", "Mr.");
    final String text = "A " + bold + ". B " + deseret + ". C " + deseretLower + ". Mr. D.";
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(sensitive, text) > 0);
    final Dictionary insensitive = dictionaryOf(false, bold + ".", deseret + ".", "Mr.");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(insensitive, text) > 0);
    // A lone high surrogate, so the window can start or end on an unpaired half.
    final Dictionary loneHalf = dictionaryOf(false, "\uD801.", "Mr.");
    Assertions.assertTrue(assertAgreesEverywhere(loneHalf, "x \uD801. y " + deseret + ". Mr. z") > 0);
  }

  @Test
  void testRepeatedAbbreviations() {
    final Dictionary dict = dictionaryOf(true, "Mr.");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict,
        "Mr. A, Mr. B, Mr. C, Mr. D, and Mr. E.") > 0);
  }

  @Test
  void testCaseInsensitiveDictionary() {
    // "i̇." is the decomposed lower case of "İ" (LATIN CAPITAL LETTER I WITH DOT
    // ABOVE), whose single code point lower case mapping is plain "i", so the two do not match.
    // The point is only that both implementations say so.
    final Dictionary dict = dictionaryOf(false, "Mr.", "TEL.", "i̇.");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict,
        "mr. Smith, MR. Jones and Tel. 555 plus İ. and I. here.") > 0);
  }

  @Test
  void testEmptyStringEntry() {
    // An empty entry matches at every position; the oracle can still veto through the
    // preceding-character branch, so the window has to probe length zero as well.
    final Dictionary onlyEmpty = dictionaryOf(true, "");
    Assertions.assertFalse(legacyIsAcceptableBreak(onlyEmpty, "a b", 0, 2),
        "the empty entry has to be able to veto on its own, otherwise this proves nothing");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(onlyEmpty, "a b c d.") > 0);
    final Dictionary dict = dictionaryOf(true, "", "Mr.");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict, "Mr. A said no.") > 0);
  }

  @Test
  void testMultiTokenEntryUsesFirstTokenOnly() {
    final Dictionary dict = new Dictionary(true);
    dict.put(new StringList("Mr.", "Smith"));
    dict.put(new StringList("St.", "Louis"));
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict, "Mr. Smith of St. Louis left.") > 0);
  }

  @Test
  void testApostropheAndOpeningBracketPrefixes() {
    final Dictionary dict = dictionaryOf(true, "Mr.", "cf.");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict,
        "He said 'Mr. X' and (cf. Y) and `Mr. Z` and ´cf. W´ and xMr. V.") > 0);
  }

  @Test
  void testSegmentStartInsideAnAbbreviation() {
    // fromIndex walking through the middle of an abbreviation is what the sentence loop does
    // after accepting a break, and it is where the "match at segment start" branch fires.
    final Dictionary dict = dictionaryOf(true, "Mr.", "r.", ".");
    Assertions.assertTrue(assertAgreesEverywhereAndVetoes(dict, "Mr. Mr. Mr.") > 0);
  }

  /*
   * ------------------------------------------------------------------------------------------
   * Seeded random sweep.
   * ------------------------------------------------------------------------------------------
   */

  @Test
  void testSeededRandomTexts() {
    final String[] pieces = {
        "Mr.", "Dr.", "e.g.", "i.e.", "U.S.A.", "etc.", "St.", "Nr.", "z.B.", "vs.",
        " ", "  ", "\n", "\t", " ", "(", ")", "'", "`", "´", "\"",
        "word", "Word", "WORD", "x", ".", "?", "!", "3.5", "ç", "ä", "Straße",
        new String(Character.toChars(0x10400)), new String(Character.toChars(0x1D400)),
    };
    final Dictionary[] dicts = {
        dictionaryOf(true, "Mr.", "Dr.", "e.g.", "etc.", "St.", "U.S.A.", ".", "Nr."),
        dictionaryOf(false, "mr.", "DR.", "E.g.", "etc.", "st.", "u.s.a.", "z.b.", "vs.",
            "straße", "ç.", new String(Character.toChars(0x10400)) + "."),
        dictionaryOf(false, "notpresent.", "alsonot."),
    };

    final Random random = new Random(20260803L);
    long compared = 0;
    int vetoing = 0;
    for (int run = 0; run < 250; run++) {
      final StringBuilder text = new StringBuilder();
      while (text.length() < 70) {
        text.append(pieces[random.nextInt(pieces.length)]);
      }
      final String sample = text.toString();
      for (Dictionary dict : dicts) {
        compared += assertAgreesEverywhere(dict, sample);
        if (!legacyIsAcceptableBreak(dict, sample, 0, sample.length() - 1)) {
          vetoing++;
        }
      }
    }
    Assertions.assertTrue(compared > 1_500_000L,
        "the sweep degenerated, only " + compared + " decisions compared");
    Assertions.assertTrue(vetoing > 0, "no sampled text ever vetoed");
  }

  /*
   * ------------------------------------------------------------------------------------------
   * Corpus level: identical spans out of sentPosDetect.
   * ------------------------------------------------------------------------------------------
   */

  @Test
  void testSentPosDetectAgreesOnCorpus() throws IOException {
    for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.GERMAN}) {
      assertCorpusRunsAgree(loadAbbDictionary(locale),
          "the shipped abbreviation dictionary of " + locale);
    }
  }

  @Test
  void testSentPosDetectAgreesOnCaseSensitiveDictionary() {
    assertCorpusRunsAgree(dictionaryOf(true, "Mr.", "Mrs.", "Ms.", "Dr.", "St.", "etc.",
        "e.g.", "i.e.", "vs.", "No.", "Jr.", "Sr.", "Prof.", "Inc.", "Ltd."),
        "a case-sensitive dictionary");
  }

  /**
   * The path every user of a published sentence model is on: no abbreviation dictionary at all,
   * so the veto is never entered. This is the case an optimisation of the veto is most likely to
   * regress by accident, and the least likely to be noticed.
   */
  @Test
  void testNoDictionaryConfiguredIsUnchangedOnCorpus() {
    // A model published without an abbreviation dictionary, used through the one argument
    // constructor, and a model that has one but is asked to ignore it.
    assertNoDictionaryRunAgrees(modelWithoutAbbreviations,
        new SentenceDetectorME(modelWithoutAbbreviations));
    assertNoDictionaryRunAgrees(sentdetectModel,
        new SentenceDetectorME(sentdetectModel, (Dictionary) null));
  }

  private static void assertNoDictionaryRunAgrees(SentenceModel model, SentenceDetectorME fixed) {
    final LegacyAbbreviationSentenceDetectorME legacy =
        new LegacyAbbreviationSentenceDetectorME(model, null);

    final Span[] expected = legacy.sentPosDetect(corpus);
    final double[] expectedProbs = legacy.probs();
    final Span[] actual = fixed.sentPosDetect(corpus);
    final double[] actualProbs = fixed.probs();

    Assertions.assertTrue(expected.length > 100,
        "corpus produced too few sentences to be meaningful: " + expected.length);
    Assertions.assertEquals(0, legacy.vetoes(),
        "the veto must not be reachable without a dictionary");
    Assertions.assertArrayEquals(expected, actual);
    Assertions.assertArrayEquals(expectedProbs, actualProbs, 0.0d);
  }

  private static void assertCorpusRunsAgree(Dictionary dict, String description) {
    final SentenceDetectorME fixed = new SentenceDetectorME(sentdetectModel, dict);
    final LegacyAbbreviationSentenceDetectorME legacy =
        new LegacyAbbreviationSentenceDetectorME(sentdetectModel, dict);

    final Span[] expected = legacy.sentPosDetect(corpus);
    final double[] expectedProbs = legacy.probs();
    final Span[] actual = fixed.sentPosDetect(corpus);
    final double[] actualProbs = fixed.probs();

    Assertions.assertTrue(expected.length > 100,
        "corpus produced too few sentences to be meaningful: " + expected.length);
    Assertions.assertTrue(legacy.vetoes() > 0,
        "the veto never fired for " + description + ", so this run proves nothing");
    Assertions.assertArrayEquals(expected, actual, "span mismatch for " + description);
    Assertions.assertArrayEquals(expectedProbs, actualProbs, 0.0d,
        "probability mismatch for " + description);
  }

  /*
   * ------------------------------------------------------------------------------------------
   * Documented differences, outside the contract of the method.
   * ------------------------------------------------------------------------------------------
   */

  @Test
  void testCandidateIndexOutsideTextNoLongerThrows() {
    final Dictionary dict = dictionaryOf(true, "Mr.");
    final SentenceDetectorME detector = new SentenceDetectorME(sentdetectModel, dict);
    final String text = "Mr. x";
    // The previous implementation indexed past the end of the text here.
    Assertions.assertThrows(IndexOutOfBoundsException.class,
        () -> legacyIsAcceptableBreak(dict, text, 0, text.length()));
    Assertions.assertTrue(detector.isAcceptableBreak(text, 0, text.length()));
    Assertions.assertTrue(detector.isAcceptableBreak(text, 0, text.length() + 100));
  }

  @Test
  void testNegativeFromIndexIsStillUndefined() {
    final Dictionary dict = dictionaryOf(true, "Mr.");
    final SentenceDetectorME detector = new SentenceDetectorME(sentdetectModel, dict);
    // A negative segment start makes the character before the first match unreadable. Both
    // implementations fail on it the same way when the match sits at the start of the text.
    final String atStart = "Mr. x";
    Assertions.assertThrows(IndexOutOfBoundsException.class,
        () -> legacyIsAcceptableBreak(dict, atStart, -1, 3));
    Assertions.assertThrows(IndexOutOfBoundsException.class,
        () -> detector.isAcceptableBreak(atStart, -1, 3));
    // They part company when the match is too far left to be relevant: the previous
    // implementation still read index -1 for it, the bounded window never looks there.
    final String far = "Mr. and something else.";
    Assertions.assertThrows(IndexOutOfBoundsException.class,
        () -> legacyIsAcceptableBreak(dict, far, -1, far.length() - 1));
    Assertions.assertTrue(detector.isAcceptableBreak(far, -1, far.length() - 1));
  }

  @Test
  void testCandidateBeforeSegmentStartAcceptsAsBefore() {
    final Dictionary dict = dictionaryOf(true, "Mr.");
    final SentenceDetectorME detector = new SentenceDetectorME(sentdetectModel, dict);
    final String text = "Mr. x";
    Assertions.assertTrue(legacyIsAcceptableBreak(dict, text, 4, 2));
    Assertions.assertTrue(detector.isAcceptableBreak(text, 4, 2));
  }

  /*
   * ------------------------------------------------------------------------------------------
   * Helpers.
   * ------------------------------------------------------------------------------------------
   */

  private static String readCorpus() throws IOException {
    final List<String> lines = new ArrayList<>();
    for (String resource : new String[] {"/opennlp/tools/sentdetect/Sentences.txt",
        "/opennlp/tools/sentdetect/Sentences_DE.txt"}) {
      try (InputStream in = SentenceDetectorMEAbbreviationEquivalenceTest.class
          .getResourceAsStream(resource)) {
        Assertions.assertNotNull(in, resource + " is not on the test classpath");
        final String all = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        for (String line : all.split("\n")) {
          if (!line.isBlank()) {
            lines.add(line.strip());
          }
        }
      }
    }
    // The shipped training corpora happen to contain almost no abbreviations, which would make
    // the comparison runs above vacuous. This tail supplies them, in the shape they occur in.
    lines.add("Mr. Smith called tel. 555 1234 this morning. Mrs. Smith did not.");
    lines.add("Ms. Adams, Mr. Brown and Mrs. Clark met in the hall.");
    lines.add("Ask Mr. Brown or, failing that, Ms. Adams (cf. tel. 555 1234).");
    lines.add("Er wohnt in der S. Bahnstrasse, vgl. S. 12, ca. 30 Minuten entfernt.");
    lines.add("Das gilt z.B. fuer Bek. 4 ff. und lt. V. 7 ugs. auch sonst.");
    lines.add("Siehe ca. 20 Stueck, z. B. Bek. 9, S. 3 f. und ff.");
    return String.join(" ", lines);
  }
}
