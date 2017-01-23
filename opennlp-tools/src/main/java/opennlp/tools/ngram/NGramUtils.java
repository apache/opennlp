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

package opennlp.tools.ngram;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

import opennlp.tools.util.StringList;

/**
 * Utility class for ngrams.
 * Some methods apply specifically to certain 'n' values, for e.g. tri/bi/uni-grams.
 */
public class NGramUtils {

  /**
   * calculate the probability of a ngram in a vocabulary using Laplace smoothing algorithm
   *
   * @param ngram the ngram to get the probability for
   * @param set   the vocabulary
   * @param size  the size of the vocabulary
   * @param k     the smoothing factor
   * @return the Laplace smoothing probability
   * @see <a href="https://en.wikipedia.org/wiki/Additive_smoothing">Additive Smoothing</a>
   */
  public static double calculateLaplaceSmoothingProbability(StringList ngram,
      Iterable<StringList> set, int size, Double k) {
    return (count(ngram, set) + k) / (count(getNMinusOneTokenFirst(ngram), set) + k * 1);
  }

  /**
   * calculate the probability of a unigram in a vocabulary using maximum likelihood estimation
   *
   * @param word the only word in the unigram
   * @param set  the vocabulary
   * @return the maximum likelihood probability
   */
  public static double calculateUnigramMLProbability(String word, Collection<StringList> set) {
    double vocSize = 0d;
    for (StringList s : set) {
      vocSize += s.size();
    }
    return count(new StringList(word), set) / vocSize;
  }

  /**
   * calculate the probability of a bigram in a vocabulary using maximum likelihood estimation
   *
   * @param x0  first word in the bigram
   * @param x1  second word in the bigram
   * @param set the vocabulary
   * @return the maximum likelihood probability
   */
  public static double calculateBigramMLProbability(String x0, String x1, Collection<StringList> set) {
    return calculateNgramMLProbability(new StringList(x0, x1), set);
  }

  /**
   * calculate the probability of a trigram in a vocabulary using maximum likelihood estimation
   *
   * @param x0  first word in the trigram
   * @param x1  second word in the trigram
   * @param x2  third word in the trigram
   * @param set the vocabulary
   * @return the maximum likelihood probability
   */
  public static double calculateTrigramMLProbability(String x0, String x1, String x2,
                                                     Iterable<StringList> set) {
    return calculateNgramMLProbability(new StringList(x0, x1, x2), set);
  }

  /**
   * calculate the probability of a ngram in a vocabulary using maximum likelihood estimation
   *
   * @param ngram a ngram
   * @param set   the vocabulary
   * @return the maximum likelihood probability
   */
  public static double calculateNgramMLProbability(StringList ngram, Iterable<StringList> set) {
    StringList ngramMinusOne = getNMinusOneTokenFirst(ngram);
    return count(ngram, set) / count(ngramMinusOne, set);
  }

  /**
   * calculate the probability of a bigram in a vocabulary using prior Laplace smoothing algorithm
   *
   * @param x0  the first word in the bigram
   * @param x1  the second word in the bigram
   * @param set the vocabulary
   * @param k   the smoothing factor
   * @return the prior Laplace smoothiing probability
   */
  public static double calculateBigramPriorSmoothingProbability(String x0, String x1,
                                                                Collection<StringList> set, Double k) {
    return (count(new StringList(x0, x1), set) + k * calculateUnigramMLProbability(x1, set)) /
        (count(new StringList(x0), set) + k * set.size());
  }

  /**
   * calculate the probability of a trigram in a vocabulary using a linear interpolation algorithm
   *
   * @param x0      the first word in the trigram
   * @param x1      the second word in the trigram
   * @param x2      the third word in the trigram
   * @param set     the vocabulary
   * @param lambda1 trigram interpolation factor
   * @param lambda2 bigram interpolation factor
   * @param lambda3 unigram interpolation factor
   * @return the linear interpolation probability
   */
  public static double calculateTrigramLinearInterpolationProbability(String x0, String x1,
                                                                      String x2, Collection<StringList> set,
      Double lambda1, Double lambda2, Double lambda3) {
    assert lambda1 + lambda2 + lambda3 == 1 : "lambdas sum should be equals to 1";
    assert lambda1 > 0 && lambda2 > 0 && lambda3 > 0 : "lambdas should all be greater than 0";

    return lambda1 * calculateTrigramMLProbability(x0, x1, x2, set) +
        lambda2 * calculateBigramMLProbability(x1, x2, set) +
        lambda3 * calculateUnigramMLProbability(x2, set);

  }

