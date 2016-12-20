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

package opennlp.tools.parser.chunking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.parser.AbstractContextGenerator;
import opennlp.tools.parser.Cons;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.StringList;

/**
 * Class to generator predictive contexts for deciding how constituents should be combined together.
 */
public class BuildContextGenerator extends AbstractContextGenerator {

  private Dictionary dict;
  private String[] unigram;
  private String[] bigram;
  private String[] trigram;

  /**
   * Creates a new context generator for making decisions about combining constitients togehter.
   *
   */
  public BuildContextGenerator() {
    super();
    zeroBackOff = false;
    useLabel = true;
  }

  public BuildContextGenerator(Dictionary dict) {
    this();
    this.dict = dict;
    unigram = new String[1];
    bigram = new String[2];
    trigram = new String[3];
  }

  public String[] getContext(Object o) {
    Object[] params = (Object[]) o;
    return getContext((Parse[]) params[0], (Integer) params[1]);
  }

  /**
   * Returns the predictive context used to determine how constituent at the specified index
   * should be combined with other contisuents.
   * @param constituents The constituents which have yet to be combined into new constituents.
   * @param index The index of the constituent whcihi is being considered.
   * @return the context for building constituents at the specified index.
   */
  public String[] getContext(Parse[] constituents, int index) {
    List<String> features = new ArrayList<>(100);
    int ps = constituents.length;

    // cons(-2), cons(-1), cons(0), cons(1), cons(2)
    // cons(-2)

    Collection<Parse> punct2s = null;
    Collection<Parse> punct_2s = null;

    Parse p_2 = null;
    if (index - 2 >= 0) {
      p_2 = constituents[index - 2];
    }

    Parse p_1 = null;
    if (index - 1 >= 0) {
      p_1 = constituents[index - 1];
      punct_2s = p_1.getPreviousPunctuationSet();
    }
    Parse p0 = constituents[index];
    Collection<Parse> punct_1s = p0.getPreviousPunctuationSet();
    Collection<Parse> punct1s = p0.getNextPunctuationSet();

    Parse p1 = null;
    if (index + 1 < ps) {
      p1 = constituents[index + 1];
      punct2s = p1.getNextPunctuationSet();
    }

    Parse p2 = null;
    if (index + 2 < ps) {
      p2 = constituents[index + 2];
    }

    boolean u_2 = true;
    boolean u_1 = true;
    boolean u0 = true;
    boolean u1 = true;
    boolean u2 = true;
    boolean b_2_1 = true;
    boolean b_10 = true;
    boolean b01 = true;
    boolean b12 = true;
    boolean t_2_10 = true;
    boolean t_101 = true;
    boolean t012 = true;

    if (dict != null) {

      if (p_2 != null) {
        unigram[0] = p_2.getHead().getCoveredText();
        u_2 = dict.contains(new StringList(unigram));
      }

      if (p2 != null) {
        unigram[0] = p2.getHead().getCoveredText();
        u2 = dict.contains(new StringList(unigram));
      }

      unigram[0] = p0.getHead().getCoveredText();
      u0 = dict.contains(new StringList(unigram));

      if (p_2 != null && p_1 != null) {
        bigram[0] = p_2.getHead().getCoveredText();
        bigram[1] = p_1.getHead().getCoveredText();
        b_2_1 = dict.contains(new StringList(bigram));

        trigram[0] = p_2.getHead().getCoveredText();
        trigram[1] = p_1.getHead().getCoveredText();
        trigram[2] = p0.getHead().getCoveredText();
        t_2_10 = dict.contains(new StringList(trigram));
      }
      if (p_1 != null && p1 != null) {
        trigram[0] = p_1.getHead().getCoveredText();
        trigram[1] = p0.getHead().getCoveredText();
        trigram[2] = p1.getHead().getCoveredText();
        t_101 = dict.contains(new StringList(trigram));
      }
      if (p_1 != null) {
        unigram[0] = p_1.getHead().getCoveredText();
        u_1 = dict.contains(new StringList(unigram));

        //extra check for 2==null case
        b_2_1 = b_2_1 && u_1 & u_2;
        t_2_10 = t_2_10 && u_1 & u_2 & u0;
        t_101 = t_101 && u_1 & u0 && u1;

        bigram[0] = p_1.getHead().getCoveredText();
        bigram[1] = p0.getHead().getCoveredText();
        b_10 = dict.contains(new StringList(bigram)) && u_1 && u0;
      }
      if (p1 != null && p2 != null) {
        bigram[0] = p1.getHead().getCoveredText();
        bigram[1] = p2.getHead().getCoveredText();
        b12 = dict.contains(new StringList(bigram));

        trigram[0] = p0.getHead().getCoveredText();
        trigram[1] = p1.getHead().getCoveredText();
        trigram[2] = p2.getHead().getCoveredText();
        t012 = dict.contains(new StringList(trigram));
      }
      if (p1 != null) {
        unigram[0] = p1.getHead().getCoveredText();
        u1 = dict.contains(new StringList(unigram));

        //extra check for 2==null case
        b12 = b12 && u1 && u2;
        t012 = t012 && u1 && u2 && u0;
        t_101 = t_101 && u0 && u_1 && u1;

        bigram[0] = p0.getHead().getCoveredText();
        bigram[1] = p1.getHead().getCoveredText();
        b01 = dict.contains(new StringList(bigram));
        b01 = b01 && u0 && u1;
      }
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

    Cons c_2 = new Cons(consp_2,consbop_2,-2,u_2);
    Cons c_1 = new Cons(consp_1,consbop_1,-1,u_1);
    Cons c0 = new Cons(consp0,consbop0,0,u0);
    Cons c1 = new Cons(consp1,consbop1,1,u1);
    Cons c2 = new Cons(consp2,consbop2,2,u2);

    //default
    features.add("default");
    //first constituent label
    //features.add("fl="+constituents[0].getLabel());

    // features.add("stage=cons(i)");
    // cons(-2), cons(-1), cons(0), cons(1), cons(2)
    if (u0) features.add(consp0);
    features.add(consbop0);

    if (u_2) features.add(consp_2);
    features.add(consbop_2);
    if (u_1) features.add(consp_1);
    features.add(consbop_1);
    if (u1) features.add(consp1);
    features.add(consbop1);
    if (u2) features.add(consp2);
    features.add(consbop2);

    //cons(0),cons(1)
    cons2(features,c0,c1,punct1s,b01);
    //cons(-1),cons(0)
    cons2(features,c_1,c0,punct_1s,b_10);
    //features.add("stage=cons(0),cons(1),cons(2)");
    cons3(features,c0,c1,c2,punct1s,punct2s,t012,b01,b12);
    cons3(features,c_2,c_1,c0,punct_2s,punct_1s,t_2_10,b_2_1,b_10);
    cons3(features,c_1,c0,c1,punct_1s,punct1s,t_101,b_10,b01);
    //features.add("stage=other");
    String p0Tag = p0.getType();
    if (p0Tag.equals("-RRB-")) {
      for (int pi = index - 1; pi >= 0; pi--) {
        Parse p = constituents[pi];
        if (p.getType().equals("-LRB-")) {
          features.add("bracketsmatch");
          break;
        }
        if (p.getLabel().startsWith(Parser.START)) {
          break;
        }
      }
    }
    if (p0Tag.equals("-RCB-")) {
      for (int pi = index - 1; pi >= 0; pi--) {
        Parse p = constituents[pi];
        if (p.getType().equals("-LCB-")) {
          features.add("bracketsmatch");
          break;
        }
        if (p.getLabel().startsWith(Parser.START)) {
          break;
        }
      }
    }
    if (p0Tag.equals("''")) {
      for (int pi = index - 1; pi >= 0; pi--) {
        Parse p = constituents[pi];
        if (p.getType().equals("``")) {
          features.add("quotesmatch");
          break;
        }
        if (p.getLabel().startsWith(Parser.START)) {
          break;
        }
      }
    }
    if (p0Tag.equals("'")) {
      for (int pi = index - 1; pi >= 0; pi--) {
        Parse p = constituents[pi];
        if (p.getType().equals("`")) {
          features.add("quotesmatch");
          break;
        }
        if (p.getLabel().startsWith(Parser.START)) {
          break;
        }
      }
    }
    if (p0Tag.equals(",")) {
      for (int pi = index - 1; pi >= 0; pi--) {
        Parse p = constituents[pi];
        if (p.getType().equals(",")) {
          features.add("iscomma");
          break;
        }
        if (p.getLabel().startsWith(Parser.START)) {
          break;
        }
      }
    }
    if (p0Tag.equals(".") && index == ps - 1) {
      for (int pi = index - 1; pi >= 0; pi--) {
        Parse p = constituents[pi];
        if (p.getLabel().startsWith(Parser.START)) {
          if (pi == 0) {
            features.add("endofsentence");
          }
          break;
        }
      }
    }
    return features.toArray(new String[features.size()]);
  }
}
