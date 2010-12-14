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


package opennlp.tools.lang.english;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;

import opennlp.tools.parser.Constituent;
import opennlp.tools.parser.GapLabeler;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.chunking.Parser;

/**
 * Class for storing the English head rules associated with parsing. 
 */
public class HeadRules implements opennlp.tools.parser.HeadRules, GapLabeler {
  
  private static class HeadRule {
    public boolean leftToRight;
    public String[] tags;
    public HeadRule(boolean l2r, String[] tags) {
      leftToRight = l2r;
      this.tags = tags;
    }
  }
  
  private Map<String, HeadRule> headRules;
  private Set<String> punctSet;
  
  /**
   * Creates a new set of head rules based on the specified head rules file.
   * 
   * @param ruleFile the head rules file.
   * 
   * @throws IOException if the head rules file can not be read.
   */
  public HeadRules(String ruleFile) throws IOException {
    this(new BufferedReader(new FileReader(ruleFile)));
  }
  
  /**
   * Creates a new set of head rules based on the specified reader.
   * 
   * @param rulesReader the head rules reader.
   * 
   * @throws IOException if the head rules reader can not be read.
   */
  public HeadRules(BufferedReader rulesReader) throws IOException {
    readHeadRules(rulesReader);
    punctSet = new HashSet<String>();
    punctSet.add(".");
    punctSet.add(",");
    punctSet.add("``");
    punctSet.add("''");
    //punctSet.add(":");
  }

  public Set<String> getPunctuationTags() {
    return punctSet;
  }
    
  public Parse getHead(Parse[] constituents, String type) {
    if (constituents[0].getType() == Parser.TOK_NODE) {
      return null;
    }
    HeadRule hr;
    if (type.equals("NP") || type.equals("NX")) {
      String[] tags1 = { "NN", "NNP", "NNPS", "NNS", "NX", "JJR", "POS" };
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = tags1.length - 1; ti >= 0; ti--) {
          if (constituents[ci].getType().equals(tags1[ti])) {
            return constituents[ci].getHead();
          }
        }
      }
      for (int ci = 0; ci < constituents.length; ci++) {
        if (constituents[ci].getType().equals("NP")) {
          return constituents[ci].getHead();
        }
      }
      String[] tags2 = { "$", "ADJP", "PRN" };
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = tags2.length - 1; ti >= 0; ti--) {
          if (constituents[ci].getType().equals(tags2[ti])) {
            return constituents[ci].getHead();
          }
        }
      }
      String[] tags3 = { "JJ", "JJS", "RB", "QP" };
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = tags3.length - 1; ti >= 0; ti--) {
          if (constituents[ci].getType().equals(tags3[ti])) {
            return constituents[ci].getHead();
          }
        }
      }
      return constituents[constituents.length - 1].getHead();
    }
    else if ((hr = (HeadRule) headRules.get(type)) != null) {
      String[] tags = hr.tags;
      int cl = constituents.length;
      int tl = tags.length;
      if (hr.leftToRight) {
        for (int ti = 0; ti < tl; ti++) {
          for (int ci = 0; ci < cl; ci++) {
            if (constituents[ci].getType().equals(tags[ti])) {
              return constituents[ci].getHead();
            }
          }
        }
        return constituents[0].getHead();
      }
      else {
        for (int ti = 0; ti < tl; ti++) {
          for (int ci = cl - 1; ci >= 0; ci--) {
            if (constituents[ci].getType().equals(tags[ti])) {
              return constituents[ci].getHead();
            }
          }
        }
        return constituents[cl - 1].getHead();
      }
    }
    return constituents[constituents.length - 1].getHead();
  }
    
  private void readHeadRules(BufferedReader str) throws IOException {
    String line;
    headRules = new HashMap<String, HeadRule>(30);
    while ((line = str.readLine()) != null) {
      StringTokenizer st = new StringTokenizer(line);
      String num = st.nextToken();
      String type = st.nextToken();
      String dir = st.nextToken();
      String[] tags = new String[Integer.parseInt(num)];
      int ti = 0;
      while (st.hasMoreTokens()) {
        tags[ti] = st.nextToken();
        ti++;
      }
      headRules.put(type, new HeadRule(dir.equals("1"), tags));
    }
  }

  public void labelGaps(Stack<Constituent> stack) {
    if (stack.size() > 4) {
      //Constituent con0 = (Constituent) stack.get(stack.size()-1);
      Constituent con1 = (Constituent) stack.get(stack.size()-2);
      Constituent con2 = (Constituent) stack.get(stack.size()-3);
      Constituent con3 = (Constituent) stack.get(stack.size()-4);
      Constituent con4 = (Constituent) stack.get(stack.size()-5);
      //System.err.println("con0="+con0.label+" con1="+con1.label+" con2="+con2.label+" con3="+con3.label+" con4="+con4.label);
      //subject extraction
      if (con1.getLabel().equals("NP") && con2.getLabel().equals("S") && con3.getLabel().equals("SBAR")) {
        con1.setLabel(con1.getLabel()+"-G");
        con2.setLabel(con2.getLabel()+"-G");
        con3.setLabel(con3.getLabel()+"-G");
      }
      //object extraction
      else if (con1.getLabel().equals("NP") && con2.getLabel().equals("VP") && con3.getLabel().equals("S") && con4.getLabel().equals("SBAR")) {
        con1.setLabel(con1.getLabel()+"-G");
        con2.setLabel(con2.getLabel()+"-G");
        con3.setLabel(con3.getLabel()+"-G");
        con4.setLabel(con4.getLabel()+"-G");
      }
    }
  }
  
  public void serialize(Writer writer) throws IOException {
    
    for (String type : headRules.keySet()) {
      
      HeadRule headRule = headRules.get(type);
      
      // write num of tags
      writer.write(Integer.toString(headRule.tags.length));
      writer.write(' ');
      
      // write type
      writer.write(type);
      writer.write(' ');
      
      // write l2r true == 1
      if (headRule.leftToRight)
        writer.write("1");
      else
        writer.write("0");
      
      // write tags
      for (String tag : headRule.tags) {
        writer.write(' ');
        writer.write(tag);
      }
      
      writer.write('\n');
    }
  }
}