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


package opennlp.tools.namefind;

import junit.framework.TestCase;
import opennlp.tools.util.Span;

/**
 * This is the test class for {@link NameSample}.
 * 
 * @author William Colen
 */
public class NameSampleTest extends TestCase {
  
  /**
   * Create a NameSample from scratch and validate it.
   * 
   * @param useTypes if to use nametypes
   * @return the NameSample
   */
  private NameSample createSimpleNameSample(boolean useTypes) {
    
    String[] sentence = {"U", ".", "S", ".", "President", "Barack", "Obama", "is",
        "considering", "sending", "additional", "American", "forces",
        "to", "Afghanistan", "."};
    
    Span[] names = {new Span(0, 4), new Span(5, 7), new Span(14, 15)};
    
    String[] nameTypes = {"Location", "Person", "Location"};
    
    NameSample theNameSample = null;
    
    if(useTypes) {
      theNameSample = new NameSample(sentence, names, nameTypes, false);
    }
    else {
      theNameSample = new NameSample(sentence, names, false);
    }
    
    return theNameSample;
  }
  
  /**
   * Checks if could create a NameSample without NameTypes, generate the
   * string representation and validate it.
   */
  public void testNoTypesToString() {
    String nameSampleStr = createSimpleNameSample(false).toString();
    assertEquals("<START> U . S . <END> President <START> Barack Obama <END> is considering " +
    		"sending additional American forces to <START> Afghanistan <END> .", nameSampleStr);

    // Checks if could create a NameSample without NameTypes and check
    // if the getNameTypes method returns NULL.
    assertNull(createSimpleNameSample(false).getNameTypes());
  }
  
  /**
   * Checks if could create a NameSample with NameTypes, generate the
   * string representation and validate it.
   */
  public void testWithTypesToString() {
    String nameSampleStr = createSimpleNameSample(true).toString();
    assertEquals("<START:Location> U . S . <END> President <START:Person> Barack Obama <END> is considering sending additional American forces to <START:Location> Afghanistan <END> .", nameSampleStr);
  }
}
