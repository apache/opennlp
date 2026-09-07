/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

package opennlp.tools.stemmer.hunspell;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.StringUtil;

/** Original compound and morphology fixtures. */
class HunspellCompletionTest {

  private static final String COMPOUND = "COMPOUNDFLAG C\nCOMPOUNDMIN 1\n";
  private static final String HUNGARIAN = COMPOUND + "LANG hu\nCOMPOUNDWORDMAX 2\n";
  private static final String THREE_WORDS = "3\nray/C\nme/C\nfa/C";

  /**
   * An original dictionary with expected recognition and stems.
   *
   * @param name The case identifier.
   * @param affix The affix definitions.
   * @param words The dictionary entries.
   * @param input The text to analyze.
   * @param stems The expected stems.
   * @param accepted The native recognition expectation.
   */
  private record Example(String name, String affix, String words, String input,
                         List<String> stems, boolean accepted) {
    /** {@inheritDoc} */
    @Override
    public String toString() {
      return name;
    }
  }

  /** {@return compound and morphology cases with independently written data} */
  private static Stream<Example> examples() {
    return Stream.of(
        new Example("legacy-lemma", "LEMMA_PRESENT L\nSFX A Y 1\nSFX A 0 s .\n",
            "1\nfeet/AL st:foot\n", "feets", List.of("foot"), true),
        new Example("syllable-c-reject", HUNGARIAN + "COMPOUNDSYLLABLE 5 aeiouy\n"
            + "SYLLABLENUM klmc\nSFX c Y 1\nSFX c 0 s .\n",
            THREE_WORDS + "c\n", "raymefas", List.of("raymefas"), false),
        new Example("syllable-c-accept", HUNGARIAN + "COMPOUNDSYLLABLE 6 aeiouy\n"
            + "SYLLABLENUM klmc\nSFX c Y 1\nSFX c 0 s .\n",
            THREE_WORDS + "c\n", "raymefas", List.of("ray", "me", "fa"), true),
        new Example("syllable-j-reject", HUNGARIAN + "COMPOUNDSYLLABLE 4 aeiouy\n"
            + "SYLLABLENUM klmc\nSFX J Y 1\nSFX J 0 s .\n",
            THREE_WORDS + "J\n", "raymefas", List.of("raymefas"), false),
        new Example("syllable-i-with-j", HUNGARIAN + "COMPOUNDSYLLABLE 4 aeiouy\n"
            + "SYLLABLENUM klmc\nSFX I Y 1\nSFX I 0 s .\n",
            THREE_WORDS + "IJ\n", "raymefas", List.of("raymefas"), false),
        new Example("syllable-i-without-j", HUNGARIAN + "COMPOUNDSYLLABLE 4 aeiouy\n"
            + "SYLLABLENUM klmc\nSFX I Y 1\nSFX I 0 s .\n",
            THREE_WORDS + "I\n", "raymefas", List.of("ray", "me", "fa"), true),
        new Example("syllable-terminal-inflection", HUNGARIAN + "COMPOUNDSYLLABLE 4 aeiouy\n"
            + "SFX A Y 1\nSFX A 0 a .\n", THREE_WORDS + "A\n",
            "raymefaa", List.of("ray", "me", "fa"), true),
        new Example("syllable-unaffixed-i", HUNGARIAN + "COMPOUNDSYLLABLE 3 aeiouy\n",
            THREE_WORDS + "I\n", "raymefa", List.of("ray", "me", "fa"), true),
        new Example("syllable-prefix-word-count", HUNGARIAN + "COMPOUNDSYLLABLE 2 aeiou\n"
            + "PFX A Y 1\nPFX A 0 reco .\n", "2\nme/CA\nfa/C\n",
            "recomefa", List.of("recomefa"), false),
        new Example("multiple-triple-junctions", COMPOUND + "CHECKCOMPOUNDTRIPLE\nSIMPLIFIEDTRIPLE\n",
            "2\nmill/C\nloom/C\n", "milloommilloom", List.of("mill", "loom"), true),
        new Example("multiple-pattern-junctions", COMPOUND + "CHECKCOMPOUNDPATTERN 1\n"
            + "CHECKCOMPOUNDPATTERN er b X\n", "2\nriver/C\nboat/C\n",
            "rivXoatrivXoat", List.of("rivXoatrivXoat"), false),
        new Example("compound-cross-product", COMPOUND + "COMPOUNDPERMITFLAG P\n"
            + "PFX A Y 1\nPFX A 0 re .\nSFX B Y 1\nSFX B 0 s/P .\n",
            "2\nriver/CAB\nboat/C\n", "reriversboat", List.of("river", "boat"), true),
        new Example("compound-cross-product-no-permit", COMPOUND + "COMPOUNDPERMITFLAG P\n"
            + "PFX A Y 1\nPFX A 0 re .\nSFX B Y 1\nSFX B 0 s .\n",
            "2\nriver/CAB\nboat/C\n", "reriversboat", List.of("reriversboat"), false),
        new Example("optional-affix-condition", "SFX A Y 1\nSFX A 0 s\n",
            "1\ncard/A\n", "cards", List.of("card"), true),
        new Example("optional-condition-with-morphology", "SFX A Y 1\nSFX A 0 s is:plural\n",
            "1\ncard/A\n", "cards", List.of("cards"), false),
        new Example("obsolete-compound-first", "COMPOUNDFIRST V\n",
            "2\nriver/V\nboat/V\n", "riverboat", List.of("riverboat"), false),
        new Example("obsolete-compound-last", "COMPOUNDLAST V\n",
            "2\nriver/V\nboat/V\n", "riverboat", List.of("riverboat"), false),
        new Example("obsolete-only-root", "ONLYROOT V\nSFX A Y 1\nSFX A 0 s .\n",
            "1\ncard/VA\n", "cards", List.of("card"), true),
        new Example("obsolete-hungarian-linking-vowel", "LANG hu\nHU_KOTOHANGZO V\n"
            + "SFX A Y 1\nSFX A 0 s .\n", "1\ncard/VA\n", "cards", List.of("card"), true),
        new Example("generation-option", "GENERATE 1\nSFX A Y 1\nSFX A 0 s .\n",
            "1\ncard/A\n", "cards", List.of("card"), true),
        new Example("compound-word-with-space", COMPOUND,
            "3\nriver/C\nboat/C\nriver boat\n", "riverboat", List.of("riverboat"), false),
        new Example("compound-affixed-duplicate", COMPOUND + "CHECKCOMPOUNDDUP\n"
            + "COMPOUNDPERMITFLAG P\nSFX A Y 1\nSFX A 0 s/P .\n",
            "1\nriver/CA\n", "riversriver", List.of("riversriver"), false),
        // the reference spell checker rejects these forms while its analyzer stems them
        new Example("turkish-capitalized-name", "LANG tr_TR\n", "1\nİpek\n",
            "İPEK", List.of("İpek"), false),
        new Example("azerbaijani-capitalized-name", "LANG az_AZ\n", "1\nİpek\n",
            "İPEK", List.of("İpek"), false),
        new Example("compound-pattern-suffix-flag", COMPOUND + "COMPOUNDPERMITFLAG P\n"
            + "CHECKCOMPOUNDPATTERN 1\nCHECKCOMPOUNDPATTERN s/X b\n"
            + "SFX A Y 1\nSFX A 0 s/PX .\n", "2\nriver/CA\nboat/C\n",
            "riversboat", List.of("riversboat"), false),
        new Example("compound-pattern-prefix-flag", COMPOUND + "COMPOUNDPERMITFLAG P\n"
            + "CHECKCOMPOUNDPATTERN 1\nCHECKCOMPOUNDPATTERN r r/X\n"
            + "PFX A Y 1\nPFX A 0 re/PX .\n", "2\nriver/C\nboat/CA\n",
            "riverreboat", List.of("riverreboat"), false),
        new Example("compound-cross-double-suffix", COMPOUND + "COMPOUNDPERMITFLAG P\n"
            + "COMPOUNDMORESUFFIXES\nPFX R Y 1\nPFX R 0 re .\n"
            + "SFX A Y 1\nSFX A 0 er/BP .\nSFX B Y 1\nSFX B 0 s/P .\n",
            "2\nboat/CAR\nriver/C\n", "reboatersriver", List.of("reboatersriver"), false),
        new Example("compound-cross-double-suffix-final", COMPOUND + "COMPOUNDPERMITFLAG P\n"
            + "COMPOUNDMORESUFFIXES\nPFX R Y 1\nPFX R 0 re/P .\n"
            + "SFX A Y 1\nSFX A 0 er/B .\nSFX B Y 1\nSFX B 0 s .\n",
            "2\nboat/CAR\nriver/C\n", "riverreboaters", List.of("river", "boat"), true),
        new Example("compound-complex-prefix", COMPOUND + "COMPLEXPREFIXES\n"
            + "COMPOUNDMORESUFFIXES\nPFX A Y 1\nPFX A 0 re/B .\n"
            + "PFX B Y 1\nPFX B 0 un .\n", "2\nriver/CA\nboat/C\n",
            "unreriverboat", List.of("river", "boat"), true),
        new Example("compound-complex-prefix-suffix", COMPOUND + "COMPLEXPREFIXES\n"
            + "COMPOUNDMORESUFFIXES\nCOMPOUNDPERMITFLAG P\n"
            + "PFX A Y 1\nPFX A 0 re/B .\nPFX B Y 1\nPFX B 0 un .\n"
            + "SFX S Y 1\nSFX S 0 s/P .\n", "2\nriver/CAS\nboat/C\n",
            "unreriversboat", List.of("river", "boat"), true),
        // deviations listed in the manual: Unicode case mapping, the suggester-based
        // rejection of multi-part compounds, and numeric tokens
        new Example("dotted-capital-i", "", "1\nimply\n", "İmply", List.of("imply"), false),
        new Example("dotted-capital-i-all-caps", "", "1\nİzmir\n", "İZMİR", List.of("İzmir"), true),
        // KEEPCASE with CHECKSHARPS admits the SS spelling of an all-uppercase form only
        new Example("keepcase-sharp-s-double-s", "CHECKSHARPS\nKEEPCASE k\n", "1\nmüßig/k\n",
            "MÜSSIG", List.of("müßig"), true),
        new Example("keepcase-capital-sharp-s", "CHECKSHARPS\nKEEPCASE k\n", "1\nmüßig/k\n",
            "MÜẞIG", List.of("MÜẞIG"), false),
        new Example("keepcase-sharp-s-capitalized", "CHECKSHARPS\nKEEPCASE k\n", "1\nmüßig/k\n",
            "Müßig", List.of("müßig"), true),
        new Example("multi-part-compound-near-listed-word", "TRY esianrtolcdugmphbyfvkwz\n"
            + "COMPOUNDFLAG x\n", "5\nfoo/x\nbar/x\nbaz/x\ngoobar\ngoobarbaz\n",
            "foobarbaz", List.of("foo", "bar", "baz"), false),
        new Example("numeric-token", "", "1\nfoo\n", "1.5", List.of("1.5"), true));
  }

