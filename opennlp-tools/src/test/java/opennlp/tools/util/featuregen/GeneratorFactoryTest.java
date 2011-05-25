/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import opennlp.tools.util.InvalidFormatException;

public class GeneratorFactoryTest {

  private InputStream generatorDescriptorIn;

  @Before
  public void setUp() {

    generatorDescriptorIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/TestFeatureGeneratorConfig.xml");

    // If this fails the generator descriptor could not be found
    // at the expected location
    Assert.assertNotNull(generatorDescriptorIn);
  }

  @Test
  public void testCreation() throws Exception {

    Collection<String> expectedGenerators = new ArrayList<String>();
    expectedGenerators.add(OutcomePriorFeatureGenerator.class.getName());

    AggregatedFeatureGenerator aggregatedGenerator =
      (AggregatedFeatureGenerator) GeneratorFactory.create(generatorDescriptorIn, null);



    for (AdaptiveFeatureGenerator generator :
        aggregatedGenerator.getGenerators()) {

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
    InputStream descIn = getClass().getResourceAsStream(
        "/opennlp/tools/util/featuregen/FeatureGeneratorConfigWithUnkownElement.xml");
    
    try {
      GeneratorFactory.create(descIn, null);
    }
    finally {
      descIn.close();
    }
  }
}