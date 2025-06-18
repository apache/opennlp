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

package opennlp.tools.formats.brat;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

public class BratDocumentParser {

  private static final Logger logger = LoggerFactory.getLogger(BratDocumentParser.class);

  private final SentenceDetector sentDetector;
  private final Tokenizer tokenizer;
  private final Set<String> nameTypes;

  public BratDocumentParser(SentenceDetector sentenceDetector, Tokenizer tokenizer) {
    this(sentenceDetector, tokenizer, null);
  }

  public BratDocumentParser(SentenceDetector sentenceDetector, Tokenizer tokenizer,
                            Set<String> nameTypes) {
    if (nameTypes != null && nameTypes.size() == 0) {
      throw new IllegalArgumentException("nameTypes should be null or have one or more elements");
    }
    this.sentDetector = sentenceDetector;
    this.tokenizer = tokenizer;
    this.nameTypes = nameTypes;
  }

  public List<NameSample> parse(BratDocument sample) {
    // Note: Some entities might not match sentence boundaries,
    // to be able to print warning a set of entities id must be maintained
    // to check if all entities have been used up after the matching is done

    Set<String> entityIdSet = new HashSet<>();
    Map<Integer, Span> coveredIndexes = new HashMap<>();

    for (BratAnnotation ann : sample.getAnnotations()) {
      if (isSpanAnnotation(ann)) {
        entityIdSet.add(ann.getId());

        for (Span span : ((SpanAnnotation) ann).getSpans()) {
          for (int i = span.getStart(); i < span.getEnd(); i++) {
            coveredIndexes.put(i, span);
          }
        }
      }
    }

    // Map spans to tokens, and merge fragments based on token

    //


    // Detect sentence and correct sentence spans assuming no split can be inside a name annotation
    List<Span> sentences = new ArrayList<>();
    for (Span sentence : sentDetector.sentPosDetect(sample.getText())) {
      Span conflictingName = coveredIndexes.get(sentence.getStart());

      if (sentences.size() > 0 && conflictingName != null &&
          conflictingName.getStart() < sentence.getStart()) {
        Span lastSentence = sentences.remove(sentences.size() - 1);
        sentences.add(new Span(lastSentence.getStart(), sentence.getEnd()));

        logger.info("Correcting sentence segmentation in document {}", sample.getId());
      }
      else {
        sentences.add(sentence);
      }
    }

    // TODO: Token breaks should be enforced on name span boundaries
    // a) Just split tokens
    // b) Implement a custom token split validator which can be injected into the Tokenizer

    // Currently we are missing all

    List<NameSample> samples = new ArrayList<>(sentences.size());

    for (Span sentence : sentences) {

      String sentenceText = sentence.getCoveredText(
          sample.getText()).toString();

      Span[] tokens = tokenizer.tokenizePos(sentenceText);

      // Note:
      // A begin and end token index can be identical, but map to different
      // tokens, to distinguish between between the two begin indexes are
      // stored with a negative sign, and end indexes are stored with a positive sign
      // in the tokenIndexMap.
      // The tokenIndexMap maps to the sentence local token index.

      Map<Integer, Integer> tokenIndexMap = new HashMap<>();

      for (int i = 0; i < tokens.length; i++) {
        tokenIndexMap.put(-(sentence.getStart() + tokens[i].getStart()), i);
        tokenIndexMap.put(sentence.getStart() + tokens[i].getEnd(), i + 1);
      }

      List<Span> names = new ArrayList<>();

      for (BratAnnotation ann : sample.getAnnotations()) {

        if (isSpanAnnotation(ann)) {
          SpanAnnotation entity = (SpanAnnotation) ann;

          List<Span> mappedFragments = new ArrayList<>();

          for (Span entitySpan : entity.getSpans()) {
            if (sentence.contains(entitySpan)) {
              entityIdSet.remove(ann.getId());

              entitySpan = entitySpan.trim(sample.getText());

              Integer nameBeginIndex = tokenIndexMap.get(-entitySpan.getStart());
              Integer nameEndIndex = tokenIndexMap.get(entitySpan.getEnd());

              if (nameBeginIndex != null && nameEndIndex != null) {
                mappedFragments.add(new Span(nameBeginIndex, nameEndIndex, entity.getType()));
              } else {
                logger.warn("Dropped entity {} ({}) in document {} as it is not matching " +
                        "tokenization!", entity.getId(),
                    entitySpan.getCoveredText(sample.getText()), sample.getId());
              }
            }
          }

          Collections.sort(mappedFragments);

          for (int i = 1; i < mappedFragments.size(); i++) {
            if (mappedFragments.get(i - 1).getEnd() ==
                mappedFragments.get(i).getStart()) {
              mappedFragments.set(i, new Span(mappedFragments.get(i - 1).getStart(),
                  mappedFragments.get(i).getEnd(), mappedFragments.get(i).getType()));
              mappedFragments.set(i - 1, null);
            }
          }

          for (Span span : mappedFragments) {
            if (span != null ) {
              names.add(span);
            }
          }
        }
      }

      samples.add(new NameSample(sample.getId(), Span.spansToStrings(tokens, sentenceText),
          names.toArray(new Span[0]), null, samples.size() == 0));
    }

    for (String id : entityIdSet) {
      logger.warn("Dropped entity {} in document {}"
              + ", is not matching sentence segmentation!", id, sample.getId());
    }

    return samples;
  }

  private boolean isSpanAnnotation(BratAnnotation ann) {
    return ann instanceof SpanAnnotation && (nameTypes == null || nameTypes.contains(ann.getType()));
  }
}

