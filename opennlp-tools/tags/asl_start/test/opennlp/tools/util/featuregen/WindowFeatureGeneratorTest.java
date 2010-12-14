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
import opennlp.tools.util.featuregen.WindowFeatureGenerator;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Test for the {@link WindowFeatureGenerator} class.
 */
public class WindowFeatureGeneratorTest extends TestCase {

  private String[] testSentence = new String[] {"a", "b", "c", "d", 
      "e", "f", "g", "h"};
  
  private List<String> features;
  
  protected void setUp() throws Exception {
    features = new ArrayList<String>();
  }
  
  /**
   * Tests if the {@link WindowFeatureGenerator} works as specified, with a previous
   * and next window size of zero.
   */
  public void testWithoutWindow() {
    
    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
          new IdentityFeatureGenerator(), 0, 0);
    
    int testTokenIndex = 2;
    
    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);
    
    Assert.assertEquals(1, features.size());
    
    Assert.assertEquals(features.get(0), testSentence[testTokenIndex]);
  }
  
  public void testWindowSizeOne() {
    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
        new IdentityFeatureGenerator(), 1, 1);

    int testTokenIndex = 2;
    
    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);
    
    Assert.assertEquals(3, features.size());
  }
  
  public void testWindowAtBeginOfSentence() {
    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
        new IdentityFeatureGenerator(), 1, 0);

    int testTokenIndex = 0;
    
    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);
    
    Assert.assertEquals(1, features.size());
    
    Assert.assertEquals(features.get(0), testSentence[testTokenIndex]);
  }
  
  public void testWindowAtEndOfSentence() {
    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
        new IdentityFeatureGenerator(), 0, 1);

    int testTokenIndex = testSentence.length - 1;
    
    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);
    
    Assert.assertEquals(1, features.size());
    
    Assert.assertEquals(features.get(0), testSentence[testTokenIndex]);
  }
  
  /**
   * Tests for a window size of previous and next 2 if the features are correct.
   */
  public void testForCorrectFeatures() {
    AdaptiveFeatureGenerator windowFeatureGenerator = new WindowFeatureGenerator(
        new IdentityFeatureGenerator(), 2, 2);

    int testTokenIndex = 3;
    
    windowFeatureGenerator.createFeatures(features, testSentence, testTokenIndex, null);
    
    Assert.assertEquals(5, features.size());
    
    Assert.assertTrue(features.contains(WindowFeatureGenerator.PREV_PREFIX + "2" + 
        testSentence[testTokenIndex - 2]));
    Assert.assertTrue(features.contains(WindowFeatureGenerator.PREV_PREFIX + "1" + 
        testSentence[testTokenIndex - 1]));

    Assert.assertTrue(features.contains(testSentence[testTokenIndex]));

    Assert.assertTrue(features.contains(WindowFeatureGenerator.NEXT_PREFIX + "1" + 
        testSentence[testTokenIndex + 1]));
    Assert.assertTrue(features.contains(WindowFeatureGenerator.NEXT_PREFIX + "2" + 
        testSentence[testTokenIndex + 2]));
  }
}