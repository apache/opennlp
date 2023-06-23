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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import opennlp.tools.commons.Sample;

/**
 * Represents a lemmatized sentence.
 */
public class LemmaSample implements Sample {

  private static final long serialVersionUID = -7956607496164605337L;

  private final List<String> tokens;

  private final List<String> tags;

  private final List<String> lemmas;

  /**
   * Initializes a {@link LemmaSample} instance with the given parameters.
   *
   * @param tokens The tokens.
   * @param tags The postags.
   * @param lemmas The lemmas for {@code tokens}.
   */
  public LemmaSample(String[] tokens, String[] tags, String[] lemmas) {
    this(Arrays.asList(tokens), Arrays.asList(tags), Arrays.asList(lemmas));
  }

  /**
   * Initializes a {@link LemmaSample} instance with the given parameters.
   *
   * @param tokens The tokens.
   * @param tags The postags.
   * @param lemmas The lemmas for {@code tokens}.
   */
  public LemmaSample(List<String> tokens, List<String> tags, List<String> lemmas) {

    validateArguments(tokens.size(), tags.size(), lemmas.size());

    this.tokens = Collections.unmodifiableList(new ArrayList<>(tokens));
    this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
    this.lemmas = Collections.unmodifiableList(new ArrayList<>(lemmas));
  }

  /**
   * @return Retrieves the tokens of a {@link LemmaSample}.
   */
  public String[] getTokens() {
    return tokens.toArray(new String[0]);
  }

  /**
   * @return Retrieves the postags of a {@link LemmaSample}.
   */
  public String[] getTags() {
    return tags.toArray(new String[0]);
  }

  /**
   * @return Retrieves the lemmas of a {@link LemmaSample}.
   */
  public String[] getLemmas() {
    return lemmas.toArray(new String[0]);
  }

  private void validateArguments(int tokensSize, int tagsSize, int lemmasSize)
      throws IllegalArgumentException {
    if (tokensSize != tagsSize || tagsSize != lemmasSize) {
      throw new IllegalArgumentException(
          "All arrays must have the same length: " +
              "sentenceSize: " + tokensSize + ", tagsSize: " + tagsSize +
                  ", predsSize: " + lemmasSize + "!");
    }
  }

  @Override
  public String toString() {
    StringBuilder lemmaString = new StringBuilder();

    for (int ci = 0; ci < lemmas.size(); ci++) {
      lemmaString.append(tokens.get(ci)).append("\t").append(tags.get(ci))
           .append("\t").append(lemmas.get(ci)).append("\n");
    }
    return lemmaString.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(getTokens()), Arrays.hashCode(getTags()),
        Arrays.hashCode(getLemmas()));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof LemmaSample a) {

      return Arrays.equals(getTokens(), a.getTokens())
          && Arrays.equals(getTags(), a.getTags())
          && Arrays.equals(getLemmas(), a.getLemmas());
    }

    return false;
  }
}
