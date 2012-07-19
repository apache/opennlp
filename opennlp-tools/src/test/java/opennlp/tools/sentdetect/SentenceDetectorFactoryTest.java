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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.sentdetect.DummySentenceDetectorFactory.DummyDictionary;
import opennlp.tools.sentdetect.DummySentenceDetectorFactory.DummyEOSScanner;
import opennlp.tools.sentdetect.DummySentenceDetectorFactory.DummySDContextGenerator;
import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

import org.junit.Test;

/**
 * Tests for the {@link SentenceDetectorME} class.
 */
public class SentenceDetectorFactoryTest {

  private static ObjectStream<SentenceSample> createSampleStream()
      throws IOException {
    InputStream in = SentenceDetectorFactoryTest.class.getClassLoader()
        .getResourceAsStream("opennlp/tools/sentdetect/Sentences.txt");

    return new SentenceSampleStream(new PlainTextByLineStream(
        new InputStreamReader(in)));
  }

  private static SentenceModel train(SentenceDetectorFactory factory)
      throws IOException {
    return SentenceDetectorME.train("en", createSampleStream(), factory,
        TrainingParameters.defaultParams());
  }

  static Dictionary loadAbbDictionary() throws IOException {
    InputStream in = SentenceDetectorFactoryTest.class.getClassLoader()
        .getResourceAsStream("opennlp/tools/sentdetect/abb.xml");

    return new Dictionary(in);
  }

  @Test
  public void testDefault() throws IOException {

    Dictionary dic = loadAbbDictionary();

    char[] eos = { '.', '?' };
    SentenceModel sdModel = train(new SentenceDetectorFactory("en", true, dic,
        eos));

    SentenceDetectorFactory factory = sdModel.getFactory();
    assertTrue(factory.getAbbreviationDictionary() instanceof Dictionary);
    assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    assertTrue(Arrays.equals(eos, factory.getEOSCharacters()));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    assertTrue(factory.getAbbreviationDictionary() instanceof Dictionary);
    assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    assertTrue(Arrays.equals(eos, factory.getEOSCharacters()));
  }

  @Test
  public void testNullDict() throws IOException {
    Dictionary dic = null;

    char[] eos = { '.', '?' };
    SentenceModel sdModel = train(new SentenceDetectorFactory("en", true, dic,
        eos));

    SentenceDetectorFactory factory = sdModel.getFactory();
    assertNull(factory.getAbbreviationDictionary());
    assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    assertTrue(Arrays.equals(eos, factory.getEOSCharacters()));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    assertNull(factory.getAbbreviationDictionary());
    assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    assertTrue(Arrays.equals(eos, factory.getEOSCharacters()));
  }

  @Test
  public void testDefaultEOS() throws IOException {
    Dictionary dic = null;

    char[] eos = null;
    SentenceModel sdModel = train(new SentenceDetectorFactory("en", true, dic,
        eos));

    SentenceDetectorFactory factory = sdModel.getFactory();
    assertNull(factory.getAbbreviationDictionary());
    assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    assertTrue(Arrays.equals(Factory.defaultEosCharacters,
        factory.getEOSCharacters()));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    assertNull(factory.getAbbreviationDictionary());
    assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    assertTrue(Arrays.equals(Factory.defaultEosCharacters,
        factory.getEOSCharacters()));
  }

  @Test
  public void testDummyFactory() throws IOException {

    Dictionary dic = loadAbbDictionary();

    char[] eos = { '.', '?' };
    SentenceModel sdModel = train(new DummySentenceDetectorFactory("en", true,
        dic, eos));

    SentenceDetectorFactory factory = sdModel.getFactory();
    assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    assertTrue(factory.getSDContextGenerator() instanceof DummySDContextGenerator);
    assertTrue(factory.getEndOfSentenceScanner() instanceof DummyEOSScanner);
    assertTrue(Arrays.equals(eos, factory.getEOSCharacters()));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    assertTrue(factory.getSDContextGenerator() instanceof DummySDContextGenerator);
    assertTrue(factory.getEndOfSentenceScanner() instanceof DummyEOSScanner);
    assertTrue(Arrays.equals(eos, factory.getEOSCharacters()));

    assertEquals(factory.getAbbreviationDictionary(),
        sdModel.getAbbreviations());
    assertTrue(Arrays.equals(factory.getEOSCharacters(),
        sdModel.getEosCharacters()));
  }
  
  @Test
  public void testCreateDummyFactory() throws IOException {
    Dictionary dic = loadAbbDictionary();
    char[] eos = { '.', '?' };
    
    SentenceDetectorFactory factory = SentenceDetectorFactory.create(
        DummySentenceDetectorFactory.class.getCanonicalName(), "es", false,
        dic, eos);
    
    assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    assertTrue(factory.getSDContextGenerator() instanceof DummySDContextGenerator);
    assertTrue(factory.getEndOfSentenceScanner() instanceof DummyEOSScanner);
    assertTrue(Arrays.equals(eos, factory.getEOSCharacters()));
  }

}
