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

package opennlp.tools.langdetect;

import opennlp.tools.doccat.DocumentCategorizer;
import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.Mean;

/**
 * The {@link LanguageDetectorEvaluator} measures the performance of
 * the given {@link LanguageDetector} with the provided reference
 * {@link LanguageSample}s.
 *
 * @see LanguageDetector
 * @see LanguageSample
 */
public class LanguageDetectorEvaluator extends Evaluator<LanguageSample> {

  private LanguageDetector languageDetector;

  private Mean accuracy = new Mean();

  /**
   * Initializes the current instance.
   *
   * @param langDetect the language detector instance
   */
  public LanguageDetectorEvaluator(LanguageDetector langDetect,
                                   LanguageDetectorEvaluationMonitor ... listeners) {
    super(listeners);
    this.languageDetector = langDetect;
  }

  /**
   * Evaluates the given reference {@link LanguageSample} object.
   *
   * This is done by categorizing the document from the provided
   * {@link LanguageSample}. The detected language is then used
   * to calculate and update the score.
   *
   * @param sample the reference {@link LanguageSample}.
   */
  public LanguageSample processSample(LanguageSample sample) {

    CharSequence document = sample.getContext();

    Language predicted = languageDetector.predictLanguage(document);



    if (sample.getLanguage().getLang().equals(predicted.getLang())) {
      accuracy.add(1);
    }
    else {
      accuracy.add(0);
    }

    return new LanguageSample(predicted, sample.getContext());
  }

  /**
   * Retrieves the accuracy of provided {@link DocumentCategorizer}.
   *
   * accuracy = correctly categorized documents / total documents
   *
   * @return the accuracy
   */
  public double getAccuracy() {
    return accuracy.mean();
  }

  public long getDocumentCount() {
    return accuracy.count();
  }

  /**
   * Represents this objects as human readable {@link String}.
   */
  @Override
  public String toString() {
    return "Accuracy: " + accuracy.mean() + "\n" +
        "Number of documents: " + accuracy.count();
  }
}
