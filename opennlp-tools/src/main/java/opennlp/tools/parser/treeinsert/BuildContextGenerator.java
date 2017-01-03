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


package opennlp.tools.parser.treeinsert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import opennlp.tools.parser.AbstractContextGenerator;
import opennlp.tools.parser.Cons;
import opennlp.tools.parser.Parse;

/**
 * Creates the features or contexts for the building phase of parsing.
 * This phase builds constituents from the left-most node of these
 * constituents.
 */
public class BuildContextGenerator extends AbstractContextGenerator {

  private Parse[] leftNodes;

  public BuildContextGenerator() {
    super();
    leftNodes = new Parse[2];
  }

  public String[] getContext(Object o) {
    Object[] parts = (Object[]) o;
    return getContext((Parse[]) parts[0], (Integer) parts[1]);
  }

  /**
   * Returns the contexts/features for the decision to build a new constituent for the specified parse
   * at the specified index.
   * @param constituents The constituents of the parse so far.
   * @param index The index of the constituent where a build decision is being made.
   * @return the contexts/features for the decision to build a new constituent.
   */
  public String[] getContext(Parse[] constituents, int index) {
    int ps = constituents.length;

    Parse p0 = constituents[index];

    Parse p1 = null;
    if (index + 1 < ps) {
      p1 = constituents[index + 1];
    }

    Parse p2 = null;
    if (index  + 2 < ps) {
      p2 = constituents[index + 2];
    }


    Collection<Parse> punct_1s = p0.getPreviousPunctuationSet();
    Collection<Parse> punct1s = p0.getNextPunctuationSet();

    Collection<Parse> punct2s = null;
    if (p1 != null) {
      punct2s = p1.getNextPunctuationSet();
    }


    List<Parse> rf;
    if (index == 0) {
      rf = Collections.emptyList();
    }
    else {
      //this isn't a root node so, punctSet won't be used and can be passed as empty.
      Set<String> emptyPunctSet = Collections.emptySet();
      rf = Parser.getRightFrontier(constituents[0], emptyPunctSet);
    }
    getFrontierNodes(rf,leftNodes);
    Parse p_1 = leftNodes[0];
    Parse p_2 = leftNodes[1];

    Collection<Parse> punct_2s = null;
    if (p_1 != null) {
      punct_2s = p_1.getPreviousPunctuationSet();
    }

    String consp_2 = cons(p_2, -2);
    String consp_1 = cons(p_1, -1);
    String consp0 = cons(p0, 0);
    String consp1 = cons(p1, 1);
    String consp2 = cons(p2, 2);

    String consbop_2 = consbo(p_2, -2);
    String consbop_1 = consbo(p_1, -1);
    String consbop0 = consbo(p0, 0);
    String consbop1 = consbo(p1, 1);
    String consbop2 = consbo(p2, 2);

    Cons c_2 = new Cons(consp_2,consbop_2,-2,true);
    Cons c_1 = new Cons(consp_1,consbop_1,-1,true);
    Cons c0 = new Cons(consp0,consbop0,0,true);
    Cons c1 = new Cons(consp1,consbop1,1,true);
    Cons c2 = new Cons(consp2,consbop2,2,true);

    List<String> features = new ArrayList<>();
    features.add("default");

    //unigrams
    features.add(consp_2);
    features.add(consbop_2);
    features.add(consp_1);
    features.add(consbop_1);
    features.add(consp0);
    features.add(consbop0);
    features.add(consp1);
    features.add(consbop1);
    features.add(consp2);
    features.add(consbop2);

    //cons(0),cons(1)
    cons2(features,c0,c1,punct1s,true);
    //cons(-1),cons(0)
    cons2(features,c_1,c0,punct_1s,true);
    //features.add("stage=cons(0),cons(1),cons(2)");
    cons3(features,c0,c1,c2,punct1s,punct2s,true,true,true);
    cons3(features,c_2,c_1,c0,punct_2s,punct_1s,true,true,true);
    cons3(features,c_1,c0,c1,punct_1s,punct_1s,true,true,true);

    if (rf.isEmpty()) {
      features.add(EOS + "," + consp0);
      features.add(EOS + "," + consbop0);
    }

    return features.toArray(new String[features.size()]);
  }

}
