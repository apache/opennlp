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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.featuregen.WordClusterDictionary.WordClusterDictionarySerializer;
import opennlp.tools.util.model.ArtifactSerializer;

public class GeneratorFactoryTest {

  @Test
  public void testCreationWithTokenClassFeatureGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestTokenClassFeatureGeneratorConfig.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AggregatedFeatureGenerator aggregatedGenerator =
        (AggregatedFeatureGenerator) GeneratorFactory.create(generatorDescriptorIn, null);

    Assert.assertEquals(1, aggregatedGenerator.getGenerators().size());
    Assert.assertEquals(TokenClassFeatureGenerator.class.getName(),
        aggregatedGenerator.getGenerators().iterator().next().getClass().getName());

  }

  @Test
  public void testCreationWihtSimpleDescriptor() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestFeatureGeneratorConfig.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

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
    Assert.assertEquals(0, expectedGenerators.size());
  }

  @Test
  public void testCreationWithCustomGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/CustomClassLoading.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AggregatedFeatureGenerator aggregatedGenerator =
        (AggregatedFeatureGenerator) GeneratorFactory.create(generatorDescriptorIn, null);

    Collection<AdaptiveFeatureGenerator> embeddedGenerator = aggregatedGenerator.getGenerators();

    Assert.assertEquals(1, embeddedGenerator.size());

    for (AdaptiveFeatureGenerator generator : embeddedGenerator) {
      Assert.assertEquals(TokenFeatureGenerator.class.getName(), generator.getClass().getName());
    }
  }

  /**
   * Tests the creation from a descriptor which contains an unkown element.
   * The creation should fail with an {@link InvalidFormatException}
   */
  @Test(expected = IOException.class)
  public void testCreationWithUnkownElement() throws IOException {

    try (InputStream descIn = getClass().getResourceAsStream(
            "/opennlp/tools/util/featuregen/FeatureGeneratorConfigWithUnkownElement.xml")) {
      GeneratorFactory.create(descIn, null);
    }
  }

  @Test
  public void testArtifactToSerializerMappingExtraction() throws IOException {
    // TODO: Define a new one here with custom elements ...
    InputStream descIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/CustomClassLoadingWithSerializers.xml");

    Map<String, ArtifactSerializer<?>> mapping =
        GeneratorFactory.extractCustomArtifactSerializerMappings(descIn);

    Assert.assertTrue(mapping.get("test.resource") instanceof WordClusterDictionarySerializer);
  }
}