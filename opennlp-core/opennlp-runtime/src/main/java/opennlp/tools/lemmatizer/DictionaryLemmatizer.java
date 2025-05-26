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

package opennlp.tools.lemmatizer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Lemmatizer} implementation that works by simple dictionary lookup into
 * a {@link Map} built from a file containing, for each line:
 * <p>
 * {@code word\tabpostag\tablemma}.
 */
public class DictionaryLemmatizer implements Lemmatizer {

  /*
   * The hashmap containing the dictionary.
   */
  private final Map<List<String>, List<String>> dictMap = new HashMap<>();

  /**
   * Initializes a {@link DictionaryLemmatizer} and related {@link HashMap}
   * from the input tab separated dictionary.
   * <p>
   * The input file should have, for each line, {@code word\tabpostag\tablemma}.
   * Alternatively, if multiple lemmas are possible for each word-postag pair,
   * then the format should be {@code word\tab\postag\tablemma01#lemma02#lemma03}.
   *
   * @param dictionaryStream The dictionary referenced by an open {@link InputStream}.
   * @param charset The {@link Charset character encoding} of the dictionary.
   *
   * @throws IOException Thrown if IO errors occurred while reading in from
   *                     {@code dictionaryStream}.
   */
  public DictionaryLemmatizer(final InputStream dictionaryStream, Charset charset)
          throws IOException {
    init(dictionaryStream, charset);
  }

  /**
   * Initializes a {@link DictionaryLemmatizer} and related {@link HashMap}
   * from the input tab separated dictionary.
   * <p>
   * The input file should have, for each line, {@code word\tabpostag\tablemma}.
   * Alternatively, if multiple lemmas are possible for each word-postag pair,
   * then the format should be {@code word\tab\postag\tablemma01#lemma02#lemma03}.
   *
   * @param dictionaryStream The dictionary referenced by an open {@link InputStream}.
   *
   * @throws IOException Thrown if IO errors occurred while reading in from
   *                     {@code dictionaryStream}.
   */
  public DictionaryLemmatizer(final InputStream dictionaryStream) throws IOException {
    this(dictionaryStream, StandardCharsets.UTF_8);
  }

  /**
   * Initializes a {@link DictionaryLemmatizer} and related {@link HashMap}
   * from the input tab separated dictionary.
   * <p>
   * The input file should have, for each line, {@code word\tabpostag\tablemma}.
   * Alternatively, if multiple lemmas are possible for each word-postag pair,
   * then the format should be {@code word\tab\postag\tablemma01#lemma02#lemma03}.
   *
   * @param dictionaryFile The dictionary referenced by a valid, readable {@link File}.
   *
   * @throws IOException Thrown if IO errors occurred while reading in from
   *                     {@code dictionaryFile}.
   */
  public DictionaryLemmatizer(File dictionaryFile) throws IOException {
    this(dictionaryFile, StandardCharsets.UTF_8);
  }

  /**
   * Initializes a {@link DictionaryLemmatizer} and related {@link HashMap}
   * from the input tab separated dictionary.
   * <p>
   * The input file should have, for each line, {@code word\tabpostag\tablemma}.
   * Alternatively, if multiple lemmas are possible for each word-postag pair,
   * then the format should be {@code word\tab\postag\tablemma01#lemma02#lemma03}.
   *
   * @param dictionaryFile The dictionary referenced by a valid, readable {@link File}.
   * @param charset The {@link Charset character encoding} of the dictionary.
   *
   * @throws IOException Thrown if IO errors occurred while reading in from
   *                     {@code dictionaryFile}.
   */
  public DictionaryLemmatizer(File dictionaryFile, Charset charset) throws IOException {
    try (InputStream in = new BufferedInputStream(new FileInputStream(dictionaryFile))) {
      init(in, charset);
    }
  }

  /**
   * Initializes a {@link DictionaryLemmatizer} and related {@link HashMap}
   * from the input tab separated dictionary.
   * <p>
   * The input file should have, for each line, {@code word\tabpostag\tablemma}.
   * Alternatively, if multiple lemmas are possible for each word-postag pair,
   * then the format should be {@code word\tab\postag\tablemma01#lemma02#lemma03}.
   *
   * @param dictionaryPath The dictionary referenced via a valid, readable {@link Path}.
   *
   * @throws IOException Thrown if IO errors occurred while reading in from
   *                     {@code dictionaryPath}.
   */
  public DictionaryLemmatizer(Path dictionaryPath) throws IOException {
    init(Files.newInputStream(dictionaryPath), StandardCharsets.UTF_8);
  }

  private void init(InputStream dictionary, Charset charset) throws IOException {
    final BufferedReader breader = new BufferedReader(
        new InputStreamReader(dictionary, charset));
    String line;
    while ((line = breader.readLine()) != null) {
      final String[] elems = line.split("\t");
      final String[] lemmas = elems[2].split("#");
      this.dictMap.put(Arrays.asList(elems[0], elems[1]), Arrays.asList(lemmas));
    }
  }
  /**
   * @return Retrieves the {@link Map} containing the dictionary.
   */
  public Map<List<String>, List<String>> getDictMap() {
    return this.dictMap;
  }

  /**
   * @param word The surface form word.
   * @param postag The assigned postag.
   *               
   * @return Retrieves the dictionary keys (word and postag).
   */
  private List<String> getDictKeys(final String word, final String postag) {
    return new ArrayList<>(Arrays.asList(word.toLowerCase(), postag));
  }


  @Override
  public String[] lemmatize(final String[] tokens, final String[] postags) {
    List<String> lemmas = new ArrayList<>();
    for (int i = 0; i < tokens.length; i++) {
      lemmas.add(this.lemmatize(tokens[i], postags[i]));
    }
    return lemmas.toArray(new String[0]);
  }

  @Override
  public List<List<String>> lemmatize(final List<String> tokens, final List<String> posTags) {
    List<List<String>> allLemmas = new ArrayList<>();
    for (int i = 0; i < tokens.size(); i++) {
      allLemmas.add(this.getAllLemmas(tokens.get(i), posTags.get(i)));
    }
    return allLemmas;
  }

  /**
   * Lookup lemma in a dictionary. Outputs {@code "0"} if no lemma could be found
   * for the specified {@code word}.
   *
   * @param word The token to look up the lemma for.
   * @param postag The postag.
   *
   * @return The corresponding lemma, or {@code "0"} if no lemma for {@code word}
   *         could be found.
   */
  private String lemmatize(final String word, final String postag) {
    String lemma;
    final List<String> keys = this.getDictKeys(word, postag);
    // lookup lemma as value of the map
    final List<String> keyValues = this.dictMap.get(keys);
    if ( keyValues != null && !keyValues.isEmpty()) {
      lemma = keyValues.get(0);
    } else {
      lemma = "O";
    }
    return lemma;
  }

  /**
   * Lookup every lemma for a word,pos tag in a dictionary. Outputs {@code "0"} if no
   * lemmas could be found for the specified {@code word}.
   *
   * @param word The token to look up the lemma for.
   * @param postag The postag.
   *
   * @return A list of relevant lemmas.
   */
  private List<String> getAllLemmas(final String word, final String postag) {
    List<String> lemmasList = new ArrayList<>();
    final List<String> keys = this.getDictKeys(word, postag);
    // lookup lemma as value of the map
    final List<String> keyValues = this.dictMap.get(keys);
    if (keyValues != null && !keyValues.isEmpty()) {
      lemmasList.addAll(keyValues);
    } else {
      lemmasList.add("O");
    }
    return lemmasList;
  }
}
