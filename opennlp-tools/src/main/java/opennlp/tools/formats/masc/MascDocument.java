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

package opennlp.tools.formats.masc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;

import org.xml.sax.SAXException;

import opennlp.tools.util.Span;
import opennlp.tools.util.XmlUtil;


public class MascDocument {

  private final List<MascSentence> sentences;
  private final String pathToFile;
  private Iterator<MascSentence> sentenceIterator;
  private boolean hasPennTags = false;
  private boolean hasNamedEntities = false;

  public MascDocument(String path, List<MascSentence> sentences) {
    this.pathToFile = path;
    this.sentences = sentences;
    this.sentenceIterator = sentences.iterator();
  }

  /**
   * Creates a MASC document with all of the stand-off annotations translated into the internal
   * structure.
   *
   * @param path      The path where the document header is.
   * @param f_primary The file with the raw corpus text.
   * @param f_seg     The file with segmentation into quarks.
   * @param f_ne      The file with named entities.
   * @param f_penn    The file with tokenization and Penn POS tags produced
   *                  by GATE-5.0 ANNIE application.
   * @param f_s       The file with sentence boundaries.
   * @return A document containing the text and its annotations. Immutability is not guaranteed yet.
   * @throws IOException if the raw data cannot be read or the alignment of the raw data
   *                     with annotations fails
   */
  public static MascDocument parseDocument(String path, InputStream f_primary, InputStream f_seg,
                                           InputStream f_penn, InputStream f_s, InputStream f_ne)
      throws IOException {

    String text = readText(f_primary);
    List<MascWord> words = parseWords(f_seg);
    List<Span> sentenceSpans = parseSentences(f_s);

    List<MascSentence> sentences = combineAnnotations(text, sentenceSpans, words);
    MascDocument doc = new MascDocument(path, sentences);

    // if the file has Penn POS tags, add them
    if (f_penn != null) {
      doc.addPennTags(parsePennTags(f_penn));
    }

    if (f_ne != null) {
      doc.addNamedEntityTags(parseNamedEntity(f_ne));
    }

    //todo: make the annotations immutable
    //todo: should we cleanup the document (e.g. remove sentences without tokens?)
    return doc;
  }

