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
import opennlp.tools.parser.Parse;

public class CheckContextGenerator extends AbstractContextGenerator {

  private Parse[] leftNodes;

  public CheckContextGenerator(Set<String> punctSet) {
    this.punctSet = punctSet;
    leftNodes = new Parse[2];
  }

  public String[] getContext(Object arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  public String[] getContext(Parse parent, Parse[] constituents, int index, boolean trimFrontier) {
    List<String> features = new ArrayList<>(100);
    //default
    features.add("default");
    Parse[] children = Parser.collapsePunctuation(parent.getChildren(),punctSet);
    Parse pstart = children[0];
    Parse pend = children[children.length - 1];
    String type = parent.getType();
    checkcons(pstart, "begin", type, features);
    checkcons(pend, "last", type, features);
    String production = "p=" + production(parent,false);
    String punctProduction = "pp=" + production(parent,true);
    features.add(production);
    features.add(punctProduction);


    Parse p1 = null;
    Parse p2 = null;
    Collection<Parse> p1s = constituents[index].getNextPunctuationSet();
    Collection<Parse> p2s = null;
    Collection<Parse> p_1s = constituents[index].getPreviousPunctuationSet();
    Collection<Parse> p_2s = null;
    List<Parse> rf;
    if (index == 0) {
      rf = Collections.emptyList();
    }
    else {
      rf = Parser.getRightFrontier(constituents[0], punctSet);
      if (trimFrontier) {
        int pi = rf.indexOf(parent);
        if (pi == -1) {
          throw new RuntimeException("Parent not found in right frontier:" + parent + " rf=" + rf);
        }
        else {
          for (int ri = 0; ri <= pi; ri++) {
            rf.remove(0);
          }
        }
      }
    }

    getFrontierNodes(rf,leftNodes);
    Parse p_1 = leftNodes[0];
    Parse p_2 = leftNodes[1];
    int ps = constituents.length;
    if (p_1 != null) {
      p_2s = p_1.getPreviousPunctuationSet();
    }
    if (index + 1 < ps) {
      p1 = constituents[index + 1];
      p2s = p1.getNextPunctuationSet();
    }
    if (index + 2 < ps) {
      p2 = constituents[index + 2];
    }
    surround(p_1, -1, type, p_1s, features);
    surround(p_2, -2, type, p_2s, features);
    surround(p1, 1, type, p1s, features);
    surround(p2, 2, type, p2s, features);

    return features.toArray(new String[features.size()]);
  }

}
