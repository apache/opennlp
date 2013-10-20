/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.tools.entitylinker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import opennlp.tools.ngram.NGramGenerator;

/**
 *
 *Generates scores for string comparisons.
 */
public class FuzzyStringMatcher {
/**
 * Generates a score based on an overlap of nGrams between two strings using the DiceCoefficient technique.
 *
 * @param s1 first string
 * @param s2 second string
 * @param nGrams number of chars in each gram
 * @return
 */
  public static double getDiceCoefficient(String s1, String s2, int nGrams) {
    if (s1.equals("") || s1.equals("")) {
      return 0d;
    }
    List<String> s1Grams = NGramGenerator.generate(s1.toCharArray(), nGrams, "");
    List<String> s2Grams = NGramGenerator.generate(s2.toCharArray(), nGrams, "");

    Set<String> overlap = new HashSet<String>(s1Grams);
    overlap.retainAll(s2Grams);
    double totcombigrams = overlap.size();

    return (2 * totcombigrams) / (s1Grams.size() + s2Grams.size());
  }
}
