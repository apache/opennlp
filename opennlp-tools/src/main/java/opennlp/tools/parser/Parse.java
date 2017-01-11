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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.util.Span;

/**
 * Data structure for holding parse constituents.
 */
public class Parse implements Cloneable, Comparable<Parse> {

  public static final String BRACKET_LRB = "(";
  public static final String BRACKET_RRB = ")";
  public static final String BRACKET_LCB = "{";
  public static final String BRACKET_RCB = "}";
  public static final String BRACKET_LSB = "[";
  public static final String BRACKET_RSB = "]";

  /**
   * The text string on which this parse is based.
   * This object is shared among all parses for the same sentence.
   */
  private String text;

  /**
   * The character offsets into the text for this constituent.
   */
  private Span span;

  /**
   * The syntactic type of this parse.
   */
  private String type;

  /**
   * The sub-constituents of this parse.
   */
  private List<Parse> parts;

  /**
   * The head parse of this parse. A parse can be its own head.
   */
  private Parse head;

  /**
   * A string used during parse construction to specify which
   * stage of parsing has been performed on this node.
   */
  private String label;

  /**
   * Index in the sentence of the head of this constituent.
   */
  private int headIndex;

  /**
   * The parent parse of this parse.
   */
  private Parse parent;

  /**
   * The probability associated with the syntactic type
   * assigned to this parse.
   */
  private double prob;

  /**
   * The string buffer used to track the derivation of this parse.
   */
  private StringBuffer derivation;

  /**
   * Specifies whether this constituent was built during the chunking phase.
   */
  private boolean isChunk;

  /**
   * The pattern used to find the base constituent label of a
   * Penn Treebank labeled constituent.
   */
  private static Pattern typePattern = Pattern.compile("^([^ =-]+)");

  /**
   * The pattern used to find the function tags.
   */
  private static Pattern funTypePattern = Pattern.compile("^[^ =-]+-([^ =-]+)");

  /**
   * The patter used to identify tokens in Penn Treebank labeled constituents.
   */
  private static Pattern tokenPattern = Pattern.compile("^[^ ()]+ ([^ ()]+)\\s*\\)");

  /**
   * The set of punctuation parses which are between this parse and the previous parse.
   */
  private Collection<Parse> prevPunctSet;

  /**
   * The set of punctuation parses which are between this parse and
   * the subsequent parse.
   */
  private Collection<Parse> nextPunctSet;

  /**
   * Specifies whether constituent labels should include parts specified
   * after minus character.
   */
  private static boolean useFunctionTags;

  /**
   * Creates a new parse node for this specified text and span of the specified type
   * with the specified probability and the specified head index.
   *
   * @param text The text of the sentence for which this node is a part of.
   * @param span The character offsets for this node within the specified text.
   * @param type The constituent label of this node.
   * @param p The probability of this parse.
   * @param index The token index of the head of this parse.
   */
  public Parse(String text, Span span, String type, double p, int index) {
    this.text = text;
    this.span = span;
    this.type = type;
    this.prob = p;
    this.head = this;
    this.headIndex = index;
    this.parts = new LinkedList<>();
    this.label = null;
    this.parent = null;
  }

  /**
   * Creates a new parse node for this specified text and span of the specified type with
   * the specified probability and the specified head and head index.
   *
   * @param text The text of the sentence for which this node is a part of.
   * @param span The character offsets for this node within the specified text.
   * @param type The constituent label of this node.
   * @param p The probability of this parse.
   * @param h The head token of this parse.
   */
  public Parse(String text, Span span, String type, double p, Parse h) {
    this(text, span, type, p, 0);
    if (h != null) {
      this.head = h;
      this.headIndex = h.headIndex;
    }
  }

  @Override
  public Object clone() {
    Parse p = new Parse(this.text, this.span, this.type, this.prob, this.head);
    p.parts = new LinkedList<>();
    p.parts.addAll(this.parts);

    if (derivation != null) {
      p.derivation = new StringBuffer(100);
      p.derivation.append(this.derivation.toString());
    }
    p.label = this.label;
    return (p);
  }

