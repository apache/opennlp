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
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.tokenize.DummyTokenizerFactory.DummyContextGenerator;
import opennlp.tools.tokenize.DummyTokenizerFactory.DummyDictionary;
import opennlp.tools.tokenize.lang.Factory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link TokenizerFactory} class.
 */
public class TokenizerFactoryTest {

  private static final Locale LOCALE_SPANISH = new Locale("es");
  private static final Locale LOCALE_POLISH = new Locale("pl");
  private static final Locale LOCALE_PORTUGUESE = new Locale("pt");

  private static ObjectStream<TokenSample> createSampleStream() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        TokenizerFactoryTest.class, "/opennlp/tools/tokenize/token.train");

    return new TokenSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  private static TokenizerModel train(TokenizerFactory factory)
      throws IOException {
    return TokenizerME.train(createSampleStream(), factory, TrainingParameters.defaultParams());
  }

  private static Dictionary loadAbbDictionary(Locale loc) throws IOException {
    final String abbrevDict;
    if (loc.equals(Locale.GERMAN)) {
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

    TokenizerModel model = train(new TokenizerFactory(lang, dic, false, null));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertNotNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultTokenContextGenerator.class, factory.getContextGenerator());

    String defaultPattern = Factory.DEFAULT_ALPHANUMERIC.pattern();
    Assertions.assertEquals(defaultPattern, factory.getAlphaNumericPattern().pattern());
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

    Assertions.assertEquals(defaultPattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertFalse(factory.isUseAlphaNumericOptimization());
  }

  @Test
  void testNullDict() throws IOException {

    Dictionary dic = null;
    final String lang = "eng";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, false, null));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultTokenContextGenerator.class, factory.getContextGenerator());

    String defaultPattern = Factory.DEFAULT_ALPHANUMERIC.pattern();
    Assertions.assertEquals(defaultPattern, factory.getAlphaNumericPattern().pattern());
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

    Assertions.assertEquals(defaultPattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertFalse(factory.isUseAlphaNumericOptimization());
  }

  @Test
  void testCustomPatternAndAlphaOpt() throws IOException {

    Dictionary dic = null;
    final String lang = "spa";
    String pattern = "^[0-9a-záéíóúüýñA-ZÁÉÍÓÚÝÑ]+$";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, true,
        Pattern.compile(pattern)));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultTokenContextGenerator.class, factory.getContextGenerator());

    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
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
    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());
  }

  void checkCustomPatternForTokenizerME(String lang, String pattern, String sentence,
      int expectedNumTokens) throws IOException {
    Locale loc = Locale.ENGLISH;
    if ("deu".equals(lang)) {
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
        Pattern.compile(pattern)));

    TokenizerME tokenizer = new TokenizerME(model);
    String[] tokens = tokenizer.tokenize(sentence);

    Assertions.assertEquals(expectedNumTokens, tokens.length);
    String[] sentSplit = sentence
            .replaceAll("'", " '")
            .replaceAll(",", " ,")
            .split(" ");
    for (int i = 0; i < sentSplit.length; i++) {
      String sElement = sentSplit[i];
      if (i == sentSplit.length - 1) {
        sElement = sElement.replace(".", ""); // compensate for sentence ending
      }
      Assertions.assertEquals(sElement, tokens[i]);
    }
  }

  // For language specific patterns see: opennlp.tools.tokenize.lang.Factory

  @Test
  void testCustomPatternForTokenizerMEWithAbbreviationsDeu() throws IOException {
    String lang = "deu";
    String pattern = "^[A-Za-z0-9äéöüÄÉÖÜß]+$";
    String sentence = "Ich wähle den auf S. 183 ff. mitgeteilten Traum von der botanischen Monographie.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 14);
  }

  @Test
  void testCustomPatternForTokenizerMEWithAbbreviationsFra() throws IOException {
    String lang = "fra";
    String pattern = "^[a-zA-Z0-9àâäèéêëîïôœùûüÿçÀÂÄÈÉÊËÎÏÔŒÙÛÜŸÇ]+$";
    String sentence = "Je choisis le rêve de la monographie botanique communiqué à la p. 205.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 14);
  }

  @Test
  void testCustomPatternForTokenizerMEWithAbbreviationsPol() throws IOException {
    String lang = "pol";
    String pattern = "^[A-Za-z0-9żźćńółęąśŻŹĆĄŚĘŁÓŃ]+$";
    String sentence = "W szkicu autobiograficznym pt. moje życie i psychoanaliza Freud pisze, że " +
            "jego przodkowie żyli przez wiele lat w Kolonii.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 21);
  }

  @Test
  void testCustomPatternForTokenizerMEWithAbbreviationsPor() throws IOException {
    String lang = "por";
    String pattern = "^[0-9a-záãâàéêíóõôúüçA-ZÁÃÂÀÉÊÍÓÕÔÚÜÇ]+$";
    String sentence = "O povo pernambucano, tradicionalmente inimigo dos imperadores, " +
            "lembrava-se do tempo em que o Sr. D. Pedro de Alcantara dava-se ao luxo " +
            "de visitar o norte.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 28);
  }

  @Test
  void testCustomPatternForTokenizerMEWithAbbreviationsSpa() throws IOException {
    String lang = "spa";
    String pattern = "^[0-9a-záéíóúüýñA-ZÁÉÍÓÚÝÑ]+$";
    String sentence = "Elegiremos el de la monografía botánica expuesto antes del " +
            "capítulo V en pág. 448 del presente volumen.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 18);
  }

  @Test
  void testCustomPatternForTokenizerMEPor() throws IOException {
    String lang = "por";
    String pattern = "^[0-9a-záãâàéêíóõôúüçA-ZÁÃÂÀÉÊÍÓÕÔÚÜÇ]+$";
    String sentence = "Na floresta mágica a raposa dança com unicórnios felizes.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 10);
  }

  @Test
  void testCustomPatternForTokenizerMESpa() throws IOException {
    String lang = "spa";
    String pattern = "^[0-9a-záéíóúüýñA-ZÁÉÍÓÚÝÑ]+$";
    String sentence = "En el verano los niños juegan en el parque y sus risas crean alegría.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 15);
  }

  @Test
  void testCustomPatternForTokenizerMECat() throws IOException {
    String lang = "cat";
    String pattern = "^[0-9a-zàèéíïòóúüçA-ZÀÈÉÍÏÒÓÚÜÇ]+$";
    String sentence = "Als xiuxiuejants avets l'os blau neda amb cignes i s'ho passen bé.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 15);
  }

  @Test
  void testCustomPatternForTokenizerMEIta() throws IOException {
    String lang = "ita";
    String pattern = "^[0-9a-zàèéìîíòóùüA-ZÀÈÉÌÎÍÒÓÙÜ]+$";
    String sentence = "Cosa fare di domenica per migliorare il tuo lunedì.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 10);
  }

  @Test
  void testCustomPatternForTokenizerMEWithAbbreviationsIta() throws IOException {
    String lang = "ita";
    String pattern = "^[0-9a-zàèéìîíòóùüA-ZÀÈÉÌÎÍÒÓÙÜ]+$";
    String sentence = "La chiesa fu costruita fra il 1258 ed il 1308 ca. come chiesa " +
        "del convento degli Agostiniani.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 18);
  }

  @Test
  void testContractionsIta() throws IOException {

    Dictionary dic = null;
    String lang = "ita";
    String pattern = "^[0-9a-zàèéìîíòóùüA-ZÀÈÉÌÎÍÒÓÙÜ]+$";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, true,
        Pattern.compile(pattern)));

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
    String pattern = "^[A-Za-z0-9]+$";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, true,
        Pattern.compile(pattern)));

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
    String pattern = "^[0-9A-Za-z]+$";

    TokenizerModel model = train(new DummyTokenizerFactory(lang, dic, true,
        Pattern.compile(pattern)));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertInstanceOf(DummyDictionary.class, factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DummyContextGenerator.class, factory.getContextGenerator());
    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
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
    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());
  }

  @Test
  void testCreateDummyFactory() throws IOException {
    Dictionary dic = loadAbbDictionary(Locale.ENGLISH);
    final String lang = "eng";
    String pattern = "^[0-9A-Za-z]+$";

    TokenizerFactory factory = TokenizerFactory.create(
        DummyTokenizerFactory.class.getCanonicalName(), lang, dic, true,
        Pattern.compile(pattern));

    Assertions.assertInstanceOf(DummyDictionary.class, factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DummyContextGenerator.class, factory.getContextGenerator());
    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());
  }
}
