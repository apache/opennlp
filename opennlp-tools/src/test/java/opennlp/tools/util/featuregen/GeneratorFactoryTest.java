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

import org.junit.Assert;
import org.junit.Test;

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

  static class TestParametersFeatureGenerator implements AdaptiveFeatureGenerator {

    public final int ip;
    public final float fp;
    public final long lp;
    public final double dp;
    public final boolean bp;
    public final String sp;

    TestParametersFeatureGenerator(int ip, float fp, long lp, double dp, boolean bp, String sp) {
      this.ip = ip;
      this.fp = fp;
      this.lp = lp;
      this.dp = dp;
      this.bp = bp;
      this.sp = sp;
    }

    @Override
    public void createFeatures(List<String> features, String[] tokens, int index,
                               String[] previousOutcomes) {
    }
  }

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
  public void testDictionaryArtifactToSerializerMappingExtraction() throws IOException {

    InputStream descIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestDictionarySerializerMappingExtraction.xml");

    Map<String, ArtifactSerializer<?>> mapping =
            GeneratorFactory.extractArtifactSerializerMappings(descIn);

    Assert.assertTrue(mapping.get("test.dictionary") instanceof DictionarySerializer);
    // TODO: if make the following effective, the test fails.
    // this is strange because DictionaryFeatureGeneratorFactory cast dictResource to Dictionary...
    //Assert.assertTrue(mapping.get("test.dictionary") instanceof
    //    opennlp.tools.dictionary.Dictionary);
  }

  @Test
  public void testParameters() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestParametersConfig.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator generator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assert.assertTrue(generator instanceof TestParametersFeatureGenerator);

    TestParametersFeatureGenerator featureGenerator = (TestParametersFeatureGenerator)generator;
    Assert.assertEquals(123, featureGenerator.ip);
    Assert.assertEquals(45, featureGenerator.fp, 0.1);
    Assert.assertEquals(67890, featureGenerator.lp);
    Assert.assertEquals(123456.789, featureGenerator.dp, 0.1);
    Assert.assertTrue(featureGenerator.bp);
    Assert.assertEquals("HELLO", featureGenerator.sp);
  }

  @Test
  public void testNotAutomaticallyInsertAggregatedFeatureGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestNotAutomaticallyInsertAggregatedFeatureGenerator.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assert.assertTrue(featureGenerator instanceof OutcomePriorFeatureGenerator);
  }

  @Test
  public void testAutomaticallyInsertAggregatedFeatureGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestAutomaticallyInsertAggregatedFeatureGenerator.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assert.assertTrue(featureGenerator instanceof AggregatedFeatureGenerator);

    AggregatedFeatureGenerator aggregatedFeatureGenerator = (AggregatedFeatureGenerator)featureGenerator;
    Assert.assertEquals(3, aggregatedFeatureGenerator.getGenerators().size());
    for (AdaptiveFeatureGenerator afg: aggregatedFeatureGenerator.getGenerators()) {
      Assert.assertTrue(afg instanceof OutcomePriorFeatureGenerator);
    }
  }

  @Test
  public void testNotAutomaticallyInsertAggregatedFeatureGeneratorChild() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestNotAutomaticallyInsertAggregatedFeatureGeneratorCache.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assert.assertTrue(featureGenerator instanceof CachedFeatureGenerator);

    CachedFeatureGenerator cachedFeatureGenerator = (CachedFeatureGenerator)featureGenerator;
    Assert.assertTrue(cachedFeatureGenerator.getCachedFeatureGenerator()
        instanceof OutcomePriorFeatureGenerator);
  }

  @Test
  public void testAutomaticallyInsertAggregatedFeatureGeneratorChildren() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestAutomaticallyInsertAggregatedFeatureGeneratorCache.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assert.assertTrue(featureGenerator instanceof CachedFeatureGenerator);

    CachedFeatureGenerator cachedFeatureGenerator = (CachedFeatureGenerator)featureGenerator;
    AdaptiveFeatureGenerator afg = cachedFeatureGenerator.getCachedFeatureGenerator();
    Assert.assertTrue(afg instanceof AggregatedFeatureGenerator);

    AggregatedFeatureGenerator aggregatedFeatureGenerator = (AggregatedFeatureGenerator)afg;
    Assert.assertEquals(3, aggregatedFeatureGenerator.getGenerators().size());
    for (AdaptiveFeatureGenerator afgen: aggregatedFeatureGenerator.getGenerators()) {
      Assert.assertTrue(afgen instanceof OutcomePriorFeatureGenerator);
    }
  }

  @Test
  public void testInsertCachedFeatureGenerator() throws Exception {
    InputStream generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestInsertCachedFeatureGenerator.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);

    AdaptiveFeatureGenerator featureGenerator = GeneratorFactory.create(generatorDescriptorIn, null);
    Assert.assertTrue(featureGenerator instanceof CachedFeatureGenerator);
    CachedFeatureGenerator cachedFeatureGenerator = (CachedFeatureGenerator)featureGenerator;

    Assert.assertTrue(cachedFeatureGenerator.getCachedFeatureGenerator()
        instanceof AggregatedFeatureGenerator);
    AggregatedFeatureGenerator aggregatedFeatureGenerator =
        (AggregatedFeatureGenerator)cachedFeatureGenerator.getCachedFeatureGenerator();
    Assert.assertEquals(3, aggregatedFeatureGenerator.getGenerators().size());
    for (AdaptiveFeatureGenerator afg: aggregatedFeatureGenerator.getGenerators()) {
      Assert.assertTrue(afg instanceof OutcomePriorFeatureGenerator);
    }
  }
}
