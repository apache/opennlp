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

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PrefixFeatureGeneratorTest {

  private List<String> features;

  @Before
  public void setUp() throws Exception {
    features = new ArrayList<>();
  }

  @Test
  public void lengthTest1() {
      
    String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};

    int testTokenIndex = 0;
    int suffixLength = 2;
      
    AdaptiveFeatureGenerator generator = new PrefixFeatureGenerator(suffixLength);    

    generator.createFeatures(features, testSentence, testTokenIndex, null);
    
    Assert.assertEquals(2, features.size());
    Assert.assertEquals("pre=T", features.get(0));
    Assert.assertEquals("pre=Th", features.get(1));
    
  }
  
  @Test
  public void lengthTest2() {
      
    String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};

    int testTokenIndex = 3;
    int suffixLength = 5;
      
    AdaptiveFeatureGenerator generator = new PrefixFeatureGenerator(suffixLength);    

    generator.createFeatures(features, testSentence, testTokenIndex, null);
    
    Assert.assertEquals(5, features.size());
    Assert.assertEquals("pre=e", features.get(0));
    Assert.assertEquals("pre=ex", features.get(1));
    Assert.assertEquals("pre=exa", features.get(2));
    Assert.assertEquals("pre=exam", features.get(3));
    Assert.assertEquals("pre=examp", features.get(4));
    
  }
  
  @Test
  public void lengthTest3() {
      
    String[] testSentence = new String[] {"This", "is", "an", "example", "sentence"};

    int testTokenIndex = 1;
    int suffixLength = 5;
      
    AdaptiveFeatureGenerator generator = new PrefixFeatureGenerator(suffixLength);    

    generator.createFeatures(features, testSentence, testTokenIndex, null);
        
    Assert.assertEquals(2, features.size());
    Assert.assertEquals("pre=i", features.get(0));
    Assert.assertEquals("pre=is", features.get(1));
    
  }
}
