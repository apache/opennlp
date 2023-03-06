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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.sentdetect.DummySentenceDetectorFactory.DummyDictionary;
import opennlp.tools.sentdetect.DummySentenceDetectorFactory.DummyEOSScanner;
import opennlp.tools.sentdetect.DummySentenceDetectorFactory.DummySDContextGenerator;
import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link SentenceDetectorME} class.
 */
public class SentenceDetectorFactoryTest {

  private static ObjectStream<SentenceSample> createSampleStream()
      throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        SentenceDetectorFactoryTest.class,
        "/opennlp/tools/sentdetect/Sentences.txt");

    return new SentenceSampleStream(new PlainTextByLineStream(
        in, StandardCharsets.UTF_8));
  }

  private static SentenceModel train(SentenceDetectorFactory factory)
      throws IOException {
    return SentenceDetectorME.train("eng", createSampleStream(), factory,
        TrainingParameters.defaultParams());
  }

  private static Dictionary loadAbbDictionary() throws IOException {
    InputStream in = SentenceDetectorFactoryTest.class.getClassLoader()
        .getResourceAsStream("opennlp/tools/sentdetect/abb.xml");

    return new Dictionary(in);
  }

  @Test
  void testDefault() throws IOException {

    Dictionary dic = loadAbbDictionary();

    char[] eos = {'.', '?'};
    SentenceModel sdModel = train(new SentenceDetectorFactory("eng", true, dic,
        eos));

    SentenceDetectorFactory factory = sdModel.getFactory();
    Assertions.assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    Assertions.assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    Assertions.assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());
  }

  @Test
  void testNullDict() throws IOException {
    Dictionary dic = null;

    char[] eos = {'.', '?'};
    SentenceModel sdModel = train(new SentenceDetectorFactory("eng", true,
        dic, eos));

    SentenceDetectorFactory factory = sdModel.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    Assertions.assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    Assertions.assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());
  }

  @Test
  void testDefaultEOS() throws IOException {
    Dictionary dic = null;

    char[] eos = null;
    SentenceModel sdModel = train(new SentenceDetectorFactory("eng", true,
        dic, eos));

    SentenceDetectorFactory factory = sdModel.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    Assertions.assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    Assertions.assertArrayEquals(Factory.defaultEosCharacters, factory.getEOSCharacters());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertTrue(factory.getSDContextGenerator() instanceof DefaultSDContextGenerator);
    Assertions.assertTrue(factory.getEndOfSentenceScanner() instanceof DefaultEndOfSentenceScanner);
    Assertions.assertArrayEquals(Factory.defaultEosCharacters, factory.getEOSCharacters());
  }

  @Test
  void testDummyFactory() throws IOException {

    Dictionary dic = loadAbbDictionary();

    char[] eos = {'.', '?'};
    SentenceModel sdModel = train(new DummySentenceDetectorFactory("eng", true,
        dic, eos));

    SentenceDetectorFactory factory = sdModel.getFactory();
    Assertions.assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    Assertions.assertTrue(factory.getSDContextGenerator() instanceof DummySDContextGenerator);
    Assertions.assertTrue(factory.getEndOfSentenceScanner() instanceof DummyEOSScanner);
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    Assertions.assertTrue(factory.getSDContextGenerator() instanceof DummySDContextGenerator);
    Assertions.assertTrue(factory.getEndOfSentenceScanner() instanceof DummyEOSScanner);
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());

    Assertions.assertEquals(factory.getAbbreviationDictionary(), sdModel.getAbbreviations());
    Assertions.assertArrayEquals(factory.getEOSCharacters(), sdModel.getEosCharacters());
  }

  @Test
  void testCreateDummyFactory() throws IOException {
    Dictionary dic = loadAbbDictionary();
    char[] eos = {'.', '?'};

    SentenceDetectorFactory factory = SentenceDetectorFactory.create(
        DummySentenceDetectorFactory.class.getCanonicalName(), "spa", false,
        dic, eos);

    Assertions.assertTrue(factory.getAbbreviationDictionary() instanceof DummyDictionary);
    Assertions.assertTrue(factory.getSDContextGenerator() instanceof DummySDContextGenerator);
    Assertions.assertTrue(factory.getEndOfSentenceScanner() instanceof DummyEOSScanner);
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());
  }

}
