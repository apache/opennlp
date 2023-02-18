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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Parse} class.
 */
public class ParseTest {

  public static final String PARSE_STRING = "(TOP  (S (S (NP-SBJ (PRP She)  )(VP (VBD was)  "
      + "(ADVP (RB just)  )(NP-PRD (NP (DT another)  (NN freighter)  )(PP (IN from)  (NP (DT the)  "
      + "(NNPS States)  )))))(, ,)  (CC and) (S (NP-SBJ (PRP she)  )(VP (VBD seemed)  "
      + "(ADJP-PRD (ADJP (RB as)  (JJ commonplace)  )(PP (IN as)  (NP (PRP$ her)  " +
      "(NN name)  )))))(. .)  ))";

  @Test
  void testToHashCode() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    p1.hashCode();
  }

  @Test
  void testToString() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    p1.toString();
  }

  @Test
  void testEquals() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    Assertions.assertEquals(p1, p1);
  }

  @Test
  void testParseClone() {
    Parse p1 = Parse.parseParse(PARSE_STRING);
    Parse p2 = (Parse) p1.clone();
    Assertions.assertEquals(p1, p2);
    Assertions.assertEquals(p2, p1);
  }

  @Test
  void testGetText() {
    Parse p = Parse.parseParse(PARSE_STRING);

    // TODO: Why does parse attaches a space to the end of the text ???
    String expectedText = "She was just another freighter from the States , " +
        "and she seemed as commonplace as her name . ";

    Assertions.assertEquals(expectedText, p.getText());
  }

  @Test
  void testShow() {
    Parse p1 = Parse.parseParse(PARSE_STRING);

    StringBuffer parseString = new StringBuffer();
    p1.show(parseString);
    Parse p2 = Parse.parseParse(parseString.toString());
    Assertions.assertEquals(p1, p2);
  }

  @Test
  void testTokenReplacement() {
    Parse p1 = Parse.parseParse("(TOP  (S-CLF (NP-SBJ (PRP It)  )(VP (VBD was) " +
        " (NP-PRD (NP (DT the)  (NN trial)  )(PP (IN of) " +
        " (NP (NP (NN oleomargarine)  (NN heir)  )(NP (NNP Minot) " +
        " (PRN (-LRB- -LRB-) (NNP Mickey) " +
        " (-RRB- -RRB-) )(NNP Jelke)  )))(PP (IN for) " +
        " (NP (JJ compulsory)  (NN prostitution) " +
        " ))(PP-LOC (IN in)  (NP (NNP New)  (NNP York) " +
        " )))(SBAR (WHNP-1 (WDT that)  )(S (VP (VBD put) " +
        " (NP (DT the)  (NN spotlight)  )(PP (IN on)  (NP (DT the) " +
        " (JJ international)  (NN play-girl)  ))))))(. .)  ))");

    StringBuffer parseString = new StringBuffer();
    p1.show(parseString);

    Parse p2 = Parse.parseParse(parseString.toString());
    Assertions.assertEquals(p1, p2);
  }

  @Test
  void testGetTagNodes() {
    Parse p = Parse.parseParse(PARSE_STRING);

    Parse[] tags = p.getTagNodes();

    for (Parse node : tags) {
      Assertions.assertTrue(node.isPosTag());
    }

    Assertions.assertEquals("PRP", tags[0].getType());
    Assertions.assertEquals("VBD", tags[1].getType());
    Assertions.assertEquals("RB", tags[2].getType());
    Assertions.assertEquals("DT", tags[3].getType());
    Assertions.assertEquals("NN", tags[4].getType());
    Assertions.assertEquals("IN", tags[5].getType());
    Assertions.assertEquals("DT", tags[6].getType());
    Assertions.assertEquals("NNPS", tags[7].getType());
    Assertions.assertEquals(",", tags[8].getType());
    Assertions.assertEquals("CC", tags[9].getType());
    Assertions.assertEquals("PRP", tags[10].getType());
    Assertions.assertEquals("VBD", tags[11].getType());
    Assertions.assertEquals("RB", tags[12].getType());
    Assertions.assertEquals("JJ", tags[13].getType());
    Assertions.assertEquals("IN", tags[14].getType());
    Assertions.assertEquals("PRP$", tags[15].getType());
    Assertions.assertEquals("NN", tags[16].getType());
    Assertions.assertEquals(".", tags[17].getType());
  }
}