  /**
   * Clones the right frontier of parse up to the specified node.
   *
   * @param node The last node in the right frontier of the parse tree which should be cloned.
   * @return A clone of this parse and its right frontier up to and including the specified node.
   */
  public Parse clone(Parse node) {
    if (this == node) {
      return (Parse) this.clone();
    }
    else {
      Parse c = (Parse) this.clone();
      Parse lc = c.parts.get(parts.size() - 1);
      c.parts.set(parts.size() - 1,lc.clone(node));
      return c;
    }
  }

  /**
   * Clones the right frontier of this root parse up to and including the specified node.
   *
   * @param node The last node in the right frontier of the parse tree which should be cloned.
   * @param parseIndex The child index of the parse for this root node.
   * @return A clone of this root parse and its right frontier up to and including the specified node.
   */
  public Parse cloneRoot(Parse node, int parseIndex) {
    Parse c = (Parse) this.clone();
    Parse fc = c.parts.get(parseIndex);
    c.parts.set(parseIndex,fc.clone(node));
    return c;
  }

  /**
   * Specifies whether function tags should be included as part of the constituent type.
   *
   * @param uft true is they should be included; false otherwise.
   */
  public static void useFunctionTags(boolean uft) {
    useFunctionTags = uft;
  }


  /**
   * Set the type of this constituent to the specified type.
   *
   * @param type The type of this constituent.
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Returns the constituent label for this node of the parse.
   *
   * @return The constituent label for this node of the parse.
   */
  public String getType() {
    return type;
  }

  /**
   * Returns the set of punctuation parses that occur immediately before this parse.
   *
   * @return the set of punctuation parses that occur immediately before this parse.
   */
  public Collection<Parse> getPreviousPunctuationSet() {
    return prevPunctSet;
  }

  /**
   * Designates that the specified punctuation should is prior to this parse.
   *
   * @param punct The punctuation.
   */
  public void addPreviousPunctuation(Parse punct) {
    if (this.prevPunctSet == null) {
      this.prevPunctSet = new TreeSet<>();
    }
    prevPunctSet.add(punct);
  }

  /**
   * Returns the set of punctuation parses that occur immediately after this parse.
   *
   * @return the set of punctuation parses that occur immediately after this parse.
   */
  public Collection<Parse> getNextPunctuationSet() {
    return nextPunctSet;
  }

  /**
   * Designates that the specified punctuation follows this parse.
   *
   * @param punct The punctuation set.
   */
  public void addNextPunctuation(Parse punct) {
    if (this.nextPunctSet == null) {
      this.nextPunctSet = new TreeSet<>();
    }
    nextPunctSet.add(punct);
  }

  /**
   * Sets the set of punctuation tags which follow this parse.
   *
   * @param punctSet The set of punctuation tags which follow this parse.
   */
  public void setNextPunctuation(Collection<Parse> punctSet) {
    this.nextPunctSet = punctSet;
  }

  /**
   * Sets the set of punctuation tags which preceed this parse.
   *
   * @param punctSet The set of punctuation tags which preceed this parse.
   */
  public void setPrevPunctuation(Collection<Parse> punctSet) {
    this.prevPunctSet = punctSet;
  }