  /**
   * Read in the corpus file text
   *
   * @param stream The corpus file
   * @return The text of the file
   * @throws IOException if anything goes wrong
   */
  private static String readText(InputStream stream) throws IOException {
    try {
      Reader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      StringBuilder contents = new StringBuilder();
      char[] buffer = new char[8192];
      int read;
      while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
        contents.append(buffer, 0, read);
      }
      return contents.toString();
    } finally {
      // this may throw an exception
      stream.close();
    }
  }


  /**
   * Parses the word segmentation stand-off annotation
   *
   * @param f_seg The file with segmentation
   * @return A list of individual quarks, expressed as MascWord-s
   * @throws IOException if anything goes wrong
   */
  private static List<MascWord> parseWords(InputStream f_seg) throws IOException {

    try {
      SAXParser saxParser = XmlUtil.createSaxParser();
      MascWordParser handler = new MascWordParser();
      try {
        saxParser.parse(f_seg, handler);
      } catch (SAXException e) {
        throw new IOException("Could not parse the region annotation file");
      }

      return Collections.unmodifiableList(handler.getAnchors());

    } finally {
      f_seg.close();
    }
  }

  /**
   * Parse the sentence annotation file, align it with the raw text
   *
   * @param f_s the sentence annotation file
   * @return the list of Spans delimiting each sentence
   * @throws IOException if the sentence file cannot be parsed or closed
   */
  private static List<Span> parseSentences(InputStream f_s) throws IOException {

    try {
      SAXParser saxParser = XmlUtil.createSaxParser();
      MascSentenceParser handler = new MascSentenceParser();
      try {
        saxParser.parse(f_s, handler);
      } catch (SAXException e) {
        throw new IOException("Could not parse the sentence annotation file");
      }

      List<Span> anchors = handler.getAnchors();

      /*Filter out sentence overlaps.
      Keep only those sentences  where sentence.end < nextsentence.beginning
      avoid deleting in the middle and repeatedly shifting the list by copying into a new list*/
      //todo: can we know a priori, if we need this filtering?
      List<Span> filteredAnchors = new ArrayList<>();
      for (int i = 0; i < anchors.size() - 1; i++) {
        if (anchors.get(i).getEnd() < anchors.get(i + 1).getStart()) {
          filteredAnchors.add(anchors.get(i));
        }
      }
      filteredAnchors.add(anchors.get(anchors.size() - 1));

      return Collections.unmodifiableList(filteredAnchors);

    } finally {
      f_s.close();
    }

  }

  /**
   * Parses the Penn-POS (GATE5-ANNIE) stand-off annotation
   *
   * @param f_penn The file with Penn POS tags
   * @return A map of three sub-maps: tokenToTag, from Penn token ID (int) to Penn POS-tag,
   * tokenToBase, from Penn token ID (int) to the base and tokenToQuarks, from Penn token ID
   * (int) to a List of quark IDs contained in that token.
   * @throws IOException if anything goes wrong
   */
  private static Map<String, Map> parsePennTags(InputStream f_penn) throws IOException {
    Map<String, Map> tagsAndBases = new HashMap<>();

    try {
      SAXParser saxParser = XmlUtil.createSaxParser();
      MascPennTagParser handler = new MascPennTagParser();
      try {
        saxParser.parse(f_penn, handler);
      } catch (SAXException e) {
        throw new IOException("Could not parse the Penn tag annotation file");
      }

      tagsAndBases.put("tokenToTag", handler.getTags());
      tagsAndBases.put("tokenToBase", handler.getBases());
      tagsAndBases.put("tokenToQuarks", handler.getTokenToQuarks());

      return tagsAndBases;

    } finally {
      f_penn.close();
    }
  }

  /**
   * Parses the named entity stand-off annotation
   *
   * @param f_ne The file with named entity annotations
   * @return A map with two sub-maps, entityIDtoEntityType, mapping entity ID integers
   * to entity type Strings, and entityIDsToTokens, mapping entity ID integers to Penn
   * token ID integers
   * @throws IOException if anything goes wrong
   */
  private static Map<String, Map> parseNamedEntity(InputStream f_ne) throws IOException {

    try {
      SAXParser saxParser = XmlUtil.createSaxParser();
      MascNamedEntityParser handler = new MascNamedEntityParser();
      try {
        saxParser.parse(f_ne, handler);
      } catch (SAXException e) {
        System.out.println(e.getMessage());
        throw new IOException("Could not parse the named entity annotation file");
      }

      Map<Integer, String> entityIDtoEntityType = handler.getEntityIDtoEntityType();
      Map<Integer, List<Integer>> entityIDsToTokens = handler.getEntityIDsToTokens();
      Map<String, Map> results = new HashMap<>();
      results.put("entityIDtoEntityType", entityIDtoEntityType);
      results.put("entityIDsToTokens", entityIDsToTokens);
      return results;

    } finally {
      f_ne.close();
    }
  }

  /**
   * Combines the raw text with annotations that every file should have.
   *
   * @param text          The raw text.
   * @param sentenceSpans The spans definining individual sentences. Overlaps are not permitted.
   * @param words         The quarks of the raw text.
   * @return A list of sentences, each of which is a list of quarks. Some quarks may belong to
   * more than one sentence. Quarks which do not belong to a single sentence are silently dropped.
   * @throws IOException If sentences and quarks cannot be aligned.
   */
  private static List<MascSentence> combineAnnotations(String text,
                                                       List<Span> sentenceSpans,
                                                       List<MascWord> words) throws IOException {

    int wordIndex = 0;
    int wordCount = words.size();
    List<MascSentence> sentences = new ArrayList<>();
    for (Span s : sentenceSpans) {
      if (s.getEnd() - s.getStart() > 0) {
        List<MascWord> quarks = new ArrayList<>();
        int sentenceStart = s.getStart();
        int sentenceEnd = s.getEnd();

        //todo: is it okay that quarks can cross sentence boundary? What are the implications?
        /*
        Allow quarks to cross sentence boundary.
        The decisive factor determining if a quark belongs to a sentence is if they overlap.
        I.e. sent.getEnd() > quark.getStart() && sent.getStart() < quark.getEnd()
         */
        MascWord nextWord = words.get(wordIndex);
        //Find sentence beginning, should not be needed unless overlaps occur
        while (sentenceStart < nextWord.getEnd() && wordIndex > 0) {
          wordIndex--;
          nextWord = words.get(wordIndex);
        }

        //todo: can this be translated into Span's methods .crosses()/.contains()?
        //find all quarks contained or crossing the span of that sentence
        boolean sentenceOver = false;
        while ((!sentenceOver) && wordIndex < wordCount) {
          nextWord = words.get(wordIndex);
          int nextWordStart = nextWord.getStart();
          int nextWordEnd = nextWord.getEnd();
          // word either ends or starts or ends & starts in the middle of sentence
          if (sentenceEnd > nextWordStart && sentenceStart < nextWordEnd) {
            quarks.add(nextWord);
            if (sentenceEnd == nextWordEnd) {
              sentenceOver = true;
            }
            wordIndex++;
          } else if (sentenceEnd <= nextWordStart) {
            sentenceOver = true;
          } else {
            wordIndex++;
          }
        }

        // If we are at the end of words, but not in the last sentence, throw an error
        if (!sentenceOver && sentences.size() != sentenceSpans.size() - 1) {
          throw new IOException("Sentence ends and word ends do not match." +
              "First sentence not completed ends at character: " + sentenceEnd);
        }

        MascSentence sentence = new MascSentence(sentenceStart, sentenceEnd, text, quarks,
            words);
        sentences.add(sentence);
      }
    }

    return Collections.unmodifiableList(sentences);

  }


  /**
   * Attach the named entity labels to individual tokens
   *
   * @param namedEntities A map with two sub-maps, entityIDtoEntityType, mapping entity ID integers
   *                      * to entity type Strings, and entityIDsToTokens, mapping entity ID integers to Penn
   *                      * token ID integers
   */
  private void addNamedEntityTags(Map<String, Map> namedEntities) {
    try {
      Map<Integer, String> entityIDtoEntityType = namedEntities.get("entityIDtoEntityType");
      Map<Integer, List<Integer>> entityIDsToTokens = namedEntities.get("entityIDsToTokens");

      for (MascSentence s : sentences) {
        boolean success = s.addNamedEntities(entityIDtoEntityType, entityIDsToTokens);
        if (!success) {
          System.out.println("\tIssues occurred in the file: " + pathToFile);
        }
      }
      hasNamedEntities = true;
    } catch (IOException e) {
      System.err.println("[ERROR] Failed connecting tokens and named entities.");
      System.err.println("\tThe error occurred in the file: " + pathToFile);
      System.err.println(e.getMessage());
      System.err.println(Arrays.toString(e.getStackTrace()));
    }
  }


  /**
   * Attach tags and bases to MascWords in each of the sentences.
   *
   * @param tagMaps A map of three sub-maps: tokenToTag, from Penn token ID (int) to Penn POS-tag,
   *                * tokenToBase, from Penn token ID (int) to the base and tokenToQuarks, from Penn token ID
   *                * (int) to a List of quark IDs contained in that token.
   */
  private void addPennTags(Map<String, Map> tagMaps) throws IOException {
    try {
      // Extract individual mappings
      Map<Integer, String> tokenToTag = tagMaps.get("tokenToTag");
      Map<Integer, String> tokenToBase = tagMaps.get("tokenToBase");
      Map<Integer, int[]> tokenToQuarks = tagMaps.get("tokenToQuarks");

      //Check that all tokens have at least one quark.
      for (Map.Entry<Integer, int[]> token : tokenToQuarks.entrySet()) {
        if (token.getValue().length == 0) {
          System.err.println("[ERROR] Token without quarks: " + token.getKey());
        }
      }

      Map<Integer, int[]> quarkToTokens = new HashMap<>();
      for (Map.Entry<Integer, int[]> tokenAndQuarks : tokenToQuarks.entrySet()) {
        int token = tokenAndQuarks.getKey();
        int[] quarks = tokenAndQuarks.getValue();
        for (int quark : quarks) {
          //very rarely, one quark may belong to several token
          //this is probably a mistake in the corpus annotation
          if (quarkToTokens.containsKey(quark)) {
            int[] tokens = quarkToTokens.get(quark);
            int[] newTokens = new int[tokens.length + 1];
            newTokens[0] = token;
            System.arraycopy(tokens, 0, newTokens, 1, tokens.length);
            System.out.println("[WARNING] One quark belongs to several tokens. f-seg ID: " +
                quark);
            System.out.println("\tThe error occurred in file: " + pathToFile);
            quarkToTokens.put(quark, newTokens);
          } else {
            quarkToTokens.put(quark, new int[] {token});
          }
        }
      }

      for (MascSentence s : sentences) {
        boolean success = s.tokenizePenn(tokenToQuarks, quarkToTokens, tokenToBase, tokenToTag);
        if (!success) {
          System.out.println("\tIssue occurred in file: " + pathToFile);
        }
      }

      hasPennTags = true;

    } catch (Exception e) {
      throw new IOException("Could not attach POS tags to words. " +
          e.getMessage() + Arrays.toString(e.getStackTrace()));
    }
  }


  /**
   * Check whether there is Penn tagging produced by GATE-5.0 ANNIE
   *
   * @return true if this file has aligned tags/tokens
   */
  public boolean hasPennTags() {
    return hasPennTags;
  }

  public boolean hasNamedEntities() {
    return hasNamedEntities;
  }

  /**
   * Get next sentence.
   *
   * @return Next sentence or null if end of document reached.
   */
  public MascSentence read() {
    MascSentence next = null;
    if (sentenceIterator.hasNext()) {
      next = sentenceIterator.next();
    }
    return next;
  }

  /**
   * Return the reading of sentences to the beginning of the document.
   */
  public void reset() {
    this.sentenceIterator = this.sentences.iterator();
  }

}
