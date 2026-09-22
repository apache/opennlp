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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Dictionary fixtures for Hunspell conversion, casing, compounding, and morphology.
 * The fixtures are written for this test: each declares its own words and flags for
 * the rule it exercises. The reference outcomes are recorded from Hunspell as described
 * in {@code dev/README-hunspell-dictionaries.md}.
 */
class HunspellCompatibilityTest {

  private static final String PLURAL = "SFX A Y 1\nSFX A 0 s .\n";
  private static final String COMPOUND = "COMPOUNDFLAG C\nCOMPOUNDMIN 1\n";

  /**
   * One original dictionary and an expected stemming result.
   *
   * @param name The test identifier.
   * @param affix The affix content.
   * @param words The dictionary content.
   * @param input The input form.
   * @param expected The expected stems, with identity for unknown input.
   */
  private record Example(String name, String affix, String words,
                         String input, List<String> expected) {
    /** {@inheritDoc} */
    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * Supplies the fixtures.
   *
   * @return The examples.
   */
  private static Stream<Example> examples() {
    return Stream.of(
        new Example("input-ligature", "ICONV 1\nICONV ﬁ fi\n" + PLURAL,
            "1\nfield/A\n", "ﬁelds", List.of("field")),
        new Example("longest-input-match", "ICONV 2\nICONV æ ae\nICONV æx ax\n" + PLURAL,
            "1\nax/A\n", "æxs", List.of("ax")),
        new Example("input-end-anchor", "ICONV 1\nICONV z_ s\n" + PLURAL,
            "1\nquartz/A\n", "quartzz", List.of("quartz")),
        new Example("output-conversion", "OCONV 1\nOCONV ae ä\n" + PLURAL,
            "1\nbaer/A\n", "baers", List.of("bär")),
        new Example("ignored-input-and-dictionary", "IGNORE ’\n" + PLURAL,
            "1\npe’arl/A\n", "pear’ls", List.of("pearl")),
        new Example("ignored-affix", "IGNORE ’\nSFX A Y 1\nSFX A 0 s’ .\n",
            "1\npearl/A\n", "pearls", List.of("pearl")),
        new Example("keepcase-exact", "KEEPCASE K\n" + PLURAL,
            "1\ncard/AK\n", "cards", List.of("card")),
        new Example("keepcase-title", "KEEPCASE K\n" + PLURAL,
            "1\ncard/AK\n", "Cards", List.of("Cards")),
        new Example("keepcase-uppercase", "KEEPCASE K\n" + PLURAL,
            "1\ncard/AK\n", "CARDS", List.of("CARDS")),
        new Example("mixed-case-is-not-lowercase", PLURAL,
            "1\ncard/A\n", "cArds", List.of("cArds")),
        new Example("uppercase-proper-name", PLURAL,
            "1\nMaren/A\n", "MARENS", List.of("Maren")),
        new Example("turkish-case", "LANG tr\n" + PLURAL,
            "1\nılık/A\n", "ILIKS", List.of("ılık")),
        new Example("complex-prefixes", "COMPLEXPREFIXES\nPFX B Y 1\n"
            + "PFX B 0 re/C .\nPFX C Y 1\nPFX C 0 un .\n" + PLURAL,
            "1\ndo/AB\n", "unredos", List.of("do")),
        new Example("complex-prefix-requires-continuation", "COMPLEXPREFIXES\n"
            + "PFX B Y 1\nPFX B 0 re .\nPFX C Y 1\nPFX C 0 un .\n",
            "1\ndo/BC\n", "unredo", List.of("unredo")),
        new Example("complex-prefix-single-suffix", "COMPLEXPREFIXES\n"
            + "SFX B Y 1\nSFX B 0 er/C .\nSFX C Y 1\nSFX C 0 s .\n",
            "1\nwalk/B\n", "walkers", List.of("walkers")),
        new Example("explicit-stem", PLURAL, "1\nfeet/A st:foot is:plural\n",
            "feet", List.of("foot")),
        new Example("affixed-explicit-stem", PLURAL,
            "1\nfeet/A st:foot is:plural\n", "feets", List.of("foot")),
        new Example("morphology-alias", "AM 1\nAM st:goose is:plural\n" + PLURAL,
            "1\ngeese/A\t1\n", "geese", List.of("goose")),
        new Example("derivational-suffix", "SFX A Y 1\nSFX A 0 ness/B . ds:noun\n"
            + "SFX B Y 1\nSFX B 0 es . is:plural\n",
            "1\nkind/A po:adj\n", "kindnesses", List.of("kindness")),
        new Example("surface-prefix", PLURAL, "1\nroot/A sp:pre st:base\n",
            "roots", List.of("prebase")),
        new Example("sharp-s-uppercase", "CHECKSHARPS\n" + PLURAL,
            "1\nsoße/A\n", "SOSSES", List.of("soße")),
        new Example("sharp-s-keepcase", "CHECKSHARPS\nKEEPCASE K\n" + PLURAL,
            "1\nsoße/AK\n", "SOSSES", List.of("soße")),
        new Example("forbidden-warning", "WARN W\nFORBIDWARN\n" + PLURAL,
            "1\ncard/AW\n", "cards", List.of("cards")),
        new Example("compound-rule", "COMPOUNDMIN 1\nCOMPOUNDRULE 1\nCOMPOUNDRULE RS\n"
            + PLURAL, "2\nriver/R\nboat/AS\n", "riverboats", List.of("river", "boat")),
        new Example("compound-rule-order", "COMPOUNDMIN 1\nCOMPOUNDRULE 1\nCOMPOUNDRULE RS\n",
            "2\nriver/R\nboat/S\n", "boatriver", List.of("boatriver")),
        new Example("compound-rule-star", "COMPOUNDMIN 1\nCOMPOUNDRULE 1\nCOMPOUNDRULE R*S\n",
            "3\nriver/R\nstone/R\nboat/S\n", "riverstoneboat", List.of("river", "stone", "boat")),
        new Example("compound-rule-optional", "COMPOUNDMIN 1\nCOMPOUNDRULE 1\nCOMPOUNDRULE R?TS\n",
            "3\nriver/R\nstone/T\nboat/S\n", "stoneboat", List.of("stone", "boat")),
        new Example("compound-rule-long", "FLAG long\nCOMPOUNDMIN 1\nCOMPOUNDRULE 1\n"
            + "COMPOUNDRULE (Ra)(Sa)\n", "2\nriver/Ra\nboat/Sa\n",
            "riverboat", List.of("river", "boat")),
        new Example("compound-rule-numeric", "FLAG num\nCOMPOUNDMIN 1\nCOMPOUNDRULE 1\n"
            + "COMPOUNDRULE (12)(34)\n", "2\nriver/12\nboat/34\n",
            "riverboat", List.of("river", "boat")),
        new Example("compound-rule-homonyms", "COMPOUNDMIN 1\nCOMPOUNDRULE 1\n"
            + "COMPOUNDRULE RTS\n", "3\nriver/R\nriver/T\nboat/S\n",
            "riverboat", List.of("riverboat")),
        new Example("compound-force-uppercase", COMPOUND + "FORCEUCASE U\n",
            "2\nriver/C\nboat/CU\n", "Riverboat", List.of("river", "boat")),
        new Example("compound-force-uppercase-reject", COMPOUND + "FORCEUCASE U\n",
            "2\nriver/C\nboat/CU\n", "riverboat", List.of("riverboat")),
        new Example("compound-root-count", COMPOUND + "COMPOUNDROOT R\nCOMPOUNDWORDMAX 3\n",
            "3\nrain/CR\ncoat/C\nrack/C\n", "raincoatrack", List.of("raincoatrack")),
        new Example("compound-root-count-accept", COMPOUND + "COMPOUNDROOT R\nCOMPOUNDWORDMAX 4\n",
            "3\nrain/CR\ncoat/C\nrack/C\n", "raincoatrack", List.of("rain", "coat", "rack")),
        new Example("compound-replacement-check", COMPOUND + "CHECKCOMPOUNDREP\nREP 1\nREP coat boat\n",
            "3\nrain/C\ncoat/C\nrainboat\n", "raincoat", List.of("raincoat")),
        new Example("compound-pattern", COMPOUND + "CHECKCOMPOUNDPATTERN 1\n"
            + "CHECKCOMPOUNDPATTERN er b\n", "2\nriver/C\nboat/C\n",
            "riverboat", List.of("riverboat")),
        new Example("compound-pattern-flags", COMPOUND + "CHECKCOMPOUNDPATTERN 1\n"
            + "CHECKCOMPOUNDPATTERN er/X b/Y\n", "2\nriver/C\nboat/CY\n",
            "riverboat", List.of("river", "boat")),
        new Example("compound-pattern-replacement", COMPOUND + "CHECKCOMPOUNDPATTERN 1\n"
            + "CHECKCOMPOUNDPATTERN er b X\n", "2\nriver/C\nboat/C\n",
            "rivXoat", List.of("river", "boat")),
        new Example("compound-simplified-triple", COMPOUND + "CHECKCOMPOUNDTRIPLE\nSIMPLIFIEDTRIPLE\n",
            "2\nmill/C\nloom/C\n", "milloom", List.of("mill", "loom")),
        new Example("compound-more-suffixes", COMPOUND + "COMPOUNDMORESUFFIXES\n"
            + "SFX A Y 1\nSFX A 0 er/B .\nSFX B Y 1\nSFX B 0 s .\n",
            "2\nriver/C\nboat/CA\n", "riverboaters", List.of("river", "boat")),
        new Example("compound-syllable-limit", COMPOUND + "LANG hu\nCOMPOUNDWORDMAX 2\n"
            + "COMPOUNDSYLLABLE 4 aeiouy\n", "3\nray/C\nme/C\nfa/C\n",
            "raymefa", List.of("ray", "me", "fa")),
        new Example("compound-syllable-limit-reject", COMPOUND + "LANG hu\nCOMPOUNDWORDMAX 2\n"
            + "COMPOUNDSYLLABLE 2 aeiouy\n", "3\nray/C\nme/C\nfa/C\n",
            "raymefa", List.of("raymefa")),
        new Example("break-default", PLURAL, "2\nriver/A\nboat/A\n",
            "rivers-boats", List.of("river", "boat")),
        new Example("break-recursive", PLURAL, "2\nriver/A\nboat/A\n",
            "rivers-boats-rivers", List.of("river", "boat")),
        new Example("break-start", PLURAL, "1\nriver/A\n", "-rivers", List.of("river")),
        new Example("break-end", PLURAL, "1\nriver/A\n", "rivers-", List.of("river")),
        new Example("break-custom", "BREAK 1\nBREAK ::\n" + PLURAL,
            "2\nriver/A\nboat/A\n", "rivers::boats", List.of("river", "boat")),
        new Example("break-disabled", "BREAK 0\n" + PLURAL,
            "2\nriver/A\nboat/A\n", "rivers-boats", List.of("rivers-boats")),
        new Example("break-unknown-part", PLURAL, "1\nriver/A\n",
            "rivers-absent", List.of("rivers-absent")),
        new Example("break-internal-only", "BREAK 1\nBREAK -\n" + PLURAL,
            "1\nriver/A\n", "-rivers", List.of("-rivers")),
        new Example("replacement-trailing-fields", COMPOUND + "CHECKCOMPOUNDREP\n"
            + "REP 1\nREP coat boat trailing_metadata\n",
            "3\nrain/C\ncoat/C\nrainboat\n", "raincoat", List.of("raincoat")),
        new Example("replacement-morphology", COMPOUND + "CHECKCOMPOUNDREP\n",
            "3\nrain/C\ncoat/C\nrainboat ph:raincoat\n", "raincoat", List.of("raincoat")),
        new Example("replacement-morphology-arrow", COMPOUND + "CHECKCOMPOUNDREP\n",
            "3\nrain/C\ncoat/C\nrainboat ph:coat->boat\n", "raincoat", List.of("raincoat")),
        new Example("replacement-morphology-star-unlisted", COMPOUND + "CHECKCOMPOUNDREP\n" + PLURAL,
            "3\nrain/C\ncoat/C\nrainboats/A ph:raincoats*\n", "raincoat", List.of("rain", "coat")),
        new Example("replacement-morphology-star", COMPOUND + "CHECKCOMPOUNDREP\n" + PLURAL,
            "4\nrain/C\ncoat/C\nrainboats/A ph:raincoats*\nrainboat\n",
            "raincoat", List.of("raincoat")),
        new Example("derivation-surface-prefix", "PFX U Y 1\nPFX U 0 mis . dp:pfx_mis sp:mis\n"
            + "SFX A Y 1\nSFX A 0 ment/U . ds:der_ment\n", "1\nmanage/A po:verb\n",
            "mismanagement", List.of("mismanagement")),
        new Example("derivation-inflectional-prefix", "PFX P Y 1\nPFX P 0 mis . ip:mis\n"
            + "SFX R Y 1\nSFX R 0 ment/P . ds:DER\n", "1\nmanage/R po:verb\n",
            "mismanagement", List.of("management")),
        new Example("compound-pattern-substitution-only", COMPOUND + "CHECKCOMPOUNDPATTERN 2\n"
            + "CHECKCOMPOUNDPATTERN a t k\nCHECKCOMPOUNDPATTERN ea ta y\n",
            "2\nsea/C\ntable/C\n", "sekable", List.of("sea", "table")),
        new Example("compound-pattern-substitution-second", COMPOUND + "CHECKCOMPOUNDPATTERN 2\n"
            + "CHECKCOMPOUNDPATTERN a t k\nCHECKCOMPOUNDPATTERN ea ta y\n",
            "2\nsea/C\ntable/C\n", "syble", List.of("sea", "table")),
        new Example("compound-duplicate-last-parts", COMPOUND + "CHECKCOMPOUNDDUP\n",
            "2\nfoo/C\nbar/C\n", "foofoobar", List.of("foo", "bar")),
        new Example("compound-duplicate-reject", COMPOUND + "CHECKCOMPOUNDDUP\n",
            "2\nfoo/C\nbar/C\n", "foobarbar", List.of("foobarbar")),
        new Example("compound-forbid-entry", COMPOUND_FORBID, COMPOUND_FORBID_WORDS,
            "firewardhouse", List.of("firewardhouse")),
        new Example("compound-forbid-entry-other-suffix", COMPOUND_FORBID, COMPOUND_FORBID_WORDS,
            "firewoodhouse", List.of("fire", "house")),
        new Example("compound-only-suffix-at-end", ONLY_IN_COMPOUND, ONLY_IN_COMPOUND_WORDS,
            "lampglassen", List.of("lampglassen")),
        new Example("compound-only-suffix-inside", ONLY_IN_COMPOUND, ONLY_IN_COMPOUND_WORDS,
            "glassenlamp", List.of("glass", "lamp")),
        new Example("compound-replacement-inner", COMPOUND + "CHECKCOMPOUNDREP\n"
            + "REP 1\nREP wallpaper wall_paper\n",
            "3\nwall/C\npaper/C\nwall paper\n", "paperwallpaper",
            List.of("paperwallpaper")),
        new Example("compound-replacement-inner-unaffected", COMPOUND + "CHECKCOMPOUNDREP\n"
            + "REP 1\nREP wallpaper wall_paper\n",
            "3\nwall/C\npaper/C\nwall paper\n", "paperwall",
            List.of("paper", "wall")),
        new Example("mixed-case-initial-capital", "PFX b Y 1\nPFX b e ra e\n",
            "1\neMarket/b\n", "RaMarket", List.of("eMarket")),
        new Example("mixed-case-initial-capital-entry", "PFX b Y 1\nPFX b e ra e\n",
            "1\neMarket/b\n", "EMarket", List.of("eMarket")),
        new Example("forbidden-affixed-blocks-compound", FORBIDDEN_AFFIXED, FORBIDDEN_AFFIXED_WORDS,
            "sunlightrooms", List.of("sunlightrooms")),
        new Example("forbidden-affixed-other-order", FORBIDDEN_AFFIXED, FORBIDDEN_AFFIXED_WORDS,
            "roomlightsuns", List.of("room", "light", "sun")),
        new Example("turkic-capitalized-entry", "LANG tr\n", "1\nİnci\n",
            "İNCİ", List.of("İnci")),
        new Example("break-number-sign", "BREAK 1\nBREAK #\n" + PLURAL,
            "2\nriver/A\nboat/A\n", "rivers#boats", List.of("river", "boat")),
        new Example("flag-number-sign", "NEEDAFFIX #\n" + PLURAL,
            "2\nfoo/#A\nbar/A\n", "foos", List.of("foo")),
        new Example("flag-number-sign-virtual-stem", "NEEDAFFIX #\n" + PLURAL,
            "2\nfoo/#A\nbar/A\n", "foo", List.of("foo")),
        new Example("hidden-capital-mixed-case", PLURAL, "1\neBook/A\n", "EBOOKS", List.of("Ebook")),
        new Example("hidden-capital-initial-capital", PLURAL, "1\neBook/A\n", "Ebooks", List.of("Ebooks")),
        new Example("hidden-capital-all-caps-entry", "SFX S N 1\nSFX S 0 's .\n",
            "1\nACME/S\n", "ACME'S", List.of("Acme")),
        new Example("hidden-capital-listed-form-wins", PLURAL, "2\neBook/A\nEbook\n",
            "EBOOKS", List.of("EBOOKS")),
        new Example("hidden-capital-unflagged-all-caps", PLURAL, "1\nACME\n", "Acme", List.of("Acme")),
        new Example("hidden-capital-not-in-compound", COMPOUND + PLURAL, "2\neBook/AC\ncover/C\n",
            "EBOOKCOVER", List.of("EBOOKCOVER")),
        new Example("hungarian-hyphen-moving-rule", HUNGARIAN_HYPHEN, HUNGARIAN_WORDS,
            "folyómeder-őr", List.of("folyó", "meder", "őr")),
        new Example("hungarian-hyphen-rule-needs-hyphen", HUNGARIAN_HYPHEN, HUNGARIAN_WORDS,
            "folyómeder", List.of("folyómeder")),
        new Example("hungarian-hyphen-rule-needs-language", HUNGARIAN_HYPHEN.replace("LANG hu", "LANG de"),
            HUNGARIAN_WORDS, "folyómeder-őr", List.of("folyómeder-őr")),
        new Example("hungarian-hyphen-rule-needs-flag", HUNGARIAN_HYPHEN,
            "3\nfoly/S\nmeder/Y\nőr/Y\n", "folymeder-őr", List.of("folymeder-őr")),
        new Example("hungarian-hyphen-rule-first-part-only", HUNGARIAN_HYPHEN, HUNGARIAN_WORDS,
            "őr-folyómeder", List.of("őr-folyómeder")),
        new Example("apostrophe-all-caps", "PFX P Y 1\nPFX P 0 d' .\n", "1\nOrient/P\n",
            "D'ORIENT", List.of("Orient")),
        new Example("apostrophe-capitalized", "PFX P Y 1\nPFX P 0 d' .\n", "1\nOrient/P\n",
            "D'Orient", List.of("Orient")),
        new Example("trailing-period", PLURAL, "2\ntext/A\netc.\n", "texts.", List.of("text")),
        new Example("trailing-periods", PLURAL, "2\ntext/A\netc.\n", "texts...", List.of("text")),
        new Example("trailing-period-entry", PLURAL, "2\ntext/A\netc.\n", "etc.", List.of("etc.")),
        new Example("trailing-period-not-added", PLURAL, "2\ntext/A\netc.\n", "etc", List.of("etc")),
        new Example("numeric-flag-maximum", "FLAG num\nSFX 65535 Y 1\nSFX 65535 0 s .\n",
            "1\ndog/65535\n", "dogs", List.of("dog")),
        // the manual's example of part stems here against the concatenated native stem
        new Example("compound-part-stems", COMPOUND + PLURAL, "2\nriver/C\nboat/CA\n",
            "riverboats", List.of("river", "boat")));
  }

  private static final String HUNGARIAN_HYPHEN = "LANG hu\nCOMPOUNDFLAG Y\nCOMPOUNDMIN 2\n"
      + "COMPOUNDFORBIDFLAG !\nBREAK 1\nBREAK -\nSFX S Y 1\nSFX S 0 ó .\n";
  private static final String HUNGARIAN_WORDS = "4\nfoly/S\nmeder/Y\nfolyó/F!\nőr/Y\n";
  private static final String COMPOUND_FORBID = "COMPOUNDFLAG K\nCOMPOUNDPERMITFLAG L\n"
      + "COMPOUNDFORBIDFLAG M\nSFX T Y 2\nSFX T 0 wood/LK .\nSFX T 0 ward/LK .\n";
  private static final String COMPOUND_FORBID_WORDS = "3\nfire/T\nhouse/K\nfireward/M\n";
  private static final String ONLY_IN_COMPOUND = COMPOUND + "ONLYINCOMPOUND Q\n"
      + "COMPOUNDPERMITFLAG P\nSFX D Y 1\nSFX D 0 en/QP .\n";
  private static final String ONLY_IN_COMPOUND_WORDS = "2\nlamp/C\nglass/CD\n";
  private static final String FORBIDDEN_AFFIXED = "FORBIDDENWORD F\nCOMPOUNDFLAG C\n"
      + "COMPOUNDMIN 1\nSFX S Y 1\nSFX S 0 s .\n";
  private static final String FORBIDDEN_AFFIXED_WORDS = "4\nsun/CS\nlight/C\nroom/CS\nsunlightroom/FS\n";

  /**
   * Checks the Java implementation on one fixture.
   *
   * @param example The dictionary and assertion.
   * @throws IOException If loading fails.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("examples")
  void testStemming(Example example) throws IOException {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(("SET UTF-8\n" + example.affix())
            .getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(example.words().getBytes(StandardCharsets.UTF_8)));
    final List<String> actual = new HunspellStemmer(dictionary).stemAll(example.input())
        .stream().map(CharSequence::toString).toList();
    Assertions.assertEquals(example.expected(), actual);
  }

  /**
   * The stems the reference implementation returned for a fixture when the fixtures were
   * recorded, as described in {@code dev/README-hunspell-dictionaries.md}. An empty
   * reference result is recorded as the input itself.
   *
   * @param example The fixture.
   * @return The recorded reference stems.
   */
  private static List<String> referenceStems(Example example) {
    return switch (example.name()) {
      case "keepcase-title", "keepcase-uppercase", "forbidden-warning" -> List.of("card");
      case "complex-prefixes", "sharp-s-uppercase", "sharp-s-keepcase" -> List.of(example.input());
      case "compound-rule", "compound-force-uppercase", "compound-force-uppercase-reject",
          "compound-pattern-flags" -> List.of("river");
      case "compound-root-count-accept" -> List.of("raincoat");
      case "compound-replacement-check", "replacement-trailing-fields",
          "replacement-morphology", "replacement-morphology-arrow", "replacement-morphology-star",
          "replacement-morphology-star-unlisted" ->
          List.of("rain");
      case "compound-pattern-replacement", "compound-simplified-triple",
          "compound-pattern-substitution-only", "compound-pattern-substitution-second",
          "compound-only-suffix-inside" -> List.of(example.input());
      case "compound-syllable-limit" -> List.of("rayme");
      case "compound-duplicate-last-parts" -> List.of("foofoo");
      case "compound-forbid-entry-other-suffix" -> List.of("firewood");
      case "compound-replacement-inner" -> List.of("paperwall");
      case "compound-replacement-inner-unaffected" -> List.of("paper");
      case "forbidden-affixed-other-order" -> List.of("roomlightsun");
      case "forbidden-affixed-blocks-compound" -> List.of("sunlightroom");
      case "hidden-capital-initial-capital" -> List.of("Ebook");
      case "mixed-case-initial-capital", "mixed-case-initial-capital-entry" ->
          List.of(example.input());
      case "break-default", "break-recursive", "break-start", "break-end", "break-custom",
          "break-number-sign", "hungarian-hyphen-moving-rule", "apostrophe-all-caps",
          "apostrophe-capitalized" -> List.of(example.input());
      default -> example.name().startsWith("compound-") && example.expected().size() > 1
          ? List.of(String.join("", example.expected())) : example.expected();
    };
  }

  /**
   * Whether the reference spell checker accepted a fixture input when the fixtures
   * were recorded.
   *
   * @param example The fixture.
   * @return The recorded recognition outcome.
   */
  private static boolean referenceAccepts(Example example) {
    return switch (example.name()) {
      case "keepcase-title", "keepcase-uppercase", "forbidden-warning",
          "mixed-case-is-not-lowercase", "complex-prefix-requires-continuation",
          "complex-prefix-single-suffix", "compound-rule-order", "compound-rule-homonyms",
          "compound-force-uppercase-reject", "compound-root-count", "compound-replacement-check",
          "compound-pattern", "compound-syllable-limit-reject", "break-disabled",
          "break-unknown-part", "break-internal-only", "replacement-trailing-fields",
          "replacement-morphology", "replacement-morphology-arrow", "replacement-morphology-star",
          "compound-duplicate-reject", "compound-forbid-entry", "compound-only-suffix-at-end",
          "compound-replacement-inner", "forbidden-affixed-blocks-compound",
          "flag-number-sign-virtual-stem", "hidden-capital-initial-capital",
          "hidden-capital-listed-form-wins", "hidden-capital-unflagged-all-caps",
          "hidden-capital-not-in-compound", "hungarian-hyphen-rule-needs-hyphen",
          "hungarian-hyphen-rule-needs-language", "hungarian-hyphen-rule-needs-flag",
          "hungarian-hyphen-rule-first-part-only", "trailing-period-not-added",
          "turkic-capitalized-entry" -> false;
      default -> true;
    };
  }

  /**
   * The fixtures whose recognition deliberately differs from the recorded reference
   * spell-checker outcome, each with the manual's reason.
   */
  private static final Map<String, String> RECOGNITION_DEVIATIONS = Map.of(
      "turkic-capitalized-entry", "the reference checker rejects what its analyzer stems");

  /**
   * The fixtures whose single stem differs from the recorded reference stem, each with
   * the manual's reason. Fixtures with several part stems, and fixtures the reference
   * checker rejects while its analyzer still stems them, are covered structurally.
   */
  private static final Map<String, String> STEM_DEVIATIONS = Map.ofEntries(
      Map.entry("complex-prefixes", "the reference analyzer reverses field text under COMPLEXPREFIXES"),
      Map.entry("sharp-s-uppercase", "the reference analyzer does not expand SS to a sharp s"),
      Map.entry("sharp-s-keepcase", "the reference analyzer does not expand SS to a sharp s"),
      Map.entry("compound-pattern-replacement", "the reference analyzer does not restore replaced junctions"),
      Map.entry("compound-simplified-triple", "the reference analyzer does not restore simplified triples"),
      Map.entry("mixed-case-initial-capital", "the reference analyzer keeps the initial capital"),
      Map.entry("mixed-case-initial-capital-entry", "the reference analyzer keeps the initial capital"),
      Map.entry("apostrophe-all-caps", "the reference analyzer does not undo an elided-article prefix"),
      Map.entry("apostrophe-capitalized", "the reference analyzer does not undo an elided-article prefix"),
      Map.entry("hidden-capital-initial-capital", "the reference analyzer ignores the capitalized input"),
      Map.entry("break-start", "the reference analyzer does not split at BREAK separators"),
      Map.entry("break-end", "the reference analyzer does not split at BREAK separators"));

  /**
   * Tests recognition against the recorded reference outcome. A fixture listed in
   * {@link #RECOGNITION_DEVIATIONS} must differ, so a stale entry fails too.
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
    Assertions.assertEquals(deviation == null, recognized == referenceAccepts(example),
        deviation == null ? "recognition differs from the reference" : deviation);
  }

  /**
   * Tests stems against the recorded reference stems. Part stems of compounds and
   * break forms differ by design from the concatenated reference stem, the reference
   * analyzer stems some forms its checker rejects, and the remaining differences are
   * listed in {@link #STEM_DEVIATIONS}; such a fixture must differ, so a stale entry
   * fails too.
   *
   * @param example The fixture.
   */
  @ParameterizedTest(name = "stems {0}")
  @MethodSource("examples")
  void testStemsAgainstReference(Example example) {
    final boolean same = example.expected().equals(referenceStems(example));
    if (example.expected().size() > 1 || !referenceAccepts(example)) {
      return;
    }
    final String deviation = STEM_DEVIATIONS.get(example.name());
    Assertions.assertEquals(deviation == null, same,
        deviation == null ? "stems differ from the reference: " + referenceStems(example) : deviation);
  }
}