  /**
   * Inserts the specified constituent into this parse based on its text span.This
   * method assumes that the specified constituent can be inserted into this parse.
   *
   * @param constituent The constituent to be inserted.
   */
  public void insert(final Parse constituent) {
    Span ic = constituent.span;
    if (span.contains(ic)) {
      //double oprob=c.prob;
      int pi = 0;
      int pn = parts.size();
      for (; pi < pn; pi++) {
        Parse subPart = parts.get(pi);
        //System.err.println("Parse.insert:con="+constituent+" sp["+pi+"] "+subPart+" "+subPart.getType());
        Span sp = subPart.span;
        if (sp.getStart() >= ic.getEnd()) {
          break;
        }
        // constituent contains subPart
        else if (ic.contains(sp)) {
          //System.err.println("Parse.insert:con contains subPart");
          parts.remove(pi);
          pi--;
          constituent.parts.add(subPart);
          subPart.setParent(constituent);
          //System.err.println("Parse.insert: "+subPart.hashCode()+" -> "+subPart.getParent().hashCode());
          pn = parts.size();
        }
        else if (sp.contains(ic)) {
          //System.err.println("Parse.insert:subPart contains con");
          subPart.insert(constituent);
          return;
        }
      }
      //System.err.println("Parse.insert:adding con="+constituent+" to "+this);
      parts.add(pi, constituent);
      constituent.setParent(this);
      // System.err.println("Parse.insert: "+constituent.hashCode()+" -> "
      // +constituent.getParent().hashCode());
    }
    else {
      throw new IllegalArgumentException("Inserting constituent not contained in the sentence!");
    }
  }

  /**
   * Appends the specified string buffer with a string representation of this parse.
   *
   * @param sb A string buffer into which the parse string can be appended.
   */
  public void show(StringBuffer sb) {
    int start;
    start = span.getStart();
    if (!type.equals(AbstractBottomUpParser.TOK_NODE)) {
      sb.append("(");
      sb.append(type).append(" ");
      //System.out.print(label+" ");
      //System.out.print(head+" ");
      //System.out.print(df.format(prob)+" ");
    }
    for (Iterator<Parse> i = parts.iterator(); i.hasNext();) {
      Parse c = i.next();
      Span s = c.span;
      if (start < s.getStart()) {
        //System.out.println("pre "+start+" "+s.getStart());
        sb.append(encodeToken(text.substring(start, s.getStart())));
      }
      c.show(sb);
      start = s.getEnd();
    }
    if (start < span.getEnd()) {
      sb.append(encodeToken(text.substring(start, span.getEnd())));
    }
    if (!type.equals(AbstractBottomUpParser.TOK_NODE)) {
      sb.append(")");
    }
  }

  /**
   * Displays this parse using Penn Treebank-style formatting.
   */
  public void show() {
    StringBuffer sb = new StringBuffer(text.length() * 4);
    show(sb);
    System.out.println(sb);
  }


  /**
   * Returns the probability associated with the pos-tag sequence assigned to this parse.
   *
   * @return The probability associated with the pos-tag sequence assigned to this parse.
   */
  public double getTagSequenceProb() {
    //System.err.println("Parse.getTagSequenceProb: "+type+" "+this);
    if (parts.size() == 1 && (parts.get(0)).type.equals(AbstractBottomUpParser.TOK_NODE)) {
      //System.err.println(this+" "+prob);
      return (Math.log(prob));
    }
    else if (parts.size() == 0) {
      System.err.println("Parse.getTagSequenceProb: Wrong base case!");
      return (0.0);
    }
    else {
      double sum = 0.0;
      for (Iterator<Parse> pi = parts.iterator(); pi.hasNext();) {
        sum += pi.next().getTagSequenceProb();
      }
      return sum;
    }
  }

  /**
   * Returns whether this parse is complete.
   *
   * @return Returns true if the parse contains a single top-most node.
   */
  public boolean complete() {
    return (parts.size() == 1);
  }

  public String getCoveredText() {
    return text.substring(span.getStart(), span.getEnd());
  }

  /**
   * Represents this parse in a human readable way.
   */
  @Override
  public String toString() {
    // TODO: Use the commented code in next bigger release,
    // change probably breaks backward compatibility in some
    // applications
    //StringBuffer buffer = new StringBuffer();
    //show(buffer);
    //return buffer.toString();
    return getCoveredText();
  }

