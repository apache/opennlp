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
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.sentdetect.DummySentenceDetectorFactory.DummyDictionary;
import opennlp.tools.sentdetect.DummySentenceDetectorFactory.DummyEOSScanner;
import opennlp.tools.sentdetect.DummySentenceDetectorFactory.DummySDContextGenerator;
import opennlp.tools.sentdetect.lang.Factory;

/**
 * Tests for the {@link SentenceDetectorME} class.
 */
public class SentenceDetectorFactoryTest extends AbstractSentenceDetectorTest {

  @Test
  void testDefault() throws IOException {

    Dictionary dic = loadAbbDictionary(Locale.ENGLISH);

    char[] eos = {'.', '?'};
    SentenceModel sdModel = train(
            new SentenceDetectorFactory("eng", true, dic, eos), Locale.ENGLISH);

    SentenceDetectorFactory factory = sdModel.getFactory();
    Assertions.assertInstanceOf(DefaultSDContextGenerator.class, factory.getSDContextGenerator());
    Assertions.assertInstanceOf(DefaultEndOfSentenceScanner.class, factory.getEndOfSentenceScanner());
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertInstanceOf(DefaultSDContextGenerator.class, factory.getSDContextGenerator());
    Assertions.assertInstanceOf(DefaultEndOfSentenceScanner.class, factory.getEndOfSentenceScanner());
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());
  }

  @Test
  void testNullDict() throws IOException {
    Dictionary dic = null;

    char[] eos = {'.', '?'};
    SentenceModel sdModel = train(
            new SentenceDetectorFactory("eng", true, dic, eos), Locale.ENGLISH);

    SentenceDetectorFactory factory = sdModel.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultSDContextGenerator.class, factory.getSDContextGenerator());
    Assertions.assertInstanceOf(DefaultEndOfSentenceScanner.class, factory.getEndOfSentenceScanner());
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultSDContextGenerator.class, factory.getSDContextGenerator());
    Assertions.assertInstanceOf(DefaultEndOfSentenceScanner.class, factory.getEndOfSentenceScanner());
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());
  }

  @Test
  void testDefaultEOS() throws IOException {
    Dictionary dic = null;

    char[] eos = null;
    SentenceModel sdModel = train(
            new SentenceDetectorFactory("eng", true, dic, eos), Locale.ENGLISH);

    SentenceDetectorFactory factory = sdModel.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultSDContextGenerator.class, factory.getSDContextGenerator());
    Assertions.assertInstanceOf(DefaultEndOfSentenceScanner.class, factory.getEndOfSentenceScanner());
    Assertions.assertArrayEquals(Factory.defaultEosCharacters, factory.getEOSCharacters());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertNull(factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DefaultSDContextGenerator.class, factory.getSDContextGenerator());
    Assertions.assertInstanceOf(DefaultEndOfSentenceScanner.class, factory.getEndOfSentenceScanner());
    Assertions.assertArrayEquals(Factory.defaultEosCharacters, factory.getEOSCharacters());
  }

  @Test
  void testDummyFactory() throws IOException {

    Dictionary dic = loadAbbDictionary(Locale.ENGLISH);

    char[] eos = {'.', '?'};
    SentenceModel sdModel = train(
            new DummySentenceDetectorFactory("eng", true, dic, eos), Locale.ENGLISH);

    SentenceDetectorFactory factory = sdModel.getFactory();
    Assertions.assertInstanceOf(DummyDictionary.class, factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DummySDContextGenerator.class, factory.getSDContextGenerator());
    Assertions.assertInstanceOf(DummyEOSScanner.class, factory.getEndOfSentenceScanner());
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sdModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    SentenceModel fromSerialized = new SentenceModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertInstanceOf(DummyDictionary.class, factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DummySDContextGenerator.class, factory.getSDContextGenerator());
    Assertions.assertInstanceOf(DummyEOSScanner.class, factory.getEndOfSentenceScanner());
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());

    Assertions.assertEquals(factory.getAbbreviationDictionary(), sdModel.getAbbreviations());
    Assertions.assertArrayEquals(factory.getEOSCharacters(), sdModel.getEosCharacters());
  }

  @Test
  void testCreateDummyFactory() throws IOException {
    Dictionary dic = loadAbbDictionary(Locale.ENGLISH);
    char[] eos = {'.', '?'};

    SentenceDetectorFactory factory = SentenceDetectorFactory.create(
        DummySentenceDetectorFactory.class.getCanonicalName(), "spa", false,
        dic, eos);

    Assertions.assertInstanceOf(DummyDictionary.class, factory.getAbbreviationDictionary());
    Assertions.assertInstanceOf(DummySDContextGenerator.class, factory.getSDContextGenerator());
    Assertions.assertInstanceOf(DummyEOSScanner.class, factory.getEndOfSentenceScanner());
    Assertions.assertArrayEquals(eos, factory.getEOSCharacters());
  }

}
