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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * Generates Name Sample objects for a Brat Document object.
 */
public class BratNameSampleStream extends SegmenterObjectStream<BratDocument, NameSample> {

  private SentenceDetector sentDetector;
  private Tokenizer tokenizer;

  protected BratNameSampleStream(SentenceDetector sentDetector,
      Tokenizer tokenizer, ObjectStream<BratDocument> samples) {
    super(samples);

    this.sentDetector = sentDetector;
    this.tokenizer = tokenizer;
  }

  protected BratNameSampleStream(SentenceModel sentModel, TokenizerModel tokenModel,
      ObjectStream<BratDocument> samples) {
    super(samples);

    // TODO: We can pass in custom validators here ...
    this.sentDetector = new SentenceDetectorME(sentModel);
    this.tokenizer = new TokenizerME(tokenModel);
  }

  @Override
  protected List<NameSample> read(BratDocument sample) throws IOException {

    // Note: Some entities might not match sentence boundaries,
    // to be able to print warning a set of entities id must be maintained
    // to check if all entities have been used up after the matching is done

    Set<String> entityIdSet = new HashSet<>();

    for (BratAnnotation ann : sample.getAnnotations()) {
      if (ann instanceof SpanAnnotation) {
        entityIdSet.add(ann.getId());
      }
    }

    Span sentences[] = sentDetector.sentPosDetect(sample.getText());

    // TODO: Sentence breaks should be avoided inside name annotations
    // a) Merge two sentences, if an end/begin pair is part of a name annotation
    // b) Implement a custom sentence validator which can be injected into the SD

    // How could a custom validator be injected into an already instantiated sentence detector ?1
    // Via a set method ...
    // Via constructor ... probably best option, but a bit tricky to work with the SD interface then
    //


    // TODO: Token breaks should be enforced on name span boundaries
    // a) Just split tokens
    // b) Implement a custom token split validator which can be injected into the Tokenizer

    // Currently we are missing all

    List<NameSample> samples = new ArrayList<>(sentences.length);

    for (Span sentence : sentences) {

      String sentenceText = sentence.getCoveredText(
          sample.getText()).toString();

      Span tokens[] = tokenizer.tokenizePos(sentenceText);

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

        if (ann instanceof SpanAnnotation) {
          SpanAnnotation entity = (SpanAnnotation) ann;

          Span entitySpan = entity.getSpan();

          if (sentence.contains(entitySpan)) {
            entityIdSet.remove(ann.getId());

            entitySpan = entitySpan.trim(sample.getText());

            Integer nameBeginIndex = tokenIndexMap.get(-entitySpan.getStart());
            Integer nameEndIndex = tokenIndexMap.get(entitySpan.getEnd());

            if (nameBeginIndex != null && nameEndIndex != null) {
              names.add(new Span(nameBeginIndex, nameEndIndex, entity.getType()));
            }
            else {
              System.err.println("Dropped entity " + entity.getId() + " (" + entitySpan.getCoveredText(sample.getText()) + ") " + " in document " +
                  sample.getId() + ", it is not matching tokenization!");
            }
          }
        }
      }

      samples.add(new NameSample(sample.getId(), Span.spansToStrings(tokens, sentenceText),
          names.toArray(new Span[names.size()]), null, samples.size() == 0));
    }

    for (String id : entityIdSet) {
      System.err.println("Dropped entity " + id + " in document " +
          sample.getId() + ", is not matching sentence segmentation!");
    }

    return samples;
  }
}