  /**
   * Tests Java stems under strict loading.
   *
   * @param example The fixture.
   * @throws IOException If loading fails.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("examples")
  void testStems(Example example) throws IOException {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(("SET UTF-8\n" + example.affix()).getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(example.words().getBytes(StandardCharsets.UTF_8)));
    Assertions.assertEquals(example.stems(), new HunspellStemmer(dictionary).stemAll(example.input()));
  }

  /**
   * The fixtures whose recognition deliberately differs from the recorded reference
   * spell-checker outcome, each with the manual's reason.
   */
  private static final Map<String, String> RECOGNITION_DEVIATIONS = Map.of(
      "turkish-capitalized-name", "the reference checker rejects what its analyzer stems",
      "azerbaijani-capitalized-name", "the reference checker rejects what its analyzer stems",
      "dotted-capital-i", "a dotted capital I is lowercased outside the Turkic languages",
      "multi-part-compound-near-listed-word", "the reference rejects it through its suggester",
      "numeric-token", "numbers are accepted natively before any lookup");

  /**
   * Tests recognition against the outcome recorded from the reference spell checker.
   * A fixture listed in {@link #RECOGNITION_DEVIATIONS} must differ, so a stale entry
   * fails too.
   *
   * @param example The fixture.
   * @throws IOException If loading fails.
   */
  @ParameterizedTest(name = "recognition {0}")
  @MethodSource("examples")
  void testRecognitionAgainstReference(Example example) throws IOException {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(("SET UTF-8\n" + example.affix()).getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(example.words().getBytes(StandardCharsets.UTF_8)));
    final boolean recognized = !new HunspellStemmer(dictionary).analyze(example.input()).isEmpty();
    final String deviation = RECOGNITION_DEVIATIONS.get(example.name());
    Assertions.assertEquals(deviation == null, recognized == example.accepted(),
        deviation == null ? "recognition differs from the reference" : deviation);
  }

