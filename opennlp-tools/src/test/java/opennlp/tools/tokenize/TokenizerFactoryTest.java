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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.tokenize.DummyTokenizerFactory.DummyContextGenerator;
import opennlp.tools.tokenize.DummyTokenizerFactory.DummyDictionary;
import opennlp.tools.tokenize.lang.Factory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

import org.junit.Test;

/**
 * Tests for the {@link TokenizerFactory} class.
 */
public class TokenizerFactoryTest {

  private static ObjectStream<TokenSample> createSampleStream()
      throws IOException {
    InputStream in = TokenizerFactoryTest.class.getClassLoader()
        .getResourceAsStream("opennlp/tools/tokenize/token.train");

    return new TokenSampleStream(new PlainTextByLineStream(
        new InputStreamReader(in)));
  }

  private static TokenizerModel train(TokenizerFactory factory)
      throws IOException {
    return TokenizerME.train(createSampleStream(),
        factory, TrainingParameters.defaultParams());
  }

  static Dictionary loadAbbDictionary() throws IOException {
    InputStream in = TokenizerFactoryTest.class.getClassLoader()
        .getResourceAsStream("opennlp/tools/sentdetect/abb.xml");

    return new Dictionary(in);
  }

  @Test
  public void testDefault() throws IOException {

    Dictionary dic = loadAbbDictionary();
    final String lang = "es";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, false, null));

    TokenizerFactory factory = model.getFactory();
    assertTrue(factory.getAbbreviationDictionary() instanceof Dictionary);
    assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

    assertEquals(Factory.DEFAULT_ALPHANUMERIC, factory.getAlphaNumericPattern()
        .pattern());
    assertEquals(lang, factory.getLanguageCode());
    assertEquals(lang, model.getLanguage());
    assertFalse(factory.isUseAlphaNumericOptmization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    TokenizerModel fromSerialized = new TokenizerModel(in);

    factory = fromSerialized.getFactory();
    assertTrue(factory.getAbbreviationDictionary() instanceof Dictionary);
    assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

    assertEquals(Factory.DEFAULT_ALPHANUMERIC, factory.getAlphaNumericPattern()
        .pattern());
    assertEquals(lang, factory.getLanguageCode());
    assertEquals(lang, model.getLanguage());
    assertFalse(factory.isUseAlphaNumericOptmization());
  }

  @Test
  public void testNullDict() throws IOException {

    Dictionary dic = null;
    final String lang = "es";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, false, null));

    TokenizerFactory factory = model.getFactory();
    assertNull(factory.getAbbreviationDictionary());
    assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

    assertEquals(Factory.DEFAULT_ALPHANUMERIC, factory.getAlphaNumericPattern()
        .pattern());
    assertEquals(lang, factory.getLanguageCode());
    assertEquals(lang, model.getLanguage());
    assertFalse(factory.isUseAlphaNumericOptmization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    TokenizerModel fromSerialized = new TokenizerModel(in);

    factory = fromSerialized.getFactory();
    assertNull(factory.getAbbreviationDictionary());
    assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

    assertEquals(Factory.DEFAULT_ALPHANUMERIC, factory.getAlphaNumericPattern()
        .pattern());
    assertEquals(lang, factory.getLanguageCode());
    assertEquals(lang, model.getLanguage());
    assertFalse(factory.isUseAlphaNumericOptmization());
  }

  @Test
  public void testCustomPatternAndAlphaOpt() throws IOException {

    Dictionary dic = null;
    final String lang = "es";
    String pattern = "^[0-9A-Za-z]+$";

    TokenizerModel model = train(new TokenizerFactory(lang, dic, true,
        Pattern.compile(pattern)));

    TokenizerFactory factory = model.getFactory();
    assertNull(factory.getAbbreviationDictionary());
    assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

    assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    assertEquals(lang, factory.getLanguageCode());
    assertEquals(lang, model.getLanguage());
    assertTrue(factory.isUseAlphaNumericOptmization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    TokenizerModel fromSerialized = new TokenizerModel(in);

    factory = fromSerialized.getFactory();
    assertNull(factory.getAbbreviationDictionary());
    assertTrue(factory.getContextGenerator() instanceof DefaultTokenContextGenerator);

    assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    assertEquals(lang, factory.getLanguageCode());
    assertEquals(lang, model.getLanguage());
    assertTrue(factory.isUseAlphaNumericOptmization());
  }

  @Test
  public void testDummyFactory() throws IOException {

    Dictionary dic = loadAbbDictionary();
    final String lang = "es";
    String pattern = "^[0-9A-Za-z]+$";

    TokenizerModel model = train(new DummyTokenizerFactory(lang, dic, true,
        Pattern.compile(pattern)));

    TokenizerFactory factory = model.getFactory();
    assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    assertTrue(factory.getContextGenerator() instanceof DummyContextGenerator);

    assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    assertEquals(lang, factory.getLanguageCode());
    assertEquals(lang, model.getLanguage());
    assertTrue(factory.isUseAlphaNumericOptmization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    TokenizerModel fromSerialized = new TokenizerModel(in);

    factory = fromSerialized.getFactory();
    assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    assertTrue(factory.getContextGenerator() instanceof DummyContextGenerator);

    assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    assertEquals(lang, factory.getLanguageCode());
    assertEquals(lang, model.getLanguage());
    assertTrue(factory.isUseAlphaNumericOptmization());
  }

  @Test
  public void testCreateDummyFactory() throws IOException {
    Dictionary dic = loadAbbDictionary();
    final String lang = "es";
    String pattern = "^[0-9A-Za-z]+$";

    TokenizerFactory factory = TokenizerFactory.create(
        DummyTokenizerFactory.class.getCanonicalName(), lang, dic, true,
        Pattern.compile(pattern));

    assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    assertTrue(factory.getContextGenerator() instanceof DummyContextGenerator);

    assertEquals(pattern, factory.getAlphaNumericPattern().pattern());
    assertEquals(lang, factory.getLanguageCode());
    assertTrue(factory.isUseAlphaNumericOptmization());
  }
}