  /**
   * Returns the text of the sentence over which this parse was formed.
   *
   * @return The text of the sentence over which this parse was formed.
   */
  public String getText() {
    return text;
  }

  /**
   * Returns the character offsets for this constituent.
   *
   * @return The character offsets for this constituent.
   */
  public Span getSpan() {
    return span;
  }

  /**
   * Returns the log of the product of the probability associated with all the
   * decisions which formed this constituent.
   *
   * @return The log of the product of the probability associated with all the
   * decisions which formed this constituent.
   */
  public double getProb() {
    return prob;
  }

  /**
   * Adds the specified probability log to this current log for this parse.
   *
   * @param logProb The probability of an action performed on this parse.
   */
  public void addProb(double logProb) {
    this.prob += logProb;
  }

  /**
   * Returns the child constituents of this constituent
   * .
   * @return The child constituents of this constituent.
   */
  public Parse[] getChildren() {
    return parts.toArray(new Parse[parts.size()]);
  }

  /**
   * Replaces the child at the specified index with a new child with the specified label.
   *
   * @param index The index of the child to be replaced.
   * @param label The label to be assigned to the new child.
   */
  public void setChild(int index, String label) {
    Parse newChild = (Parse) (parts.get(index)).clone();
    newChild.setLabel(label);
    parts.set(index,newChild);
  }

  public void add(Parse daughter, HeadRules rules) {
    if (daughter.prevPunctSet != null) {
      parts.addAll(daughter.prevPunctSet);
    }
    parts.add(daughter);
    this.span = new Span(span.getStart(),daughter.getSpan().getEnd());
    this.head = rules.getHead(getChildren(),type);
    if (head == null) {
      System.err.println(parts);
    }
    this.headIndex = head.headIndex;
  }

  public void remove(int index) {
    parts.remove(index);
    if (! parts.isEmpty()) {
      if (index == 0 || index == parts.size()) { //size is orig last element
        span = new Span((parts.get(0)).span.getStart(),(parts.get(parts.size() - 1)).span.getEnd());
      }
    }
  }

  public Parse adjoinRoot(Parse node, HeadRules rules, int parseIndex) {
    Parse lastChild = parts.get(parseIndex);
    Parse adjNode = new Parse(this.text,new Span(lastChild.getSpan().getStart(),
        node.getSpan().getEnd()),lastChild.getType(),1,
        rules.getHead(new Parse[]{lastChild,node},lastChild.getType()));
    adjNode.parts.add(lastChild);
    if (node.prevPunctSet != null) {
      adjNode.parts.addAll(node.prevPunctSet);
    }
    adjNode.parts.add(node);
    parts.set(parseIndex,adjNode);
    return adjNode;
  }

  /**
   * Sister adjoins this node's last child and the specified sister node and returns their
   * new parent node.  The new parent node replace this nodes last child.
   *
   * @param sister The node to be adjoined.
   * @param rules The head rules for the parser.
   * @return The new parent node of this node and the specified sister node.
   */
  public Parse adjoin(Parse sister, HeadRules rules) {
    Parse lastChild = parts.get(parts.size() - 1);
    Parse adjNode = new Parse(this.text,new Span(lastChild.getSpan().getStart(),sister.getSpan().getEnd()),
        lastChild.getType(),1,rules.getHead(new Parse[]{lastChild,sister},lastChild.getType()));
    adjNode.parts.add(lastChild);
    if (sister.prevPunctSet != null) {
      adjNode.parts.addAll(sister.prevPunctSet);
    }
    adjNode.parts.add(sister);
    parts.set(parts.size() - 1, adjNode);
    this.span = new Span(span.getStart(),sister.getSpan().getEnd());
    this.head = rules.getHead(getChildren(),type);
    this.headIndex = head.headIndex;
    return adjNode;
  }

