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

package opennlp.tools.coref.mention;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Finds head information from Penn Treebank style parses.
 */
public final class PTBHeadFinder implements HeadFinder {

  private static PTBHeadFinder instance;
  private static Set<String> skipSet = new HashSet<String>();
  static {
    skipSet.add("POS");
    skipSet.add(",");
    skipSet.add(":");
    skipSet.add(".");
    skipSet.add("''");
    skipSet.add("-RRB-");
    skipSet.add("-RCB-");
  }

  private PTBHeadFinder() {}

  /**
   * Returns an instance of this head finder.
   * @return an instance of this head finder.
   */
  public static HeadFinder getInstance() {
    if (instance == null) {
      instance = new PTBHeadFinder();
    }
    return instance;
  }

  public Parse getHead(Parse p) {
    if (p == null) {
      return null;
    }
    if (p.isNounPhrase()) {
      List<Parse> parts = p.getSyntacticChildren();
      //shallow parse POS
      if (parts.size() > 2) {
        Parse child0 = parts.get(0);
        Parse child1 = parts.get(1);
        Parse child2 = parts.get(2);
        if (child1.isToken() && child1.getSyntacticType().equals("POS") && child0.isNounPhrase() && child2.isNounPhrase()) {
          return child2;
        }
      }
      //full parse POS
      if (parts.size() > 1) {
        Parse child0 = parts.get(0);
        if (child0.isNounPhrase()) {
          List<Parse> ctoks = child0.getTokens();
          if (ctoks.size() == 0) {
            System.err.println("PTBHeadFinder: NP "+child0+" with no tokens");
          }
          Parse tok = ctoks.get(ctoks.size() - 1);
          if (tok.getSyntacticType().equals("POS")) {
            return null;
          }
        }
      }
      //coordinated nps are their own entities
      if (parts.size() > 1) {
        for (int pi = 1; pi < parts.size() - 1; pi++) {
          Parse child = parts.get(pi);
          if (child.isToken() && child.getSyntacticType().equals("CC")) {
            return null;
          }
        }
      }
      //all other NPs
      for (int pi = 0; pi < parts.size(); pi++) {
        Parse child = parts.get(pi);
        //System.err.println("PTBHeadFinder.getHead: "+p.getSyntacticType()+" "+p+" child "+pi+"="+child.getSyntacticType()+" "+child);
        if (child.isNounPhrase()) {
          return child;
        }
      }
      return null;
    }
    else {
      return null;
    }
  }

  public int getHeadIndex(Parse p) {
    List<Parse> sChildren = p.getSyntacticChildren();
    boolean countTokens = false;
    int tokenCount = 0;
    //check for NP -> NN S type structures and return last token before S as head.
    for (int sci=0,scn = sChildren.size();sci<scn;sci++) {
      Parse sc = sChildren.get(sci);
      //System.err.println("PTBHeadFinder.getHeadIndex "+p+" "+p.getSyntacticType()+" sChild "+sci+" type = "+sc.getSyntacticType());
      if (sc.getSyntacticType().startsWith("S")) {
        if (sci != 0) {
          countTokens = true;
        }
        else {
          //System.err.println("PTBHeadFinder.getHeadIndex(): NP -> S production assuming right-most head");
        }
      }
      if (countTokens) {
        tokenCount+=sc.getTokens().size();
      }
    }
    List<Parse> toks = p.getTokens();
    if (toks.size() == 0) {
      System.err.println("PTBHeadFinder.getHeadIndex(): empty tok list for parse "+p);
    }
    for (int ti = toks.size() - tokenCount -1; ti >= 0; ti--) {
      Parse tok = toks.get(ti);
      if (!skipSet.contains(tok.getSyntacticType())) {
        return ti;
      }
    }
    //System.err.println("PTBHeadFinder.getHeadIndex: "+p+" hi="+toks.size()+"-"+tokenCount+" -1 = "+(toks.size()-tokenCount -1));
    return toks.size() - tokenCount -1;
  }

  /** Returns the bottom-most head of a <code>Parse</code>.  If no
      head is available which is a child of <code>p</code> then
      <code>p</code> is returned. */
  public Parse getLastHead(Parse p) {
    Parse head;
    //System.err.print("EntityFinder.getLastHead: "+p);

    while (null != (head = getHead(p))) {
      //System.err.print(" -> "+head);
      //if (p.getEntityId() != -1 && head.getEntityId() != p.getEntityId()) {	System.err.println(p+" ("+p.getEntityId()+") -> "+head+" ("+head.getEntityId()+")");      }
      p = head;
    }
    //System.err.println(" -> null");
    return p;
  }

  public Parse getHeadToken(Parse p) {
    List<Parse> toks = p.getTokens();
    return toks.get(getHeadIndex(p));
  }
}