  /**
   * calculate the probability of a ngram in a vocabulary using the missing probability mass algorithm
   *
   * @param ngram    the ngram
   * @param discount discount factor
   * @param set      the vocabulary
   * @return the probability
   */
  public static double calculateMissingNgramProbabilityMass(StringList ngram, Double discount,
                                                            Iterable<StringList> set) {
    Double missingMass = 0d;
    Double countWord = count(ngram, set);
    for (String word : flatSet(set)) {
      missingMass += (count(getNPlusOneNgram(ngram, word), set) - discount) / countWord;
    }
    return 1 - missingMass;
  }

  /**
   * get the (n-1)th ngram of a given ngram, that is the same ngram except the last word in the ngram
   *
   * @param ngram a ngram
   * @return a ngram
   */
  public static StringList getNMinusOneTokenFirst(StringList ngram) {
    String[] tokens = new String[ngram.size() - 1];
    for (int i = 0; i < ngram.size() - 1; i++) {
      tokens[i] = ngram.getToken(i);
    }
    return tokens.length > 0 ? new StringList(tokens) : null;
  }

  /**
   * get the (n-1)th ngram of a given ngram, that is the same ngram except the first word in the ngram
   *
   * @param ngram a ngram
   * @return a ngram
   */
  public static StringList getNMinusOneTokenLast(StringList ngram) {
    String[] tokens = new String[ngram.size() - 1];
    for (int i = 1; i < ngram.size(); i++) {
      tokens[i - 1] = ngram.getToken(i);
    }
    return tokens.length > 0 ? new StringList(tokens) : null;
  }

  private static StringList getNPlusOneNgram(StringList ngram, String word) {
    String[] tokens = new String[ngram.size() + 1];
    for (int i = 0; i < ngram.size(); i++) {
      tokens[i] = ngram.getToken(i);
    }
    tokens[tokens.length - 1] = word;
    return new StringList(tokens);
  }

  private static Double count(StringList ngram, Iterable<StringList> sentences) {
    Double count = 0d;
    for (StringList sentence : sentences) {
      int idx0 = indexOf(sentence, ngram.getToken(0));
      if (idx0 >= 0 && sentence.size() >= idx0 + ngram.size()) {
        boolean match = true;
        for (int i = 1; i < ngram.size(); i++) {
          String sentenceToken = sentence.getToken(idx0 + i);
          String ngramToken = ngram.getToken(i);
          match &= sentenceToken.equals(ngramToken);
        }
        if (match) {
          count++;
        }
      }
    }
    return count;
  }

  private static int indexOf(StringList sentence, String token) {
    for (int i = 0; i < sentence.size(); i++) {
      if (token.equals(sentence.getToken(i))) {
        return i;
      }
    }
    return -1;
  }

  private static Collection<String> flatSet(Iterable<StringList> set) {
    Collection<String> flatSet = new HashSet<>();
    for (StringList sentence : set) {
      for (String word : sentence) {
        flatSet.add(word);
      }
    }
    return flatSet;
  }

  /**
   * get the ngrams of dimension n of a certain input sequence of tokens
   *
   * @param sequence a sequence of tokens
   * @param size     the size of the resulting ngrmams
   * @return all the possible ngrams of the given size derivable from the input sequence
   */
  public static Collection<StringList> getNGrams(StringList sequence, int size) {
    Collection<StringList> ngrams = new LinkedList<>();
    if (size == -1 || size >= sequence.size()) {
      ngrams.add(sequence);
    } else {
      String[] ngram = new String[size];
      for (int i = 0; i < sequence.size() - size + 1; i++) {
        ngram[0] = sequence.getToken(i);
        for (int j = 1; j < size; j++) {
          ngram[j] = sequence.getToken(i + j);
        }
        ngrams.add(new StringList(ngram));
      }
    }

    return ngrams;
  }
}
