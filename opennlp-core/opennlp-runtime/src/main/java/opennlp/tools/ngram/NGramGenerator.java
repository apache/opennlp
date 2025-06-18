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

import java.util.ArrayList;
import java.util.List;

/**
 * Generates an nGram, via an optional separator, and returns the grams as a list
 * of strings
 */
public class NGramGenerator {

  /**
   * Creates nGrams separated
   * by the separator param value i.e. {@code a,b,c,d} with {@code n = 3} and a
   * separator as {@code "-"} would return {@code a-b-c,b-c-d}.
   *
   * @param input     the input tokens the output ngrams will be derived from
   * @param n         the number of tokens as the sliding window
   * @param separator each string in each gram will be separated by this value if desired.
   *                  Pass in empty string if no separator is desired
   * @return The generated list of resulting nGrams.
   */
  public static List<String> generate(List<String> input, int n, String separator) {

    List<String> outGrams = new ArrayList<>();
    for (int i = 0; i < input.size() - (n - 1); i++) {
      final StringBuilder sb = new StringBuilder();
      
      for (int x = i; x < (n + i); x++) {
        sb.append(input.get(x));
        sb.append(separator);
      }
      String gram = sb.toString();
      gram = gram.substring(0, gram.lastIndexOf(separator));
      outGrams.add(gram);
      
    }
    return outGrams;
  }

  /**
   * Generates nGrams based on a {@code char[]} input.
   * @param input the array of chars to convert to nGram
   * @param n The number of grams (chars) that each output gram will consist of
   * @param separator each char in each gram will be separated by this value if desired.
   *                  Pass in empty string if no separator is desired
   * @return The generated list of resulting nGrams.
   */
  public static List<String> generate(char[] input, int n, String separator) {

    List<String> outGrams = new ArrayList<>();
    for (int i = 0; i < input.length - (n - 1); i++) {
      final StringBuilder sb = new StringBuilder();
     
      for (int x = i; x < (n + i); x++) {
        sb.append(input[x]);
        sb.append(separator);
      }
      String gram = sb.toString();
      gram = gram.substring(0, gram.lastIndexOf(separator));
      outGrams.add(gram);
    }
    return outGrams;
  }
}