  public void expandTopNode(Parse root) {
    boolean beforeRoot = true;
    //System.err.println("expandTopNode: parts="+parts);
    for (int pi = 0, ai = 0; pi < parts.size(); pi++,ai++) {
      Parse node = parts.get(pi);
      if (node == root) {
        beforeRoot = false;
      }
      else if (beforeRoot) {
        root.parts.add(ai,node);
        parts.remove(pi);
        pi--;
      }
      else {
        root.parts.add(node);
        parts.remove(pi);
        pi--;
      }
    }
    root.updateSpan();
  }

  /**
   * Returns the number of children for this parse node.
   *
   * @return the number of children for this parse node.
   */
  public int getChildCount() {
    return parts.size();
  }

  /**
   * Returns the index of this specified child.
   *
   * @param child A child of this parse.
   *
   * @return the index of this specified child or -1 if the specified child is not a child of this parse.
   */
  public int indexOf(Parse child) {
    return parts.indexOf(child);
  }

  /**
   * Returns the head constituent associated with this constituent.
   *
   * @return The head constituent associated with this constituent.
   */
  public Parse getHead() {
    return head;
  }

  /**
   * Returns the index within a sentence of the head token for this parse.
   *
   * @return The index within a sentence of the head token for this parse.
   */
  public int getHeadIndex() {
    return headIndex;
  }

  /**
   * Returns the label assigned to this parse node during parsing
   * which specifies how this node will be formed into a constituent.
   *
   * @return The outcome label assigned to this node during parsing.
   */
  public String getLabel() {
    return label;
  }

  /**
   * Assigns this parse the specified label.  This is used by parsing schemes to
   * tag parsing nodes while building.
   *
   * @param label A label indicating something about the stage of building for this parse node.
   */
  public void setLabel(String label) {
    this.label = label;
  }

  private static String getType(String rest) {
    if (rest.startsWith("-LCB-")) {
      return "-LCB-";
    }
    else if (rest.startsWith("-RCB-")) {
      return "-RCB-";
    }
    else if (rest.startsWith("-LRB-")) {
      return "-LRB-";
    }
    else if (rest.startsWith("-RRB-")) {
      return "-RRB-";
    }
    else if (rest.startsWith("-RSB-")) {
      return "-RSB-";
    }
    else if (rest.startsWith("-LSB-")) {
      return "-LSB-";
    }

    else if (rest.startsWith("-NONE-")) {
      return "-NONE-";
    }
    else {
      Matcher typeMatcher = typePattern.matcher(rest);
      if (typeMatcher.find()) {
        String type = typeMatcher.group(1);
        if (useFunctionTags) {
          Matcher funMatcher = funTypePattern.matcher(rest);
          if (funMatcher.find()) {
            String ftag = funMatcher.group(1);
            type = type + "-" + ftag;
          }
        }
        return type;
      }
    }
    return null;
  }

  private static String encodeToken(String token) {
    if (BRACKET_LRB.equals(token)) {
      return "-LRB-";
    }
    else if (BRACKET_RRB.equals(token)) {
      return "-RRB-";
    }
    else if (BRACKET_LCB.equals(token)) {
      return "-LCB-";
    }
    else if (BRACKET_RCB.equals(token)) {
      return "-RCB-";
    }
    else if (BRACKET_LSB.equals(token)) {
      return "-LSB-";
    }
    else if (BRACKET_RSB.equals(token)) {
      return "-RSB-";
    }

    return token;
  }

  private static String decodeToken(String token) {
    if ("-LRB-".equals(token)) {
      return BRACKET_LRB;
    }
    else if ("-RRB-".equals(token)) {
      return BRACKET_RRB;
    }
    else if ("-LCB-".equals(token)) {
      return BRACKET_LCB;
    }
    else if ("-RCB-".equals(token)) {
      return BRACKET_RCB;
    }
    else if ("-LSB-".equals(token)) {
      return BRACKET_LSB;
    }
    else if ("-RSB-".equals(token)) {
      return BRACKET_RSB;
    }

    return token;
  }

