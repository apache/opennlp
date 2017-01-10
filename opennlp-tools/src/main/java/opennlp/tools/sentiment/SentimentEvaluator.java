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

package opennlp.tools.sentiment;

import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.FMeasure;

/**
 * Class for performing evaluation on the Sentiment Analysis Parser.
 */
public class SentimentEvaluator extends Evaluator<SentimentSample> {

  private FMeasure fmeasure = new FMeasure();

  private SentimentME sentiment;

  /**
   * Constructor
   */
  public SentimentEvaluator(SentimentME sentiment,
      SentimentEvaluationMonitor... listeners) {
    super(listeners);
    this.sentiment = sentiment;
  }

  /**
   * Returns the short description of the tool
   *
   * @param reference
   *          the reference to the SentimentSample to be processed
   * @return the processed samples
   */
  @Override
  protected SentimentSample processSample(SentimentSample reference) {
    String prediction = sentiment.predict(reference.getSentence());
    String label = reference.getSentiment();

    fmeasure.updateScores(new String[] { label }, new String[] { prediction });

    return new SentimentSample(prediction, reference.getSentence());
  }

  /**
   * Returns the F-Measure
   *
   * @return the F-Measure
   */
  public FMeasure getFMeasure() {
    return fmeasure;
  }

}
