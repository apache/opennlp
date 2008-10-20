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

import opennlp.tools.util.FMeasureEvaluator;

/**
 * The {@link SentenceDetectorEvaluator} measures the performance of
 * the given {@link SentenceDetector} with the provided reference
 * {@link SentenceSample}s.
 * 
 * @see FMeasureEvaluator
 * @see SentenceDetector
 * @see SentenceSample
 */
public class SentenceDetectorEvaluator extends FMeasureEvaluator<SentenceSample> {

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
  
  private Integer[] convert(int starts[]) {
    Integer begins[] = new Integer[starts.length];
    
    for (int i = 0; i < starts.length; i++) {
      begins[i] = new Integer(starts[i]);
    }
    
    return begins;
  }
  
  public void evaluateSample(SentenceSample sample) {
    
    // TODO: Discuss with Tom changing the interface from int to Span
    
//    int starts[] = sentenceDetector.sentPosDetect(sample.getDocument());
//    
//    precisionScore.add(FMeasureEvaluator.precision(
//        convert(sample.getSentences()), convert(starts)));
//    recallScore.add(FMeasureEvaluator.recall(
//        convert(sample.getSentences()), convert(starts)));
  }
}