  /**
   * Returns the string containing the token for the specified portion of the parse string or
   * null if the portion of the parse string does not represent a token.
   *
   * @param rest The portion of the parse string remaining to be processed.
   *
   * @return The string containing the token for the specified portion of the parse string or
   *     null if the portion of the parse string does not represent a token.
   */
  private static String getToken(String rest) {
    Matcher tokenMatcher = tokenPattern.matcher(rest);
    if (tokenMatcher.find()) {
      return decodeToken(tokenMatcher.group(1));
    }
    return null;
  }

  /**
   * Computes the head parses for this parse and its sub-parses and stores this information
   * in the parse data structure.
   *
   * @param rules The head rules which determine how the head of the parse is computed.
   */
  public void updateHeads(HeadRules rules) {
    if (parts != null && parts.size() != 0) {
      for (int pi = 0, pn = parts.size(); pi < pn; pi++) {
        Parse c = parts.get(pi);
        c.updateHeads(rules);
      }
      this.head = rules.getHead(parts.toArray(new Parse[parts.size()]), type);
      if (head == null) {
        head = this;
      }
      else {
        this.headIndex = head.headIndex;
      }
    }
    else {
      this.head = this;
    }
  }

  public void updateSpan() {
    span = new Span((parts.get(0)).span.getStart(),(parts.get(parts.size() - 1)).span.getEnd());
  }

  /**
   * Prune the specified sentence parse of vacuous productions.
   *
   * @param parse
   */
  public static void pruneParse(Parse parse) {
    List<Parse> nodes = new LinkedList<>();
    nodes.add(parse);
    while (nodes.size() != 0) {
      Parse node = nodes.remove(0);
      Parse[] children = node.getChildren();
      if (children.length == 1 && node.getType().equals(children[0].getType())) {
        int index = node.getParent().parts.indexOf(node);
        children[0].setParent(node.getParent());
        node.getParent().parts.set(index,children[0]);
        node.parent = null;
        node.parts = null;
      }
      nodes.addAll(Arrays.asList(children));
    }
  }

  public static void fixPossesives(Parse parse) {
    Parse[] tags = parse.getTagNodes();
    for (int ti = 0; ti < tags.length; ti++) {
      if (tags[ti].getType().equals("POS")) {
        if (ti + 1 < tags.length && tags[ti + 1].getParent() == tags[ti].getParent().getParent()) {
          int start = tags[ti + 1].getSpan().getStart();
          int end = tags[ti + 1].getSpan().getEnd();
          for (int npi = ti + 2; npi < tags.length; npi++) {
            if (tags[npi].getParent() == tags[npi - 1].getParent()) {
              end = tags[npi].getSpan().getEnd();
            }
            else {
              break;
            }
          }
          Parse npPos = new Parse(parse.getText(), new Span(start,end), "NP", 1 , tags[ti + 1]);
          parse.insert(npPos);
        }
      }
    }
  }



  /**
   * Parses the specified tree-bank style parse string and return a Parse structure for that string.
   *
   * @param parse A tree-bank style parse string.
   *
   * @return a Parse structure for the specified tree-bank style parse string.
   */
  public static Parse parseParse(String parse) {
    return parseParse(parse,null);
  }

