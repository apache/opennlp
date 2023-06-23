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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.Span;

public class MascSentence extends Span {

  private static final Logger logger = LoggerFactory.getLogger(MascSentence.class);
  private static final long serialVersionUID = 6295507533472650848L;

  /**
   * A helper class to extract the extract a quark from the corpus file even if it is beyond the
   * bounds of the sentence.
   *
   * @param wordsById        Quarks of the sentence organized by their id
   * @param allDocumentWords Quarks of the document organized by their id
   */
  private record QuarkExtractor(Map<Integer, MascWord> wordsById, List<MascWord> allDocumentWords) {

    /**
     * Extract a quark by its key
     *
     * @param key The quark's ID
     * @return The {@link MascWord quark reference}.
     * @throws IOException Thrown if the {@code key} was not found in the document.
     */
    private MascWord get(int key) throws IOException {
      // First, check if this word is in the sentence
      // TODO: evaluate the necessity: HashMaps are O(1), right?
      if (wordsById.containsKey(key)) {
        return wordsById.get(key);
      } else {
        for (MascWord wordFromWholeDocument : allDocumentWords) {
          if (wordFromWholeDocument.getId() == key) {
            return wordFromWholeDocument;
          }
        }
      }
      throw new IOException("Word" + key + " not found in the document.");
    }

  }

  private final List<MascWord> allDocumentWords;
  private final String text;
  private final List<MascWord> words;
  private final Map<Integer, MascWord> wordsById;
  private List<MascToken> sentenceTokens = null;
  private final Map<Integer, Integer> tokensById = new HashMap<>();
  private List<Span> namedEntities = new ArrayList<>();

  /**
   * Initializes a {@link MascSentence} containing its associated text and quarks
   *
   * @param s              Start of the sentence within the corpus file
   * @param e              End of the sentence within the corpus file
   * @param text           The reference to text of the corpus file
   * @param sentenceQuarks The quarks found in that sentence
   * @param allQuarks      The reference to a list of all quarks in the file
   */
  public MascSentence(int s, int e, String text, List<MascWord> sentenceQuarks,
                      List<MascWord> allQuarks) {
    super(s, e);
    this.text = text;
    this.words = sentenceQuarks;
    this.allDocumentWords = allQuarks;

    // We'll create a map of word ID's and the word ref's to speed up the tokenization
    HashMap<Integer, MascWord> idToWordMap = new HashMap<>();
    for (MascWord w : sentenceQuarks) {
      idToWordMap.put(w.getId(), w);
    }
    wordsById = idToWordMap;
  }

  /**
   * Add the Penn tokenization and POS tagging to the sentence.
   *
   * @param tokenToQuarks A map from token ID to quarks in that token.
   * @param quarkToTokens A map of quark IDs and the token IDs containing that quark.
   * @param tokenToBase   Token ID to the token base.
   * @param tokenToTag    Token ID to the POS tag.
   *
   * @return {@code true} if no issue encountered, {@code false} if tokens cross sentence boundaries.
   * @throws IOException Thrown if IO errors occurred.
   */
  boolean tokenizePenn(Map<Integer, int[]> tokenToQuarks,
                       Map<Integer, int[]> quarkToTokens,
                       Map<Integer, String> tokenToBase,
                       Map<Integer, String> tokenToTag) throws IOException {

    boolean fileWithoutIssues = true;
    QuarkExtractor extractor = new QuarkExtractor(wordsById, allDocumentWords);
    sentenceTokens = new ArrayList<>();

    Map<Integer, Boolean> tokensProcessed = new HashMap<>();
    for (MascWord w : words) {
      int currentQuarkId = w.getId();
      //extract the node to which this word belongs
      int[] tokens = quarkToTokens.get(currentQuarkId);

      //Only continue, if the word belongs to at least one node
      if (tokens != null) {
        for (int token : tokens) {
          //check if we already have the token
          if (!tokensProcessed.containsKey(token)) {

            int[] quarksOfToken = tokenToQuarks.get(token); // Get the quark IDs contained in the token
            if (quarksOfToken == null) {
              logger.warn("Token without quarks found: {}", token);
            }

            for (int quark : quarksOfToken) {
              if (!wordsById.containsKey(quark)) {
                fileWithoutIssues = false;
                logger.warn("Some tokens cross sentence boundaries." +
                    "\n\tQuark ID: {}" +
                    "\n\tPenn token ID: {}", quark, token );
              }
            }

            /*Because there are some quarks which are parts of tokens outside a sentence
            We need to check every time if that quark was actually assigned to the sentence
            If not, we need to extract it manually from the whole document*/
            MascWord[] quarks = new MascWord[quarksOfToken.length]; //Get the actual quark references
            for (int currentQuark = 0; currentQuark < quarks.length; currentQuark++) {
              quarks[currentQuark] = extractor.get(quarksOfToken[currentQuark]);
            }

            int start = extractor.get(quarksOfToken[0]).getStart();
            int end = extractor.get(quarksOfToken[quarksOfToken.length - 1]).getEnd();

            //only insert tokens with non-zero length, apparently some of them exist in the corpus
            if (end - start > 0) {
              sentenceTokens.add(new MascToken(start, end, token, tokenToTag.get(token),
                  tokenToBase.get(token), quarks));
              tokensProcessed.put(token, true);
            }
          }
        }
      }
    }
    for (int i = 0; i < sentenceTokens.size(); i++) {
      MascToken t = sentenceTokens.get(i);
      tokensById.put(t.getTokenId(), i);
    }

    sentenceTokens = Collections.unmodifiableList(sentenceTokens);
    return fileWithoutIssues;
  }

