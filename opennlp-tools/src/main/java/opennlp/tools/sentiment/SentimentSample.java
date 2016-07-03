/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentiment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Class for holding text used for sentiment analysis.
 */
public class SentimentSample {

  private final String sentiment;
  private final List<String> sentence;

  /**
   * Initializes the current instance.
   *
   * @param sentiment
   *          training sentiment
   * @param sentence
   *          training sentence
   */
  public SentimentSample(String sentiment, String[] sentence) {
    if (sentiment == null) {
      throw new IllegalArgumentException("sentiment must not be null");
    }
    if (sentence == null) {
      throw new IllegalArgumentException("sentence must not be null");
    }

    this.sentiment = sentiment;
    this.sentence = Collections
        .unmodifiableList(new ArrayList<String>(Arrays.asList(sentence)));
  }

  /**
   * Returns the sentiment
   *
   * @return the sentiment
   */
  public String getSentiment() {
    return sentiment;
  }

  /**
   * Returns the sentence used
   *
   * @return the sentence
   */
  public String[] getSentence() {
    return sentence.toArray(new String[0]);
  }
}