  /**
   * Parses the specified tree-bank style parse string and return a Parse structure
   * for that string.
   *
   * @param parse A tree-bank style parse string.
   * @param gl The gap labeler.
   *
   * @return a Parse structure for the specified tree-bank style parse string.
   */
  public static Parse parseParse(String parse, GapLabeler gl) {
    StringBuilder text = new StringBuilder();
    int offset = 0;
    Stack<Constituent> stack = new Stack<>();
    List<Constituent> cons = new LinkedList<>();
    for (int ci = 0, cl = parse.length(); ci < cl; ci++) {
      char c = parse.charAt(ci);
      if (c == '(') {
        String rest = parse.substring(ci + 1);
        String type = getType(rest);
        if (type == null) {
          System.err.println("null type for: " + rest);
        }
        String token = getToken(rest);
        stack.push(new Constituent(type, new Span(offset,offset)));
        if (token != null) {
          if (Objects.equals(type, "-NONE-") && gl != null) {
            //System.err.println("stack.size="+stack.size());
            gl.labelGaps(stack);
          }
          else {
            cons.add(new Constituent(AbstractBottomUpParser.TOK_NODE,
                new Span(offset, offset + token.length())));
            text.append(token).append(" ");
            offset += token.length() + 1;
          }
        }
      }
      else if (c == ')') {
        Constituent con = stack.pop();
        int start = con.getSpan().getStart();
        if (start < offset) {
          cons.add(new Constituent(con.getLabel(), new Span(start, offset - 1)));
        }
      }
    }
    String txt = text.toString();
    int tokenIndex = -1;
    Parse p = new Parse(txt, new Span(0, txt.length()), AbstractBottomUpParser.TOP_NODE, 1,0);
    for (int ci = 0; ci < cons.size(); ci++) {
      Constituent con = cons.get(ci);
      String type = con.getLabel();
      if (!type.equals(AbstractBottomUpParser.TOP_NODE)) {
        if (AbstractBottomUpParser.TOK_NODE.equals(type)) {
          tokenIndex++;
        }
        Parse c = new Parse(txt, con.getSpan(), type, 1,tokenIndex);
        //System.err.println("insert["+ci+"] "+type+" "+c.toString()+" "+c.hashCode());
        p.insert(c);
        //codeTree(p);
      }
    }
    return p;
  }

  /**
   * Returns the parent parse node of this constituent.
   *
   * @return The parent parse node of this constituent.
   */
  public Parse getParent() {
    return parent;
  }

  /**
   * Specifies the parent parse node for this constituent.
   *
   * @param parent The parent parse node for this constituent.
   */
  public void setParent(Parse parent) {
    this.parent = parent;
  }

  /**
   * Indicates whether this parse node is a pos-tag.
   *
   * @return true if this node is a pos-tag, false otherwise.
   */
  public boolean isPosTag() {
    return (parts.size() == 1 &&
        (parts.get(0)).getType().equals(AbstractBottomUpParser.TOK_NODE));
  }

  /**
   * Returns true if this constituent contains no sub-constituents.
   *
   * @return true if this constituent contains no sub-constituents; false otherwise.
   */
  public boolean isFlat() {
    boolean flat = true;
    for (int ci = 0; ci < parts.size(); ci++) {
      flat &= (parts.get(ci)).isPosTag();
    }
    return flat;
  }

  public void isChunk(boolean ic) {
    this.isChunk = ic;
  }

  public boolean isChunk() {
    return isChunk;
  }

  /**
   * Returns the parse nodes which are children of this node and which are pos tags.
   *
   * @return the parse nodes which are children of this node and which are pos tags.
   */
  public Parse[] getTagNodes() {
    List<Parse> tags = new LinkedList<>();
    List<Parse> nodes = new LinkedList<>();
    nodes.addAll(this.parts);
    while (nodes.size() != 0) {
      Parse p = nodes.remove(0);
      if (p.isPosTag()) {
        tags.add(p);
      }
      else {
        nodes.addAll(0,p.parts);
      }
    }
    return tags.toArray(new Parse[tags.size()]);
  }

  /**
   * Returns the deepest shared parent of this node and the specified node.
   * If the nodes are identical then their parent is returned.
   * If one node is the parent of the other then the parent node is returned.
   *
   * @param node The node from which parents are compared to this node's parents.
   *
   * @return the deepest shared parent of this node and the specified node.
   */
  public Parse getCommonParent(Parse node) {
    if (this == node) {
      return parent;
    }
    Set<Parse> parents = new HashSet<>();
    Parse cparent = this;
    while (cparent != null) {
      parents.add(cparent);
      cparent = cparent.getParent();
    }
    while (node != null) {
      if (parents.contains(node)) {
        return node;
      }
      node = node.getParent();
    }
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof Parse) {
      Parse p = (Parse) obj;

      return Objects.equals(label, p.label) && span.equals(p.span)
          && text.equals(p.text) && parts.equals(p.parts);
    }

