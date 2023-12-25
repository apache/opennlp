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

package opennlp.tools.postag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.postag.DummyPOSTaggerFactory.DummyPOSContextGenerator;
import opennlp.tools.postag.DummyPOSTaggerFactory.DummyPOSDictionary;
import opennlp.tools.postag.DummyPOSTaggerFactory.DummyPOSSequenceValidator;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link POSTaggerFactory} class.
 */
public class POSTaggerFactoryTest {

  private static ObjectStream<POSSample> createSampleStream()
      throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        POSTaggerFactoryTest.class,
        "/opennlp/tools/postag/AnnotatedSentences.txt");

    return new WordTagSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  private static POSModel trainPOSModel(POSTaggerFactory factory)
      throws IOException {
    return POSTaggerME.train("eng", createSampleStream(),
        TrainingParameters.defaultParams(), factory);
  }

  @Test
  void testPOSTaggerWithCustomFactory() throws IOException {
    DummyPOSDictionary posDict = new DummyPOSDictionary(
        POSDictionary.create(POSDictionaryTest.class
            .getResourceAsStream("TagDictionaryCaseSensitive.xml")));

    POSModel posModel = trainPOSModel(new DummyPOSTaggerFactory(posDict));

    POSTaggerFactory factory = posModel.getFactory();
    Assertions.assertInstanceOf(DummyPOSDictionary.class, factory.getTagDictionary());
    Assertions.assertInstanceOf(DummyPOSContextGenerator.class, factory.getPOSContextGenerator());
    Assertions.assertInstanceOf(DummyPOSSequenceValidator.class, factory.getSequenceValidator());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    posModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    POSModel fromSerialized = new POSModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertInstanceOf(DummyPOSDictionary.class, factory.getTagDictionary());
    Assertions.assertInstanceOf(DummyPOSContextGenerator.class, factory.getPOSContextGenerator());
    Assertions.assertInstanceOf(DummyPOSSequenceValidator.class, factory.getSequenceValidator());
  }

  @Test
  void testPOSTaggerWithDefaultFactory() throws IOException {
    POSDictionary posDict = POSDictionary.create(POSDictionaryTest.class
        .getResourceAsStream("TagDictionaryCaseSensitive.xml"));
    POSModel posModel = trainPOSModel(new POSTaggerFactory(null, null, posDict));

    POSTaggerFactory factory = posModel.getFactory();
    Assertions.assertInstanceOf(POSDictionary.class, factory.getTagDictionary());
    Assertions.assertNotNull(factory.getPOSContextGenerator());
    Assertions.assertInstanceOf(DefaultPOSSequenceValidator.class, factory.getSequenceValidator());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    posModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    POSModel fromSerialized = new POSModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertInstanceOf(POSDictionary.class, factory.getTagDictionary());
    Assertions.assertNotNull(factory.getPOSContextGenerator());
    Assertions.assertInstanceOf(DefaultPOSSequenceValidator.class, factory.getSequenceValidator());
  }

  @Test
  void testCreateWithInvalidName() {
    Assertions.assertThrows(InvalidFormatException.class, () -> BaseToolFactory.create("X", null));
  }

  @Test
  void testCreateWithInvalidName2() {
    Assertions.assertThrows(InvalidFormatException.class, () -> POSTaggerFactory.create("X", null, null));
  }

  @Test
  void testCreateWithHierarchy() {
    Assertions.assertThrows(InvalidFormatException.class, () ->
            BaseToolFactory.create(Object.class.getCanonicalName(), null));
  }

  @Test
  void testCreateWithHierarchy2() {
    Assertions.assertThrows(InvalidFormatException.class, () ->
            POSTaggerFactory.create(this.getClass().getCanonicalName(), null, null));
  }
}
