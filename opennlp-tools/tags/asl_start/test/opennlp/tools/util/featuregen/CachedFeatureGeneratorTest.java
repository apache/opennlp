///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2008 OpenNlp
// 
//This library is free software; you can redistribute it and/or
//modify it under the terms of the GNU Lesser General Public
//License as published by the Free Software Foundation; either
//version 2.1 of the License, or (at your option) any later version.
// 
//This library is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Lesser General Public License for more details.
// 
//You should have received a copy of the GNU Lesser General Public
//License along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//////////////////////////////////////////////////////////////////////////////
package opennlp.tools.util.featuregen;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.CachedFeatureGenerator;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Test for the {@link CachedFeatureGenerator} class.
 */
public class CachedFeatureGeneratorTest extends TestCase {
  
  private AdaptiveFeatureGenerator identityGenerator[] = new AdaptiveFeatureGenerator[] {
      new IdentityFeatureGenerator()};
  
  private String testSentence1[];
  
  private String testSentence2[];
  
  private List<String> features;
  
  protected void setUp() throws Exception {
    
    testSentence1 = new String[] {"a1", "b1", "c1", "d1"};
    
    testSentence2 = new String[] {"a2", "b2", "c2", "d2"};
    
    features = new ArrayList<String>();
  }
  
  /**
   * Tests if cache works for one sentence and two different token indexes.
   */
  public void testCachingOfSentence() {
    CachedFeatureGenerator generator = new CachedFeatureGenerator(identityGenerator);
    
    int testIndex = 0;
    
    // after this call features are cached for testIndex
    generator.createFeatures(features, testSentence1, testIndex, null);

    Assert.assertEquals(1, generator.getNumberOfCacheMisses());
    Assert.assertEquals(0, generator.getNumberOfCacheHits());
    
    Assert.assertTrue(features.contains(testSentence1[testIndex]));
    
    features.clear();
    
    // check if features are really cached
    
    final String expectedToken = testSentence1[testIndex];
    
    testSentence1[testIndex] = null;
    
    generator.createFeatures(features, testSentence1, testIndex, null);
    
    Assert.assertEquals(1, generator.getNumberOfCacheMisses());
    Assert.assertEquals(1, generator.getNumberOfCacheHits());
    
    Assert.assertTrue(features.contains(expectedToken));
    
    Assert.assertEquals(1, features.size()); 
    
    features.clear();
    
    // try caching with an other index
    
    int testIndex2 = testIndex + 1;
    
    generator.createFeatures(features, testSentence1, testIndex2, null);
    
    Assert.assertEquals(2, generator.getNumberOfCacheMisses());
    Assert.assertEquals(1, generator.getNumberOfCacheHits());
    
    Assert.assertTrue(features.contains(testSentence1[testIndex2]));
    
    features.clear();
    
    // now check if cache still contains feature for testIndex
    
    generator.createFeatures(features, testSentence1, testIndex, null);
    
    Assert.assertTrue(features.contains(expectedToken));
  }
  
  /**
   * Tests if the cache was cleared after the sentence changed.
   */
  public void testCacheClearAfterSentenceChange() {
    CachedFeatureGenerator generator = new CachedFeatureGenerator(identityGenerator);
    
    int testIndex = 0;
    
    // use generator with sentence 1
    generator.createFeatures(features, testSentence1, testIndex, null);
    
    features.clear();
    
    // use another sentence but same index
    generator.createFeatures(features, testSentence2, testIndex, null);
    
    Assert.assertEquals(2, generator.getNumberOfCacheMisses());
    Assert.assertEquals(0, generator.getNumberOfCacheHits());
    
    Assert.assertTrue(features.contains(testSentence2[testIndex]));
    
    Assert.assertEquals(1, features.size()); 
    
    features.clear();
    
    // check if features are really cached
    final String expectedToken = testSentence2[testIndex];
    
    testSentence2[testIndex] = null;
    
    generator.createFeatures(features, testSentence2, testIndex, null);
    
    Assert.assertTrue(features.contains(expectedToken));
    
    Assert.assertEquals(1, features.size()); 
  }
}