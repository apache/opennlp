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

package opennlp.tools.formats.conllu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.StringUtil;

/**
 * The CoNNL-U Format is specified
 * <a href="http://universaldependencies.org/format.html">here</a>.
 */
public class ConlluStream implements ObjectStream<ConlluSentence> {

  private static final String TEXT_LANG_PREFIX = "text_";
  private static final String INVALID_MULTIWORD_ID = "Invalid multiword token id: ";

  private final ObjectStream<String> sentenceStream;

  /**
   * Initializes a {@link ConlluStream}.
   *
   * @param in The {@link InputStreamFactory} to use. Characters will be interpreted in UTF-8.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public ConlluStream(InputStreamFactory in) throws IOException {
    this.sentenceStream = new ParagraphStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  @Override
  public ConlluSentence read() throws IOException {
    String sentence = sentenceStream.read();

    if (sentence != null) {
      List<ConlluWordLine> wordLines = new ArrayList<>();

      BufferedReader reader = new BufferedReader(new StringReader(sentence));

      boolean newDocument = false;
      boolean newParagraph = false;
      String documentId = null;
      String paragraphId = null;
      String sentenceId = null;
      String text = null;
      Map<Locale, String> textLang = null;
      String translit = null;

      String line;
      while ((line = reader.readLine())  != null) {
        // # indicates a comment line and contains additional data
        if (line.trim().startsWith("#")) {
          String commentLine = line.trim().substring(1);

          int separator = commentLine.indexOf('=');

          if (separator != -1) {
            String firstPart = commentLine.substring(0, separator).trim();
            String secondPart = commentLine.substring(separator + 1, commentLine.length()).trim();

            if (!secondPart.isEmpty()) {
              switch (firstPart) {
                case "newdoc id":
                  newDocument = true;
                  documentId = secondPart;
                  break;
                case "newpar id":
                  newParagraph = true;
                  paragraphId = secondPart;
                  break;
                case "sent_id":
                  sentenceId = secondPart;
                  break;
                case "text":
                  text = secondPart;
                  break;
                case "translit":
                  translit = secondPart;
                  break;
              }
            }

            if (firstPart.startsWith(TEXT_LANG_PREFIX)) {
              if (textLang == null) {
                textLang = new HashMap<>();
              }
              addTextLang(firstPart, secondPart, textLang);
            }
          }
          else {
            switch (commentLine.trim()) {
              case "newdoc":
                newDocument = true;
                break;
              case "newpar":
                newParagraph = true;
                break;
            }
          }
        }
        else {
          wordLines.add(new ConlluWordLine(line));
        }
      }

      wordLines = postProcessContractions(wordLines);

      return new ConlluSentence(wordLines, sentenceId, text, newDocument, documentId, newParagraph,
              paragraphId, textLang, translit);
    }

    return null;
  }

  /**
   * Merges the word lines of each multiword token range into the range line and removes them.
   * Stops at the first missing id before allocating further entries in a range. A long
   * cursor permits an inclusive range ending at the largest integer without wrapping.
   *
   * @param lines The word lines of one sentence.
   * @return The lines with each range merged.
   * @throws InvalidFormatException Thrown if a range names a word id that has no line.
   */
  private List<ConlluWordLine> postProcessContractions(List<ConlluWordLine> lines)
      throws InvalidFormatException {


    // 1. Find contractions
    Map<String, Integer> index = new HashMap<>();
    Map<String, List<String>> contractions = new HashMap<>();
    List<String> linesToDelete = new ArrayList<>();

    for (int i = 0; i < lines.size(); i++) {
      ConlluWordLine line = lines.get(i);
      index.put(line.getId(), i);
    }
    for (ConlluWordLine line : lines) {
      if (line.getId().contains("-")) {
        List<String> expandedContractions = new ArrayList<>();
        int[] range = parseContractionRange(line.getId());
        int start = range[0];
        int end = range[1];
        for (long j = start; j <= end; j++) {
          String js = Long.toString(j);
          if (!index.containsKey(js)) {
            throw new InvalidFormatException("Multiword token " + line.getId()
                + " has no word line for id " + js);
          }
          expandedContractions.add(js);
          linesToDelete.add(js);
        }
        contractions.put(line.getId(), expandedContractions);
      }
    }

    // 2. Merge annotation
    for (Entry<String, List<String>> entry : contractions.entrySet()) {
      final String contractionId = entry.getKey();
      final List<String> expandedContractions = entry.getValue();
      int contractionIndex = index.get(contractionId);
      ConlluWordLine contraction = lines.get(contractionIndex);
      List<ConlluWordLine> expandedParts = new ArrayList<>();
      for (String id : expandedContractions) {
        expandedParts.add(lines.get(index.get(id)));
      }
      ConlluWordLine merged = mergeAnnotation(contraction, expandedParts);
      lines.set(contractionIndex, merged);
    }

    // 3. Delete the expanded parts
    for (int i = linesToDelete.size() - 1; i >= 0; i--) {
      lines.remove(index.get(linesToDelete.get(i)).intValue());
    }
    return lines;
  }

