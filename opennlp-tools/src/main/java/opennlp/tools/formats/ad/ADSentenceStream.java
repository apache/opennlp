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

package opennlp.tools.formats.ad;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Node;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Stream filter which merges text lines into sentences, following the Arvores
 * Deitadas syntax.
 * <p>
 * Information about the format:<br>
 * Susana Afonso.
 * "Árvores deitadas: Descrição do formato e das opções de análise na Floresta Sintáctica"
 * .<br>
 * 12 de Fevereiro de 2006.
 * http://www.linguateca.pt/documentos/Afonso2006ArvoresDeitadas.pdf
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ADSentenceStream extends FilterObjectStream<String, ADSentenceStream.Sentence> {

  public static class Sentence {

    private String text;
    private Node root;
    private String metadata;

    public static final String META_LABEL_FINAL = "final";

    public String getText() {
      return text;
    }

    public void setText(String text) {
      this.text = text;
    }

    public Node getRoot() {
      return root;
    }

    public void setRoot(Node root) {
      this.root = root;
    }

    public void setMetadata(String metadata) {
      this.metadata = metadata;
    }

    public String getMetadata() {
      return metadata;
    }

  }

  /**
   * Parses a sample of AD corpus. A sentence in AD corpus is represented by a
   * Tree. In this class we declare some types to represent that tree. Today we get only
   * the first alternative (A1).
   */
  public static class SentenceParser {

    private Pattern nodePattern = Pattern
        .compile("([=-]*)([^:=]+:[^\\(\\s]+)(\\(([^\\)]+)\\))?\\s*(?:(\\((<.+>)\\))*)\\s*$");
    private Pattern leafPattern = Pattern
        .compile("^([=-]*)([^:=]+):([^\\(\\s]+)\\([\"'](.+)[\"']\\s*((?:<.+>)*)\\s*([^\\)]+)?\\)\\s+(.+)");
    private Pattern bizarreLeafPattern = Pattern
        .compile("^([=-]*)([^:=]+=[^\\(\\s]+)\\(([\"'].+[\"'])?\\s*([^\\)]+)?\\)\\s+(.+)");
    private Pattern punctuationPattern = Pattern.compile("^(=*)(\\W+)$");

    private String text,meta;

    /**
     * Parse the sentence
     */
    public Sentence parse(String sentenceString, int para, boolean isTitle, boolean isBox) {
      BufferedReader reader = new BufferedReader(new StringReader(sentenceString));
      Sentence sentence = new Sentence();
      Node root = new Node();
      try {
        // first line is <s ...>
        String line = reader.readLine();

        boolean useSameTextAndMeta = false; // to handle cases where there are diff sug of parse (&&)

        // should find the source source
        while (!line.startsWith("SOURCE")) {
          if (line.equals("&&")) {
            // same sentence again!
            useSameTextAndMeta = true;
            break;
          }
          line = reader.readLine();
          if (line == null) {
            return null;
          }
        }
        if (!useSameTextAndMeta) {
          // got source, get the metadata
          String metaFromSource = line.substring(7);
          line = reader.readLine();
          // we should have the plain sentence
          // we remove the first token
          int start = line.indexOf(" ");
          text = line.substring(start + 1).trim();
          text = fixPunctuation(text);
          String titleTag = "";
          if (isTitle) titleTag = " title";
          String boxTag = "";
          if (isBox) boxTag = " box";
          if (start > 0) {
            meta = line.substring(0, start) + " p=" + para + titleTag + boxTag + metaFromSource;
          }
        }
        sentence.setText(text);
        sentence.setMetadata(meta);
        // now we look for the root node

        // skip lines starting with ###
        line = reader.readLine();
        while (line != null && line.startsWith("###")) {
          line = reader.readLine();
        }

        // got the root. Add it to the stack
        Stack<Node> nodeStack = new Stack<>();

        root.setSyntacticTag("ROOT");
        root.setLevel(0);
        nodeStack.add(root);


        /* now we have to take care of the lastLevel. Every time it raises, we will add the
        leaf to the node at the top. If it decreases, we remove the top. */

        while (line != null && line.length() != 0 && !line.startsWith("</s>") && !line.equals("&&")) {
          TreeElement element = this.getElement(line);

          if (element != null) {
            // The idea here is to keep a stack of nodes that are candidates for
            // parenting the following elements (nodes and leafs).

            // 1) When we get a new element, we check its level and remove from
            // the top of the stack nodes that are brothers or nephews.
            while (!nodeStack.isEmpty() && element.getLevel() > 0
                && element.getLevel() <= nodeStack.peek().getLevel()) {
              Node nephew = nodeStack.pop();
            }

            if (element.isLeaf() ) {
              // 2a) If the element is a leaf and there is no parent candidate,
              // add it as a daughter of the root.
              if (nodeStack.isEmpty()) {
                root.addElement(element);
              } else {
                // 2b) There are parent candidates.
                // look for the node with the correct level
                Node peek = nodeStack.peek();
                if (element.level == 0) { // add to the root
                  nodeStack.firstElement().addElement(element);
                } else {
                  Node parent = null;
                  int index = nodeStack.size() - 1;
                  while (parent == null) {
                    if (peek.getLevel() < element.getLevel()) {
                      parent = peek;
                    } else {
                      index--;
                      if (index > -1) {
                        peek = nodeStack.get(index);
                      } else {
                        parent = nodeStack.firstElement();
                      }
                    }
                  }
                  parent.addElement(element);
                }
              }
            } else {
              // 3) Check if the element that is at the top of the stack is this
              // node parent, if yes add it as a son
              if (!nodeStack.isEmpty() && nodeStack.peek().getLevel() < element.getLevel()) {
                nodeStack.peek().addElement(element);
              } else {
                System.err.println("should not happen!");
              }
              // 4) Add it to the stack so it is a parent candidate.
              nodeStack.push((Node) element);

            }
          }
          line = reader.readLine();
        }

      } catch (Exception e) {
        System.err.println(sentenceString);
        e.printStackTrace();
        return sentence;
      }
      // second line should be SOURCE
      sentence.setRoot(root);
      return sentence;
    }

    private String fixPunctuation(String text) {
      text = text.replaceAll("\\»\\s+\\.", "».");
      text = text.replaceAll("\\»\\s+\\,", "»,");
      return text;
    }

    /**
     * Parse a tree element from a AD line
     *
     * @param line
     *          the AD line
     * @return the tree element
     */
    public TreeElement getElement(String line) {
      // Note: all levels are higher than 1, because 0 is reserved for the root.

      // try node
      Matcher nodeMatcher = nodePattern.matcher(line);
      if (nodeMatcher.matches()) {
        int level = nodeMatcher.group(1).length() + 1;
        String syntacticTag = nodeMatcher.group(2);
        Node node = new Node();
        node.setLevel(level);
        node.setSyntacticTag(syntacticTag);
        return node;
      }

      Matcher leafMatcher = leafPattern.matcher(line);
      if (leafMatcher.matches()) {
        int level = leafMatcher.group(1).length() + 1;
        String syntacticTag = leafMatcher.group(2);
        String funcTag = leafMatcher.group(3);
        String lemma = leafMatcher.group(4);
        String secondaryTag = leafMatcher.group(5);
        String morphologicalTag = leafMatcher.group(6);
        String lexeme = leafMatcher.group(7);
        Leaf leaf = new Leaf();
        leaf.setLevel(level);
        leaf.setSyntacticTag(syntacticTag);
        leaf.setFunctionalTag(funcTag);
        leaf.setSecondaryTag(secondaryTag);
        leaf.setMorphologicalTag(morphologicalTag);
        leaf.setLexeme(lexeme);
        leaf.setLemma(lemma);

        return leaf;
      }

      Matcher punctuationMatcher = punctuationPattern.matcher(line);
      if (punctuationMatcher.matches()) {
        int level = punctuationMatcher.group(1).length() + 1;
        String lexeme = punctuationMatcher.group(2);
        Leaf leaf = new Leaf();
        leaf.setLevel(level);
        leaf.setLexeme(lexeme);
        return leaf;
      }

      // process the bizarre cases
      if (line.equals("_") || line.startsWith("<lixo") || line.startsWith("pause")) {
        return null;
      }

      if (line.startsWith("=")) {
        Matcher bizarreLeafMatcher = bizarreLeafPattern.matcher(line);
        if (bizarreLeafMatcher.matches()) {
          int level = bizarreLeafMatcher.group(1).length() + 1;
          String syntacticTag = bizarreLeafMatcher.group(2);
          String lemma = bizarreLeafMatcher.group(3);
          String morphologicalTag = bizarreLeafMatcher.group(4);
          String lexeme = bizarreLeafMatcher.group(5);
          Leaf leaf = new Leaf();
          leaf.setLevel(level);
          leaf.setSyntacticTag(syntacticTag);
          leaf.setMorphologicalTag(morphologicalTag);
          leaf.setLexeme(lexeme);
          if (lemma != null) {
            if (lemma.length() > 2) {
              lemma = lemma.substring(1, lemma.length() - 1);
            }
            leaf.setLemma(lemma);
          }

          return leaf;
        } else {
          int level = line.lastIndexOf("=") + 1;
          String lexeme = line.substring(level + 1);

          if (lexeme.matches("\\w.*?[\\.<>].*")) {
            return null;
          }

          Leaf leaf = new Leaf();
          leaf.setLevel(level + 1);
          leaf.setSyntacticTag("");
          leaf.setMorphologicalTag("");
          leaf.setLexeme(lexeme);

          return leaf;
        }
      }

      System.err.println("Couldn't parse leaf: " + line);
      Leaf leaf = new Leaf();
      leaf.setLevel(1);
      leaf.setSyntacticTag("");
      leaf.setMorphologicalTag("");
      leaf.setLexeme(line);

      return leaf;
    }

    /** Represents a tree element, Node or Leaf */
    public abstract class TreeElement {

      private String syntacticTag;
      private String morphologicalTag;
      private int level;

      public boolean isLeaf() {
        return false;
      }

      public void setSyntacticTag(String syntacticTag) {
        this.syntacticTag = syntacticTag;
      }

      public String getSyntacticTag() {
        return syntacticTag;
      }

      public void setLevel(int level) {
        this.level = level;
      }

      public int getLevel() {
        return level;
      }

      public void setMorphologicalTag(String morphologicalTag) {
        this.morphologicalTag = morphologicalTag;
      }

      public String getMorphologicalTag() {
        return morphologicalTag;
      }
    }

    /** Represents the AD node */
    public class Node extends TreeElement {
      private List<TreeElement> elems = new ArrayList<>();

      public void addElement(TreeElement element) {
        elems.add(element);
      }

      public TreeElement[] getElements() {
        return elems.toArray(new TreeElement[elems.size()]);
      }

      @Override
      public String toString() {
        StringBuilder sb = new StringBuilder();
        // print itself and its children
        for (int i = 0; i < this.getLevel(); i++) {
          sb.append("=");
        }
        sb.append(this.getSyntacticTag());
        if (this.getMorphologicalTag() != null) {
          sb.append(this.getMorphologicalTag());
        }
        sb.append("\n");
        for (TreeElement element : elems) {
          sb.append(element.toString());
        }
        return sb.toString();
      }
    }

    /** Represents the AD leaf */
    public class Leaf extends TreeElement {

      private String word;
      private String lemma;
      private String secondaryTag;
      private String functionalTag;

      @Override
      public boolean isLeaf() {
        return true;
      }

      public void setFunctionalTag(String funcTag) {
        this.functionalTag = funcTag;
      }

      public String getFunctionalTag() {
        return this.functionalTag;
      }

      public void setSecondaryTag(String secondaryTag) {
        this.secondaryTag = secondaryTag;
      }

      public String getSecondaryTag() {
        return this.secondaryTag;
      }

      public void setLexeme(String lexeme) {
        this.word = lexeme;
      }

      public String getLexeme() {
        return word;
      }

      private String emptyOrString(String value, String prefix, String suffix) {
        if (value == null) return "";
        return prefix + value + suffix;
      }

      @Override
      public String toString() {
        StringBuilder sb = new StringBuilder();
        // print itself and its children
        for (int i = 0; i < this.getLevel(); i++) {
          sb.append("=");
        }
        if (this.getSyntacticTag() != null) {
          sb.append(this.getSyntacticTag()).append(":")
              .append(getFunctionalTag()).append("(")
              .append(emptyOrString(getLemma(), "'", "' "))
              .append(emptyOrString(getSecondaryTag(), "", " "))
              .append(this.getMorphologicalTag()).append(") ");
        }
        sb.append(this.word).append("\n");
        return sb.toString();
      }

      public void setLemma(String lemma) {
        this.lemma = lemma;
      }

      public String getLemma() {
        return lemma;
      }
    }

  }

  /**
   * The start sentence pattern
   */
  private static final Pattern sentStart = Pattern.compile("<s[^>]*>");

  /**
   * The end sentence pattern
   */
  private static final Pattern sentEnd = Pattern.compile("</s>");
  private static final Pattern extEnd = Pattern.compile("</ext>");

  /**
   * The start sentence pattern
   */
  private static final Pattern titleStart = Pattern.compile("<t[^>]*>");

  /**
   * The end sentence pattern
   */
  private static final Pattern titleEnd = Pattern.compile("</t>");

  /**
   * The start sentence pattern
   */
  private static final Pattern boxStart = Pattern.compile("<caixa[^>]*>");

  /**
   * The end sentence pattern
   */
  private static final Pattern boxEnd = Pattern.compile("</caixa>");


  /**
   * The start sentence pattern
   */
  private static final Pattern paraStart = Pattern.compile("<p[^>]*>");

  /**
   * The start sentence pattern
   */
  private static final Pattern textStart = Pattern.compile("<ext[^>]*>");

  private SentenceParser parser;

  private int paraID = 0;
  private boolean isTitle = false;
  private boolean isBox = false;

  public ADSentenceStream(ObjectStream<String> lineStream) {
    super(lineStream);
    parser = new SentenceParser();
  }


  public Sentence read() throws IOException {

    StringBuilder sentence = new StringBuilder();
    boolean sentenceStarted = false;

    while (true) {
      String line = samples.read();

      if (line != null) {

        if (sentenceStarted) {
          if (sentEnd.matcher(line).matches() || extEnd.matcher(line).matches()) {
            sentenceStarted = false;
          } else if (!line.startsWith("A1")) {
            sentence.append(line).append('\n');
          }
        } else {
          if (sentStart.matcher(line).matches()) {
            sentenceStarted = true;
          } else if (paraStart.matcher(line).matches()) {
            paraID++;
          } else if (titleStart.matcher(line).matches()) {
            isTitle = true;
          } else if (titleEnd.matcher(line).matches()) {
            isTitle = false;
          } else if (textStart.matcher(line).matches()) {
            paraID = 0;
          } else if (boxStart.matcher(line).matches()) {
            isBox = true;
          } else if (boxEnd.matcher(line).matches()) {
            isBox = false;
          }
        }


        if (!sentenceStarted && sentence.length() > 0) {
          return parser.parse(sentence.toString(), paraID, isTitle, isBox);
        }

      } else {
        // handle end of file
        if (sentenceStarted) {
          if (sentence.length() > 0) {
            return parser.parse(sentence.toString(), paraID, isTitle, isBox);
          }
        } else {
          return null;
        }
      }
    }
  }
}
