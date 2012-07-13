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

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.postag.DummyPOSTaggerFactory.DummyPOSContextGenerator;
import opennlp.tools.postag.DummyPOSTaggerFactory.DummyPOSDictionary;
import opennlp.tools.postag.DummyPOSTaggerFactory.DummyPOSSequenceValidator;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

import org.junit.Test;

/**
 * Tests for the {@link POSTaggerFactory} class.
 */
public class POSTaggerFactoryTest {

  private static ObjectStream<POSSample> createSampleStream()
      throws IOException {
    InputStream in = POSTaggerFactoryTest.class.getClassLoader()
        .getResourceAsStream("opennlp/tools/postag/AnnotatedSentences.txt");

    return new WordTagSampleStream((new InputStreamReader(in)));
  }

  static POSModel trainPOSModel(ModelType type, POSTaggerFactory factory)
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

    POSModel posModel = trainPOSModel(ModelType.MAXENT,
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
    assertTrue(factory.getDictionary() instanceof Dictionary);
  }
  
  @Test
  public void testPOSTaggerWithDefaultFactory() throws IOException {
    POSDictionary posDict = POSDictionary.create(POSDictionaryTest.class
            .getResourceAsStream("TagDictionaryCaseSensitive.xml"));
    Dictionary dic = POSTaggerME.buildNGramDictionary(createSampleStream(), 0);

    POSModel posModel = trainPOSModel(ModelType.MAXENT,
        new POSTaggerFactory(dic, posDict));

    POSTaggerFactory factory = posModel.getFactory();
    assertTrue(factory.getTagDictionary() instanceof POSDictionary);
    assertTrue(factory.getPOSContextGenerator() instanceof POSContextGenerator);
    assertTrue(factory.getSequenceValidator() instanceof DefaultPOSSequenceValidator);
    assertTrue(factory.getDictionary() instanceof Dictionary);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    posModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    POSModel fromSerialized = new POSModel(in);

    factory = fromSerialized.getFactory();
    assertTrue(factory.getTagDictionary() instanceof POSDictionary);
    assertTrue(factory.getPOSContextGenerator() instanceof POSContextGenerator);
    assertTrue(factory.getSequenceValidator() instanceof DefaultPOSSequenceValidator);
    assertTrue(factory.getDictionary() instanceof Dictionary);
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