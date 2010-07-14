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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import opennlp.tools.parser.Parse;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.Span;

/**
 * This class is a wrapper for {@link opennlp.tools.parser.Parse} mapping it to the API specified in {@link opennlp.tools.coref.mention.Parse}.
 *  This allows coreference to be done on the output of the parser.
 */
public class DefaultParse extends AbstractParse {

  public static String[] NAME_TYPES = {"person", "organization", "location", "date", "time", "percentage", "money"};
  
  private Parse parse;
  private int sentenceNumber;
  private static Set<String> entitySet = new HashSet<String>(Arrays.asList(NAME_TYPES));

  /**
   * Initializes the current instance.
   *
   * @param parse
   * @param sentenceNumber
   */
  public DefaultParse(Parse parse, int sentenceNumber) {
    this.parse = parse;
    this.sentenceNumber = sentenceNumber;
  }

  public int getSentenceNumber() {
    return sentenceNumber;
  }

  public List<opennlp.tools.coref.mention.Parse> getNamedEntities() {
    List<Parse> names = new ArrayList<Parse>();
    List<Parse> kids = new LinkedList<Parse>(Arrays.asList(parse.getChildren()));
    while (kids.size() > 0) {
      Parse p = kids.remove(0);
      if (entitySet.contains(p.getType())) {
        names.add(p);
      }
      else {
        kids.addAll(Arrays.asList(p.getChildren()));
      }
    }
    return createParses(names.toArray(new Parse[names.size()]));
  }

  public List<opennlp.tools.coref.mention.Parse> getChildren() {
    return createParses(parse.getChildren());
  }

  public List<opennlp.tools.coref.mention.Parse> getSyntacticChildren() {
    List<Parse> kids = new ArrayList<Parse>(Arrays.asList(parse.getChildren()));
    for (int ci = 0; ci < kids.size(); ci++) {
      Parse kid = kids.get(ci);
      if (entitySet.contains(kid.getType())) {
        kids.remove(ci);
        kids.addAll(ci, Arrays.asList(kid.getChildren()));
        ci--;
      }
    }
    return createParses(kids.toArray(new Parse[kids.size()]));
  }

  public List<opennlp.tools.coref.mention.Parse> getTokens() {
    List<Parse> tokens = new ArrayList<Parse>();
    List<Parse> kids = new LinkedList<Parse>(Arrays.asList(parse.getChildren()));
    while (kids.size() > 0) {
      Parse p = kids.remove(0);
      if (p.isPosTag()) {
        tokens.add(p);
      }
      else {
        kids.addAll(0,Arrays.asList(p.getChildren()));
      }
    }
    return createParses(tokens.toArray(new Parse[tokens.size()]));
  }

  public String getSyntacticType() {
    if (entitySet.contains(parse.getType())) {
      return null;
    }
    else {
      return parse.getType();
    }
  }

  private List<opennlp.tools.coref.mention.Parse> createParses(Parse[] parses) {
    List<opennlp.tools.coref.mention.Parse> newParses =
      new ArrayList<opennlp.tools.coref.mention.Parse>(parses.length);

    for (int pi=0,pn=parses.length;pi<pn;pi++) {
      newParses.add(new DefaultParse(parses[pi],sentenceNumber));
    }

    return newParses;
  }

  public String getEntityType() {
    if (entitySet.contains(parse.getType())) {
      return parse.getType();
    }
    else {
      return null;
    }
  }

  public boolean isParentNAC() {
    Parse parent = parse.getParent();
    while(parent != null) {
      if (parent.getType().equals("NAC")) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  public opennlp.tools.coref.mention.Parse getParent() {
    Parse parent = parse.getParent();
    if (parent == null) {
      return null;
    }
    else {
      return new DefaultParse(parent,sentenceNumber);
    }
  }

  public boolean isNamedEntity() {
    if (entitySet.contains(parse.getType())) {
      return true;
    }
    else {
      return false;
    }
  }

  public boolean isNounPhrase() {
    return parse.getType().equals("NP");
  }

  public boolean isSentence() {
    return parse.getType().equals(Parser.TOP_NODE);
  }

  public boolean isToken() {
    return parse.isPosTag();
  }

  public int getEntityId() {
    return -1;
  }

  public Span getSpan() {
    return parse.getSpan();
  }

  public int compareTo(opennlp.tools.coref.mention.Parse p) {

    if (p == this) {
      return 0;
    }

    if (getSentenceNumber() < p.getSentenceNumber()) {
      return -1;
    }
    else if (getSentenceNumber() > p.getSentenceNumber()) {
      return 1;
    }
    else {
      return parse.getSpan().compareTo(p.getSpan());
    }
  }

  @Override
  public String toString() {
    return parse.toString();
  }


  public opennlp.tools.coref.mention.Parse getPreviousToken() {
    Parse parent = parse.getParent();
    Parse node = parse;
    int index=-1;
    //find parent with previous children
    while(parent != null && index < 0) {
      index = parent.indexOf(node)-1;
      if (index < 0) {
        node = parent;
        parent = parent.getParent();
      }
    }
    //find right-most child which is a token
    if (index < 0) {
      return null;
    }
    else {
      Parse p = parent.getChildren()[index];
      while (!p.isPosTag()) {
        Parse[] kids = p.getChildren();
        p = kids[kids.length-1];
      }
      return new DefaultParse(p,sentenceNumber);
    }
  }

  public opennlp.tools.coref.mention.Parse getNextToken() {
    Parse parent = parse.getParent();
    Parse node = parse;
    int index=-1;
    //find parent with subsequent children
    while(parent != null) {
      index = parent.indexOf(node)+1;
      if (index == parent.getChildCount()) {
        node = parent;
        parent = parent.getParent();
      }
      else {
        break;
      }
    }
    //find left-most child which is a token
    if (parent == null) {
      return null;
    }
    else {
      Parse p = parent.getChildren()[index];
      while (!p.isPosTag()) {
        p = p.getChildren()[0];
      }
      return new DefaultParse(p,sentenceNumber);
    }
  }

  @Override
  public boolean equals(Object o) {

    boolean result;

    if (o == this) {
      result = true;
    }
    else if (o instanceof DefaultParse) {
      result = parse == ((DefaultParse) o).parse;
    }
    else {
      result = false;
    }

    return result;
  }

  @Override
  public int hashCode() {
    return parse.hashCode();
  }

  /**
   * Retrieves the {@link Parse}.
   *
   * @return the {@link Parse}
   */
  public Parse getParse() {
    return parse;
  }
}
