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

package opennlp.tools.util.featuregen;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.DictionarySerializer;

public class GeneratorFactoryTest {

  static class TestParametersFeatureGeneratorFactory extends
      GeneratorFactory.AbstractXmlFeatureGeneratorFactory {

    public TestParametersFeatureGeneratorFactory() {
      super();
    }

    @Override
    public AdaptiveFeatureGenerator create() throws InvalidFormatException {
      return new TestParametersFeatureGenerator(
          getInt("intParam"),
          getFloat("floatParam"),
          getLong("longParam"),
          getDouble("doubleParam"),
          getBool("boolParam"),
          getStr("strParam"));
    }
  }

  record TestParametersFeatureGenerator(int ip, float fp, long lp, double dp, boolean bp,
                                        String sp) implements AdaptiveFeatureGenerator {

    @Override
    public void createFeatures(List<String> features, String[] tokens, int index,
                               String[] previousOutcomes) {
    }
  }

  @Test
  void testCreationWithTokenClassFeatureGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestTokenClassFeatureGeneratorConfig.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assertions.assertNotNull(generatorDescriptorIn);

    AggregatedFeatureGenerator aggregatedGenerator =
        (AggregatedFeatureGenerator) GeneratorFactory.create(generatorDescriptorIn, null);

    Assertions.assertEquals(1, aggregatedGenerator.getGenerators().size());
    Assertions.assertEquals(TokenClassFeatureGenerator.class.getName(),
        aggregatedGenerator.getGenerators().iterator().next().getClass().getName());

  }

  @Test
  void testCreationWihtSimpleDescriptor() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestFeatureGeneratorConfig.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assertions.assertNotNull(generatorDescriptorIn);

    Collection<String> expectedGenerators = new ArrayList<>();
    expectedGenerators.add(OutcomePriorFeatureGenerator.class.getName());

    AggregatedFeatureGenerator aggregatedGenerator =
        (AggregatedFeatureGenerator) GeneratorFactory.create(generatorDescriptorIn, null);


    for (AdaptiveFeatureGenerator generator : aggregatedGenerator.getGenerators()) {

      expectedGenerators.remove(generator.getClass().getName());

      // if of kind which requires parameters check that
    }

    // If this fails not all expected generators were found and
    // removed from the expected generators collection
    Assertions.assertEquals(0, expectedGenerators.size());
  }

  /**
   * Tests the creation from a descriptor which contains an unkown element.
   * The creation should fail with an {@link InvalidFormatException}
   */
  @Test
  void testCreationWithUnkownElement() {

    Assertions.assertThrows(IOException.class, () -> {

      try (InputStream descIn = getClass().getResourceAsStream(
          "/opennlp/tools/util/featuregen/FeatureGeneratorConfigWithUnkownElement.xml")) {
        GeneratorFactory.create(descIn, null);
      }
    });
  }

  @Test
  void testDictionaryArtifactToSerializerMappingExtraction() throws IOException {

    InputStream descIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestDictionarySerializerMappingExtraction.xml");

    Map<String, ArtifactSerializer<?>> mapping =
        GeneratorFactory.extractArtifactSerializerMappings(descIn);

    Assertions.assertInstanceOf(DictionarySerializer.class, mapping.get("test.dictionary"));
    // TODO: if make the following effective, the test fails.
    // this is strange because DictionaryFeatureGeneratorFactory cast dictResource to Dictionary...
    //Assert.assertTrue(mapping.get("test.dictionary") instanceof
    //    opennlp.tools.dictionary.Dictionary);
  }

  @Test
  void testParameters() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestParametersConfig.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assertions.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator generator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assertions.assertInstanceOf(TestParametersFeatureGenerator.class, generator);

    TestParametersFeatureGenerator featureGenerator = (TestParametersFeatureGenerator) generator;
    Assertions.assertEquals(123, featureGenerator.ip);
    Assertions.assertEquals(featureGenerator.fp, 0.1, 45);
    Assertions.assertEquals(67890, featureGenerator.lp);
    Assertions.assertEquals(featureGenerator.dp, 0.1, 123456.789);
    Assertions.assertTrue(featureGenerator.bp);
    Assertions.assertEquals("HELLO", featureGenerator.sp);
  }

  @Test
  void testNotAutomaticallyInsertAggregatedFeatureGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestNotAutomaticallyInsertAggregatedFeatureGenerator.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assertions.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assertions.assertInstanceOf(OutcomePriorFeatureGenerator.class, featureGenerator);
  }

  @Test
  void testAutomaticallyInsertAggregatedFeatureGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestAutomaticallyInsertAggregatedFeatureGenerator.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assertions.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assertions.assertInstanceOf(AggregatedFeatureGenerator.class, featureGenerator);

    AggregatedFeatureGenerator aggregatedFeatureGenerator = (AggregatedFeatureGenerator) featureGenerator;
    Assertions.assertEquals(3, aggregatedFeatureGenerator.getGenerators().size());
    for (AdaptiveFeatureGenerator afg : aggregatedFeatureGenerator.getGenerators()) {
      Assertions.assertInstanceOf(OutcomePriorFeatureGenerator.class, afg);
    }
  }

  @Test
  void testNotAutomaticallyInsertAggregatedFeatureGeneratorChild() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestNotAutomaticallyInsertAggregatedFeatureGeneratorCache.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assertions.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assertions.assertInstanceOf(CachedFeatureGenerator.class, featureGenerator);

    CachedFeatureGenerator cachedFeatureGenerator = (CachedFeatureGenerator) featureGenerator;
    Assertions.assertInstanceOf(OutcomePriorFeatureGenerator.class,
            cachedFeatureGenerator.getCachedFeatureGenerator());
  }

  @Test
  void testAutomaticallyInsertAggregatedFeatureGeneratorChildren() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestAutomaticallyInsertAggregatedFeatureGeneratorCache.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assertions.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assertions.assertInstanceOf(CachedFeatureGenerator.class, featureGenerator);

    CachedFeatureGenerator cachedFeatureGenerator = (CachedFeatureGenerator) featureGenerator;
    AdaptiveFeatureGenerator afg = cachedFeatureGenerator.getCachedFeatureGenerator();
    Assertions.assertInstanceOf(AggregatedFeatureGenerator.class, afg);

    AggregatedFeatureGenerator aggregatedFeatureGenerator = (AggregatedFeatureGenerator) afg;
    Assertions.assertEquals(3, aggregatedFeatureGenerator.getGenerators().size());
    for (AdaptiveFeatureGenerator afgen : aggregatedFeatureGenerator.getGenerators()) {
      Assertions.assertInstanceOf(OutcomePriorFeatureGenerator.class, afgen);
    }
  }

  @Test
  void testInsertCachedFeatureGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestInsertCachedFeatureGenerator.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assertions.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assertions.assertInstanceOf(CachedFeatureGenerator.class, featureGenerator);
    CachedFeatureGenerator cachedFeatureGenerator = (CachedFeatureGenerator) featureGenerator;

    Assertions.assertInstanceOf(AggregatedFeatureGenerator.class,
            cachedFeatureGenerator.getCachedFeatureGenerator());
    AggregatedFeatureGenerator aggregatedFeatureGenerator =
        (AggregatedFeatureGenerator) cachedFeatureGenerator.getCachedFeatureGenerator();
    Assertions.assertEquals(3, aggregatedFeatureGenerator.getGenerators().size());
    for (AdaptiveFeatureGenerator afg : aggregatedFeatureGenerator.getGenerators()) {
      Assertions.assertInstanceOf(OutcomePriorFeatureGenerator.class, afg);
    }
  }
}
