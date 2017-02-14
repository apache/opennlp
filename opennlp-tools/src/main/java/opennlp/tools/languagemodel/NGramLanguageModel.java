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
 * using Stupid Backoff to get the probabilities of the ngrams.
 */
public class NGramLanguageModel extends NGramModel implements LanguageModel {

  private static final int DEFAULT_N = 3;

  private final int n;

  public NGramLanguageModel() {
    this(DEFAULT_N);
  }

  public NGramLanguageModel(int n) {
    this.n = n;
  }

  public NGramLanguageModel(InputStream in) throws IOException {
    this(in, DEFAULT_N);
  }

  public NGramLanguageModel(InputStream in, int n)
      throws IOException {
    super(in);
    this.n = n;
  }

  @Override
  public double calculateProbability(StringList sample) {
    double probability = 0d;
    if (size() > 0) {
      for (StringList ngram : NGramUtils.getNGrams(sample, n)) {
        double score = stupidBackoff(ngram);
        probability += Math.log(score);
        if (Double.isNaN(probability)) {
          probability = 0d;
        }
      }
      probability = Math.exp(probability);
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
