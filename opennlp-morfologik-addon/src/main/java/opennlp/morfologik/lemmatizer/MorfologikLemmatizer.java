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

package opennlp.morfologik.lemmatizer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import morfologik.stemming.Dictionary;
import morfologik.stemming.DictionaryLookup;
import morfologik.stemming.WordData;

import opennlp.tools.lemmatizer.Lemmatizer;

/**
 * A {@link Lemmatizer} implementation based on Morfologik binary
 * dictionaries
 */
public class MorfologikLemmatizer implements Lemmatizer {

  private final Dictionary dictionary;

  /**
   * Initializes a {@link MorfologikLemmatizer} and related {@link Dictionary}
   * from the input tab separated dictionary.
   *
   * @param dictionaryPath The dictionary referenced via a valid, readable {@link Path}.
   *
   * @throws IOException Thrown if IO errors occurred while reading in from
   *                     {@code dictionaryPath}.
   */
  public MorfologikLemmatizer(Path dictionaryPath) throws IOException {
    this(Dictionary.read(dictionaryPath));
  }

  /**
   * Initializes a {@link MorfologikLemmatizer} and related {@link Dictionary}
   * from the input tab separated dictionary.
   *
   * @param dictionary The {@link Dictionary} to be used.
   */
  public MorfologikLemmatizer(Dictionary dictionary) {
    this.dictionary = dictionary;
  }

  private List<String> lemmatize(String word, String postag) {
    List<WordData> dictMap = new DictionaryLookup(dictionary).lookup(word.toLowerCase());
    Set<String> lemmas = new HashSet<>();
    for (WordData wordData : dictMap) {
      if (Objects.equals(postag, asString(wordData.getTag()))) {
        lemmas.add(asString(wordData.getStem()));
      }
    }
    return Collections.unmodifiableList(new ArrayList<>(lemmas));
  }

  private String asString(CharSequence tag) {
    if (tag == null) {
      return null;
    }
    return tag.toString();
  }

  @Override
  public String[] lemmatize(String[] toks, String[] tags) {
    String[] lemmas = new String[toks.length];
    for (int i = 0; i < toks.length; i++) {
      List<String> l = lemmatize(toks[i], tags[i]);
      if (l.size() > 0) {
        lemmas[i] = l.get(0);
      } else {
        lemmas[i] = null;
      }
    }
    return lemmas;
  }

  @Override
  public List<List<String>> lemmatize(List<String> toks, List<String> tags) {
    List<List<String>> lemmas = new ArrayList<>();
    for (int i = 0; i < toks.size(); i++) {
      lemmas.add(lemmatize(toks.get(i), tags.get(i)));
    }
    return lemmas;
  }
}