  /**
   * Requires a public analysis operation preserving entry and affix fields.
   *
   * @throws Exception If reflection, loading, or analysis fails.
   */
  @Test
  void testMorphologicalAnalysis() throws Exception {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(("SFX A Y 1\nSFX A 0 s . is:plural\n")
            .getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream("2\ncard/A po:noun\ncard/A po:verb\n".getBytes(StandardCharsets.UTF_8)));
    final HunspellStemmer stemmer = new HunspellStemmer(dictionary);
    Assertions.assertEquals(List.of("st:card po:noun is:plural", "st:card po:verb is:plural"),
        stemmer.analyze("cards"));
    Assertions.assertEquals(List.of(), stemmer.analyze("unlisted"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> stemmer.analyze(null));
    Assertions.assertEquals(List.of(), stemmer.analyze(""));
    Assertions.assertThrows(UnsupportedOperationException.class, () -> stemmer.analyze("cards").clear());
  }

  /**
   * Checks the documented maximum number of CHECKSHARPS case variants.
   *
   * @throws IOException If fixture loading fails.
   */
  @Test
  void testSharpVariantLimit() throws IOException {
    final StringBuilder words = new StringBuilder("129\nSSSSSSSSSSSS\n");
    for (int mask = 0; mask < 64; mask++) {
      final StringBuilder entry = new StringBuilder();
      for (int bit = 0; bit < 6; bit++) {
        entry.append((mask & (1 << bit)) == 0 ? "ss" : "ß");
      }
      words.append(entry).append('\n');
      words.append(StringUtil.toUpperCase(entry.substring(0, 1))).append(entry.substring(1)).append('\n');
    }
    final HunspellStemmer stemmer = new HunspellStemmer(HunspellDictionary.load(
        new ByteArrayInputStream("CHECKSHARPS\n".getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(words.toString().getBytes(StandardCharsets.UTF_8))));
    Assertions.assertTrue(stemmer.stemAll("SSSSSSSSSSSS").size() <= 64);
  }

  /**
   * Checks shared use and protection of dictionary flags returned for inspection.
   *
   * @throws Exception If fixture loading or a worker fails.
   */
  @Test
  void testSharedMorphologyAndImmutableFlags() throws Exception {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream("SFX A Y 1\nSFX A 0 s . is:plural\n".getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream("2\ncard/A po:noun\ncard/A po:verb\n".getBytes(StandardCharsets.UTF_8)));
    dictionary.lookup("card").getFirst()[0] = 'Z';
    dictionary.lookup("card").clear();
    final HunspellStemmer stemmer = new HunspellStemmer(dictionary);
    final List<String> expected = List.of("st:card po:noun is:plural", "st:card po:verb is:plural");
    try (var executor = Executors.newFixedThreadPool(4)) {
      final List<Callable<List<String>>> tasks = new ArrayList<>();
      for (int i = 0; i < 64; i++) {
        tasks.add(() -> {
          Assertions.assertEquals(List.of("card"), stemmer.stemAll("cards"));
          Assertions.assertEquals(List.of(), stemmer.analyze("unlisted"));
          return stemmer.analyze("cards");
        });
      }
      for (var result : executor.invokeAll(tasks)) {
        Assertions.assertEquals(expected, result.get());
      }
    }
  }

  /**
   * Supplies morphology expected from the native reference.
   *
   * @return Affix content, dictionary content, input, and expected analysis.
   */
  private static Stream<String[]> morphology() {
    return Stream.of(
        new String[] {"SFX A Y 1\nSFX A 0 s .\n", "1\ncard/A\n", "cards", "st:card fl:A"},
        new String[] {"SFX A Y 1\nSFX A 0 s . is:plural\n", "1\ncard/A po:noun\n",
            "cards", "st:card po:noun is:plural"},
        new String[] {"AM 2\nAM st:foot ts:present\nAM is:plural\nSFX A Y 1\nSFX A 0 s . 2\n",
            "1\nfeet/A\t1\n", "feets", "st:foot ts:present is:plural"},
        new String[] {"PFX B Y 1\nPFX B 0 re . dp:again\nSFX A Y 1\nSFX A 0 s . is:plural\n",
            "1\ngo/AB po:verb\n", "regos", "dp:again st:go po:verb is:plural"},
        new String[] {COMPOUND + "SFX A Y 1\nSFX A 0 s . is:plural\n",
            "2\nriver/C po:noun\nboat/CA po:noun\n", "riverboats",
            "pa:river st:river po:noun pa:boats st:boat po:noun is:plural"},
        new String[] {"SFX A Y 1\nSFX A 0 s . is:plural\n", "1\ncard/A custom\n",
            "cards", "st:card is:plural"},
        new String[] {"NEEDAFFIX X\nSFX A Y 1\nSFX A 0 0 .\n", "1\nfoo/XA\n", "foo", "st:foo fl:A"},
        new String[] {"PFX C Y 1\nPFX C 0 pre .\n", "1\nfoo/C\n", "prefoo", "pre st:foo fl:C"},
        new String[] {"PFX C Y 1\nPFX C 0 pre .\n", "1\nfoo/C po:noun\n", "prefoo", "pre st:foo po:noun"},
        new String[] {"PFX B Y 1\nPFX B 0 re .\nSFX A Y 1\nSFX A 0 s . is:plural\n",
            "1\ngo/AB po:verb\n", "regos", "fl:B st:go po:verb is:plural"},
        new String[] {"PFX B Y 1\nPFX B 0 re . dp:again\nSFX A Y 1\nSFX A 0 s .\n",
            "1\ngo/AB\n", "regos", "dp:again st:go fl:A"},
        new String[] {"SFX A Y 1\nSFX A 0 er/B .\nSFX B Y 1\nSFX B 0 s .\n",
            "1\nwalk/A po:verb\n", "walkers", "st:walk po:verb fl:A fl:B"},
        new String[] {COMPOUND, "2\nfoo/C id:1\nbar/C\n", "foobar", "pa:foo st:foo id:1 pa:bar"},
        new String[] {COMPOUND, "3\nfoo/C\nbar/C id:2\nbaz/C\n", "foobarbaz",
            "pa:foo st:foo pa:bar st:bar id:2 pa:baz"},
        new String[] {COMPOUND + "SFX A Y 1\nSFX A 0 s .\n", "2\nfoo/C id:1\nbar/CA\n", "foobars",
            "pa:foo st:foo id:1 pa:bars st:bar fl:A"},
        new String[] {"SFX A Y 1\nSFX A 0 s .\n", "1\niPod/A po:noun\n", "IPODS", "st:Ipod po:noun fl:A"});
  }

  /**
   * Compares morphological fields with native output on original fixtures.
   *
   * @param affix The affix content.
   * @param words The entry content.
   * @param input The input word.
   * @param expected The expected analysis.
   * @throws Exception If parsing or reference execution fails.
   */
  /**
   * Analyses that include a rule adding and removing no material, in reference order.
   *
   * @return Affix content, word list, input, and every expected analysis.
   */
  private static Stream<Arguments> zeroAffixAnalyses() {
    return Stream.of(
        Arguments.of("SFX A Y 1\nSFX A 0 0 . is:zero\n", "1\nbar/A\n", "bar",
            List.of("st:bar", "st:bar is:zero")),
        Arguments.of("PFX A Y 1\nPFX A 0 0 . dp:zero\n", "1\nbar/A\n", "bar",
            List.of("st:bar", "dp:zero st:bar fl:A")),
        Arguments.of("NEEDAFFIX X\nSFX A Y 1\nSFX A 0 0 . >\nSFX B Y 1\nSFX B 0 0 . <ZERO>>\n"
            + "SFX C Y 2\nSFX C 0 0/XAB . <ZERODERIV>\nSFX C 0 baz/XAB . <DERIV>\n",
            "1\nbar/XABC\t<BAR\n", "bar",
            List.of("st:bar <BAR >", "st:bar <BAR <ZERO>>", "st:bar <BAR <ZERODERIV> >",
                "st:bar <BAR <ZERODERIV> <ZERO>>")));
  }

  /**
   * Tests that a rule without material is undone on its own and inside a continuation.
   *
   * @param affix The affix content.
   * @param words The word list.
   * @param input The analyzed word.
   * @param expected The distinct analyses, as recorded from the reference analyzer.
   * @throws IOException If loading fails.
   */
  @ParameterizedTest
  @MethodSource("zeroAffixAnalyses")
  void testZeroAffixAnalyses(String affix, String words, String input, List<String> expected)
      throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(HunspellDictionary.load(
        new ByteArrayInputStream(affix.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(words.getBytes(StandardCharsets.UTF_8))));
    Assertions.assertEquals(new TreeSet<>(expected), new TreeSet<>(stemmer.analyze(input)));
  }

  /**
   * Tests that only the first listed homonym decides whether a spelling is forbidden.
   * The reference spell checker accepts {@code foo} with the valid homonym listed first
   * and rejects it with the forbidden homonym listed first.
   *
   * @throws IOException If loading fails.
   */
  @Test
  void testForbiddenFirstHomonym() throws IOException {
    final String affix = "FORBIDDENWORD X\nCOMPOUNDFLAG Y\nCOMPOUNDMIN 1\n";
    final HunspellStemmer allowed = new HunspellStemmer(HunspellDictionary.load(
        new ByteArrayInputStream(affix.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream("2\nfoo/S\nfoo/YX\n".getBytes(StandardCharsets.UTF_8))));
    Assertions.assertEquals(List.of("st:foo"), allowed.analyze("foo"));
    Assertions.assertEquals(List.of(), allowed.analyze("foofoo"));
    final HunspellStemmer forbidden = new HunspellStemmer(HunspellDictionary.load(
        new ByteArrayInputStream(affix.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream("2\nfoo/YX\nfoo/S\n".getBytes(StandardCharsets.UTF_8))));
    Assertions.assertEquals(List.of(), forbidden.analyze("foo"));
  }

  /**
   * Tests analyses against the field text recorded from the reference analyzer, with
   * separator whitespace normalized to single spaces.
   *
   * @param affix The affix content.
   * @param words The word list.
   * @param input The analyzed word.
   * @param expected The recorded analysis.
   * @throws IOException If loading fails.
   */
  @ParameterizedTest
  @MethodSource("morphology")
  void testMorphologyFields(String affix, String words, String input, String expected) throws IOException {
    final HunspellStemmer stemmer = new HunspellStemmer(HunspellDictionary.load(
        new ByteArrayInputStream(affix.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(words.getBytes(StandardCharsets.UTF_8))));
    Assertions.assertEquals(List.of(expected), stemmer.analyze(input));
  }
}
