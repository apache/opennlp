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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * The CoNNL-U Format is specified
 * <a href="http://universaldependencies.org/format.html">here</a>.
 */
public class ConlluStream implements ObjectStream<ConlluSentence> {
  private final ObjectStream<String> sentenceStream;

  private static final Pattern regex = Pattern.compile("text_([a-z]{2,3})");

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

            if (firstPart.startsWith("text_")) {
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

  private List<ConlluWordLine> postProcessContractions(List<ConlluWordLine> lines) {


    // 1. Find contractions
    Map<String, Integer> index = new HashMap<>();
    Map<String, List<String>> contractions = new HashMap<>();
    List<String> linesToDelete = new ArrayList<>();

    for (int i = 0; i < lines.size(); i++) {
      ConlluWordLine line = lines.get(i);
      index.put(line.getId(), i);
      if (line.getId().contains("-")) {
        List<String> expandedContractions = new ArrayList<>();
        String[] ids = line.getId().split("-");
        int start = Integer.parseInt(ids[0]);
        int end = Integer.parseInt(ids[1]);
        for (int j = start; j <= end; j++) {
          String js = Integer.toString(j);
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
    String lang = "";
    try {
      Matcher regexMatcher = regex.matcher(firstPart);
      if (regexMatcher.find()) {
        lang = regexMatcher.group(1);
      }
    } catch (PatternSyntaxException e) {
      throw new InvalidFormatException(e);
    }
    if (!lang.isEmpty()) {
      textLang.put(new Locale(lang), secondPart);
    }
    else {
      throw new InvalidFormatException(String.format("Locale language code is invalid: %s", lang));
    }
    return textLang;
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
