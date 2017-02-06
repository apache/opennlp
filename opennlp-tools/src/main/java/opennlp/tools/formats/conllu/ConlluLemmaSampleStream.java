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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.lemmatizer.LemmaSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

public class ConlluLemmaSampleStream extends FilterObjectStream<ConlluSentence, LemmaSample> {

  private final ConlluTagset tagset;

  ConlluLemmaSampleStream(ObjectStream<ConlluSentence> samples, ConlluTagset tagset) {
    super(samples);
    this.tagset = tagset;
  }

  @Override
  public LemmaSample read() throws IOException {
    ConlluSentence sentence = samples.read();

    if (sentence != null) {
      List<String> tokens = new ArrayList<>();
      List<String> tags = new ArrayList<>();
      List<String> lemmas = new ArrayList<>();

      for (ConlluWordLine line : sentence.getWordLines()) {
        tokens.add(line.getForm());
        tags.add(line.getPosTag(tagset));
        lemmas.add(line.getLemma());
      }

      return new LemmaSample(tokens, tags, lemmas);
    }

    return null;
  }
}
