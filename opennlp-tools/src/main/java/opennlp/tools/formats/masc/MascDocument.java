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

import java.io.BufferedInputStream;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import opennlp.tools.util.Span;
import opennlp.tools.util.XmlUtil;


public class MascDocument {

  private static final Logger logger = LoggerFactory.getLogger(MascDocument.class);
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
   * Initializes a {@link MascDocument} with all the stand-off annotations translated into the
   * internal structure.
   *
   * @param path      The path where the document header is.
   * @param f_primary The {@link InputStream file} with the raw corpus text.
   * @param f_seg     The {@link InputStream file} with segmentation into quarks.
   * @param f_ne      The {@link InputStream file} with named entities.
   * @param f_penn    The {@link InputStream file} with tokenization and Penn POS tags produced
   *                  by GATE-5.0 ANNIE application.
   * @param f_s       The {@link InputStream file} with sentence boundaries.
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
    final MascDocument doc = new MascDocument(path, sentences);

    // if the file has Penn POS tags, add them
    if (f_penn != null) {
      doc.addPennTags(parsePennTags(f_penn));
    }

    if (f_ne != null) {
      doc.addNamedEntityTags(parseNamedEntity(f_ne));
    }

    //TODO: make the annotations immutable
    //TODO: should we cleanup the document (e.g. remove sentences without tokens?)
    return doc;
  }

  /**
   * Reads in the corpus file text.
   *
   * @param stream A valid, open {@link InputStream stream} for a corpus file.
   *
   * @return The text of the file.
   * @throws IOException Thrown if IO errors occurred.
   */
  private static String readText(InputStream stream) throws IOException {
    try (Reader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      StringBuilder contents = new StringBuilder();
      char[] buffer = new char[8192];
      int read;
      while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
        contents.append(buffer, 0, read);
      }
      return contents.toString();
    }
  }


  /**
   * Parses the word segmentation stand-off annotation
   *
   * @param f_seg A valid, open {@link InputStream stream} for a file with segmentation.
   * @return A list of individual quarks, expressed as MascWord-s
   * @throws IOException Thrown if IO errors occurred.
   */
  private static List<MascWord> parseWords(InputStream f_seg) throws IOException {

    try (BufferedInputStream bStream = new BufferedInputStream(f_seg)) {
      SAXParser saxParser = XmlUtil.createSaxParser();
      MascWordParser handler = new MascWordParser();
      try {
        saxParser.parse(bStream, handler);
      } catch (SAXException e) {
        throw new IOException("Could not parse the region annotation file");
      }

      return Collections.unmodifiableList(handler.getAnchors());
    }
  }

  /**
   * Parses the sentence annotation file, align it with the raw text
   *
   * @param f_s A valid, open {@link InputStream stream} for a sentence annotation file.
   * @return The {@link List<Span>} delimiting each sentence.
   * @throws IOException if the sentence file cannot be parsed or closed
   */
  private static List<Span> parseSentences(InputStream f_s) throws IOException {

    try (BufferedInputStream bStream = new BufferedInputStream(f_s)) {
      SAXParser saxParser = XmlUtil.createSaxParser();
      MascSentenceParser handler = new MascSentenceParser();
      try {
        saxParser.parse(bStream, handler);
      } catch (SAXException e) {
        throw new IOException("Could not parse the sentence annotation file");
      }

      List<Span> anchors = handler.getAnchors();

      /*
       * Filter out sentence overlaps.
       * Keep only those sentences  where sentence.end < nextSentence.beginning
       * avoid deleting in the middle and repeatedly shifting the list by copying into a new list
       */
      //TODO: can we know a priori, if we need this filtering?
      List<Span> filteredAnchors = new ArrayList<>();
      for (int i = 0; i < anchors.size() - 1; i++) {
        if (anchors.get(i).getEnd() < anchors.get(i + 1).getStart()) {
          filteredAnchors.add(anchors.get(i));
        }
      }
      filteredAnchors.add(anchors.get(anchors.size() - 1));

      return Collections.unmodifiableList(filteredAnchors);
    }

  }

  /**
   * Parses the Penn-POS (GATE5-ANNIE) stand-off annotation.
   *
   * @param f_penn A valid, open {@link InputStream stream} for a file with Penn POS tags.
   *               
   * @return A map of three sub-maps: tokenToTag, from Penn token ID (int) to Penn POS-tag,
   * tokenToBase, from Penn token ID (int) to the base and tokenToQuarks, from Penn token ID
   * (int) to a List of quark IDs contained in that token.
   * @throws IOException Thrown if IO errors occurred.
   */
  private static Map<String, Map<Integer, ?>> parsePennTags(InputStream f_penn) throws IOException {
    Map<String, Map<Integer, ?>> tagsAndBases = new HashMap<>();

    try (BufferedInputStream bStream = new BufferedInputStream(f_penn)) {
      SAXParser saxParser = XmlUtil.createSaxParser();
      MascPennTagParser handler = new MascPennTagParser();
      try {
        saxParser.parse(bStream, handler);
      } catch (SAXException e) {
        throw new IOException("Could not parse the Penn tag annotation file");
      }

      tagsAndBases.put("tokenToTag", handler.getTags());
      tagsAndBases.put("tokenToBase", handler.getBases());
      tagsAndBases.put("tokenToQuarks", handler.getTokenToQuarks());

      return tagsAndBases;
    }
  }

  /**
   * Parses the named entity stand-off annotation.
   *
   * @param f_ne A valid, open {@link InputStream stream} for a file with named entity annotations.
   * @return A map with two sub-maps, entityIDtoEntityType, mapping entity ID integers
   * to entity type Strings, and entityIDsToTokens, mapping entity ID integers to Penn
   * token ID integers.
   * @throws IOException Thrown if IO errors occurred.
   */
  private static Map<String, Map<Integer, ?>> parseNamedEntity(InputStream f_ne) throws IOException {

    try (BufferedInputStream bStream = new BufferedInputStream(f_ne)) {
      SAXParser saxParser = XmlUtil.createSaxParser();
      MascNamedEntityParser handler = new MascNamedEntityParser();
      try {
        saxParser.parse(bStream, handler);
      } catch (SAXException e) {
        throw new IOException("Could not parse the named entity annotation file", e);
      }

      Map<Integer, String> entityIDtoEntityType = handler.getEntityIDtoEntityType();
      Map<Integer, List<Integer>> entityIDsToTokens = handler.getEntityIDsToTokens();
      Map<String, Map<Integer, ?>> results = new HashMap<>();
      results.put("entityIDtoEntityType", entityIDtoEntityType);
      results.put("entityIDsToTokens", entityIDsToTokens);
      return results;
    }
  }

  /**
   * Combines the raw text with annotations that every file should have.
   *
   * @param text          The raw text.
   * @param sentenceSpans The spans defining individual sentences. Overlaps are not permitted.
   * @param words         The quarks of the raw text.
   * @return A list of sentences, each of which is a list of quarks. Some quarks may belong to
   * more than one sentence. Quarks which do not belong to a single sentence are silently dropped.
   * @throws IOException If sentences and quarks cannot be aligned.
   */
  private static List<MascSentence> combineAnnotations(String text, List<Span> sentenceSpans,
                                                       List<MascWord> words) throws IOException {

    int wordIndex = 0;
    int wordCount = words.size();
    List<MascSentence> sentences = new ArrayList<>();
    for (Span s : sentenceSpans) {
      if (s.getEnd() - s.getStart() > 0) {
        List<MascWord> quarks = new ArrayList<>();
        int sentenceStart = s.getStart();
        int sentenceEnd = s.getEnd();

        // TODO: is it okay that quarks can cross sentence boundary? What are the implications?
        /*
         * Allow quarks to cross sentence boundary.
         * The decisive factor determining if a quark belongs to a sentence is if they overlap.
         * I.e. sent.getEnd() > quark.getStart() && sent.getStart() < quark.getEnd()
         */
        MascWord nextWord = words.get(wordIndex);
        // Find sentence beginning, should not be needed unless overlaps occur
        while (sentenceStart < nextWord.getEnd() && wordIndex > 0) {
          wordIndex--;
          nextWord = words.get(wordIndex);
        }

        // TODO: can this be translated into Span's methods .crosses()/.contains()?
        // Find all quarks contained or crossing the span of that sentence
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
   * Attaches the named entity labels to individual tokens.
   *
   * @param namedEntities A map with two sub-maps, entityIDtoEntityType, mapping entity ID integers
   *                      to entity type Strings, and entityIDsToTokens, mapping entity ID integers to Penn
   *                      token ID integers
   */
  private void addNamedEntityTags(Map<String, Map<Integer, ?>> namedEntities) {
    try {
      Map<Integer, String> entityIDtoEntityType =
              (Map<Integer, String>) namedEntities.get("entityIDtoEntityType");
      Map<Integer, List<Integer>> entityIDsToTokens =
              (Map<Integer, List<Integer>>) namedEntities.get("entityIDsToTokens");

      for (MascSentence s : sentences) {
        boolean success = s.addNamedEntities(entityIDtoEntityType, entityIDsToTokens);
        if (!success) {
          logger.warn("Issues occurred in the file:  {}", pathToFile);
        }
      }
      hasNamedEntities = true;
    } catch (IOException e) {
      logger.error("Failed connecting tokens and named entities. " +
              "The error occurred in the file: {}", pathToFile, e);
    }
  }


  /**
   * Attach tags and bases to MascWords in each of the sentences.
   *
   * @param tagMaps A map of three sub-maps: tokenToTag, from Penn token ID (int) to Penn POS-tag,
   *                * tokenToBase, from Penn token ID (int) to the base and tokenToQuarks, from Penn token ID
   *                * (int) to a List of quark IDs contained in that token.
   */
  private void addPennTags(Map<String, Map<Integer, ?>> tagMaps) throws IOException {
    try {
      // Extract individual mappings
      Map<Integer, String> tokenToTag = (Map<Integer, String>) tagMaps.get("tokenToTag");
      Map<Integer, String> tokenToBase = (Map<Integer, String>) tagMaps.get("tokenToBase");
      Map<Integer, int[]> tokenToQuarks = (Map<Integer, int[]>) tagMaps.get("tokenToQuarks");

      //Check that all tokens have at least one quark.
      for (Map.Entry<Integer, int[]> token : tokenToQuarks.entrySet()) {
        if (token.getValue().length == 0) {
          logger.warn("Token without quarks: {}", token.getKey());
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
            logger.warn("One quark belongs to several tokens. f-seg ID: {}.", quark);
            logger.warn("The error occurred in file: {}", pathToFile);
            quarkToTokens.put(quark, newTokens);
          } else {
            quarkToTokens.put(quark, new int[] {token});
          }
        }
      }

      for (MascSentence s : sentences) {
        boolean success = s.tokenizePenn(tokenToQuarks, quarkToTokens, tokenToBase, tokenToTag);
        if (!success) {
          logger.warn("Issues occurred in the file:  {}", pathToFile);
        }
      }

      hasPennTags = true;

    } catch (Exception e) {
      throw new IOException("Could not attach POS tags to words. " +
          e.getMessage() + Arrays.toString(e.getStackTrace()));
    }
  }


  /**
   * Checks whether there is Penn tagging produced by GATE-5.0 ANNIE.
   *
   * @return {@code true} if this file has aligned tags/tokens, {@code false} otherwise.
   */
  public boolean hasPennTags() {
    return hasPennTags;
  }

  /**
   * Checks whether there is NER by GATE-5.0 ANNIE.
   *
   * @return {@code true} if this file has named entities, {@code false} otherwise.
   */
  public boolean hasNamedEntities() {
    return hasNamedEntities;
  }

  /**
   * @return Retrieves the next sentence or {@code null} if end of document reached.
   */
  public MascSentence read() {
    MascSentence next = null;
    if (sentenceIterator.hasNext()) {
      next = sentenceIterator.next();
    }
    return next;
  }

  /**
   * Resets the reading of sentences to the beginning of the document.
   */
  public void reset() {
    this.sentenceIterator = this.sentences.iterator();
  }

}
