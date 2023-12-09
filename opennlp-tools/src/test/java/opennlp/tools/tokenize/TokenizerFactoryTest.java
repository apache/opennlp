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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

  private static ObjectStream<TokenSample> createSampleStream() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        TokenizerFactoryTest.class, "/opennlp/tools/tokenize/token.train");

    return new TokenSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  private static TokenizerModel train(TokenizerFactory factory)
      throws IOException {
    return TokenizerME.train(createSampleStream(), factory, TrainingParameters.defaultParams());
  }

  private static Dictionary loadAbbDictionary() throws IOException {
    InputStream in = TokenizerFactoryTest.class.getClassLoader()
        .getResourceAsStream("opennlp/tools/sentdetect/abb.xml");

    return new Dictionary(in);
  }

  @Test
  void testDefault() throws IOException {

    Dictionary dic = loadAbbDictionary();
    final String lang = "eng";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, false, null));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertNotNull(factory.getAbbreviationDictionary());
    Assertions.assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

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
    Assertions.assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

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
    Assertions.assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

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
    Assertions.assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

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
    Assertions.assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

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
    Assertions.assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);
    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());
  }

  void checkCustomPatternForTokenizerME(String lang, String pattern, String sentence,
      int expectedNumTokens) throws IOException {

    TokenizerModel model = train(new TokenizerFactory(lang, null, true,
        Pattern.compile(pattern)));

    TokenizerME tokenizer = new TokenizerME(model);
    String[] tokens = tokenizer.tokenize(sentence);

    Assertions.assertEquals(expectedNumTokens, tokens.length);
    String[] sentSplit = sentence.replaceAll("\\.", " .")
        .replaceAll("'", " '").split(" ");
    for (int i = 0; i < sentSplit.length; i++) {
      Assertions.assertEquals(sentSplit[i], tokens[i]);
    }
  }

  @Test
  void testCustomPatternForTokenizerMEDeu() throws IOException {
    String lang = "deu";
    String pattern = "^[A-Za-z0-9äéöüÄÉÖÜß]+$";
    String sentence = "Ich wähle den auf S. 183 ff. mitgeteilten Traum von der botanischen Monographie.";
    checkCustomPatternForTokenizerME(lang, pattern, sentence, 16);
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
    String sentence = "Als xiuxiuejants avets l'ós blau neda amb cignes i s'ho passen bé.";
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

    Dictionary dic = loadAbbDictionary();
    final String lang = "eng";
    String pattern = "^[0-9A-Za-z]+$";

    TokenizerModel model = train(new DummyTokenizerFactory(lang, dic, true,
        Pattern.compile(pattern)));

    TokenizerFactory factory = model.getFactory();
    Assertions.assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    Assertions.assertTrue(factory.getContextGenerator() instanceof DummyContextGenerator);
    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    TokenizerModel fromSerialized = new TokenizerModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    Assertions.assertTrue(factory.getContextGenerator() instanceof DummyContextGenerator);
    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());
  }

  @Test
  void testCreateDummyFactory() throws IOException {
    Dictionary dic = loadAbbDictionary();
    final String lang = "eng";
    String pattern = "^[0-9A-Za-z]+$";

    TokenizerFactory factory = TokenizerFactory.create(
        DummyTokenizerFactory.class.getCanonicalName(), lang, dic, true,
        Pattern.compile(pattern));

    Assertions.assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    Assertions.assertTrue(factory.getContextGenerator() instanceof DummyContextGenerator);
    Assertions.assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    Assertions.assertEquals(lang, factory.getLanguageCode());
    Assertions.assertTrue(factory.isUseAlphaNumericOptimization());
  }
}
