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
package opennlp.tools.doccat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

import org.junit.Test;

/**
 * Tests for the {@link DoccatFactory} class.
 */
public class DoccatFactoryTest {

  private static ObjectStream<DocumentSample> createSampleStream()
      throws IOException {

    InputStreamFactory isf = new ResourceAsStreamFactory(
        DoccatFactoryTest.class, "/opennlp/tools/doccat/DoccatSample.txt");

    return new DocumentSampleStream(new PlainTextByLineStream(isf, "UTF-8"));
  }

  private static DoccatModel train() throws IOException {
    return DocumentCategorizerME.train("x-unspecified", createSampleStream(),
        TrainingParameters.defaultParams(), new DoccatFactory());
  }

  private static DoccatModel train(DoccatFactory factory) throws IOException {
    return DocumentCategorizerME.train("x-unspecified", createSampleStream(),
        TrainingParameters.defaultParams(), factory);
  }

  @Test
  public void testDefault() throws IOException {
    DoccatModel model = train();

    assertNotNull(model);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    DoccatModel fromSerialized = new DoccatModel(in);

    DoccatFactory factory = fromSerialized.getFactory();

    assertNotNull(factory);

    assertEquals(1, factory.getFeatureGenerators().length);
    assertEquals(BagOfWordsFeatureGenerator.class,
        factory.getFeatureGenerators()[0].getClass());

    assertEquals(WhitespaceTokenizer.INSTANCE, factory.getTokenizer());

  }

  @Test
  public void testCustom() throws IOException {
    FeatureGenerator[] featureGenerators = { new BagOfWordsFeatureGenerator(),
        new NGramFeatureGenerator(), new NGramFeatureGenerator(2,3) };
    DoccatFactory factory = new DoccatFactory(SimpleTokenizer.INSTANCE,
        featureGenerators);

    DoccatModel model = train(factory);

    assertNotNull(model);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    DoccatModel fromSerialized = new DoccatModel(in);

    factory = fromSerialized.getFactory();

    assertNotNull(factory);

    assertEquals(3, factory.getFeatureGenerators().length);
    assertEquals(BagOfWordsFeatureGenerator.class,
        factory.getFeatureGenerators()[0].getClass());
    assertEquals(NGramFeatureGenerator.class,
        factory.getFeatureGenerators()[1].getClass());
    assertEquals(NGramFeatureGenerator.class,factory.getFeatureGenerators()[2].getClass());

    assertEquals(SimpleTokenizer.INSTANCE.getClass(), factory.getTokenizer()
        .getClass());

  }

}
