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
 * Whitespace in the markup and the tree lines is the Unicode White_Space property, see
 * {@link StringUtil#isUnicodeWhitespace(char)}, independent of the whitespace mode.
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

    private static final char LEVEL_MARK = '=';
    private static final char LEVEL_HYPHEN = '-';
    private static final char TAG_SEPARATOR = ':';
    private static final char BIZARRE_TAG_SEPARATOR = '=';
    private static final char GROUP_OPEN = '(';
    private static final char GROUP_CLOSE = ')';
    private static final char BRACKET_OPEN = '<';
    private static final char BRACKET_CLOSE = '>';
    private static final char DOUBLE_QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';
    private static final char CLOSING_GUILLEMET = '\u00BB';
    private static final char PERIOD = '.';
    private static final char COMMA = ',';
    private static final char UNDERSCORE = '_';
    private static final String TAG_GROUP_OPEN = "(<";
    private static final String TAG_GROUP_CLOSE = ">)";

    /**
     * A tag at the start of a line.
     *
     * @param start The index where the tag starts, which is the length of the level prefix.
     * @param separator The index of the separator between the two parts of the tag.
     * @param end The index after the tag.
     */
    private record Tag(int start, int separator, int end) {
    }

    /**
     * The end of a leaf line.
     *
     * @param morphologyStart The index where the morphological tag starts.
     * @param close The index of the closing parenthesis, which ends the morphological tag.
     * @param lexemeStart The index where the lexeme starts.
     */
    private record LeafEnd(int morphologyStart, int close, int lexemeStart) {
    }

    /**
     * The part of a leaf line after the lemma.
     *
     * @param tagsEnd The index after the secondary tags.
     * @param end The end of the line.
     */
    private record LeafRest(int tagsEnd, LeafEnd end) {
    }

    /**
     * A punctuation line.
     *
     * @param level The level, one more than the count of leading equals signs.
     * @param lexeme The lexeme.
     */
    record PunctuationLine(int level, String lexeme) {
    }

    /**
     * What the scans of one line remember: for each index, whether no end of a leaf line was
     * found there, and the index of the next closing parenthesis at or after it.
     */
    private static final class LineScan {

      private final boolean[] noEndAt;
      private final int[] nextClose;

      LineScan(String line) {
        noEndAt = new boolean[line.length() + 1];
        nextClose = new int[line.length() + 1];
        int close = -1;
        nextClose[line.length()] = close;
        for (int i = line.length() - 1; i >= 0; i--) {
          if (line.charAt(i) == GROUP_CLOSE) {
            close = i;
          }
          nextClose[i] = close;
        }
      }
    }

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

    /**
     * Removes the whitespace between a closing guillemet and a period or a comma that follows
     * it.
     *
     * @param text The text.
     * @return The text with those runs joined, or the text itself if it has no closing
     *         guillemet.
     */
    private String fixPunctuation(String text) {
      if (text.indexOf(CLOSING_GUILLEMET) == -1) {
        return text;
      }
      StringBuilder fixed = new StringBuilder(text.length());
      int i = 0;
      while (i < text.length()) {
        char c = text.charAt(i);
        fixed.append(c);
        i++;
        if (c == CLOSING_GUILLEMET) {
          int next = skipWhitespace(text, i);
          if (next > i && next < text.length()
              && (text.charAt(next) == PERIOD || text.charAt(next) == COMMA)) {
            i = next;
          }
        }
      }
      return fixed.toString();
    }

    /**
     * Parses a punctuation line: leading equals signs followed by one or more characters, none
     * of which is a letter, a decimal digit, or an underscore. Letters and digits are judged by
     * code point, so an accented or non-Latin word is not punctuation. A line of equals signs
     * only also matches, with the last one as lexeme.
     *
     * @param line The line.
     * @return The level and the lexeme, or {@code null} if the line is not a punctuation line.
     */
    PunctuationLine parsePunctuationLine(String line) {
      if (line.isEmpty()) {
        return null;
      }
      int i = 0;
      while (i < line.length()) {
        int cp = line.codePointAt(i);
        if (Character.isLetterOrDigit(cp) || cp == UNDERSCORE) {
          return null;
        }
        i += Character.charCount(cp);
      }
      int equals = 0;
      while (equals < line.length() && line.charAt(equals) == LEVEL_MARK) {
        equals++;
      }
      if (equals == line.length()) {
        return new PunctuationLine(equals, String.valueOf(LEVEL_MARK));
      }
      return new PunctuationLine(equals + 1, line.substring(equals));
    }

    /**
     * Reads one tree line into a {@link Node} or a {@link Leaf}. A leaf line with an equals
     * sign in its tag part, as in {@code =H==CJT:num("818-5817" ...)}, takes the functional
     * tag after the last colon and the syntactic tag before it; without a colon it has no
     * functional tag.
     *
     * @param line The tree line.
     * @return The element, or {@code null} for a line that is no element.
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

      PunctuationLine punctuation = parsePunctuationLine(line);
      if (punctuation != null) {
        leaf = new Leaf();
        leaf.setLevel(punctuation.level());
        leaf.setLexeme(punctuation.lexeme());
        return leaf;
      }

      // process the bizarre cases
      if (line.equals("_") || line.startsWith("<lixo") || line.startsWith("pause")) {
        return null;
      }

      if (!line.isEmpty() && line.charAt(0) == LEVEL_MARK) {
        leaf = parseBizarreLeaf(line);
        if (leaf != null) {
          return leaf;
        }
        int level = line.lastIndexOf(LEVEL_MARK) + 1;
        if (level == line.length()) {
          return null;
        }
        String lexeme = line.substring(level);

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
      int prefix = levelPrefixEnd(line);
      for (Tag tag = scanLevelAndTag(line, prefix, TAG_SEPARATOR, prefix); tag != null;
           tag = scanLevelAndTag(line, prefix, TAG_SEPARATOR, tag.start() - 1)) {
        if (isNodeTail(line, tag.end())) {
          Node node = new Node();
          node.setLevel(tag.start() + 1);
          node.setSyntacticTag(line.substring(tag.start(), tag.end()));
          return node;
        }
      }
      return null;
    }

    /**
     * Parses a leaf line: the level prefix, a syntactic tag, a colon, a functional tag, and in
     * parentheses a quoted lemma, secondary tags in angle brackets, and a morphological tag,
     * then whitespace and the lexeme.
     *
     * @param line The line.
     * @return The leaf, or {@code null} if the line is not a leaf line.
     */
    private Leaf parseLeaf(String line) {
      int prefix = levelPrefixEnd(line);
      LineScan scan = null;
      for (Tag tag = scanLevelAndTag(line, prefix, TAG_SEPARATOR, prefix); tag != null;
           tag = scanLevelAndTag(line, prefix, TAG_SEPARATOR, tag.start() - 1)) {
        if (isLemmaStart(line, tag.end())) {
          if (scan == null) {
            scan = new LineScan(line);
          }
          Leaf leaf = parseLeafAfterTag(line, tag, scan);
          if (leaf != null) {
            return leaf;
          }
        }
      }
      return null;
    }

    /**
     * Tests for the start of a quoted lemma: an opening parenthesis and a quote.
     *
     * @param line The line.
     * @param from The index after the tag.
     * @return {@code true} if a quoted lemma starts there.
     */
    private boolean isLemmaStart(String line, int from) {
      return from + 1 < line.length() && line.charAt(from) == GROUP_OPEN
          && isQuote(line.charAt(from + 1));
    }

    /**
     * Parses the part of a leaf line after the tag, see {@link #parseLeaf(String)}.
     *
     * @param line The line.
     * @param tag The tag.
     * @param scan What the scans of the line remember.
     * @return The leaf, or {@code null} if the part after the tag does not have that form.
     */
    private Leaf parseLeafAfterTag(String line, Tag tag, LineScan scan) {
      int lemmaStart = tag.end() + 2;
      // the longest lemma after which the rest of the line still parses is used
      for (int lemmaEnd = line.length() - 1; lemmaEnd > lemmaStart; lemmaEnd--) {
        if (!isQuote(line.charAt(lemmaEnd))) {
          continue;
        }
        int tagsStart = skipWhitespace(line, lemmaEnd + 1);
        LeafRest rest = scanSecondaryTags(line, tagsStart, scan);
        if (rest != null) {
          Leaf leaf = new Leaf();
          leaf.setLevel(tag.start() + 1);
          leaf.setSyntacticTag(line.substring(tag.start(), tag.separator()));
          leaf.setFunctionalTag(line.substring(tag.separator() + 1, tag.end()));
          leaf.setLemma(line.substring(lemmaStart, lemmaEnd));
          leaf.setSecondaryTag(line.substring(tagsStart, rest.tagsEnd()));
          leaf.setMorphologicalTag(morphology(line, rest.end()));
          leaf.setLexeme(line.substring(rest.end().lexemeStart()));
          return leaf;
        }
      }
      return null;
    }

    /**
     * Parses a leaf line with an equals sign in place of the colon in its tag: the level prefix,
     * the tag, and in parentheses an optional quoted lemma and an optional morphological tag,
     * then whitespace and the lexeme.
     *
     * @param line The line.
     * @return The leaf, or {@code null} if the line does not have that form.
     */
    private Leaf parseBizarreLeaf(String line) {
      int prefix = levelPrefixEnd(line);
      LineScan scan = null;
      for (Tag tag = scanLevelAndTag(line, prefix, BIZARRE_TAG_SEPARATOR, prefix); tag != null;
           tag = scanLevelAndTag(line, prefix, BIZARRE_TAG_SEPARATOR, tag.start() - 1)) {
        if (tag.end() < line.length() && line.charAt(tag.end()) == GROUP_OPEN) {
          if (scan == null) {
            scan = new LineScan(line);
          }
          Leaf leaf = parseBizarreLeafAfterTag(line, tag, scan);
          if (leaf != null) {
            return leaf;
          }
        }
      }
      return null;
    }

    /**
     * Parses the part of a leaf line after a tag with an equals sign and the opening
     * parenthesis, see {@link #parseBizarreLeaf(String)}.
     *
     * @param line The line.
     * @param tag The tag.
     * @param scan What the scans of the line remember.
     * @return The leaf, or {@code null} if the part after the tag does not have that form.
     */
    private Leaf parseBizarreLeafAfterTag(String line, Tag tag, LineScan scan) {
      int open = tag.end() + 1;
      String lemma = null;
      LeafEnd end = null;
      if (open < line.length() && isQuote(line.charAt(open))) {
        for (int lemmaEnd = line.length() - 1; lemmaEnd > open + 1 && end == null; lemmaEnd--) {
          if (isQuote(line.charAt(lemmaEnd))) {
            end = scanLeafEnd(line, lemmaEnd + 1, scan);
            if (end != null) {
              lemma = line.substring(open + 1, lemmaEnd);
            }
          }
        }
      }
      if (end == null) {
        end = scanLeafEnd(line, open, scan);
        if (end == null) {
          return null;
        }
      }
      Leaf leaf = new Leaf();
      leaf.setLevel(tag.start() + 1);
      String syntacticTag = line.substring(tag.start(), tag.end());
      int colon = syntacticTag.lastIndexOf(TAG_SEPARATOR);
      if (colon > 0 && colon < syntacticTag.length() - 1) {
        leaf.setSyntacticTag(syntacticTag.substring(0, colon));
        leaf.setFunctionalTag(syntacticTag.substring(colon + 1));
      } else {
        leaf.setSyntacticTag(syntacticTag);
      }
      leaf.setMorphologicalTag(morphology(line, end));
      leaf.setLexeme(line.substring(end.lexemeStart()));
      leaf.setLemma(lemma);
      return leaf;
    }

    /**
     * Reads the morphological tag at the end of a leaf line.
     *
     * @param line The line.
     * @param end The end of the line.
     * @return The tag, or {@code null} if it is empty.
     */
    private String morphology(String line, LeafEnd end) {
      return end.morphologyStart() == end.close()
          ? null : line.substring(end.morphologyStart(), end.close());
    }

    /**
     * Finds the end of the level prefix: the run of equals signs and hyphens at the start of
     * the line.
     *
     * @param line The line.
     * @return The index after the run, or 0 if the line does not start with one.
     */
    private int levelPrefixEnd(String line) {
      int run = 0;
      while (run < line.length()
          && (line.charAt(run) == LEVEL_MARK || line.charAt(run) == LEVEL_HYPHEN)) {
        run++;
      }
      return run;
    }

    /**
     * Scans the tag at the start of a line after the level prefix. A longer prefix is
     * preferred; hyphens at its end may move into the tag, so the callers try the next shorter
     * prefix when the rest of the line does not parse.
     *
     * @param line The line.
     * @param prefix The index after the level prefix, see {@link #levelPrefixEnd(String)}.
     * @param separator The character between the two parts of the tag.
     * @param maxStart The highest index where the tag may start: the end of the prefix for the
     *                 first candidate, one less than the previous start for the next one.
     * @return The tag, or {@code null} if there is no further candidate.
     */
    private Tag scanLevelAndTag(String line, int prefix, char separator, int maxStart) {
      for (int start = Math.min(prefix, maxStart); start >= 0; start--) {
        if (start == prefix || line.charAt(start) == LEVEL_HYPHEN) {
          Tag tag = scanTag(line, start, separator);
          if (tag != null) {
            return tag;
          }
        }
      }
      return null;
    }

    /**
     * Scans a tag: one or more characters other than a colon or an equals sign, the separator,
     * and one or more characters that are neither an opening parenthesis nor whitespace.
     *
     * @param line The line.
     * @param from The index where the tag starts.
     * @param separator The character between the two parts of the tag.
     * @return The tag, or {@code null} if there is no tag at {@code from}.
     */
    private Tag scanTag(String line, int from, char separator) {
      int i = from;
      while (i < line.length() && line.charAt(i) != TAG_SEPARATOR
          && line.charAt(i) != BIZARRE_TAG_SEPARATOR) {
        i++;
      }
      if (i == from || i == line.length() || line.charAt(i) != separator) {
        return null;
      }
      int end = i + 1;
      while (end < line.length() && line.charAt(end) != GROUP_OPEN
          && !StringUtil.isUnicodeWhitespace(line.charAt(end))) {
        end++;
      }
      return end == i + 1 ? null : new Tag(from, i, end);
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
      if (from < line.length() && line.charAt(from) == GROUP_OPEN) {
        int close = line.indexOf(GROUP_CLOSE, from + 1);
        if (close > from + 1 && isTagGroupRun(line, close + 1)) {
          return true;
        }
      }
      return isTagGroupRun(line, from);
    }

    /**
     * Tests for tag groups up to the end of the line: after optional whitespace either no more
     * text, or an opening parenthesis and angle bracket, one or more characters, a closing angle
     * bracket and parenthesis, and optional whitespace.
     *
     * @param line The line.
     * @param from The index where the tag groups start.
     * @return {@code true} if the rest of the line has that form.
     */
    private boolean isTagGroupRun(String line, int from) {
      int start = skipWhitespace(line, from);
      int end = line.length();
      while (end > start && StringUtil.isUnicodeWhitespace(line.charAt(end - 1))) {
        end--;
      }
      if (start == end) {
        return true;
      }
      int contentStart = start + TAG_GROUP_OPEN.length();
      int contentEnd = end - TAG_GROUP_CLOSE.length();
      return contentEnd > contentStart && line.startsWith(TAG_GROUP_OPEN, start)
          && line.startsWith(TAG_GROUP_CLOSE, contentEnd);
    }

    /**
     * Scans secondary tags and the end of a leaf line after them. Each tag is an opening angle
     * bracket, one or more characters, and a closing angle bracket; a longer tag, and then one
     * more tag, is preferred when the rest of the line still parses after it.
     *
     * @param line The line.
     * @param from The index where the next secondary tag would start.
     * @param scan What the scans of the line remember.
     * @return The rest of the line after the lemma, or {@code null} if it does not have that
     *         form.
     */
    private LeafRest scanSecondaryTags(String line, int from, LineScan scan) {
      if (scan.noEndAt[from]) {
        return null;
      }
      if (from < line.length() && line.charAt(from) == BRACKET_OPEN) {
        for (int close = line.length() - 1; close > from + 1; close--) {
          if (line.charAt(close) == BRACKET_CLOSE) {
            LeafRest rest = scanSecondaryTags(line, close + 1, scan);
            if (rest != null) {
              return rest;
            }
          }
        }
      }
      LeafEnd end = scanLeafEnd(line, from, scan);
      if (end == null) {
        scan.noEndAt[from] = true;
        return null;
      }
      return new LeafRest(from, end);
    }

    /**
     * Scans the end of a leaf line: optional whitespace, an optional morphological tag up to the
     * first closing parenthesis, that parenthesis, whitespace, and the lexeme.
     *
     * @param line The line.
     * @param from The index after the secondary tags or the lemma.
     * @param scan What the scans of the line remember.
     * @return The end of the line, or {@code null} if it does not have that form.
     */
    private LeafEnd scanLeafEnd(String line, int from, LineScan scan) {
      int morphologyStart = skipWhitespace(line, from);
      int close = scan.nextClose[morphologyStart];
      if (close == -1) {
        return null;
      }
      int lexemeStart = scanLexemeStart(line, close);
      return lexemeStart == -1 ? null : new LeafEnd(morphologyStart, close, lexemeStart);
    }

    /**
     * Finds the lexeme after the closing parenthesis: whitespace, then the rest of the line. A
     * line with no text or only whitespace after the parenthesis has no lexeme.
     *
     * @param line The line.
     * @param close The index of the closing parenthesis.
     * @return The start of the lexeme, or -1 if there is none.
     */
    private int scanLexemeStart(String line, int close) {
      int lexemeStart = skipWhitespace(line, close + 1);
      return lexemeStart == close + 1 || lexemeStart == line.length() ? -1 : lexemeStart;
    }

    /**
     * Tests whether a lexeme starts with a letter, a digit, or an underscore and has a period
     * or an angle bracket after it. Letters and digits are checked by code point, so a word of
     * any script counts.
     *
     * @param lexeme The lexeme.
     * @return {@code true} for such a lexeme.
     */
    private boolean isWordWithMarkup(String lexeme) {
      if (lexeme.isEmpty()) {
        return false;
      }
      int first = lexeme.codePointAt(0);
      if (!(Character.isLetterOrDigit(first) || first == UNDERSCORE)) {
        return false;
      }
      for (int i = Character.charCount(first); i < lexeme.length(); i++) {
        char c = lexeme.charAt(i);
        if (c == PERIOD || c == BRACKET_OPEN || c == BRACKET_CLOSE) {
          return true;
        }
      }
      return false;
    }

    /**
     * Skips whitespace as {@link StringUtil#isUnicodeWhitespace(char)} defines it.
     *
     * @param line The line.
     * @param from The index to start at.
     * @return The index of the first character at or after {@code from} that is not
     *         whitespace, or the length of the line.
     */
    private int skipWhitespace(String line, int from) {
      int i = from;
      while (i < line.length() && StringUtil.isUnicodeWhitespace(line.charAt(i))) {
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
      return c == DOUBLE_QUOTE || c == SINGLE_QUOTE;
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
  private static final char TAG_OPEN = '<';
  private static final char TAG_CLOSE = '>';
  private static final String CLOSING_TAG_OPEN = "</";

  private final SentenceParser parser;

  private int paraID = 0;
  private boolean isTitle = false;
  private boolean isBox = false;

  public ADSentenceStream(ObjectStream<String> lineStream) {
    super(lineStream);
    parser = new SentenceParser();
  }


  /**
   * Reads the next sentence.
   *
   * @return The next {@link Sentence}, or {@code null} at the end of the input. A sentence cut
   *         off before the closing tag is returned with the lines read so far; an opening tag
   *         with no lines after it at the end of the input yields no sentence.
   * @throws IOException Thrown if the line stream cannot be read.
   */
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
        if (sentenceStarted && sentence.length() > 0) {
          return parser.parse(sentence.toString(), paraID, isTitle, isBox);
        }
        return null;
      }
    }
  }

  /**
   * Tests whether a line is an opening markup tag with the given name: the name right after the
   * opening angle bracket, then either the closing angle bracket or whitespace and attributes,
   * which contain no closing angle bracket, then the closing angle bracket as the last character.
   * A self-closing tag is not an opening tag.
   *
   * @param line The line.
   * @param name The tag name.
   * @return {@code true} if the whole line is such a tag.
   */
  boolean isOpeningTag(String line, String name) {
    int last = line.length() - 1;
    int afterName = name.length() + 1;
    if (last < afterName || line.charAt(0) != TAG_OPEN || !line.startsWith(name, 1)
        || line.charAt(last) != TAG_CLOSE) {
      return false;
    }
    if (afterName < last && !StringUtil.isUnicodeWhitespace(line.charAt(afterName))) {
      return false;
    }
    return line.indexOf(TAG_CLOSE, afterName) == last;
  }

  /**
   * Tests whether a line is exactly the closing markup tag with the given name.
   *
   * @param line The line.
   * @param name The tag name.
   * @return {@code true} if the whole line is that closing tag.
   */
  boolean isClosingTag(String line, String name) {
    return line.length() == name.length() + CLOSING_TAG_OPEN.length() + 1
        && line.startsWith(CLOSING_TAG_OPEN) && line.startsWith(name, CLOSING_TAG_OPEN.length())
        && line.charAt(line.length() - 1) == TAG_CLOSE;
  }
}
