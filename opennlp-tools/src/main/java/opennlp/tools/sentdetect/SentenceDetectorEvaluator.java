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

package opennlp.tools.sentdetect;

import opennlp.tools.util.Span;
import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.FMeasure;

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
   * @param listeners evaluation sample listeners
   */
  public SentenceDetectorEvaluator(SentenceDetector sentenceDetector,
                                   SentenceDetectorEvaluationMonitor... listeners) {
    super(listeners);
    this.sentenceDetector = sentenceDetector;
  }

  private Span[] trimSpans(String document, Span spans[]) {
    Span trimedSpans[] = new Span[spans.length];

    for (int i = 0; i < spans.length; i++) {
      trimedSpans[i] = spans[i].trim(document);
    }

    return trimedSpans;
  }

  @Override
  protected SentenceSample processSample(SentenceSample sample) {
    Span predictions[] =
        trimSpans(sample.getDocument(), sentenceDetector.sentPosDetect(sample.getDocument()));
    Span[] references = trimSpans(sample.getDocument(), sample.getSentences());

    fmeasure.updateScores(references, predictions);

    return new SentenceSample(sample.getDocument(), predictions);
  }

  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
