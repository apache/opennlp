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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.commons.Internal;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Node;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringUtil;

/**
 * Stream filter which merges text lines into sentences, following the Arvores
 * Deitadas syntax.
 * <p>
 * Information about the format:<br>
 * Susana Afonso.
 * <a href="http://www.linguateca.pt/documentos/Afonso2006ArvoresDeitadas.pdf">
 *   "Árvores deitadas: Descrição do formato e das opções de análise na Floresta Sintáctica"</a>.
 * <br>
 * 12 de Fevereiro de 2006.
 * <p>
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class ADSentenceStream extends FilterObjectStream<String, ADSentenceStream.Sentence> {

  public record Sentence (String text, Node root, String metadata) {
    public static final String META_LABEL_FINAL = "final";
  }

  /**
   * Parses a sample of AD corpus. A sentence in AD corpus is represented by a
   * Tree. In this class we declare some types to represent that tree. Today we get only
   * the first alternative (A1).
   */
  public static class SentenceParser {

    private static final Logger logger = LoggerFactory.getLogger(SentenceParser.class);

    private static final char TAG_SEPARATOR = ':';
    private static final char BIZARRE_TAG_SEPARATOR = '=';
    private static final String TAG_GROUP_OPEN = "(<";
    private static final String TAG_GROUP_CLOSE = ">)";

    private String text,meta;

    /**
     * Parses a sentence string into a {@link Sentence}.
     *
     * @param sentenceString The input string to parse.
     * @param isTitle {@code true} if it represents a title element, {@code false} otherwise.
     * @param para The parameter number.
     * @param isBox {@code true} if it represents a box element, {@code false} otherwise.
     *
     * @return A {@link Sentence} instance parsed from {@code sentenceString}.
     */
    public Sentence parse(String sentenceString, int para, boolean isTitle, boolean isBox) {
      Sentence sentence;
      Node root = new Node();
      try (BufferedReader reader = new BufferedReader(new StringReader(sentenceString))) {
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
        sentence = new Sentence(text, root, meta);
        // now we look for the root node
        do {
          line = reader.readLine();
        } while (line != null && line.startsWith("###")); // skip lines starting with ###

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
                logger.warn("should not happen!");
              }
              // 4) Add it to the stack so it is a parent candidate.
              nodeStack.push((Node) element);

            }
          }
          line = reader.readLine();
        }

      } catch (Exception e) {
        logger.warn("Caught exception for the given sentence: '{}'", sentenceString, e);
        return null;
      }
      // second line should be SOURCE
      return sentence;
    }

    private String fixPunctuation(String text) {
      text = replaceGuillemetPunctuation(text, '.', "».");
      text = replaceGuillemetPunctuation(text, ',', "»,");
      return text;
    }

    /**
     * Removes the ASCII whitespace between a closing guillemet and a following punctuation
     * character.
     *
     * @param text The text.
     * @param punct The punctuation character.
     * @param replacement The two characters to write in place of guillemet, whitespace, and
     *                    punctuation.
     * @return The text with those runs joined.
     */
    private String replaceGuillemetPunctuation(String text, char punct, String replacement) {
      StringBuilder fixed = new StringBuilder(text.length());
      int i = 0;
      while (i < text.length()) {
        char c = text.charAt(i);
        if (c == '»' && i + 1 < text.length()) {
          int j = i + 1;
          while (j < text.length() && StringUtil.isAsciiWhitespace(text.charAt(j))) {
            j++;
          }
          if (j > i + 1 && j < text.length() && text.charAt(j) == punct) {
            fixed.append(replacement);
            i = j + 1;
            continue;
          }
        }
        fixed.append(c);
        i++;
      }
      return fixed.toString();
    }

    /**
     * Parses a punctuation line: leading equals signs followed by one or more characters that
     * are not ASCII letters, digits, or underscores. A line of equals signs only also matches,
     * with the last one as lexeme.
     *
     * @param line The line.
     * @return The level, as one more than the count of leading equals signs, and the lexeme,
     *         or {@code null} if the line is not a punctuation line.
     */
    private String[] parsePunctuationLine(String line) {
      if (line.isEmpty()) {
        return null;
      }
      for (int i = 0; i < line.length(); i++) {
        if (isAsciiWord(line.charAt(i))) {
          return null;
        }
      }
      int equals = 0;
      while (equals < line.length() && line.charAt(equals) == '=') {
        equals++;
      }
      if (equals == line.length()) {
        return new String[] {String.valueOf(equals), "="};
      }
      return new String[] {String.valueOf(equals + 1), line.substring(equals)};
    }

    /**
     * Tests for an ASCII letter, digit, or underscore.
     *
     * @param c The character.
     * @return {@code true} for a word character.
     */
    private boolean isAsciiWord(char c) {
      return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9') || c == '_';
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

      Node node = parseNode(line);
      if (node != null) {
        return node;
      }

      Leaf leaf = parseLeaf(line);
      if (leaf != null) {
        return leaf;
      }

      String[] punctuation = parsePunctuationLine(line);
      if (punctuation != null) {
        leaf = new Leaf();
        leaf.setLevel(Integer.parseInt(punctuation[0]));
        leaf.setLexeme(punctuation[1]);
        return leaf;
      }

      // process the bizarre cases
      if (line.equals("_") || line.startsWith("<lixo") || line.startsWith("pause")) {
        return null;
      }

      if (line.startsWith("=")) {
        leaf = parseBizarreLeaf(line);
        if (leaf != null) {
          return leaf;
        }
        int level = line.lastIndexOf("=") + 1;
        String lexeme = line.substring(level + 1);

        if (isWordWithMarkup(lexeme)) {
          return null;
        }

        leaf = new Leaf();
        leaf.setLevel(level + 1);
        leaf.setSyntacticTag("");
        leaf.setMorphologicalTag("");
        leaf.setFunctionalTag("");
        leaf.setLexeme(lexeme);

        return leaf;
      }

      logger.warn("Couldn't parse leaf: {}", line);
      leaf = new Leaf();
      leaf.setLevel(1);
      leaf.setSyntacticTag("");
      leaf.setMorphologicalTag("");
      leaf.setFunctionalTag("");
      leaf.setLexeme(line);

      return leaf;
    }

    /**
     * Parses a node line: the level prefix, a syntactic tag with a colon, an optional part in
     * parentheses, and optional tag groups.
     *
     * @param line The line.
     * @return The node, or {@code null} if the line is not a node line.
     */
    private Node parseNode(String line) {
      for (int[] tag = scanLevelAndTag(line, TAG_SEPARATOR, line.length()); tag != null;
           tag = scanLevelAndTag(line, TAG_SEPARATOR, tag[0] - 1)) {
        if (isNodeTail(line, tag[1])) {
          Node node = new Node();
          node.setLevel(tag[0] + 1);
          node.setSyntacticTag(line.substring(tag[0], tag[1]));
          return node;
        }
      }
      return null;
    }

    /**
     * Parses a leaf line: the level prefix, a syntactic tag, a colon, a functional tag, and in
     * parentheses a quoted lemma, secondary tags in angle brackets, and a morphological tag,
     * then ASCII whitespace and the lexeme.
     *
     * @param line The line.
     * @return The leaf, or {@code null} if the line is not a leaf line.
     */
    private Leaf parseLeaf(String line) {
      boolean[] noRestAt = new boolean[line.length() + 1];
      for (int[] tag = scanLevelAndTag(line, TAG_SEPARATOR, line.length()); tag != null;
           tag = scanLevelAndTag(line, TAG_SEPARATOR, tag[0] - 1)) {
        Leaf leaf = parseLeafAfterTag(line, tag[0], tag[1], noRestAt);
        if (leaf != null) {
          return leaf;
        }
      }
      return null;
    }

    /**
     * Parses the part of a leaf line after the tag, see {@link #parseLeaf(String)}.
     *
     * @param line The line.
     * @param start The index where the tag starts.
     * @param tagEnd The index after the tag.
     * @param noRestAt The indexes after which no rest was found so far, updated here.
     * @return The leaf, or {@code null} if the part after the tag does not have that form.
     */
    private Leaf parseLeafAfterTag(String line, int start, int tagEnd, boolean[] noRestAt) {
      if (tagEnd + 1 >= line.length() || line.charAt(tagEnd) != '('
          || !isQuote(line.charAt(tagEnd + 1))) {
        return null;
      }
      int lemmaStart = tagEnd + 2;
      // the longest lemma after which the rest of the line still parses wins
      for (int lemmaEnd = indexOfLineTerminator(line, lemmaStart) - 1; lemmaEnd > lemmaStart;
           lemmaEnd--) {
        if (!isQuote(line.charAt(lemmaEnd))) {
          continue;
        }
        int[] rest = scanLeafRest(line, lemmaEnd + 1, noRestAt);
        if (rest != null) {
          int separator = line.indexOf(TAG_SEPARATOR, start);
          Leaf leaf = new Leaf();
          leaf.setLevel(start + 1);
          leaf.setSyntacticTag(line.substring(start, separator));
          leaf.setFunctionalTag(line.substring(separator + 1, tagEnd));
          leaf.setLemma(line.substring(lemmaStart, lemmaEnd));
          leaf.setSecondaryTag(line.substring(rest[0], rest[1]));
          leaf.setMorphologicalTag(rest[2] == rest[3] ? null : line.substring(rest[2], rest[3]));
          leaf.setLexeme(line.substring(rest[4]));
          return leaf;
        }
      }
      return null;
    }

    /**
     * Parses a leaf line whose tag has an equals sign in place of the colon: the level prefix,
     * the tag, and in parentheses an optional quoted lemma and an optional morphological tag,
     * then ASCII whitespace and the lexeme.
     *
     * @param line The line.
     * @return The leaf, or {@code null} if the line does not have that form.
     */
    private Leaf parseBizarreLeaf(String line) {
      for (int[] tag = scanLevelAndTag(line, BIZARRE_TAG_SEPARATOR, line.length()); tag != null;
           tag = scanLevelAndTag(line, BIZARRE_TAG_SEPARATOR, tag[0] - 1)) {
        Leaf leaf = parseBizarreLeafAfterTag(line, tag[0], tag[1]);
        if (leaf != null) {
          return leaf;
        }
      }
      return null;
    }

    /**
     * Parses the part of a leaf line after a tag with an equals sign, see
     * {@link #parseBizarreLeaf(String)}.
     *
     * @param line The line.
     * @param start The index where the tag starts.
     * @param tagEnd The index after the tag.
     * @return The leaf, or {@code null} if the part after the tag does not have that form.
     */
    private Leaf parseBizarreLeafAfterTag(String line, int start, int tagEnd) {
      if (tagEnd == line.length() || line.charAt(tagEnd) != '(') {
        return null;
      }
      int open = tagEnd + 1;
      String lemma = null;
      int[] rest = null;
      if (open < line.length() && isQuote(line.charAt(open))) {
        for (int lemmaEnd = indexOfLineTerminator(line, open + 1) - 1;
             lemmaEnd > open + 1 && rest == null; lemmaEnd--) {
          if (isQuote(line.charAt(lemmaEnd))) {
            rest = scanMorphologyAndLexeme(line, lemmaEnd + 1);
            if (rest != null) {
              lemma = line.substring(open + 1, lemmaEnd);
            }
          }
        }
      }
      if (rest == null) {
        rest = scanMorphologyAndLexeme(line, open);
        if (rest == null) {
          return null;
        }
      }
      Leaf leaf = new Leaf();
      leaf.setLevel(start + 1);
      leaf.setSyntacticTag(line.substring(start, tagEnd));
      leaf.setMorphologicalTag(rest[0] == rest[1] ? null : line.substring(rest[0], rest[1]));
      leaf.setLexeme(line.substring(rest[2]));
      leaf.setLemma(lemma);
      return leaf;
    }

    /**
     * Scans the level prefix and the tag at the start of a line. The prefix is the run of equals
     * signs and hyphens, the tag one or more characters other than a colon or an equals sign,
     * the separator, and one or more characters that are neither an opening parenthesis nor
     * ASCII whitespace. A longer prefix is preferred; hyphens at its end may move into the tag,
     * so the callers try the next shorter prefix when the rest of the line does not parse.
     *
     * @param line The line.
     * @param separator The character between the two parts of the tag.
     * @param maxStart The highest index where the tag may start: the length of the line for the
     *                 first candidate, one less than the previous start for the next one.
     * @return The index where the tag starts, which is the length of the prefix, and the index
     *         after the tag, or {@code null} if there is no further candidate.
     */
    private int[] scanLevelAndTag(String line, char separator, int maxStart) {
      int run = 0;
      while (run < line.length() && (line.charAt(run) == '=' || line.charAt(run) == '-')) {
        run++;
      }
      for (int start = Math.min(run, maxStart); start >= 0; start--) {
        if (start == run || line.charAt(start) == '-') {
          int end = scanTag(line, start, separator);
          if (end != -1) {
            return new int[] {start, end};
          }
        }
      }
      return null;
    }

    /**
     * Scans a tag: one or more characters other than a colon or an equals sign, the separator,
     * and one or more characters that are neither an opening parenthesis nor ASCII whitespace.
     *
     * @param line The line.
     * @param from The index where the tag starts.
     * @param separator The character between the two parts of the tag.
     * @return The index after the tag, or -1 if there is no tag at {@code from}.
     */
    private int scanTag(String line, int from, char separator) {
      int i = from;
      while (i < line.length() && line.charAt(i) != TAG_SEPARATOR
          && line.charAt(i) != BIZARRE_TAG_SEPARATOR) {
        i++;
      }
      if (i == from || i == line.length() || line.charAt(i) != separator) {
        return -1;
      }
      int end = i + 1;
      while (end < line.length() && line.charAt(end) != '('
          && !StringUtil.isAsciiWhitespace(line.charAt(end))) {
        end++;
      }
      return end == i + 1 ? -1 : end;
    }

    /**
     * Tests the rest of a node line after the tag: an optional part in parentheses, then the tag
     * groups.
     *
     * @param line The line.
     * @param from The index after the tag.
     * @return {@code true} if the rest of the line has that form.
     */
    private boolean isNodeTail(String line, int from) {
      if (from < line.length() && line.charAt(from) == '(') {
        int close = line.indexOf(')', from + 1);
        if (close > from + 1 && isTagGroupRun(line, close + 1)) {
          return true;
        }
      }
      return isTagGroupRun(line, from);
    }

    /**
     * Tests for tag groups up to the end of the line: after optional ASCII whitespace either
     * nothing, or an opening parenthesis and angle bracket, one or more characters other than a
     * line terminator, a closing angle bracket and parenthesis, and optional ASCII whitespace.
     *
     * @param line The line.
     * @param from The index where the tag groups start.
     * @return {@code true} if the rest of the line has that form.
     */
    private boolean isTagGroupRun(String line, int from) {
      int start = skipAsciiWhitespace(line, from);
      int end = line.length();
      while (end > start && StringUtil.isAsciiWhitespace(line.charAt(end - 1))) {
        end--;
      }
      if (start == end) {
        return true;
      }
      int contentStart = start + TAG_GROUP_OPEN.length();
      int contentEnd = end - TAG_GROUP_CLOSE.length();
      return contentEnd > contentStart && line.startsWith(TAG_GROUP_OPEN, start)
          && line.startsWith(TAG_GROUP_CLOSE, contentEnd)
          && indexOfLineTerminator(line, contentStart) >= contentEnd;
    }

    /**
     * Scans the rest of a leaf line after the lemma: optional ASCII whitespace, secondary tags,
     * optional ASCII whitespace, an optional morphological tag, the closing parenthesis, ASCII
     * whitespace, and the lexeme.
     *
     * @param line The line.
     * @param from The index after the lemma.
     * @param noRestAt The indexes after which no rest was found so far, updated here.
     * @return The start and end of the secondary tags, the start of the morphological tag, the
     *         index of the closing parenthesis, and the start of the lexeme, or {@code null} if
     *         the rest of the line does not have that form.
     */
    private int[] scanLeafRest(String line, int from, boolean[] noRestAt) {
      int tagsStart = skipAsciiWhitespace(line, from);
      int[] rest = scanSecondaryTags(line, tagsStart, noRestAt);
      return rest == null ? null : new int[] {tagsStart, rest[0], rest[1], rest[2], rest[3]};
    }

    /**
     * Scans secondary tags and the rest of a leaf line after them. Each tag is an opening angle
     * bracket, one or more characters other than a line terminator, and a closing angle
     * bracket; a longer tag, and then one more tag, is preferred when the rest of the line still
     * parses after it.
     *
     * @param line The line.
     * @param from The index where the next secondary tag would start.
     * @param noRestAt The indexes after which no rest was found so far, updated here.
     * @return The end of the secondary tags, the start of the morphological tag, the index of the
     *         closing parenthesis, and the start of the lexeme, or {@code null} if the rest of the
     *         line does not have that form.
     */
    private int[] scanSecondaryTags(String line, int from, boolean[] noRestAt) {
      if (noRestAt[from]) {
        return null;
      }
      if (from < line.length() && line.charAt(from) == '<') {
        for (int close = indexOfLineTerminator(line, from + 1) - 1; close > from + 1; close--) {
          if (line.charAt(close) == '>') {
            int[] rest = scanSecondaryTags(line, close + 1, noRestAt);
            if (rest != null) {
              return rest;
            }
          }
        }
      }
      int[] rest = scanMorphologyAndLexeme(line, from);
      if (rest == null) {
        noRestAt[from] = true;
        return null;
      }
      return new int[] {from, rest[0], rest[1], rest[2]};
    }

    /**
     * Scans the end of a leaf line: optional ASCII whitespace, an optional morphological tag up
     * to the first closing parenthesis, that parenthesis, ASCII whitespace, and the lexeme.
     *
     * @param line The line.
     * @param from The index after the secondary tags.
     * @return The start of the morphological tag, the index of the closing parenthesis, and the
     *         start of the lexeme, or {@code null} if the end of the line does not have that form.
     */
    private int[] scanMorphologyAndLexeme(String line, int from) {
      int morphologyStart = skipAsciiWhitespace(line, from);
      int close = line.indexOf(')', morphologyStart);
      if (close == -1) {
        return null;
      }
      int lexemeStart = scanLexemeStart(line, close);
      return lexemeStart == -1 ? null : new int[] {morphologyStart, close, lexemeStart};
    }

    /**
     * Finds the lexeme after the closing parenthesis: ASCII whitespace, then one or more
     * characters other than a line terminator up to the end of the line. When only whitespace
     * follows the parenthesis, the last character is the lexeme.
     *
     * @param line The line.
     * @param close The index of the closing parenthesis.
     * @return The start of the lexeme, or -1 if there is none.
     */
    private int scanLexemeStart(String line, int close) {
      int lexemeStart = skipAsciiWhitespace(line, close + 1);
      if (lexemeStart == close + 1) {
        return -1;
      }
      if (lexemeStart == line.length()) {
        lexemeStart--;
        return lexemeStart > close + 1 && !isLineTerminator(line.charAt(lexemeStart))
            ? lexemeStart : -1;
      }
      return indexOfLineTerminator(line, lexemeStart) == line.length() ? lexemeStart : -1;
    }

    /**
     * Tests whether a lexeme starts with an ASCII letter, digit, or underscore and has a period
     * or an angle bracket after it, with no line terminator anywhere.
     *
     * @param lexeme The lexeme.
     * @return {@code true} for such a lexeme.
     */
    private boolean isWordWithMarkup(String lexeme) {
      if (lexeme.isEmpty() || !isAsciiWord(lexeme.charAt(0))
          || indexOfLineTerminator(lexeme, 0) < lexeme.length()) {
        return false;
      }
      for (int i = 1; i < lexeme.length(); i++) {
        char c = lexeme.charAt(i);
        if (c == '.' || c == '<' || c == '>') {
          return true;
        }
      }
      return false;
    }

    /**
     * Skips ASCII whitespace.
     *
     * @param line The line.
     * @param from The index to start at.
     * @return The index of the first character at or after {@code from} that is not ASCII
     *         whitespace, or the length of the line.
     */
    private int skipAsciiWhitespace(String line, int from) {
      int i = from;
      while (i < line.length() && StringUtil.isAsciiWhitespace(line.charAt(i))) {
        i++;
      }
      return i;
    }

    /**
     * Tests for a double or single quote.
     *
     * @param c The character.
     * @return {@code true} for one of the two.
     */
    private boolean isQuote(char c) {
      return c == '"' || c == '\'';
    }

    /**
     * Finds the first line terminator at or after an index.
     *
     * @param text The text.
     * @param from The index to start at.
     * @return The index of the terminator, or the length of the text if there is none.
     */
    static int indexOfLineTerminator(CharSequence text, int from) {
      for (int i = from; i < text.length(); i++) {
        if (isLineTerminator(text.charAt(i))) {
          return i;
        }
      }
      return text.length();
    }

    /**
     * Tests for a line terminator: line feed, carriage return, next line, line separator, or
     * paragraph separator.
     *
     * @param c The character.
     * @return {@code true} for one of those five characters.
     */
    private static boolean isLineTerminator(char c) {
      return c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029';
    }

    /** Represents a tree element, Node or Leaf */
    public abstract static class TreeElement {

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
    public static class Node extends TreeElement {
      private final List<TreeElement> elems = new ArrayList<>();

      public void addElement(TreeElement element) {
        elems.add(element);
      }

      public TreeElement[] getElements() {
        return elems.toArray(new TreeElement[0]);
      }

      @Override
      public String toString() {
        StringBuilder sb = new StringBuilder();
        // print itself and its children
        sb.append("=".repeat(Math.max(0, this.getLevel())));
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
    public static class Leaf extends TreeElement {

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
        sb.append("=".repeat(Math.max(0, this.getLevel())));
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

  private static final String SENTENCE_TAG = "s";
  private static final String TEXT_TAG = "ext";
  private static final String TITLE_TAG = "t";
  private static final String BOX_TAG = "caixa";
  private static final String PARAGRAPH_TAG = "p";

  private final SentenceParser parser;

  private int paraID = 0;
  private boolean isTitle = false;
  private boolean isBox = false;

  public ADSentenceStream(ObjectStream<String> lineStream) {
    super(lineStream);
    parser = new SentenceParser();
  }


  @Override
  public Sentence read() throws IOException {

    final StringBuilder sentence = new StringBuilder();
    boolean sentenceStarted = false;

    while (true) {
      String line = samples.read();

      if (line != null) {

        if (sentenceStarted) {
          if (isClosingTag(line, SENTENCE_TAG) || isClosingTag(line, TEXT_TAG)) {
            sentenceStarted = false;
          } else if (!line.startsWith("A1")) {
            sentence.append(line).append('\n');
          }
        } else {
          if (isOpeningTag(line, SENTENCE_TAG)) {
            sentenceStarted = true;
          } else if (isOpeningTag(line, PARAGRAPH_TAG)) {
            paraID++;
          } else if (isOpeningTag(line, TITLE_TAG)) {
            isTitle = true;
          } else if (isClosingTag(line, TITLE_TAG)) {
            isTitle = false;
          } else if (isOpeningTag(line, TEXT_TAG)) {
            paraID = 0;
          } else if (isOpeningTag(line, BOX_TAG)) {
            isBox = true;
          } else if (isClosingTag(line, BOX_TAG)) {
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

  /**
   * Tests whether a line is an opening markup tag with the given name: the name right after the
   * opening angle bracket, then any characters other than a closing angle bracket, then the
   * closing angle bracket as the last character.
   *
   * @param line The line.
   * @param name The tag name.
   * @return {@code true} if the whole line is such a tag.
   */
  static boolean isOpeningTag(String line, String name) {
    int last = line.length() - 1;
    if (last <= name.length() || line.charAt(0) != '<' || !line.startsWith(name, 1)
        || line.charAt(last) != '>') {
      return false;
    }
    return line.indexOf('>', name.length() + 1) == last;
  }

  /**
   * Tests whether a line is the closing markup tag with the given name and nothing else.
   *
   * @param line The line.
   * @param name The tag name.
   * @return {@code true} if the whole line is that closing tag.
   */
  static boolean isClosingTag(String line, String name) {
    return line.length() == name.length() + 3 && line.startsWith("</") && line.startsWith(name, 2)
        && line.charAt(line.length() - 1) == '>';
  }
}
