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

import java.util.List;

/**
 * Class for holding text used for sentiment analysis.
 */
public class SentimentSample {

  private final String sentiment;
  private final List<String> sentence;
  private final boolean isClearAdaptiveData;
  private final String id = null;

  /**
   * Instantiates a {@link SentimentSample} object.
   *
   * @param sentiment
   *          training sentiment
   * @param sentence
   *          training sentence
   */
  public SentimentSample(String sentiment, String[] sentence) {
    this(sentiment, sentence, true);
  }

  public SentimentSample(String sentiment, String[] sentence,
      boolean clearAdaptiveData) {
    if (sentiment == null) {
      throw new IllegalArgumentException("sentiment must not be null");
    }
    if (sentence == null) {
      throw new IllegalArgumentException("sentence must not be null");
    }

    this.sentiment = sentiment;
    this.sentence = List.of(sentence);
    this.isClearAdaptiveData = clearAdaptiveData;
  }

  /**
   * @return Returns the sentiment.
   */
  public String getSentiment() {
    return sentiment;
  }

  /**
   * @return Returns the sentence.
   */
  public String[] getSentence() {
    return sentence.toArray(new String[0]);
  }

  public String getId() {
    return id;
  }

  /**
   * @return Returns the value of isClearAdaptiveData, {@code true} or {@code false}.
   */
  public boolean isClearAdaptiveDataSet() {
    return isClearAdaptiveData;
  }

}
