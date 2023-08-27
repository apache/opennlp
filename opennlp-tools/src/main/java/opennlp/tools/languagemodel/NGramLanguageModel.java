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

package opennlp.tools.languagemodel;

import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.ngram.NGramModel;
import opennlp.tools.ngram.NGramUtils;
import opennlp.tools.util.StringList;

/**
 * A {@link LanguageModel} based on a {@link NGramModel} using Stupid Backoff to get
 * the probabilities of the ngrams.
 */
public class NGramLanguageModel extends NGramModel implements LanguageModel {

  private static final int DEFAULT_N = 3;

  private final int n;

  /**
   * Initializes an {@link NGramLanguageModel} with {@link #DEFAULT_N}.
   */
  public NGramLanguageModel() {
    this(DEFAULT_N);
  }

  /**
   * Initializes an {@link NGramLanguageModel} with the given {@code n} for the ngram size.
   *
   * @param n The size of the ngrams to be used. Must be greater than {@code 0}.
   *          
   * @throws IllegalArgumentException Thrown if one of the arguments was invalid.
   */
  public NGramLanguageModel(int n) {
    if (n <= 0) {
      throw new IllegalArgumentException("Parameter 'n' must be greater than 0.");
    }
    this.n = n;
  }

  /**
   * Initializes a {@link NGramLanguageModel} instance via a valid {@link InputStream}.
   *
   * @param in The {@link InputStream} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   * @throws IllegalArgumentException Thrown if one of the arguments was invalid.
   */
  public NGramLanguageModel(InputStream in) throws IOException {
    this(in, DEFAULT_N);
  }

  /**
   * Initializes a {@link NGramLanguageModel} instance via a valid {@link InputStream}.
   *
   * @param in The {@link InputStream} used for loading the model.
   * @param n The size of the ngrams to be used. Must be greater than {@code 0}.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   * @throws IllegalArgumentException Thrown if one of the arguments was invalid.
   */
  public NGramLanguageModel(InputStream in, int n) throws IOException {
    super(in);
    if (n <= 0) {
      throw new IllegalArgumentException("Parameter 'n' must be greater than 0.");
    }
    this.n = n;
  }

  /**
   * Adds further tokens.
   *
   * @param tokens Text elements to add to the {@link NGramLanguageModel}.
   */
  public void add(String... tokens) {
    add(new StringList(tokens), 1, n);
  }

  @Override
  public double calculateProbability(String... tokens) {
    double probability = 0d;
    if (size() > 0) {
      for (String[] ngram : NGramUtils.getNGrams(tokens, n)) {
        double score = stupidBackoff(new StringList(ngram));
        probability += StrictMath.log(score);
        if (Double.isNaN(probability)) {
          probability = 0d;
          break;
        }
      }
      probability = StrictMath.exp(probability);
    }
    return probability;
  }

  private double calculateProbability(StringList tokens) {
    double probability = 0d;
    if (size() > 0) {
      for (StringList ngram : NGramUtils.getNGrams(tokens, n)) {
        double score = stupidBackoff(ngram);
        probability += StrictMath.log(score);
        if (Double.isNaN(probability)) {
          probability = 0d;
          break;
        }
      }
      probability = StrictMath.exp(probability);
    }
    return probability;
  }

  @Override
  public String[] predictNextTokens(String... tokens) {
    double maxProb = Double.NEGATIVE_INFINITY;
    String[] token = null;

    for (StringList ngram : this) {
      String[] sequence = new String[ngram.size() + tokens.length];
      System.arraycopy(tokens, 0, sequence, 0, tokens.length);
      for (int i = 0; i < ngram.size(); i++) {
        sequence[i + tokens.length] = ngram.getToken(i);
      }
      double v = calculateProbability(sequence);
      if (v > maxProb) {
        maxProb = v;
        token = new String[ngram.size()];
        for (int i = 0; i < ngram.size(); i++) {
          token[i] = ngram.getToken(i);
        }
      }
    }

    return token;
  }

  private double stupidBackoff(StringList ngram) {
    int count = getCount(ngram);
    StringList nMinusOneToken = NGramUtils.getNMinusOneTokenFirst(ngram);
    if (nMinusOneToken == null || nMinusOneToken.size() == 0) {
      return (double) count / (double) size();
    } else if (count > 0) {
      double countM1 = getCount(nMinusOneToken);
      if (countM1 == 0d) {
        countM1 = size(); // to avoid Infinite if n-1grams do not exist
      }
      return (double) count / countM1;
    } else {
      return 0.4 * stupidBackoff(NGramUtils.getNMinusOneTokenLast(ngram));
    }

  }

}
