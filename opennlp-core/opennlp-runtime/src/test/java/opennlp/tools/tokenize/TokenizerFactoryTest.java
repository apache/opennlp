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

package opennlp.tools.tokenize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.tokenize.DummyTokenizerFactory.DummyContextGenerator;
import opennlp.tools.tokenize.DummyTokenizerFactory.DummyDictionary;
import opennlp.tools.tokenize.lang.Factory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.normalizer.CodePointSet;

/**
 * Tests for the {@link TokenizerFactory} class.
 */
public class TokenizerFactoryTest {

  private static final Locale LOCALE_DUTCH = Locale.of("nl");
  private static final Locale LOCALE_POLISH = Locale.of("pl");
  private static final Locale LOCALE_PORTUGUESE = Locale.of("pt");
  private static final Locale LOCALE_SPANISH = Locale.of("es");

  private static final String MANIFEST_ENTRY = "manifest.properties";
  private static final String CHARACTER_POLICY_KEY = "characterPolicy";
  private static final String LEGACY_PATTERN_KEY = "alphaNumericPattern";
  private static final String CUSTOM_LEGACY_PATTERN = "^[A-Za-z0-9_]+$";
  private static final Factory LANGUAGE_FACTORY = new Factory();

  private static ObjectStream<TokenSample> createSampleStream() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        TokenizerFactoryTest.class, "/opennlp/tools/tokenize/token.train");

    return new TokenSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  private static TokenizerModel train(TokenizerFactory factory)
      throws IOException {
    return TokenizerME.train(createSampleStream(), factory, TrainingParameters.defaultParams());
  }

  /* One English model with the alphanumeric optimization, rewritten by the manifest tests. */
  private static TokenizerModel engModel;

  @BeforeAll
  static void trainEnglishModel() throws IOException {
    engModel = train(new TokenizerFactory("eng", null, true));
  }

  private static Dictionary loadAbbDictionary(Locale loc) throws IOException {
    final String abbrevDict;
    if (loc.equals(LOCALE_DUTCH)) {
      abbrevDict = "opennlp/tools/lang/abb_NL.xml";
    } else if (loc.equals(Locale.GERMAN)) {
      abbrevDict = "opennlp/tools/lang/abb_DE.xml";
    } else if (loc.equals(Locale.FRENCH)) {
      abbrevDict = "opennlp/tools/lang/abb_FR.xml";
    } else if (loc.equals(Locale.ITALIAN)) {
      abbrevDict = "opennlp/tools/lang/abb_IT.xml";
    } else if (loc.equals(LOCALE_POLISH)) {
      abbrevDict = "opennlp/tools/lang/abb_PL.xml";
    } else if (loc.equals(LOCALE_PORTUGUESE)) {
      abbrevDict = "opennlp/tools/lang/abb_PT.xml";
    } else if (loc.equals(LOCALE_SPANISH)) {
      abbrevDict = "opennlp/tools/lang/abb_ES.xml";
    } else {
      abbrevDict = "opennlp/tools/lang/abb_EN.xml";
    }
    return new Dictionary(TokenizerFactoryTest.class.getClassLoader()
            .getResourceAsStream(abbrevDict));
  }

  @Test
  void testDefault() throws IOException {

    Dictionary dic = loadAbbDictionary(Locale.ENGLISH);
    final String lang = "eng";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, false));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertNotNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultTokenContextGenerator.class, factory.getContextGenerator());

    Assertions.assertEquals(Factory.DEFAULT_CHARACTER_POLICY, factory.getCharacterPolicy());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertFalse(factory.isUseAlphaNumericOptimization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    TokenizerModel fromSerialized = new TokenizerModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertNotNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultTokenContextGenerator.class, factory.getContextGenerator());

    Assertions.assertEquals(Factory.DEFAULT_CHARACTER_POLICY, factory.getCharacterPolicy());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertFalse(factory.isUseAlphaNumericOptimization());
  }

  @Test
  void testNullDict() throws IOException {

    Dictionary dic = null;
    final String lang = "eng";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, false));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultTokenContextGenerator.class, factory.getContextGenerator());

    Assertions.assertEquals(Factory.DEFAULT_CHARACTER_POLICY, factory.getCharacterPolicy());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertFalse(factory.isUseAlphaNumericOptimization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    TokenizerModel fromSerialized = new TokenizerModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultTokenContextGenerator.class, factory.getContextGenerator());

    Assertions.assertEquals(Factory.DEFAULT_CHARACTER_POLICY, factory.getCharacterPolicy());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertFalse(factory.isUseAlphaNumericOptimization());
  }

  @Test
  void testCustomPolicyAndAlphaOpt() throws IOException {

    Dictionary dic = null;
    final String lang = "spa";
    TokenizerCharacterPolicy ascii = TokenizerCharacterPolicy.ascii();
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(
        ascii.getLetters().union(CodePointSet.of('ñ', 'Ñ')), ascii.getDigits(),
        CodePointSet.of(0x0301));

    TokenizerModel model = train(new TokenizerFactory(lang, dic, true, policy));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultTokenContextGenerator.class, factory.getContextGenerator());

    Assertions.assertEquals(policy, factory.getCharacterPolicy());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    TokenizerModel fromSerialized = new TokenizerModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultTokenContextGenerator.class, factory.getContextGenerator());
    Assertions.assertEquals(policy, factory.getCharacterPolicy());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());
  }

  /**
   * A language policy is written with the pattern of earlier releases that accepts the same
   * characters, so those releases apply the same characters to the model.
   */
  @Test
  void testManifestCarriesTheCharacterPolicyAndItsLegacyPattern() throws IOException {
    final String lang = "spa";
    TokenizerModel model = train(new TokenizerFactory(lang, null, true));

    Properties manifest = readManifest(serialize(model));

    Assertions.assertEquals(LANGUAGE_FACTORY.getCharacterPolicy(lang).toString(),
        manifest.getProperty(CHARACTER_POLICY_KEY));
    Assertions.assertEquals(LegacyAlphaNumericPatterns.BY_LANGUAGE.get("es"),
        manifest.getProperty(LEGACY_PATTERN_KEY));
  }

  /** A custom policy has no pattern of an earlier release, so only the policy is written. */
  @Test
  void testManifestOfACustomPolicyHasNoLegacyPattern() throws IOException {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(
        CodePointSet.ofRange('a', 'z'), CodePointSet.ofRange('0', '9'), CodePointSet.of());
    TokenizerModel model = train(new TokenizerFactory("eng", null, true, policy));

    Properties manifest = readManifest(serialize(model));

    Assertions.assertEquals(policy.toString(), manifest.getProperty(CHARACTER_POLICY_KEY));
    Assertions.assertNull(manifest.getProperty(LEGACY_PATTERN_KEY));
  }

  /**
   * Supplies each legacy regex with the language code of its policy.
   *
   * @return Regex and language code pairs.
   */
  static Stream<Arguments> legacyPatterns() {
    return LegacyAlphaNumericPatterns.BY_LANGUAGE.entrySet().stream()
        .map(entry -> Arguments.of(entry.getValue(), entry.getKey()));
  }

  @ParameterizedTest
  @MethodSource("legacyPatterns")
  void testLegacyPatternInTheManifestMapsToItsPolicy(String pattern, String lang)
      throws IOException {
    byte[] legacy = rewriteManifest(engModel, Map.of(LEGACY_PATTERN_KEY, pattern), CHARACTER_POLICY_KEY);

    TokenizerModel loaded = new TokenizerModel(new ByteArrayInputStream(legacy));

    Assertions.assertEquals(LANGUAGE_FACTORY.getCharacterPolicy(lang),
        loaded.getFactory().getCharacterPolicy());
    Assertions.assertTrue(loaded.getFactory().isUseAlphaNumericOptimization());
    Assertions.assertArrayEquals(new String[] {"test", ","}, new TokenizerME(loaded).tokenize("test,"));
  }

  /**
   * A model whose manifest has a custom pattern loads, and its alphanumeric optimization tests
   * candidate tokens with that pattern: "it's" stays one token only because the pattern accepts
   * the apostrophe; the English model splits it otherwise.
   */
  @Test
  @SuppressWarnings("removal")
  void testCustomLegacyPatternInTheManifestIsEvaluatedAsARegularExpression() throws IOException {
    final String withApostrophe = "^[A-Za-z0-9']+$";
    byte[] legacy = rewriteManifest(engModel, Map.of(LEGACY_PATTERN_KEY, withApostrophe),
        CHARACTER_POLICY_KEY);

    TokenizerModel loaded = new TokenizerModel(new ByteArrayInputStream(legacy));

    Assertions.assertNull(loaded.getFactory().getCharacterPolicy());
    Assertions.assertEquals(withApostrophe,
        loaded.getFactory().getAlphaNumericPattern().pattern());
    Assertions.assertArrayEquals(new String[] {"it's"}, new TokenizerME(loaded).tokenize("it's"));
    Assertions.assertArrayEquals(new String[] {"it", "'s"},
        new TokenizerME(engModel).tokenize("it's"));
  }

  @Test
  void testInvalidLegacyPatternInTheManifestIsRejected() throws IOException {
    byte[] invalid = rewriteManifest(engModel, Map.of(LEGACY_PATTERN_KEY, "[a-z"),
        CHARACTER_POLICY_KEY);

    InvalidFormatException e = assertRejectedAtLoad(invalid);

    Assertions.assertEquals("The tokenizer model for language 'eng' has an invalid "
        + LEGACY_PATTERN_KEY + " '[a-z'", e.getMessage());
  }

  /**
   * The deprecated constructor with a custom pattern writes only that pattern to the manifest,
   * and the model tokenizes with it after loading.
   */
  @Test
  @SuppressWarnings("removal")
  void testDeprecatedPatternConstructorRoundTrips() throws IOException {
    TokenizerModel model = train(new TokenizerFactory("eng", null, true,
        java.util.regex.Pattern.compile(CUSTOM_LEGACY_PATTERN)));
    byte[] bytes = serialize(model);

    Properties manifest = readManifest(bytes);
    TokenizerModel loaded = new TokenizerModel(new ByteArrayInputStream(bytes));

    Assertions.assertEquals(CUSTOM_LEGACY_PATTERN, manifest.getProperty(LEGACY_PATTERN_KEY));
    Assertions.assertNull(manifest.getProperty(CHARACTER_POLICY_KEY));
    Assertions.assertNull(loaded.getFactory().getCharacterPolicy());
    Assertions.assertEquals(CUSTOM_LEGACY_PATTERN,
        loaded.getFactory().getAlphaNumericPattern().pattern());
  }

  /**
   * The deprecated pattern getter returns the pattern of earlier releases for a language policy.
   *
   * @param pattern The pattern of the language.
   * @param lang The language code.
   */
  @ParameterizedTest
  @MethodSource("legacyPatterns")
  @SuppressWarnings("removal")
  void testDeprecatedPatternGetterReturnsTheLegacyPatternOfTheLanguage(String pattern,
                                                                       String lang) {
    TokenizerFactory factory = new TokenizerFactory(lang, null, true);

    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(pattern, LANGUAGE_FACTORY.getAlphanumeric(lang).pattern());
  }

  /**
   * A deprecated pattern that an earlier release wrote for a language is replaced by the policy
   * of that language, so the factory takes the policy path instead of the regular expression.
   */
  @Test
  @SuppressWarnings("removal")
  void testKnownLegacyPatternIsReplacedByItsPolicy() throws InvalidFormatException {
    TokenizerFactory byConstructor = new TokenizerFactory("eng", null, true,
        Factory.DEFAULT_ALPHANUMERIC);
    TokenizerFactory byCreate = TokenizerFactory.create(null, "eng", null, true,
        Factory.DEFAULT_ALPHANUMERIC);

    Assertions.assertEquals(TokenizerCharacterPolicy.ascii(), byConstructor.getCharacterPolicy());
    Assertions.assertEquals(TokenizerCharacterPolicy.ascii(), byCreate.getCharacterPolicy());
    Assertions.assertEquals(Factory.DEFAULT_ALPHANUMERIC.pattern(),
        byConstructor.getAlphaNumericPattern().pattern());
  }

  /**
   * The custom policy of the migration example, ASCII letters and digits plus two combining
   * marks, is passed to the factory as the example shows, and it keeps the accented word whole.
   */
  @Test
  void testDocumentedCustomPolicyIsPassedToTheFactory() throws InvalidFormatException {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(
        CodePointSet.ofRange('A', 'Z').union(CodePointSet.ofRange('a', 'z')),
        CodePointSet.ofRange('0', '9'),
        CodePointSet.of(0x0300, 0x0301));
    TokenizerFactory factory = new TokenizerFactory("en", null, true, policy);

    Assertions.assertSame(policy, factory.getCharacterPolicy());
    Assertions.assertTrue(policy.test("Cafe\u0301"));
    Assertions.assertNotNull(TokenizerFactory.create(null, "eng", null, true));
  }

  /** A deprecated create call with a {@code null} pattern uses the policy of the language. */
  @Test
  @SuppressWarnings("removal")
  void testDeprecatedCreateWithoutPatternUsesTheLanguagePolicy() throws InvalidFormatException {
    TokenizerFactory factory = TokenizerFactory.create(null, "spa", null, true,
        (java.util.regex.Pattern) null);

    Assertions.assertEquals(LANGUAGE_FACTORY.getCharacterPolicy("spa"),
        factory.getCharacterPolicy());
  }

  @ParameterizedTest
  @CsvSource({"spa, es", "eng, en", "x-unspecified, en"})
  void testManifestWithoutPolicyOrPatternUsesTheLanguageDefault(String lang, String policyLang)
      throws IOException {
    TokenizerModel model = train(new TokenizerFactory(lang, null, true));
    byte[] bare = rewriteManifest(model, Map.of(), CHARACTER_POLICY_KEY, LEGACY_PATTERN_KEY);

    TokenizerModel loaded = new TokenizerModel(new ByteArrayInputStream(bare));

    Assertions.assertEquals(LANGUAGE_FACTORY.getCharacterPolicy(policyLang),
        loaded.getFactory().getCharacterPolicy());
  }

  @Test
  void testMalformedCharacterPolicyInTheManifestIsRejected() throws IOException {
    byte[] malformed = rewriteManifest(engModel,
        Map.of(CHARACTER_POLICY_KEY, "letters=zz;digits=;marks="));

    InvalidFormatException e = assertRejectedAtLoad(malformed);

    Assertions.assertTrue(e.getMessage().contains(CHARACTER_POLICY_KEY), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("zz"), e.getMessage());
  }

  @Test
  void testCharacterPolicyInTheManifestWinsOverALegacyPattern() throws IOException {
    byte[] both = rewriteManifest(engModel, Map.of(LEGACY_PATTERN_KEY, CUSTOM_LEGACY_PATTERN));

    TokenizerModel loaded = new TokenizerModel(new ByteArrayInputStream(both));

    Assertions.assertEquals(Factory.DEFAULT_CHARACTER_POLICY, loaded.getFactory().getCharacterPolicy());
  }

  /**
   * Loads {@code model} and expects the {@link InvalidFormatException} of the factory, which
   * {@link opennlp.tools.util.model.BaseModel} surfaces as the cause of an
   * {@link IllegalArgumentException}.
   *
   * @param model The model bytes.
   * @return The exception the factory raised.
   */
  private static InvalidFormatException assertRejectedAtLoad(byte[] model) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new TokenizerModel(new ByteArrayInputStream(model)));
    return Assertions.assertInstanceOf(InvalidFormatException.class, e.getCause());
  }

  private static byte[] serialize(TokenizerModel model) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    return out.toByteArray();
  }

  private static Properties readManifest(byte[] model) throws IOException {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(model))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (MANIFEST_ENTRY.equals(entry.getName())) {
          Properties manifest = new Properties();
          manifest.load(zip);
          return manifest;
        }
      }
    }
    throw new IOException("No " + MANIFEST_ENTRY + " in the model");
  }

  /**
   * Serializes {@code model} with its manifest changed, the way a model written by an
   * earlier release would read.
   *
   * @param model The model to serialize.
   * @param put The manifest properties to set.
   * @param remove The manifest properties to drop.
   * @return The model bytes.
   */
  private static byte[] rewriteManifest(TokenizerModel model, Map<String, String> put,
      String... remove) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(serialize(model)));
         ZipOutputStream rewritten = new ZipOutputStream(out)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        rewritten.putNextEntry(new ZipEntry(entry.getName()));
        if (MANIFEST_ENTRY.equals(entry.getName())) {
          Properties manifest = new Properties();
          manifest.load(zip);
          for (String key : remove) {
            manifest.remove(key);
          }
          manifest.putAll(put);
          manifest.store(rewritten, "");
        } else {
          zip.transferTo(rewritten);
        }
        rewritten.closeEntry();
      }
    }
    return out.toByteArray();
  }

  void checkLanguagePolicyForTokenizerME(String lang, String sentence,
      int expectedNumTokens) throws IOException {
    Locale loc = Locale.ENGLISH;
    if ("dut".equals(lang) || "nld".equals(lang)) {
      loc = LOCALE_DUTCH;
    } else if ("deu".equals(lang)) {
      loc = Locale.GERMAN;
    } else if ("fra".equals(lang)) {
      loc = Locale.FRENCH;
    } else if ("ita".equals(lang)) {
      loc = Locale.ITALIAN;
    } else if ("pol".equals(lang)) {
      loc = LOCALE_POLISH;
    } else if ("por".equals(lang)) {
      loc = LOCALE_PORTUGUESE;
    } else if ("spa".equals(lang)) {
      loc = LOCALE_SPANISH;
    }
    TokenizerModel model = train(new TokenizerFactory(lang, loadAbbDictionary(loc), true,
        LANGUAGE_FACTORY.getCharacterPolicy(lang)));

    TokenizerME tokenizer = new TokenizerME(model);
    String[] tokens = tokenizer.tokenize(sentence);

    Assertions.assertEquals(expectedNumTokens, tokens.length);
    String[] sentSplit = sentence
            .replace("'", " '")
            .replace(",", " ,")
            .split(" ");
    for (int i = 0; i < sentSplit.length; i++) {
      String sElement = sentSplit[i];
      if (i == sentSplit.length - 1) {
        sElement = sElement.replace(".", ""); // compensate for sentence ending
      }
      Assertions.assertEquals(sElement, tokens[i]);
    }
  }

  // For language specific policies see: opennlp.tools.tokenize.lang.Factory

  @Test
  void testLanguagePolicyForTokenizerMEWithAbbreviationsDeu() throws IOException {
    String lang = "deu";
    String sentence = "Ich wähle den auf S. 183 ff. mitgeteilten " +
            "Traum von der botanischen Monographie.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 14);
  }

  @Test // to verify OPENNLP-1555
  void testLanguagePolicyForTokenizerMEWithMultiDotAbbreviationsDeu() throws IOException {
    String lang = "deu";
    // Adds an extra "z.B.", the compact form of "z. B." (zum Beispiel => for example)
    String sentence = "Ich wähle z.B. den auf S. 183 ff. mitgeteilten " +
            "Traum von der botanischen Monographie.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 15);
  }

  @Test
  void testLanguagePolicyForTokenizerMEWithAbbreviationsDut() throws IOException {
    String lang = "dut";
    String sentence = "Ik kies voor de droom van de botanische monografie die " +
            "op p. 183 en volgende wordt beschreven.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 18);
  }

  @Test // to verify OPENNLP-1555
  void testLanguagePolicyForTokenizerMEWithMultiDotAbbreviationsDut() throws IOException {
    String lang = "dut";
    String sentence = "Ik kies voor de droom van de botanische monografie die " +
            "op p. 183 e.v. wordt beschreven.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 17);
  }

  @Test
  void testLanguagePolicyForTokenizerMEWithAbbreviationsFra() throws IOException {
    String lang = "fra";
    String sentence = "Je choisis le rêve de la monographie botanique communiqué à la p. 205.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 14);
  }

  @Test
  void testLanguagePolicyForTokenizerMEWithAbbreviationsPol() throws IOException {
    String lang = "pol";
    String sentence = "W szkicu autobiograficznym pt. moje życie i psychoanaliza Freud pisze, że " +
            "jego przodkowie żyli przez wiele lat w Kolonii.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 21);
  }

  @Test
  void testLanguagePolicyForTokenizerMEWithAbbreviationsPor() throws IOException {
    String lang = "por";
    String sentence = "O povo pernambucano, tradicionalmente inimigo dos imperadores, " +
            "lembrava-se do tempo em que o Sr. D. Pedro de Alcantara dava-se ao luxo " +
            "de visitar o norte.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 28);
  }

  @Test
  void testLanguagePolicyForTokenizerMEWithAbbreviationsSpa() throws IOException {
    String lang = "spa";
    String sentence = "Elegiremos el de la monografía botánica expuesto antes del " +
            "capítulo V en pág. 448 del presente volumen.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 18);
  }

  @Test
  void testLanguagePolicyForTokenizerMEPor() throws IOException {
    String lang = "por";
    String sentence = "Na floresta mágica a raposa dança com unicórnios felizes.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 10);
  }

  @Test
  void testLanguagePolicyForTokenizerMESpa() throws IOException {
    String lang = "spa";
    String sentence = "En el verano los niños juegan en el parque y sus risas crean alegría.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 15);
  }

  @Test
  void testLanguagePolicyForTokenizerMECat() throws IOException {
    String lang = "cat";
    String sentence = "Als xiuxiuejants avets l'os blau neda amb cignes i s'ho passen bé.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 15);
  }

  @Test
  void testLanguagePolicyForTokenizerMEIta() throws IOException {
    String lang = "ita";
    String sentence = "Cosa fare di domenica per migliorare il tuo lunedì.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 10);
  }

  @Test
  void testLanguagePolicyForTokenizerMEWithAbbreviationsIta() throws IOException {
    String lang = "ita";
    String sentence = "La chiesa fu costruita fra il 1258 ed il 1308 ca. come chiesa " +
        "del convento degli Agostiniani.";
    checkLanguagePolicyForTokenizerME(lang, sentence, 18);
  }

  @Test
  void testContractionsIta() throws IOException {

    Dictionary dic = null;
    String lang = "ita";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, true,
        LANGUAGE_FACTORY.getCharacterPolicy(lang)));

    TokenizerME tokenizer = new TokenizerME(model);
    String sentence = "La contrazione di \"dove è\" è \"dov'è\".";
    String[] tokens = tokenizer.tokenize(sentence);

    Assertions.assertEquals(11, tokens.length);
    String[] sentSplit = sentence.replaceAll("\\.", " .")
        .replaceAll("'", " '").replaceAll("([^ ])\"", "$1 \"").split(" ");
    for (int i = 0; i < sentSplit.length; i++) {
      Assertions.assertEquals(sentSplit[i], tokens[i]);
    }
  }

  @Test
  void testContractionsEng() throws IOException {

    Dictionary dic = null;
    String lang = "eng";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, true,
        LANGUAGE_FACTORY.getCharacterPolicy(lang)));

    TokenizerME tokenizer = new TokenizerME(model);
    String sentence = "The cat wasn't in the house and the dog wasn't either.";
    String[] tokens = tokenizer.tokenize(sentence);

    Assertions.assertEquals(14, tokens.length);
    String[] sentSplit = sentence.replaceAll("\\.", " .")
        .replaceAll("'", " '").split(" ");
    for (int i = 0; i < sentSplit.length; i++) {
      Assertions.assertEquals(sentSplit[i], tokens[i]);
    }
  }

  @Test
  void testDummyFactory() throws IOException {

    Dictionary dic = loadAbbDictionary(Locale.ENGLISH);
    final String lang = "eng";
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.ascii();

    TokenizerModel model = train(new DummyTokenizerFactory(lang, dic, true, policy));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertInstanceOf(DummyDictionary.class, factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DummyContextGenerator.class, factory.getContextGenerator());
    Assertions.assertEquals(policy, factory.getCharacterPolicy());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    TokenizerModel fromSerialized = new TokenizerModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertInstanceOf(DummyDictionary.class, factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DummyContextGenerator.class, factory.getContextGenerator());
    Assertions.assertEquals(policy, factory.getCharacterPolicy());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());
  }

  @Test
  void testCreateDummyFactory() throws IOException {
    Dictionary dic = loadAbbDictionary(Locale.ENGLISH);
    final String lang = "eng";
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.ascii();

    TokenizerFactory factory = TokenizerFactory.create(
        DummyTokenizerFactory.class.getCanonicalName(), lang, dic, true, policy);

    Assertions.assertInstanceOf(DummyDictionary.class, factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DummyContextGenerator.class, factory.getContextGenerator());
    Assertions.assertEquals(policy, factory.getCharacterPolicy());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());
  }
}
