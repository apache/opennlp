/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.sentdetect;

import opennlp.tools.util.Evaluator;
import opennlp.tools.util.FMeasure;
import opennlp.tools.util.Span;

/**
 * The {@link SentenceDetectorEvaluator} measures the performance of
 * the given {@link SentenceDetector} with the provided reference
 * {@link SentenceSample}s.
 *
 * @see Evaluator
 * @see SentenceDetector
 * @see SentenceSample
 */
public class SentenceDetectorEvaluator extends Evaluator<SentenceSample> {

  private FMeasure fmeasure = new FMeasure();
  
  /**
   * The {@link SentenceDetector} used to predict sentences.
   */
  private SentenceDetector sentenceDetector;

  /**
   * Initializes the current instance.
   *
   * @param sentenceDetector
   */
  public SentenceDetectorEvaluator(SentenceDetector sentenceDetector) {
    this.sentenceDetector = sentenceDetector;
  }

  public void evaluateSample(SentenceSample sample) {

    Span starts[] = sentenceDetector.sentPosDetect(sample.getDocument());

    fmeasure.updateScores(sample.getSentences(), starts);
  }
  
  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