  /**
   * Merges token level annotations.
   *
   * @param contraction The line that receives the annotation.
   * @param expandedParts The lines to get annotation.
   *
   * @return The {@link ConlluWordLine merged line}.
   */
  private ConlluWordLine mergeAnnotation(ConlluWordLine contraction,
                                         List<ConlluWordLine> expandedParts) {
    String id = contraction.getId();
    String form = contraction.getForm();
    String lemma = expandedParts.stream()
        .filter(p -> !"_".equals(p.getLemma()))
        .map(ConlluWordLine::getLemma)
        .collect(Collectors.joining("+"));

    String uPosTag = expandedParts.stream()
        .filter(p -> !"_".equals(p.getPosTag(ConlluTagset.U)))
        .map(p -> p.getPosTag(ConlluTagset.U))
        .collect(Collectors.joining("+"));

    String xPosTag = expandedParts.stream()
        .filter(p -> !"_".equals(p.getPosTag(ConlluTagset.X)))
        .map(p -> p.getPosTag(ConlluTagset.X))
        .collect(Collectors.joining("+"));

    String feats = expandedParts.stream()
        .filter(p -> !"_".equals(p.getFeats()))
        .map(ConlluWordLine::getFeats)
        .collect(Collectors.joining("+"));

    String head = contraction.getHead();
    String deprel = contraction.getDeprel();
    String deps = contraction.getDeps();
    String misc = contraction.getMisc();

    return new ConlluWordLine(id, form, lemma, uPosTag, xPosTag, feats,head, deprel, deps, misc);
  }

  private Map<Locale, String> addTextLang(String firstPart, String secondPart,
                                          Map<Locale, String> textLang) throws InvalidFormatException {
    String lang = extractTextLang(firstPart);
    if (!lang.isEmpty()) {
      textLang.put(Locale.of(lang), secondPart);
    }
    else {
      throw new InvalidFormatException(String.format("Locale language code is invalid: %s", lang));
    }
    return textLang;
  }

  /**
   * Parses a multiword token id of the form {@code start-end}, where both sides are ASCII
   * digit runs and the range does not run backwards.
   *
   * @param id The token id, which holds a hyphen.
   * @return The start and end of the range.
   * @throws InvalidFormatException If the id is not two digit runs joined by one hyphen, or if
   *         the end is less than the start.
   */
  static int[] parseContractionRange(String id) throws InvalidFormatException {
    int hyphen = id.indexOf('-');
    int startEnd = StringUtil.endOfAsciiDigits(id, 0);
    int endEnd = StringUtil.endOfAsciiDigits(id, hyphen + 1);
    if (hyphen < 1 || startEnd != hyphen || endEnd == hyphen + 1 || endEnd != id.length()) {
      throw new InvalidFormatException(INVALID_MULTIWORD_ID + id);
    }
    int start;
    int end;
    try {
      start = Integer.parseInt(id, 0, hyphen, 10);
      end = Integer.parseInt(id, hyphen + 1, id.length(), 10);
    } catch (NumberFormatException e) {
      throw new InvalidFormatException(INVALID_MULTIWORD_ID + id, e);
    }
    if (end < start) {
      throw new InvalidFormatException("Multiword token id runs backwards: " + id);
    }
    return new int[] {start, end};
  }

  /**
   * Extracts the language code from a {@code text_xx} or {@code text_xxx} comment key: the two
   * or three ASCII lowercase letters, preferring three, after the first {@code text_} that at
   * least two follow.
   *
   * @param firstPart The comment key.
   * @return The language code, or an empty string if there is none.
   */
  private String extractTextLang(String firstPart) {
    int from = 0;
    while ((from = firstPart.indexOf(TEXT_LANG_PREFIX, from)) != -1) {
      int i = from + TEXT_LANG_PREFIX.length();
      int len = 0;
      while (len < 3 && i + len < firstPart.length()
          && firstPart.charAt(i + len) >= 'a' && firstPart.charAt(i + len) <= 'z') {
        len++;
      }
      if (len >= 2) {
        return firstPart.substring(i, i + len);
      }
      from++;
    }
    return "";
  }

  @Override
  public void close() throws IOException {
    sentenceStream.close();
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    sentenceStream.reset();
  }
}
