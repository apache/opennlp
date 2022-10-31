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
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.featuregen.WordClusterDictionary.WordClusterDictionarySerializer;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.DictionarySerializer;

@Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
public class GeneratorFactoryClassicFormatTest {

  @Test
  void testCreationWithTokenClassFeatureGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestTokenClassFeatureGeneratorConfig_classic.xml");

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
        "/opennlp/tools/util/featuregen/TestFeatureGeneratorConfig_classic.xml");

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

  @Test
  void testCreationWithCustomGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/CustomClassLoading_classic.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assertions.assertNotNull(generatorDescriptorIn);

    AggregatedFeatureGenerator aggregatedGenerator =
        (AggregatedFeatureGenerator) GeneratorFactory.create(generatorDescriptorIn, null);

    Collection<AdaptiveFeatureGenerator> embeddedGenerator = aggregatedGenerator.getGenerators();

    Assertions.assertEquals(1, embeddedGenerator.size());

    for (AdaptiveFeatureGenerator generator : embeddedGenerator) {
      Assertions.assertEquals(TokenFeatureGenerator.class.getName(), generator.getClass().getName());
    }
  }

  /**
   * Tests the creation from a descriptor which contains an unkown element.
   * The creation should fail with an {@link InvalidFormatException}
   */
  @Test
  void testCreationWithUnkownElement() {

    Assertions.assertThrows(IOException.class, () -> {

      try (InputStream descIn = getClass().getResourceAsStream(
          "/opennlp/tools/util/featuregen/FeatureGeneratorConfigWithUnkownElement_classic.xml")) {
        GeneratorFactory.create(descIn, null);
      }
    });
  }

  @Test
  void testArtifactToSerializerMappingExtraction() throws IOException {
    // TODO: Define a new one here with custom elements ...
    InputStream descIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/CustomClassLoadingWithSerializers_classic.xml");

    Map<String, ArtifactSerializer<?>> mapping =
        GeneratorFactory.extractArtifactSerializerMappings(descIn);

    Assertions.assertTrue(mapping.get("test.resource") instanceof WordClusterDictionarySerializer);
  }

  @Test
  void testDictionaryArtifactToSerializerMappingExtraction() throws IOException {

    InputStream descIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestDictionarySerializerMappingExtraction_classic.xml");

    Map<String, ArtifactSerializer<?>> mapping =
        GeneratorFactory.extractArtifactSerializerMappings(descIn);

    Assertions.assertTrue(mapping.get("test.dictionary") instanceof DictionarySerializer);
    // TODO: if make the following effective, the test fails.
    // this is strange because DictionaryFeatureGeneratorFactory cast dictResource to Dictionary...
    //Assert.assertTrue(mapping.get("test.dictionary") instanceof
    //    opennlp.tools.dictionary.Dictionary);
  }
}
