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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import opennlp.tools.dictionary.Dictionary;
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
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link POSTaggerFactory} class.
 */
public class POSTaggerFactoryTest {

  private static ObjectStream<POSSample> createSampleStream()
      throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        POSTaggerFactoryTest.class,
        "/opennlp/tools/postag/AnnotatedSentences.txt");

    return new WordTagSampleStream(new PlainTextByLineStream(in, UTF_8));
  }

  private static POSModel trainPOSModel(POSTaggerFactory factory)
      throws IOException {
    return POSTaggerME.train("en", createSampleStream(),
        TrainingParameters.defaultParams(), factory);
  }

  @Test
  public void testPOSTaggerWithCustomFactory() throws IOException {
    DummyPOSDictionary posDict = new DummyPOSDictionary(
        POSDictionary.create(POSDictionaryTest.class
            .getResourceAsStream("TagDictionaryCaseSensitive.xml")));
    Dictionary dic = POSTaggerME.buildNGramDictionary(createSampleStream(), 0);

    POSModel posModel = trainPOSModel(
      new DummyPOSTaggerFactory(dic, posDict));

    POSTaggerFactory factory = posModel.getFactory();
    assertTrue(factory.getTagDictionary() instanceof DummyPOSDictionary);
    assertTrue(factory.getPOSContextGenerator() instanceof DummyPOSContextGenerator);
    assertTrue(factory.getSequenceValidator() instanceof DummyPOSSequenceValidator);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    posModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    POSModel fromSerialized = new POSModel(in);

    factory = fromSerialized.getFactory();
    assertTrue(factory.getTagDictionary() instanceof DummyPOSDictionary);
    assertTrue(factory.getPOSContextGenerator() instanceof DummyPOSContextGenerator);
    assertTrue(factory.getSequenceValidator() instanceof DummyPOSSequenceValidator);
    assertTrue(factory.getDictionary() != null);
  }

  @Test
  public void testPOSTaggerWithDefaultFactory() throws IOException {
    POSDictionary posDict = POSDictionary.create(POSDictionaryTest.class
            .getResourceAsStream("TagDictionaryCaseSensitive.xml"));
    Dictionary dic = POSTaggerME.buildNGramDictionary(createSampleStream(), 0);

    POSModel posModel = trainPOSModel(
      new POSTaggerFactory(dic, posDict));

    POSTaggerFactory factory = posModel.getFactory();
    assertTrue(factory.getTagDictionary() instanceof POSDictionary);
    assertTrue(factory.getPOSContextGenerator() != null);
    assertTrue(factory.getSequenceValidator() instanceof DefaultPOSSequenceValidator);
    assertTrue(factory.getDictionary() != null);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    posModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    POSModel fromSerialized = new POSModel(in);

    factory = fromSerialized.getFactory();
    assertTrue(factory.getTagDictionary() instanceof POSDictionary);
    assertTrue(factory.getPOSContextGenerator() != null);
    assertTrue(factory.getSequenceValidator() instanceof DefaultPOSSequenceValidator);
    assertTrue(factory.getDictionary() != null);
  }

  @Test(expected = InvalidFormatException.class)
  public void testCreateWithInvalidName() throws InvalidFormatException {
    BaseToolFactory.create("X", null);
  }

  @Test(expected = InvalidFormatException.class)
  public void testCreateWithInvalidName2() throws InvalidFormatException {
    POSTaggerFactory.create("X", null, null);
  }

  @Test(expected = InvalidFormatException.class)
  public void testCreateWithHierarchy() throws InvalidFormatException {
    BaseToolFactory.create(Object.class.getCanonicalName(), null);
  }

  @Test(expected = InvalidFormatException.class)
  public void testCreateWithHierarchy2() throws InvalidFormatException {
    POSTaggerFactory.create(this.getClass().getCanonicalName(), null, null);
  }
}
