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

package opennlp.tools.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the {@link Parse} class.
 */
public class ParseTest {

  static final String PARSE_STRING = "(TOP  (S (S (NP-SBJ (PRP She)  )(VP (VBD was)  (ADVP (RB just)  )(NP-PRD (NP (DT another)  (NN freighter)  )(PP (IN from)  (NP (DT the)  (NNPS States)  )))))(, ,)  (CC and) (S (NP-SBJ (PRP she)  )(VP (VBD seemed)  (ADJP-PRD (ADJP (RB as)  (JJ commonplace)  )(PP (IN as)  (NP (PRP$ her)  (NN name)  )))))(. .)  ))";
  
  @Test
  public void testParseClone() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    Parse p2 = (Parse) p1.clone();
    assertTrue(p1.equals(p2));
    assertTrue(p2.equals(p1));
  }
  
  @Test
  public void testGetText() {
    Parse p = Parse.parseParse(PARSE_STRING);
    
    // TODO: Why does parse attaches a space to the end of the text ???
    String expectedText = "She was just another freighter from the States , and she seemed as commonplace as her name . ";
    
    assertEquals(expectedText, p.getText());
  }
  
  @Test
  public void testShow() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    
    StringBuffer parseString = new StringBuffer();
    p1.show(parseString);
    
    Parse p2 = Parse.parseParse(parseString.toString());
    
    assertEquals(p1, p2);
  }
  
  @Test
  public void testGetTagNodes() {
    Parse p = Parse.parseParse(PARSE_STRING);
    
    Parse tags[] = p.getTagNodes();
    
    for (Parse node : tags) {
      assertTrue(node.isPosTag());
    }
    
    assertEquals("PRP", tags[0].getType());
    assertEquals("VBD", tags[1].getType());
    assertEquals("RB", tags[2].getType());
    assertEquals("DT", tags[3].getType());
    assertEquals("NN", tags[4].getType());
    assertEquals("IN", tags[5].getType());
    assertEquals("DT", tags[6].getType());
    assertEquals("NNPS", tags[7].getType());
    assertEquals(",", tags[8].getType());
    assertEquals("CC", tags[9].getType());
    assertEquals("PRP", tags[10].getType());
    assertEquals("VBD", tags[11].getType());
    assertEquals("RB", tags[12].getType());
    assertEquals("JJ", tags[13].getType());
    assertEquals("IN", tags[14].getType());
    assertEquals("PRP$", tags[15].getType());
    assertEquals("NN", tags[16].getType());
    assertEquals(".", tags[17].getType());
  }
}
