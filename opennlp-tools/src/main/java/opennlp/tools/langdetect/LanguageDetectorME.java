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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Implements learnable Language Detector
 */
public class LanguageDetectorME implements LanguageDetector {

  private LanguageDetectorModel model;
  private LanguageDetectorContextGenerator mContextGenerator;

  /**
   * Initializes the current instance with a language detector model. Default feature
   * generation is used.
   *
   * @param model the language detector model
   */
  public LanguageDetectorME(LanguageDetectorModel model) {
    this.model = model;
    this.mContextGenerator = model.getFactory().getContextGenerator();
  }

  @Override
  public Language[] predictLanguages(CharSequence content) {
    double[] eval = model.getMaxentModel().eval(mContextGenerator.getContext(content.toString()));
    Language[] arr = new Language[eval.length];
    for (int i = 0; i < eval.length; i++) {
      arr[i] = new Language(model.getMaxentModel().getOutcome(i), eval[i]);
    }

    Arrays.sort(arr, (o1, o2) -> Double.compare(o2.getConfidence(), o1.getConfidence()));
    return arr;
  }

  @Override
  public Language predictLanguage(CharSequence content) {
    return predictLanguages(content)[0];
  }

  @Override
  public String[] getSupportedLanguages() {
    int numberLanguages = model.getMaxentModel().getNumOutcomes();
    String[] languages = new String[numberLanguages];
    for (int i = 0; i < numberLanguages; i++) {
      languages[i] = model.getMaxentModel().getOutcome(i);
    }
    return languages;
  }


  public static LanguageDetectorModel train(ObjectStream<LanguageSample> samples,
                                            TrainingParameters mlParams,
                                            LanguageDetectorFactory factory)
      throws IOException {

    Map<String, String> manifestInfoEntries = new HashMap<>();

    mlParams.putIfAbsent(AbstractEventTrainer.DATA_INDEXER_PARAM,
        AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE);

    EventTrainer trainer = TrainerFactory.getEventTrainer(
        mlParams, manifestInfoEntries);

    MaxentModel model = trainer.train(
        new LanguageDetectorEventStream(samples, factory.getContextGenerator()));

    return new LanguageDetectorModel(model, manifestInfoEntries, factory);
  }
}
