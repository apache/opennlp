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
 * A {@link opennlp.tools.languagemodel.LanguageModel} based on a {@link opennlp.tools.ngram.NGramModel}
 * using Laplace smoothing probability estimation to get the probabilities of the ngrams.
 * See also {@link NGramUtils#calculateLaplaceSmoothingProbability(
 *opennlp.tools.util.StringList, Iterable, int, Double)}.
 */
public class NGramLanguageModel extends NGramModel implements LanguageModel {

  private static final int DEFAULT_N = 3;
  private static final double DEFAULT_K = 1d;

  private final int n;
  private final double k;

  public NGramLanguageModel() {
    this(DEFAULT_N, DEFAULT_K);
  }

  public NGramLanguageModel(int n) {
    this(n, DEFAULT_K);
  }

  public NGramLanguageModel(double k) {
    this(DEFAULT_N, k);
  }

  public NGramLanguageModel(int n, double k) {
    this.n = n;
    this.k = k;
  }

  public NGramLanguageModel(InputStream in) throws IOException {
    this(in, DEFAULT_N, DEFAULT_K);
  }

  public NGramLanguageModel(InputStream in, double k) throws IOException {
    this(in, DEFAULT_N, k);
  }

  public NGramLanguageModel(InputStream in, int n) throws IOException {
    this(in, n, DEFAULT_K);
  }

  public NGramLanguageModel(InputStream in, int n, double k)
      throws IOException {
    super(in);
    this.n = n;
    this.k = k;
  }

  @Override
  public double calculateProbability(StringList sample) {
    double probability = 0d;
    if (size() > 0) {
      for (StringList ngram : NGramUtils.getNGrams(sample, n)) {
        StringList nMinusOneToken = NGramUtils
            .getNMinusOneTokenFirst(ngram);
        if (size() > 1000000) {
          // use stupid backoff
          probability += Math.log(
              getStupidBackoffProbability(ngram, nMinusOneToken));
        } else {
          // use laplace smoothing
          probability += Math.log(
              getLaplaceSmoothingProbability(ngram, nMinusOneToken));
        }
      }
      if (Double.isNaN(probability)) {
        probability = 0d;
      } else if (probability != 0) {
        probability = Math.exp(probability);
      }

    }
    return probability;
  }

  @Override
  public StringList predictNextTokens(StringList tokens) {
    double maxProb = Double.NEGATIVE_INFINITY;
    StringList token = null;

    for (StringList ngram : this) {
      String[] sequence = new String[ngram.size() + tokens.size()];
      for (int i = 0; i < tokens.size(); i++) {
        sequence[i] = tokens.getToken(i);
      }
      for (int i = 0; i < ngram.size(); i++) {
        sequence[i + tokens.size()] = ngram.getToken(i);
      }
      StringList sample = new StringList(sequence);
      double v = calculateProbability(sample);
      if (v > maxProb) {
        maxProb = v;
        token = ngram;
      }
    }

    return token;
  }

  private double getLaplaceSmoothingProbability(StringList ngram,
                                                StringList nMinusOneToken) {
    return (getCount(ngram) + k) / (getCount(nMinusOneToken) + k * size());
  }

  private double getStupidBackoffProbability(StringList ngram,
                                             StringList nMinusOneToken) {
    int count = getCount(ngram);
    if (nMinusOneToken == null || nMinusOneToken.size() == 0) {
      return count / size();
    } else if (count > 0) {
      return ((double) count) / ((double) getCount(
          nMinusOneToken)); // maximum likelihood probability
    } else {
      StringList nextNgram = NGramUtils.getNMinusOneTokenLast(ngram);
      return 0.4d * getStupidBackoffProbability(nextNgram,
          NGramUtils.getNMinusOneTokenFirst(nextNgram));
    }
  }

}