  /**
   * Add the named entity annotation to the tokenized sentence
   *
   * @param entityIDtoEntityType Maps the named entity ID to its type
   * @param entityIDsToTokens    A list of tokens covered by each named entity
   *
   * @return {@code true} if all went well, {@code false} if named entities overlap.
   * @throws IOException Thrown if IO errors occurred.
   */
  boolean addNamedEntities(Map<Integer, String> entityIDtoEntityType,
                           Map<Integer, List<Integer>> entityIDsToTokens) throws IOException {
    boolean fileWithoutIssues = true;
    if (sentenceTokens == null) {
      throw new IOException("Named entity labels provided for an un-tokenized sentence.");
    }

    //for each named entity identify its span
    for (Map.Entry<Integer, List<Integer>> namedEntity : entityIDsToTokens.entrySet()) {

      int entityID = namedEntity.getKey();
      String type = entityIDtoEntityType.get(entityID);

      List<Integer> tokenIDs = namedEntity.getValue();

      int start = sentenceTokens.size();
      int end = 0;
      boolean entityInThisSentence = false;
      for (int tokenID : tokenIDs) {

        if (tokensById.containsKey(tokenID)) {
          entityInThisSentence = true;
          if (tokensById.get(tokenID) < start) {
            start = tokensById.get(tokenID);
          }
          if (tokensById.get(tokenID) > end) {
            end = tokensById.get(tokenID) + 1;
          }
        }
      }

      if (entityInThisSentence) {
        namedEntities.add(new Span(start, end, type));
      }

    }

    Comparator<Span> compareByStart = Comparator.comparingInt(Span::getStart);
    namedEntities.sort(compareByStart);

    Set<Integer> overlaps = new HashSet<>();
    int leftIndex = 0;
    int rightIndex = leftIndex + 1;
    while (rightIndex < namedEntities.size()) {
      Span leftSpan = namedEntities.get(leftIndex);
      Span rightSpan = namedEntities.get(rightIndex);
      if (leftSpan.contains(rightSpan) || leftSpan.crosses(rightSpan)) {
        logger.warn("Named entities overlap. This is forbidden in OpenNLP." +
            "\n\tKeeping the longer of them.");
        if (rightSpan.length() > leftSpan.length()) {
          overlaps.add(leftIndex);
        } else {
          overlaps.add(rightIndex);
        }
        fileWithoutIssues = false;
        rightIndex++;
      } else {
        leftIndex++;
      }
    }

    if (!fileWithoutIssues) {
      List<Span> namedEntitiesNoOverlaps = new ArrayList<>();
      for (int i = 0; i < namedEntities.size() - 1; i++) {
        if (!overlaps.contains(i)) {
          namedEntitiesNoOverlaps.add(namedEntities.get(i));
        }
      }
      namedEntities = Collections.unmodifiableList(namedEntitiesNoOverlaps);
    }

    return fileWithoutIssues;
  }

  /**
   * @return Retrieves the {@link List<Span> named entities}, e.g. {@code Span(1,3, "org")} for tokens [1,3).
   */
  public List<Span> getNamedEntities() {
    return namedEntities;
  }

  /**
   * @return Retrieves text of the sentence as defined by the sentence segmentation annotation.
   */
  public String getSentDetectText() {
    return text.substring(getStart(), getEnd());
  }

  /**
   * @return Retrieves text of the sentence as defined by the tokens in it.
   */
  public String getTokenText() {
    if (sentenceTokens.isEmpty()) {
      return "";
    }
    return text.substring(sentenceTokens.get(0).getStart(),
        sentenceTokens.get(sentenceTokens.size() - 1).getEnd());
  }

  /**
   * @return The texts of the individual tokens in the sentence
   */
  public List<String> getTokenStrings() {
    List<String> tokenArray = new ArrayList<>();
    for (MascToken t : sentenceTokens) {
      tokenArray.add(text.substring(t.getStart(), t.getEnd()));
    }

    return Collections.unmodifiableList(tokenArray);

  }

  /**
   * Retrieves the boundaries of individual tokens.
   *
   * @return The {@link List<Span> spans} representing the tokens of the sentence,
   *         according to Penn tokenization.
   */
  public List<Span> getTokensSpans() {

    List<Span> tokenSpans = new ArrayList<>();
    int offset = sentenceTokens.isEmpty() ? 0 : sentenceTokens.get(0).getStart();

    for (MascToken i : sentenceTokens) {
      tokenSpans.add(new Span(i.getStart() - offset, i.getEnd() - offset));
    }

    return Collections.unmodifiableList(tokenSpans);
  }

  /**
   * @return Get the (individual) tags of tokens in the sentence.
   * 
   * @throws IOException Thrown if used on an un-tokenized sentence.
   */
  public List<String> getTags() throws IOException {
    List<String> tags = new ArrayList<>();
    for (MascToken t : sentenceTokens) {
      tags.add(t.getPos());
    }
    return tags;
  }

}