    return false;
  }

  @Override
  public int hashCode() {
    // Note: label is missing here!
    return Objects.hash(span, text);
  }

  public int compareTo(Parse p) {
    if (this.getProb() > p.getProb()) {
      return -1;
    }
    else if (this.getProb() < p.getProb()) {
      return 1;
    }
    return 0;
  }

  /**
   * Returns the derivation string for this parse if one has been created.
   *
   * @return the derivation string for this parse or null if no derivation string has been created.
   */
  public StringBuffer getDerivation() {
    return derivation;
  }

  /**
   * Specifies the derivation string to be associated with this parse.
   *
   * @param derivation The derivation string to be associated with this parse.
   */
  public void setDerivation(StringBuffer derivation) {
    this.derivation = derivation;
  }

  private void codeTree(Parse p,int[] levels) {
    Parse[] kids = p.getChildren();
    StringBuilder levelsBuff = new StringBuilder();
    levelsBuff.append("[");
    int[] nlevels = new int[levels.length + 1];
    for (int li = 0; li < levels.length; li++) {
      nlevels[li] = levels[li];
      levelsBuff.append(levels[li]).append(".");
    }
    for (int ki = 0; ki < kids.length; ki++) {
      nlevels[levels.length] = ki;
      System.out.println(levelsBuff.toString() + ki + "] " + kids[ki].getType() +
          " " + kids[ki].hashCode() + " -> " + kids[ki].getParent().hashCode() +
          " " + kids[ki].getParent().getType() + " " + kids[ki].getCoveredText());
      codeTree(kids[ki],nlevels);
    }
  }

  /**
   * Prints to standard out a representation of the specified parse which
   * contains hash codes so that parent/child relationships can be explicitly seen.
   */
  public void showCodeTree() {
    codeTree(this,new int[0]);
  }

  /**
   * Utility method to inserts named entities.
   *
   * @param tag
   * @param names
   * @param tokens
   */
  public static void addNames(String tag, Span[] names, Parse[] tokens) {
    for (Span nameTokenSpan : names) {
      Parse startToken = tokens[nameTokenSpan.getStart()];
      Parse endToken = tokens[nameTokenSpan.getEnd() - 1];
      Parse commonParent = startToken.getCommonParent(endToken);
      //System.err.println("addNames: "+startToken+" .. "+endToken+" commonParent = "+commonParent);
      if (commonParent != null) {
        Span nameSpan = new Span(startToken.getSpan().getStart(), endToken.getSpan().getEnd());
        if (nameSpan.equals(commonParent.getSpan())) {
          commonParent.insert(new Parse(commonParent.getText(), nameSpan, tag, 1.0, endToken.getHeadIndex()));
        } else {
          Parse[] kids = commonParent.getChildren();
          boolean crossingKids = false;
          for (Parse kid : kids) {
            if (nameSpan.crosses(kid.getSpan())) {
              crossingKids = true;
            }
          }
          if (!crossingKids) {
            commonParent.insert(new Parse(commonParent.getText(), nameSpan,
                tag, 1.0, endToken.getHeadIndex()));
          } else {
            if (commonParent.getType().equals("NP")) {
              Parse[] grandKids = kids[0].getChildren();
              if (grandKids.length > 1 && nameSpan.contains(grandKids[grandKids.length - 1].getSpan())) {
                commonParent.insert(new Parse(commonParent.getText(), commonParent.getSpan(),
                    tag, 1.0, commonParent.getHeadIndex()));
              }
            }
          }
        }
      }
    }
  }
}
