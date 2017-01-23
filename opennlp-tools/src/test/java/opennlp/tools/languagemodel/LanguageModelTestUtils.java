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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;

import org.junit.Ignore;

import opennlp.tools.ngram.NGramUtils;
import opennlp.tools.util.StringList;

/**
 * Utility class for language models tests
 */
@Ignore
public class LanguageModelTestUtils {

  private static final java.math.MathContext CONTEXT = MathContext.DECIMAL128;
  private static Random r = new Random();

  private static final char[] chars = new char[]{'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'};

  public static Collection<StringList> generateRandomVocabulary(int size) {
    Collection<StringList> vocabulary = new LinkedList<>();
    for (int i = 0; i < size; i++) {
      StringList sentence = generateRandomSentence();
      vocabulary.add(sentence);
    }
    return vocabulary;
  }

  public static StringList generateRandomSentence() {
    int dimension = r.nextInt(10) + 1;
    String[] sentence = new String[dimension];
    for (int j = 0; j < dimension; j++) {
      int i = r.nextInt(10);
      char c = chars[i];
      sentence[j] = c + "-" + c + "-" + c;
    }
    return new StringList(sentence);
  }

  public static double getPerplexity(LanguageModel lm, Collection<StringList> testSet, int ngramSize)
      throws ArithmeticException {
    BigDecimal perplexity = new BigDecimal(1d);

    for (StringList sentence : testSet) {
      for (StringList ngram : NGramUtils.getNGrams(sentence, ngramSize)) {
        double ngramProbability = lm.calculateProbability(ngram);
        perplexity = perplexity.multiply(new BigDecimal(1d).divide(
            new BigDecimal(ngramProbability), CONTEXT));
      }
    }

    double p = Math.log(perplexity.doubleValue());
    if (Double.isInfinite(p) || Double.isNaN(p)) {
      return Double.POSITIVE_INFINITY; // over/underflow -> too high perplexity
    } else {
      BigDecimal log = new BigDecimal(p);
      return Math.pow(Math.E, log.divide(new BigDecimal(testSet.size()), CONTEXT).doubleValue());
    }
  }

}
